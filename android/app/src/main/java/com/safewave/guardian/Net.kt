package com.safewave.guardian

import android.content.Context
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** 관제 사이트(SafeWave AI Monitor)와 같은 Firebase 프로젝트·경로를 씁니다. */
object Config {
    const val API_KEY = "AIzaSyCWj1AJbB3XdxIXqCVIDYDyK2XX8jXT0T8"   // 공개되어도 되는 웹 API 키 (사이트와 동일)
    const val DB_URL = "https://y-csts-default-rtdb.asia-southeast1.firebasedatabase.app"
    const val ROOT = "/safewave/sw012b30e61a4cb665482a"
    const val DEFAULT_NODE = "daegu-102-1"
    const val OFFLINE_AFTER_MS = 20_000L   // 센서 신호가 이 시간 넘게 안 바뀌면 연결 끊김으로 봄
}

class Prefs(ctx: Context) {
    private val p = ctx.applicationContext.getSharedPreferences("safewave", Context.MODE_PRIVATE)
    var uid: String?
        get() = p.getString("uid", null)
        set(v) { p.edit().putString("uid", v).apply() }
    var email: String?
        get() = p.getString("email", null)
        set(v) { p.edit().putString("email", v).apply() }
    var refreshToken: String?
        get() = p.getString("rt", null)
        set(v) { p.edit().putString("rt", v).apply() }
    var name: String?
        get() = p.getString("name", null)
        set(v) { p.edit().putString("name", v).apply() }
    var node: String
        get() = p.getString("node", Config.DEFAULT_NODE) ?: Config.DEFAULT_NODE
        set(v) { p.edit().putString("node", v).apply() }
    /** 글자 크기: 0 보통, 1 크게, 2 아주 크게 */
    var fontLevel: Int
        get() = p.getInt("font", 1)
        set(v) { p.edit().putInt("font", v).apply() }
    var voice: Boolean
        get() = p.getBoolean("voice", true)
        set(v) { p.edit().putBoolean("voice", v).apply() }
    var alarmSound: Boolean
        get() = p.getBoolean("sound", true)
        set(v) { p.edit().putBoolean("sound", v).apply() }

    /** 설정 탭의 갤럭시 플립 커버 화면 안내 보이기 */
    var flipGuide: Boolean
        get() = p.getBoolean("flipGuide", true)
        set(v) { p.edit().putBoolean("flipGuide", v).apply() }
    /** 마지막으로 읽은 공지 시각 / 알림 보낸 공지 시각 */
    var noticeRead: Long
        get() = p.getLong("noticeRead", 0L)
        set(v) { p.edit().putLong("noticeRead", v).apply() }
    var noticeNotified: Long
        get() = p.getLong("noticeNotified", 0L)
        set(v) { p.edit().putLong("noticeNotified", v).apply() }

    fun clearSession() {
        p.edit().remove("uid").remove("email").remove("rt").remove("name").apply()
    }
}

object Http {
    data class Res(val code: Int, val body: String)

    fun call(
        method: String, url: String, body: String? = null, contentType: String = "application/json",
        headers: Map<String, String> = emptyMap()
    ): Res {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.requestMethod = method
            headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
            c.connectTimeout = 15_000
            c.readTimeout = 20_000
            if (body != null) {
                c.doOutput = true
                c.setRequestProperty("Content-Type", "$contentType; charset=utf-8")
                c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = c.responseCode
            val stream = if (code in 200..299) c.inputStream else c.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            return Res(code, text)
        } finally {
            c.disconnect()
        }
    }
}

class AuthException(message: String) : Exception(message)

/** Firebase 이메일·비밀번호 로그인 (사이트와 같은 계정) */
object Auth {
    data class Session(val uid: String, val email: String, val idToken: String, val refreshToken: String, val exp: Long)

    @Volatile var session: Session? = null
    private val lock = Any()

    private val ERR = linkedMapOf(
        "EMAIL_EXISTS" to "이미 가입된 이메일입니다. 로그인해 주세요.",
        "EMAIL_NOT_FOUND" to "가입되지 않은 이메일입니다.",
        "INVALID_PASSWORD" to "비밀번호가 맞지 않습니다.",
        "INVALID_LOGIN_CREDENTIALS" to "이메일 또는 비밀번호가 맞지 않습니다.",
        "USER_DISABLED" to "사용이 중지된 계정입니다.",
        "TOO_MANY_ATTEMPTS_TRY_LATER" to "시도가 너무 많습니다. 잠시 후 다시 해 주세요.",
        "WEAK_PASSWORD" to "비밀번호는 6자 이상이어야 합니다.",
        "INVALID_EMAIL" to "이메일 형식이 올바르지 않습니다.",
        "MISSING_PASSWORD" to "비밀번호를 입력해 주세요.",
        "MISSING_EMAIL" to "이메일을 입력해 주세요.",
        "TOKEN_EXPIRED" to "로그인이 만료되었습니다. 다시 로그인해 주세요.",
        "INVALID_REFRESH_TOKEN" to "로그인이 만료되었습니다. 다시 로그인해 주세요.",
        "USER_NOT_FOUND" to "로그인이 만료되었습니다. 다시 로그인해 주세요."
    )

