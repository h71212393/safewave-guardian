package com.safewave.guardian

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.speech.tts.TextToSpeech
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * 앱을 닫아도 센서 상태를 계속 지켜보는 서비스.
 * 긴급이면 잠금 화면 위로 경보 화면을 띄우고, 경보음·진동·음성으로 알림.
 */
class WatchService : Service() {

    companion object {
        const val CH_WATCH = "watch"
        const val CH_ALARM = "alarm_v2"
        const val CH_GRACE = "grace"
        const val CH_NOTICE = "notice"
        const val N_NOTICE = 3
        const val N_WATCH = 1
        const val N_ALERT = 2
        const val ACTION_ACK = "com.safewave.guardian.ACK"
        const val ACTION_SILENCE = "com.safewave.guardian.SILENCE"

        fun start(ctx: Context) {
            runCatching { ContextCompat.startForegroundService(ctx, Intent(ctx, WatchService::class.java)) }
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, WatchService::class.java))
        }

        fun channels(ctx: Context) {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CH_WATCH, "지키는 중 표시", NotificationManager.IMPORTANCE_LOW).apply {
                description = "센서와 연결되어 있다는 표시입니다"
                setShowBadge(false)
            })
            nm.createNotificationChannel(NotificationChannel(CH_GRACE, "낙상 의심", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "넘어진 것 같을 때 미리 알려 드립니다"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 400, 200, 400)
            })
            nm.createNotificationChannel(NotificationChannel(CH_NOTICE, "공지사항", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "관리자가 새 공지사항을 올리면 알려 드립니다"
            })
            runCatching { nm.deleteNotificationChannel("alarm") }   // 예전 무음 채널 정리
            nm.createNotificationChannel(NotificationChannel(CH_ALARM, "긴급 낙상 경보", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "넘어지고 반응이 없을 때 울리는 경보입니다"
                // 소리·진동이 있어야 갤럭시 플립 커버 화면과 잠금 화면이 켜짐
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM) ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
                )
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 900, 500, 900, 500, 900)
                enableLights(true)
                lightColor = Color.RED
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setBypassDnd(true)
            })
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var stream: NodeStream? = null
    private var player: MediaPlayer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var wake: PowerManager.WakeLock? = null
    private var lastKey = ""
    private var alarmJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        channels(this)
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        ServiceCompat.startForeground(this, N_WATCH, watchNotification("센서와 연결하는 중이에요"), type)

        tts = TextToSpeech(this) { st ->
            ttsReady = st == TextToSpeech.SUCCESS
            if (ttsReady) runCatching { tts?.language = Locale.KOREAN; tts?.setSpeechRate(0.9f) }
        }

        stream = NodeStream(this, Store::onStreamEvent, Store::setConnection).also { it.start() }

        // 상태가 바뀔 때마다 알림·경보 처리
        scope.launch {
            combine(Store.live, Store.testAlarm, Store.silenced) { l, t, s -> Triple(l, t, s) }
                .collect { (l, t, s) -> onState(l, t, s) }
        }
        // 2초마다 연결 끊김 여부 다시 계산
        scope.launch { while (isActive) { Store.recompute(); delay(2_000) } }
        // 대상자 정보는 5분마다
        scope.launch { while (isActive) { Store.refreshShared(this@WatchService); checkNotice(); delay(5 * 60_000L) } }
        // 50분마다 새 토큰으로 다시 연결 (토큰은 1시간짜리)
        scope.launch { while (isActive) { delay(50 * 60_000L); stream?.reconnect() } }
        // 1분마다 권한 확인 (관리자가 이용 중지하면 멈춤)
        scope.launch {
            while (isActive) {
                delay(60_000)
                val g = Store.loadMe(this@WatchService)
                if (g == Gate.LOGGED_OUT || g == Gate.KICKED || g == Gate.REJECTED || g == Gate.PENDING) { stopSelf(); break }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_ACK -> scope.launch {
                if (Store.testAlarm.value) Store.testAlarm.value = false else Store.ack(this@WatchService)
            }
            ACTION_SILENCE -> Store.silenced.value = true
        }
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        stream?.stop()
        stopAlarm()
        runCatching { tts?.shutdown() }
        getSystemService(NotificationManager::class.java).cancel(N_ALERT)
        super.onDestroy()
    }

    // ---------------- 상태 처리 ----------------
    private fun name() = Store.profile.value.displayName

    private fun onState(l: Live, test: Boolean, silenced: Boolean) {
        val nm = getSystemService(NotificationManager::class.java)
        val key = when {
            test -> "test"
            l.alarming -> "emergency"
            l.status == Status.GRACE && !l.acking -> "grace"
            else -> l.status.name
        }
        nm.notify(N_WATCH, watchNotification(watchText(l)))
        if (key != lastKey) {
            val prev = lastKey
            lastKey = key
            when (key) {
                "emergency", "test" -> {
                    Store.silenced.value = false
                    nm.notify(N_ALERT, alarmNotification(test))
                    val words = if (test) "시험 경보입니다. 실제 상황이 아닙니다."
                                else if (Store.profile.value.people.isNotEmpty()) "긴급 상황입니다. ${name()} 님 댁에서 낙상이 감지되었습니다. 확인해 주세요."
                                else "긴급 상황입니다. ${name()} 님이 넘어지셨습니다. 확인해 주세요."
                    speak(words)
                    startAlarm(if (Prefs(this).voice) 6_000L else 3_000L)
                    openOnCoverIfFolded()
                    if (test) scope.launch { delay(20_000); Store.testAlarm.value = false }
                }
                "grace" -> {
                    stopAlarm()
                    nm.notify(N_ALERT, graceNotification(l))
                    speak(if (Store.profile.value.people.isNotEmpty()) "${name()} 님 댁에서 낙상이 의심됩니다. 지금 확인하고 있습니다."
                          else "${name()} 님이 넘어지신 것 같습니다. 지금 확인하고 있습니다.")
                }
                else -> {
                    stopAlarm()
                    nm.cancel(N_ALERT)
                    if (prev == "emergency" && (l.status == Status.NORMAL || l.status == Status.EMPTY)) speak("알람이 해제되었습니다.")
                }
            }
        } else if (key == "grace") {
            nm.notify(N_ALERT, graceNotification(l))   // 남은 시간 갱신
        }
        if (silenced) stopAlarm()
        StatusWidget.update(this)
    }

    /** 새 공지가 올라오면 알림 (처음 실행 때는 기존 공지로 알리지 않음) */
    private fun checkNotice() {
        val newest = Store.notices.value.maxByOrNull { it.ts } ?: return
        val p = Prefs(this)
        if (p.noticeNotified == 0L) { p.noticeNotified = newest.ts; return }
        if (newest.ts <= p.noticeNotified) return
        p.noticeNotified = newest.ts
        val open = PendingIntent.getActivity(
            this, 4, Intent(this, MainActivity::class.java).putExtra("open", "notice")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = NotificationCompat.Builder(this, CH_NOTICE)
            .setSmallIcon(R.drawable.ic_notify)
            .setContentTitle("새 공지사항")
            .setContentText(newest.title)
            .setStyle(NotificationCompat.BigTextStyle().bigText(newest.title + "\n" + newest.body.take(200)))
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        getSystemService(NotificationManager::class.java).notify(N_NOTICE, n)
    }

    /** 접혀 있으면 커버 화면에 경보 화면을 바로 띄워 봄 (기기에 따라 막힐 수 있어 알림이 기본) */
    private fun openOnCoverIfFolded() {
        val opts = Cover.launchOptions(this) ?: return
        runCatching {
            startActivity(Intent(this, AlarmActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), opts)
        }
    }

    private fun watchText(l: Live): String = when {
        l.status == Status.EMERGENCY && l.acking -> "확인을 보냈어요 · 센서 응답 기다리는 중"
        l.status == Status.EMERGENCY -> "긴급! ${name()} 님이 넘어지셨어요"
        l.status == Status.GRACE -> "넘어진 것 같아요 · 확인 중"
        l.status == Status.NORMAL -> "${name()} 님 · 안전해요"
        l.status == Status.EMPTY -> "${name()} 님 · 집에 안 계세요"
        l.status == Status.OFFLINE -> "센서와 연결이 끊겼어요"
        else -> "센서와 연결하는 중이에요"
    }

    // ---------------- 알림 ----------------
    private fun openApp(): PendingIntent = PendingIntent.getActivity(
        this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    private fun watchNotification(text: String): Notification =
        NotificationCompat.Builder(this, CH_WATCH)
            .setSmallIcon(R.drawable.ic_notify)
            .setContentTitle("SafeWave 지키는 중")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openApp())
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

    private fun graceNotification(l: Live): Notification =
        NotificationCompat.Builder(this, CH_GRACE)
            .setSmallIcon(R.drawable.ic_notify)
            .setColor(Color.parseColor("#B45309"))
            .setContentTitle("넘어진 것 같아요")
            .setContentText("${name()} 님 반응을 확인하고 있어요" + (l.graceLeft?.let { " · ${it}초 남음" } ?: ""))
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openApp())
            .build()

    private fun alarmNotification(test: Boolean): Notification {
        val full = PendingIntent.getActivity(
            this, 1, Intent(this, AlarmActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT, Cover.launchOptions(this)
        )
        val ack = PendingIntent.getService(
            this, 2, Intent(this, WatchService::class.java).setAction(ACTION_ACK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val mute = PendingIntent.getService(
            this, 3, Intent(this, WatchService::class.java).setAction(ACTION_SILENCE),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CH_ALARM)
            .setSmallIcon(R.drawable.ic_notify)
            .setColor(Color.parseColor("#B91C1C"))
            .setContentTitle(if (test) "시험 경보 (실제 상황 아님)"
                else if (Store.profile.value.people.isNotEmpty()) "긴급! ${name()} 님 댁 낙상 감지" else "긴급! ${name()} 님이 넘어지셨어요")
            .setContentText("눌러서 확인해 주세요")
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(false)
            .setOnlyAlertOnce(false)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setFullScreenIntent(full, true)
            .setContentIntent(full)
            .addAction(0, "확인했어요", ack)
            .addAction(0, "소리 끄기", mute)
            .build()
    }

    // ---------------- 소리·진동·음성 ----------------
    private fun speak(text: String) {
        if (!Prefs(this).voice || !ttsReady) return
        runCatching { tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "sw-${System.currentTimeMillis()}") }
    }

    private fun startAlarm(delayMs: Long) {
        alarmJob?.cancel()
        wake = wake ?: (getSystemService(Context.POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "safewave:alarm").apply { acquire(10 * 60_000L) }
        vibrate(true)
        alarmJob = scope.launch {
            delay(delayMs)
            if (Store.silenced.value || !Prefs(this@WatchService).alarmSound || player != null) return@launch
            runCatching {
                val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                player = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    setDataSource(this@WatchService, uri)
                    isLooping = true
                    prepare()
                    start()
                }
            }
        }
    }

    private fun stopAlarm() {
        alarmJob?.cancel(); alarmJob = null
        runCatching { player?.stop() }
        runCatching { player?.release() }
        player = null
        vibrate(false)
        runCatching { if (wake?.isHeld == true) wake?.release() }
        wake = null
    }

    @Suppress("DEPRECATION")
    private fun vibrate(on: Boolean) {
        val v = getSystemService(Vibrator::class.java) ?: return
        if (on) runCatching { v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 900, 500), 0)) }
        else runCatching { v.cancel() }
    }
}
