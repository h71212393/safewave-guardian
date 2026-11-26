package com.safewave.guardian

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * 긴급 문자 자동 발송.
 * 설정에서 "이 폰에서 문자 보내기" 를 켠 안드로이드 폰이, 긴급이 되면 자기 번호로 비상연락처에 문자를 보냄 (폰 요금제 문자).
 * 여러 폰이 켜져 있어도 공유 기록(logs)에 "문자 보냄" 이 최근 3분 안에 있으면 보내지 않음 → 보통 한 폰만 보냄.
 * 문자 받을 사람: 대상자 정보 비상연락처 중 "자동 문자" 가 켜진 휴대폰 번호 (최대 4명)
 */
object SmsAlert {
    private const val SITE = "h71212393.github.io/safewave"
    @Volatile private var sentNode: String? = null      // 이번 경보에 긴급 문자를 보낸 센서 (해제 문자용)

    fun permitted(ctx: Context) =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED

    fun active(ctx: Context) = Prefs(ctx).smsSender && permitted(ctx)

    private fun digits(p: String) = p.filter { it.isDigit() }
    fun targets(): List<Contact> =
        Store.profile.value.contacts.filter { it.sms && digits(it.phone).matches(Regex("01\\d{8,9}")) }.take(4)

    @Suppress("DEPRECATION")
    private fun manager(ctx: Context): SmsManager =
        if (Build.VERSION.SDK_INT >= 31) ctx.getSystemService(SmsManager::class.java) else SmsManager.getDefault()

    /** 보낸 사람 수 */
    private fun send(ctx: Context, list: List<Contact>, text: String): Int {
        val sm = manager(ctx)
        var ok = 0
        for (c in list) runCatching {
            val parts = sm.divideMessage(text)
            if (parts.size > 1) sm.sendMultipartTextMessage(digits(c.phone), null, parts, null, null)
            else sm.sendTextMessage(digits(c.phone), null, text, null, null)
            ok++
        }
        return ok
    }

    private fun who(list: List<Contact>) = list.joinToString(", ") { it.name.ifEmpty { digits(it.phone).takeLast(4) } }

    private fun log(ctx: Context, node: String, msg: String, cls: String, sms: Boolean = false) {
        val me = Store.me.value?.name.orEmpty()
        val body = JSONObject().put("msg", (msg + if (me.isNotEmpty()) " · $me 폰" else "").take(300)).put("cls", cls)
            .put("by", if (me.isEmpty()) JSONObject.NULL else me).put("ts", JSONObject().put(".sv", "timestamp"))
        if (sms) body.put("sms", true)
        runCatching { Db.post(ctx, "/logs/$node", body.toString()) }
    }

    /** 다른 폰이 최근 3분 안에 이 센서로 긴급 문자를 보냈는지 */
    private fun recentlySent(ctx: Context, node: String): Boolean = runCatching {
        val b = Db.get(ctx, "/logs/$node", "&orderBy=%22%24key%22&limitToLast=10")
        if (b.trim() == "null") return@runCatching false
        val j = JSONObject(b)
        val now = System.currentTimeMillis()
        j.keys().asSequence().any { k ->
            val d = j.optJSONObject(k)
            d != null && d.optBoolean("sms", false) && now - d.optLong("ts", 0L) < 180_000L
        }
    }.getOrDefault(false)

    /** 긴급이 되면 (WatchService 가 부름) */
    suspend fun emergency(ctx: Context, l: Live) = withContext(Dispatchers.IO) {
        if (!active(ctx) || l.node.isEmpty()) return@withContext
        if (Store.smsOn[l.node] == false) { log(ctx, l.node, "긴급 문자 안 보냄 (관제 사이트 장치 설정에서 꺼짐)", "wn"); return@withContext }
        val list = targets()
        if (list.isEmpty()) { log(ctx, l.node, "긴급 문자 못 보냄: '자동 문자' 켜진 비상연락처가 없음", "wn"); return@withContext }
        if (recentlySent(ctx, l.node)) return@withContext    // 다른 폰이 이미 보냄
        val room = l.room.ifEmpty { "집" }
        val text = if (l.test) "[SafeWave 시험] $room 낙상 테스트 경보입니다. 실제 상황 아님. $SITE"
                   else "[SafeWave 긴급] $room 낙상 감지, 움직임 없음. 바로 확인하세요 $SITE"
        log(ctx, l.node, "${if (l.test) "시험 " else ""}긴급 문자 발송: ${who(list)}", "cr", sms = true)   // 먼저 기록 (다른 폰이 겹쳐 보내지 않게)
        val n = send(ctx, list, text)
        if (n < list.size) log(ctx, l.node, "긴급 문자 일부 실패 (${list.size - n}명) · 폰의 문자 기능·요금제 확인", "wn")
        if (n > 0) sentNode = l.node
        Unit
    }

    /** 긴급이 풀리면 (이 폰이 긴급 문자를 보냈을 때만) */
    suspend fun cleared(ctx: Context) = withContext(Dispatchers.IO) {
        val node = sentNode ?: return@withContext
        sentNode = null
        if (!active(ctx)) return@withContext
        val list = targets()
        if (list.isEmpty()) return@withContext
        val n = send(ctx, list, "[SafeWave] ${Rooms.name(node)} 경보 해제, 정상으로 돌아왔어요.")
        log(ctx, node, "해제 문자 발송 (${n}명)", "ok")
    }

    /** 설정의 "시험 문자 보내기". 결과 안내 문구 */
    suspend fun test(ctx: Context): String = withContext(Dispatchers.IO) {
        if (!permitted(ctx)) return@withContext "문자 권한이 없어요. 스위치를 다시 켜서 권한을 허용해 주세요."
        val list = targets()
        if (list.isEmpty()) return@withContext "받을 연락처가 없어요. 비상연락처에 휴대폰 번호를 넣고 '자동 문자'를 켜 주세요."
        val n = send(ctx, list, "[SafeWave 시험] 긴급 문자 시험입니다. 실제 상황 아님.")
        log(ctx, Prefs(ctx).node, "시험 문자 발송: ${who(list)} (${n}명)", "ok")
        if (n == list.size) "시험 문자를 보냈어요: ${who(list)}" else "일부 못 보냈어요 (${n}/${list.size}). 폰의 문자 기능을 확인해 주세요."
    }
}
