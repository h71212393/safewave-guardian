package com.safewave.guardian

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * 관제 사이트와 같은 Claude AI 중계 서버(Cloudflare Worker)를 부름.
 * API 키는 서버에만 있고, 앱은 로그인 토큰만 보냄 (승인 회원만 쓸 수 있음).
 */
object Ai {
    private const val URL = "https://safewave-ai.dydgh12394.workers.dev"
    private const val ORIGIN = "https://h71212393.github.io"   // 서버가 허용하는 주소 (관제 사이트와 같은 곳으로 취급)

    suspend fun ask(ctx: Context, prompt: String): String = withContext(Dispatchers.IO) {
        val r = Http.call(
            "POST", URL, JSONObject().put("prompt", prompt.take(11_500)).toString(),
            headers = mapOf("Authorization" to "Bearer ${Auth.token(ctx)}", "Origin" to ORIGIN),
            readTimeout = 60_000
        )
        val j = runCatching { JSONObject(r.body) }.getOrElse { JSONObject() }
        if (r.code == 429) throw IOException("질문이 너무 잦아요. 1분 뒤에 다시 해 주세요.")
        if (r.code == 401) throw IOException("승인된 회원만 쓸 수 있어요.")
        if (r.code !in 200..299) throw IOException("AI 가 잠시 응답하지 않아요 (${r.code}).")
        if (j.optBoolean("refused")) "이 질문에는 답하기 어려워요. 다른 말로 물어봐 주세요."
        else j.optString("text").ifEmpty { "답을 받지 못했어요. 다시 해 주세요." }
    }

    private val hm = SimpleDateFormat("M/d a h:mm", Locale.KOREAN)

    /** AI 에게 같이 주는 지금 상황 (방 상태 + 최근 기록) */
    fun context(onlyToday: Boolean): String {
        val p = Store.profile.value
        val sb = StringBuilder()
        sb.append("어르신: ${p.displayName}")
        if (p.age.isNotEmpty()) sb.append(" (${p.age}세)")
        if (p.cond.isNotEmpty()) sb.append(", 질환: ${p.cond}")
        if (p.mob.isNotEmpty()) sb.append(", 거동: ${p.mob}")
        sb.append("\n지금 방 상태: ")
        sb.append(Store.rooms.value.values.joinToString(", ") { "${it.room} ${statusWord(it.status)}" })
        val start = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0) }.timeInMillis
        val recs = Store.records.value.filter { !onlyToday || it.ts >= start }.take(50)
        sb.append("\n${if (onlyToday) "오늘" else "최근"} 기록 (최신순, ${recs.size}개):\n")
        if (recs.isEmpty()) sb.append("(없음)\n")
        recs.forEach { sb.append("- ${hm.format(Date(it.ts))} ${it.msg}\n") }
        return sb.toString()
    }

    fun statusWord(s: Status) = when (s) {
        Status.NORMAL -> "사람 있음(정상)"; Status.EMPTY -> "비어 있음"; Status.GRACE -> "낙상 의심"
        Status.EMERGENCY -> "긴급(낙상 후 움직임 없음)"; Status.OFFLINE -> "센서 연결 끊김"; Status.LOADING -> "확인 중"
    }

    private const val RULES = "당신은 SafeWave(60GHz 레이더 낙상 감지) 보호자 앱의 도우미입니다. " +
        "보호자는 어르신의 가족입니다. 쉬운 우리말로, 짧게, 존댓말로 답하세요. 마크다운 기호(#, *, 표)는 쓰지 마세요. " +
        "의학적 진단은 하지 말고, 위급해 보이면 119 또는 가족에게 먼저 전화하라고 안내하세요. " +
        "기록에 없는 일은 지어내지 마세요."

    fun summaryPrompt() = RULES + "\n\n" + context(true) +
        "\n위 기록으로 오늘 하루를 보호자에게 3~4줄로 요약해 주세요. " +
        "첫 줄은 한 문장 결론(예: 오늘은 별일 없었어요), 그다음 낙상·경고 횟수, 확인한 사람, 센서 연결 문제가 있었으면 그것을 알려 주세요."

    fun chatPrompt(history: List<Pair<Boolean, String>>, q: String) = RULES + "\n\n" + context(false) +
        "\n지금까지 대화:\n" + history.takeLast(8).joinToString("\n") { (mine, t) -> (if (mine) "보호자: " else "도우미: ") + t.take(600) } +
        "\n보호자: $q\n도우미:"
}
