package com.safewave.guardian

import android.content.Context
import java.net.HttpURLConnection
import java.net.URL

/**
 * Firebase 실시간 스트림 (Server-Sent Events) 으로 nodes/<노드> 를 계속 받음.
 * 사이트의 EventSource 와 같은 방식. 끊기면 자동으로 다시 연결하고, 로그인 토큰이 만료되면 새로 받아 다시 연결.
 */
class NodeStream(
    private val ctx: Context,
    private val nodeId: String,
    private val onEvent: (type: String, data: String) -> Unit,
    private val onStatus: (ok: Boolean, msg: String?) -> Unit
) {
    @Volatile private var running = false
    @Volatile private var conn: HttpURLConnection? = null
    private var thread: Thread? = null

    fun start() {
        if (running) return
        running = true
        thread = Thread({ loop() }, "safewave-stream-$nodeId").apply { isDaemon = true; start() }
    }

    fun stop() {
        running = false
        runCatching { conn?.disconnect() }
        thread?.interrupt()
    }

    /** 지금 연결을 끊고 새 토큰으로 다시 연결 */
    fun reconnect() {
        runCatching { conn?.disconnect() }
    }

    private fun pause(ms: Long) {
        try { Thread.sleep(ms) } catch (_: InterruptedException) { }
    }

    private fun loop() {
        var backoff = 2_000L
        while (running) {
            try {
                val url = Db.url(ctx, "/nodes/$nodeId")
                val c = URL(url).openConnection() as HttpURLConnection
                conn = c
                c.setRequestProperty("Accept", "text/event-stream")
                c.connectTimeout = 15_000
                c.readTimeout = 75_000          // 서버가 30초마다 keep-alive 를 보냄
                c.instanceFollowRedirects = true
                val code = c.responseCode
                if (code == 401 || code == 403) {
                    onStatus(false, "데이터를 볼 권한이 없습니다")
                    runCatching { Auth.refresh(ctx) }
                    pause(15_000)
                    continue
                }
                if (code != 200) {
                    onStatus(false, "서버 응답 오류 ($code)")
                } else {
                    onStatus(true, null)
                    backoff = 2_000L
                    c.inputStream.bufferedReader(Charsets.UTF_8).use { r ->
                        var ev = ""
                        val data = StringBuilder()
                        while (running) {
                            val line = r.readLine() ?: break
                            if (line.isEmpty()) {
                                if (ev == "auth_revoked" || ev == "cancel") {
                                    runCatching { Auth.refresh(ctx) }
                                    break
                                }
                                if (ev.isNotEmpty() && ev != "keep-alive") onEvent(ev, data.toString())
                                ev = ""
                                data.setLength(0)
                            } else if (line.startsWith("event:")) {
                                ev = line.substring(6).trim()
                            } else if (line.startsWith("data:")) {
                                data.append(line.substring(5).trim())
                            }
                        }
                    }
                }
            } catch (e: AuthException) {
                onStatus(false, e.message)
                pause(15_000)
            } catch (e: Exception) {
                if (!running) break
                onStatus(false, "인터넷 연결을 확인하고 있어요")
            } finally {
                runCatching { conn?.disconnect() }
            }
            if (running) {
                pause(backoff)
                backoff = (backoff * 2).coerceAtMost(30_000L)
            }
        }
    }
}
