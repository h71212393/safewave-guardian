package com.safewave.guardian.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.safewave.guardian.Ai
import com.safewave.guardian.Live
import com.safewave.guardian.Rooms
import com.safewave.guardian.Status
import com.safewave.guardian.Store
import kotlinx.coroutines.launch

// ---------------- 내가 갈게요 ----------------
/** 경보 중: 아무도 안 눌렀으면 "내가 갈게요" 버튼, 누가 눌렀으면 누가 가는지 표시 */
@Composable
fun GoBox(goer: String?, onGo: (() -> Unit)?, bg: Color) {
    if (goer != null) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.White).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(if (goer == "나") "내가 간다고 알렸어요" else "$goer 님이 가고 계세요",
                fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = bg)
            Text(if (goer == "나") "다른 보호자와 관제 사이트에 표시돼요. 도착하면 '확인했어요'를 눌러 주세요."
                 else "도착하면 그분이 확인을 눌러요. 급하면 전화로 함께 챙겨 주세요.",
                fontSize = 18.sp, color = C.ink, lineHeight = 26.sp)
        }
    } else if (onGo != null) {
        var sent by remember { mutableStateOf(false) }
        BigButton(if (sent) "알리는 중..." else "내가 갈게요", { sent = true; onGo() }, Modifier.heightIn(min = 84.dp),
            color = Color(0xFF0F172A), icon = Icons.Default.Person, enabled = !sent)
    }
}

// ---------------- 방별 상태 ----------------
private fun shortWord(l: Live) = when (l.status) {
    Status.NORMAL -> "사람 있음"
    Status.EMPTY -> "비어 있음"
    Status.GRACE -> if (l.acking) "확인 보냄" else "넘어짐 의심"
    Status.EMERGENCY -> if (l.acking) "확인 보냄" else "긴급!"
    Status.OFFLINE -> "연결 끊김"
    Status.LOADING -> "확인 중"
}

/** 거실·큰방 상태를 한 줄에 색깔 카드로. 누르면 그 방의 관제 화면 */
@Composable
fun RoomCards(rooms: Map<String, Live>, onOpen: (String) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Rooms.list.forEach { (id, room) ->
            val l = rooms[id] ?: Live(room = room)
            val k = look(l, "")
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(18.dp)).background(k.color)
                    .clickable { onOpen(id) }.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(room, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text(shortWord(l), fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, maxLines = 1)
            }
        }
    }
}

// ---------------- 위쪽 작은 탭 (요약/사이트, 기록/공지) ----------------
@Composable
fun SegTabs(labels: List<String>, sel: Int, onSel: (Int) -> Unit, dot: Int = -1) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0xFFE2E8F0)).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        labels.forEachIndexed { i, t ->
            Box(
                Modifier.weight(1f).heightIn(min = 52.dp).clip(RoundedCornerShape(12.dp))
                    .background(if (sel == i) C.brand else Color.Transparent).clickable { onSel(i) },
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(t, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = if (sel == i) Color.White else C.sub)
                    if (i == dot) Box(Modifier.padding(start = 6.dp).size(10.dp).clip(CircleShape).background(C.crit))
                }
            }
        }
    }
}

// ---------------- AI ----------------
@Composable
fun AiScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val summary by Store.aiSummary.collectAsState()
    val chat by Store.aiChat.collectAsState()
    var busySum by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var q by remember { mutableStateOf("") }
    var err by remember { mutableStateOf("") }

    fun send(text: String) {
        val t = text.trim()
        if (t.isEmpty() || busy) return
        val hist = Store.aiChat.value
        Store.aiChat.value = hist + (true to t)
        q = ""; busy = true; err = ""
        scope.launch {
            try {
                Store.refreshRecords(ctx)
                val a = Ai.ask(ctx, Ai.chatPrompt(hist, t))
                Store.aiChat.value = Store.aiChat.value + (false to a)
            } catch (e: Exception) { err = e.message ?: "AI 와 연결하지 못했어요." }
            busy = false
        }
    }

    Page {
        Title("AI 도우미")
        Card {
            Text("오늘 하루 요약", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = C.ink)
            if (summary.isEmpty()) Body("버튼을 누르면 오늘 거실·큰방 기록을 AI 가 정리해 드려요.")
            else Text(summary, fontSize = 21.sp, color = C.ink, lineHeight = 31.sp)
            BigButton(
                if (busySum) "정리하는 중..." else if (summary.isEmpty()) "오늘 요약 보기" else "다시 요약하기",
                {
                    busySum = true; err = ""
                    scope.launch {
                        try {
                            Store.refreshRecords(ctx)
                            Store.aiSummary.value = Ai.ask(ctx, Ai.summaryPrompt())
                        } catch (e: Exception) { err = e.message ?: "AI 와 연결하지 못했어요." }
                        busySum = false
                    }
                },
                color = C.brand, icon = Icons.Default.Face, enabled = !busySum
            )
        }
        Card {
            Text("물어보기", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = C.ink)
            if (chat.isEmpty()) {
                Body("이렇게 물어볼 수 있어요:", size = 18)
                listOf("오늘 별일 없으셨어?", "넘어지시면 어떻게 해야 해?", "센서가 끊기면 어떻게 해?").forEach { s ->
                    BigButton(s, { send(s) }, outlined = true, contentColor = C.brand, enabled = !busy)
                }
            }
            chat.forEach { (mine, t) -> Bubble(mine, t) }
            if (busy) Body("답을 기다리는 중이에요...", size = 18)
            BigField("궁금한 것을 적어 주세요", q, singleLine = false) { q = it }
            BigButton(if (busy) "보내는 중..." else "보내기", { send(q) }, icon = Icons.Default.Send, enabled = !busy && q.isNotBlank())
            if (chat.isNotEmpty()) TextButton({ Store.aiChat.value = emptyList(); err = "" }) {
                Text("대화 지우기", fontSize = 18.sp, color = C.sub)
            }
        }
        if (err.isNotEmpty()) Body(err, color = C.crit)
        Body("AI 답은 참고용이에요. 위급할 땐 AI 말고 119 나 가족에게 먼저 전화하세요.", size = 17)
    }
}

@Composable
private fun Bubble(mine: Boolean, text: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
        Text(
            text, fontSize = 20.sp, lineHeight = 29.sp, color = C.ink, textAlign = TextAlign.Start,
            modifier = Modifier.fillMaxWidth(0.88f).clip(RoundedCornerShape(16.dp))
                .background(if (mine) Color(0xFFDDE5FF) else Color(0xFFF1F5F9)).padding(14.dp)
        )
    }
}
