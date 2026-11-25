package com.safewave.guardian

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.safewave.guardian.ui.C
import com.safewave.guardian.ui.CallScreen
import com.safewave.guardian.ui.EmergencyScreen
import com.safewave.guardian.ui.HomeScreen
import com.safewave.guardian.ui.InfoScreen
import com.safewave.guardian.ui.LoginScreen
import com.safewave.guardian.ui.NoticeScreen
import com.safewave.guardian.ui.RecordsScreen
import com.safewave.guardian.ui.SafeTheme
import com.safewave.guardian.ui.SettingsScreen
import com.safewave.guardian.ui.Site
import com.safewave.guardian.ui.SiteScreen
import com.safewave.guardian.ui.WaitScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

const val TAB_NOTICE = 3
const val TAB_SITE = 5     // 관제 사이트 (앱 안에서 웹 화면으로)

fun Context.dial(number: String) {
    val n = number.filter { it.isDigit() || it == '+' }
    if (n.isEmpty()) return
    runCatching { startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$n")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

class MainActivity : ComponentActivity() {

    private val needNotify = mutableStateOf(false)
    private val openTab = mutableIntStateOf(-1)
    private val needFullScreen = mutableStateOf(false)
    private val needBattery = mutableStateOf(false)

    private val notifPerm = registerForActivityResult(ActivityResultContracts.RequestPermission()) { checkPerms() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WatchService.channels(this)
        Store.fontLevel.value = Prefs(this).fontLevel
        handleOpen(intent)
        setContent {
            val font by Store.fontLevel.collectAsState()
            SafeTheme(font) { App() }
        }
        lifecycleScope.launch { if (Store.loadMe(this@MainActivity) == Gate.READY) onReady() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleOpen(intent)
    }

    private fun handleOpen(i: Intent?) {
        if (i?.getStringExtra("open") == "notice") openTab.intValue = TAB_NOTICE
    }

    override fun onResume() {
        super.onResume()
        checkPerms()
        if (Store.gate.value == Gate.READY) lifecycleScope.launch {
            Store.refreshShared(this@MainActivity)
            Store.refreshRecords(this@MainActivity)
        }
    }

    private fun onReady() {
        WatchService.start(this)
        StatusWidget.update(this, force = true)
        checkPerms()
        if (needNotify.value && Build.VERSION.SDK_INT >= 33) notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
        lifecycleScope.launch { Store.refreshShared(this@MainActivity); Store.refreshRecords(this@MainActivity) }
    }

    private fun checkPerms() {
        needNotify.value = Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        needFullScreen.value = Build.VERSION.SDK_INT >= 34 &&
            !getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
        needBattery.value = !(getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(packageName)
    }

    private fun openNotifySettings() {
        if (Build.VERSION.SDK_INT >= 33 && shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS).not() &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            // 한 번 거절했으면 시스템 설정 화면으로
            runCatching {
                startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
                return
            }
        }
        if (Build.VERSION.SDK_INT >= 33) notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun openFullScreenSettings() {
        if (Build.VERSION.SDK_INT >= 34) runCatching {
            startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:$packageName")))
        }
    }

    @android.annotation.SuppressLint("BatteryLife")
    private fun openBatterySettings() {
        runCatching {
            startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
        }.onFailure { runCatching { startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) } }
    }

    private fun logout() {
        WatchService.stop(this)
        Auth.signOut(this)
        Site.clear()   // 관제 사이트 로그인도 같이 지움
        Store.reset()
        Store.gate.value = Gate.LOGGED_OUT
        StatusWidget.update(this, force = true)
    }

    @Composable
    private fun App() {
        val ctx = this
        val scope = rememberCoroutineScope()
        val gate by Store.gate.collectAsState()
        val gateMsg by Store.gateMsg.collectAsState()
        var busy by remember { mutableStateOf(false) }
        var err by remember { mutableStateOf("") }

        Box(Modifier.fillMaxSize().background(C.bg).systemBarsPadding()) {
            when (gate) {
                Gate.CHECKING -> Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                    androidx.compose.foundation.layout.Spacer(Modifier.height(160.dp))
                    CircularProgressIndicator(Modifier.size(64.dp), color = C.brand)
                    Text("잠시만 기다려 주세요", fontSize = 24.sp, color = C.ink, modifier = Modifier.padding(top = 24.dp))
                }
                Gate.LOGGED_OUT -> LoginScreen(
                    busy, err.ifEmpty { gateMsg },
                    onLogin = { email, pw ->
                        scope.launch {
                            busy = true; err = ""
                            try {
                                withContext(Dispatchers.IO) { Auth.signIn(ctx, email, pw) }
                                if (Store.loadMe(ctx) == Gate.READY) onReady()
                            } catch (e: AuthException) { err = e.message ?: "로그인하지 못했어요."
                            } catch (e: Exception) { err = "인터넷 연결을 확인해 주세요." }
                            busy = false
                        }
                    },
                    onJoin = { name, email, pw ->
                        scope.launch {
                            busy = true; err = ""
                            try {
                                withContext(Dispatchers.IO) { Auth.signUp(ctx, email, pw) }
                                Prefs(ctx).name = name.trim()
                                if (Store.loadMe(ctx, name.trim()) == Gate.READY) onReady()
                            } catch (e: AuthException) { err = e.message ?: "가입하지 못했어요."
                            } catch (e: Exception) { err = "인터넷 연결을 확인해 주세요." }
                            busy = false
                        }
                    }
                )
                Gate.PENDING -> WaitScreen(
                    "관리자 승인을 기다리고 있어요",
                    "관리자가 관제 사이트의 회원 관리에서 승인하면 바로 쓸 수 있어요. 승인 후 아래 버튼을 눌러 주세요.",
                    onRetry = { scope.launch { if (Store.loadMe(ctx) == Gate.READY) onReady() } }, onLogout = ::logout
                )
                Gate.REJECTED -> WaitScreen("가입이 거절되었어요", "관리자에게 문의해 주세요.", null, ::logout)
                Gate.KICKED -> WaitScreen("이용이 중지된 계정이에요", "관리자에게 문의해 주세요.", null, ::logout)
                Gate.ERROR -> WaitScreen(
                    "연결할 수 없어요", gateMsg.ifEmpty { "인터넷 연결을 확인해 주세요." },
                    onRetry = { scope.launch { if (Store.loadMe(ctx) == Gate.READY) onReady() } }, onLogout = ::logout
                )
                Gate.READY -> Main()
            }
        }
    }

    @Composable
    private fun Main() {
        val ctx = this
        val scope = rememberCoroutineScope()
        val live by Store.live.collectAsState()
        val profile by Store.profile.collectAsState()
        val recs by Store.records.collectAsState()
        val me by Store.me.collectAsState()
        val test by Store.testAlarm.collectAsState()
        val font by Store.fontLevel.collectAsState()
        var tab by remember { mutableIntStateOf(0) }
        var info by remember { mutableStateOf(false) }
        var ackBusy by remember { mutableStateOf(false) }
        var ackMsg by remember { mutableStateOf("") }
        var voice by remember { mutableStateOf(Prefs(ctx).voice) }
        var sound by remember { mutableStateOf(Prefs(ctx).alarmSound) }
        var flipGuide by remember { mutableStateOf(Prefs(ctx).flipGuide) }
        val notices by Store.notices.collectAsState()
        var noticeRead by remember { mutableStateOf(Prefs(ctx).noticeRead) }
        val newest = notices.maxByOrNull { it.ts }
        val unread = newest != null && newest.ts > noticeRead
        LaunchedEffect(openTab.intValue) { if (openTab.intValue >= 0) { info = false; tab = openTab.intValue; openTab.intValue = -1 } }

        // 화면이 켜져 있는 동안 기록은 15초마다
        LaunchedEffect(Unit) { while (true) { Store.refreshRecords(ctx); delay(15_000) } }
        LaunchedEffect(Unit) { while (true) { delay(30_000); Store.refreshShared(ctx) } }
        LaunchedEffect(live.status) { if (live.status != Status.EMERGENCY && live.status != Status.GRACE) ackMsg = "" }

        val ack: () -> Unit = {
            scope.launch {
                ackBusy = true
                val ok = Store.ack(ctx)
                ackMsg = if (ok) "확인을 보냈어요. 관제 사이트와 다른 보호자에게도 표시돼요."
                         else "확인을 보내지 못했어요. 인터넷을 확인하고 다시 눌러 주세요."
                ackBusy = false
            }
        }
        val first = profile.contacts.firstOrNull { it.phone.isNotBlank() }

        // 긴급이거나 시험 경보면 화면 전체를 경보로
        if (live.alarming || test) {
            EmergencyScreen(
                profile.displayName, test && !live.alarming, ackBusy, first,
                onAck = { if (test && !live.alarming) Store.testAlarm.value = false else ack() },
                onSilence = { Store.silenced.value = true },
                onCall = { ctx.dial(it.phone) }, on119 = { ctx.dial("119") }
            )
            return
        }

        // 관제 사이트 화면은 한 번 만들어 두고 계속 씀 (탭을 오갈 때마다 새로 불러오지 않게)
        // (처음 "관제" 탭을 열 때 만듦 → 안 쓰면 데이터·배터리를 쓰지 않음)
        var web by remember { mutableStateOf<android.webkit.WebView?>(null) }
        if (tab == TAB_SITE && web == null) web = Site.create(ctx)
        BackHandler(enabled = info || tab != 0) {
            val w = web
            if (info) info = false
            else if (tab == TAB_SITE && w != null && w.canGoBack()) w.goBack()
            else tab = 0
        }

        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) {
                if (info) InfoScreen(profile, me?.canEdit == true, onBack = { info = false },
                    onSave = { np, done -> scope.launch { done(Store.saveProfile(ctx, np, "대상자 정보")) } },
                    onSavePeople = { list, what, done -> scope.launch { done(Store.savePeople(ctx, list, what)) } })
                else when (tab) {
                    0 -> HomeScreen(live, profile, recs, ackBusy, ackMsg, ack,
                        onCall = { ctx.dial(it.phone) }, on119 = { ctx.dial("119") },
                        onInfo = { info = true }, onRecords = { tab = 2 },
                        newNotice = if (unread) newest else null, onNotice = { tab = TAB_NOTICE })
                    1 -> CallScreen(profile, me?.canEdit == true, onCall = { ctx.dial(it.phone) }, on119 = { ctx.dial("119") },
                        onSave = { list, done -> scope.launch { done(Store.saveProfile(ctx, profile.copy(contacts = list), "비상연락처")) } })
                    2 -> RecordsScreen(recs) { scope.launch { Store.refreshRecords(ctx) } }
                    TAB_NOTICE -> NoticeScreen(
                        notices, me?.canEdit == true, noticeRead,
                        onRead = { noticeRead = it; Prefs(ctx).noticeRead = it },
                        onRefresh = { scope.launch { Store.refreshShared(ctx) } },
                        onSave = { old, t, b, pin, done -> scope.launch { done(Store.saveNotice(ctx, old, t, b, pin)) } },
                        onDelete = { n, done -> scope.launch { done(Store.deleteNotice(ctx, n)) } }
                    )
                    TAB_SITE -> web?.let { SiteScreen(it) }
                    else -> SettingsScreen(
                        me, font, voice, sound, Prefs(ctx).node,
                        flipGuide, { flipGuide = it; Prefs(ctx).flipGuide = it },
                        needNotify.value, needFullScreen.value, needBattery.value,
                        onFont = { Prefs(ctx).fontLevel = it; Store.fontLevel.value = it },
                        onVoice = { voice = it; Prefs(ctx).voice = it },
                        onSound = { sound = it; Prefs(ctx).alarmSound = it },
                        onTest = { WatchService.start(ctx); Store.testAlarm.value = true },
                        onNotify = ::openNotifySettings, onFullScreen = ::openFullScreenSettings,
                        onBattery = ::openBatterySettings, onLogout = ::logout
                    )
                }
            }
            NavigationBar(containerColor = Color.White, tonalElevation = 0.dp) {
                val items = listOf("홈" to Icons.Default.Home, "전화" to Icons.Default.Phone,
                    "기록" to Icons.Default.List, "공지" to Icons.Default.Notifications, "설정" to Icons.Default.Settings,
                    "관제" to Icons.Default.Info)   // 5 = TAB_SITE: 관제 사이트
                items.forEachIndexed { i, (label, icon) ->
                    NavigationBarItem(
                        selected = tab == i && !info,
                        onClick = { info = false; tab = i },
                        icon = {
                            if (i == TAB_NOTICE && unread) BadgedBox(badge = { Badge(containerColor = C.crit) }) {
                                Icon(icon, contentDescription = "$label (새 글)", modifier = Modifier.size(32.dp))
                            } else Icon(icon, contentDescription = label, modifier = Modifier.size(32.dp))
                        },
                        label = { Text(label, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1) },   // 메뉴가 6개라 조금 작게
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = C.brand, selectedTextColor = C.brand,
                            unselectedIconColor = C.sub, unselectedTextColor = C.sub,
                            indicatorColor = Color(0xFFDDE5FF)
                        )
                    )
                }
            }
        }
    }
}