    fun korean(code: String): String {
        for ((k, v) in ERR) if (code.startsWith(k)) return v
        return "오류가 발생했습니다 ($code)"
    }

    private fun parse(r: Http.Res): JSONObject {
        val j = runCatching { JSONObject(r.body) }.getOrElse { JSONObject() }
        if (r.code !in 200..299) {
            val msg = j.optJSONObject("error")?.optString("message").orEmpty().ifEmpty { "HTTP ${r.code}" }
            throw AuthException(korean(msg))
        }
        return j
    }

    private fun save(ctx: Context, j: JSONObject, fallbackEmail: String) {
        val s = Session(
            uid = j.optString("localId").ifEmpty { j.optString("user_id") },
            email = j.optString("email").ifEmpty { fallbackEmail },
            idToken = j.optString("idToken").ifEmpty { j.optString("id_token") },
            refreshToken = j.optString("refreshToken").ifEmpty { j.optString("refresh_token") },
            exp = System.currentTimeMillis() +
                (j.optString("expiresIn").ifEmpty { j.optString("expires_in") }.toLongOrNull() ?: 3600L) * 1000L
        )
        session = s
        val p = Prefs(ctx)
        p.uid = s.uid; p.email = s.email; p.refreshToken = s.refreshToken
    }

    private fun idt(method: String, body: JSONObject): JSONObject =
        parse(Http.call("POST", "https://identitytoolkit.googleapis.com/v1/accounts:$method?key=${Config.API_KEY}", body.toString()))

    fun signIn(ctx: Context, email: String, pw: String) {
        val j = idt("signInWithPassword", JSONObject().put("email", email).put("password", pw).put("returnSecureToken", true))
        save(ctx, j, email)
    }

    fun signUp(ctx: Context, email: String, pw: String) {
        val j = idt("signUp", JSONObject().put("email", email).put("password", pw).put("returnSecureToken", true))
        save(ctx, j, email)
    }

    fun refresh(ctx: Context) {
        val p = Prefs(ctx)
        val rt = session?.refreshToken ?: p.refreshToken ?: throw AuthException("로그인이 필요합니다.")
        val r = Http.call(
            "POST", "https://securetoken.googleapis.com/v1/token?key=${Config.API_KEY}",
            "grant_type=refresh_token&refresh_token=" + URLEncoder.encode(rt, "UTF-8"),
            "application/x-www-form-urlencoded"
        )
        save(ctx, parse(r), session?.email ?: p.email ?: "")
    }

    /** 유효한 로그인 토큰. 만료 5분 전이면 새로 받음 */
    fun token(ctx: Context): String = synchronized(lock) {
        val s = session
        if (s == null || s.exp - System.currentTimeMillis() < 5 * 60_000L) refresh(ctx)
        session!!.idToken
    }

    fun uid(ctx: Context): String? = session?.uid ?: Prefs(ctx).uid
    fun email(ctx: Context): String = session?.email ?: Prefs(ctx).email ?: ""
    fun hasSaved(ctx: Context) = session != null || Prefs(ctx).refreshToken != null

    fun signOut(ctx: Context) {
        session = null
        Prefs(ctx).clearSession()
    }
}

/** Firebase Realtime Database REST (사이트와 같은 safewave/<키>/... 경로) */
object Db {
    fun url(ctx: Context, path: String, query: String = ""): String =
        Config.DB_URL + Config.ROOT + path + ".json?auth=" + URLEncoder.encode(Auth.token(ctx), "UTF-8") + query

    private fun check(r: Http.Res): String {
        if (r.code == 401 || r.code == 403) throw AuthException("데이터를 볼 권한이 없습니다. 관리자 승인을 확인해 주세요.")
        if (r.code !in 200..299) throw IOException("서버 오류 (${r.code})")
        return r.body
    }

    fun get(ctx: Context, path: String, query: String = ""): String = check(Http.call("GET", url(ctx, path, query)))
    fun put(ctx: Context, path: String, json: String): String = check(Http.call("PUT", url(ctx, path), json))
    fun post(ctx: Context, path: String, json: String): String = check(Http.call("POST", url(ctx, path), json))
    /** 일부만 고치기 (사이트의 shareSave 와 같은 PATCH) */
    fun patch(ctx: Context, path: String, json: String): String =
        check(Http.call("POST", url(ctx, path), json, headers = mapOf("X-HTTP-Method-Override" to "PATCH")))
}
