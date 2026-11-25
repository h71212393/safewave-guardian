package com.safewave.guardian.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import com.safewave.guardian.RoomView
import kotlinx.coroutines.delay
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

// ---------------- 실시간 레이더 ----------------
private const val ROOM_M = 5f   // 관제 사이트와 같은 5m × 5m 지도, 레이더는 가운데

/** 레이더 값(m) → 지도 위 위치(m). 관제 사이트의 가운데 맞추기·뒤집기 설정을 그대로 씀 */
private fun toMap(l: Live, v: RoomView): Offset? {
    val px = l.px ?: return null
    val py = l.py ?: return null
    var ax = (px - v.offX) * (if (v.flipX) -1f else 1f)
    var ay = (py - v.offY) * (if (v.flipY) 1f else -1f)   // 레이더 앞쪽이 지도 위쪽
    if (v.swap) { val t = ax; ax = ay; ay = t }
    return Offset((ROOM_M / 2 + ax).coerceIn(0.15f, ROOM_M - 0.15f), (ROOM_M / 2 + ay).coerceIn(0.15f, ROOM_M - 0.15f))
}

@Composable
fun RadarScreen(rooms: Map<String, Live>, views: Map<String, RoomView>) {
    Page {
        Title("실시간 레이더")
        Body("점이 사람이 있는 곳이에요. 가운데 파란 점이 레이더, 위쪽이 레이더 앞쪽이에요. 초록 칸은 예외 구역이에요.", size = 17)
        Rooms.list.forEach { (id, room) ->
            val l = rooms[id] ?: Live(room = room)
            val k = look(l, "")
            Card {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(room, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = C.ink, modifier = Modifier.weight(1f))
                    Text(shortWord(l), fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Color.White,
                        modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(k.color).padding(horizontal = 12.dp, vertical = 6.dp))
                }
                RadarMap(l, views[id] ?: RoomView(), k.color)
            }
        }
    }
}

@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
@Composable
private fun RadarMap(l: Live, v: RoomView, dotColor: Color) {
    val now = toMap(l, v)
    // 사람을 놓치면 마지막 위치를 4초 동안 흐리게 보여 줌
    var last by remember { mutableStateOf<Offset?>(null) }
    var lastAt by remember { mutableLongStateOf(0L) }
    val trail = remember { mutableStateListOf<Offset>() }
    LaunchedEffect(now) {
        if (now != null) {
            last = now; lastAt = System.currentTimeMillis()
            trail.add(now); while (trail.size > 14) trail.removeAt(0)
        }
    }
    var tick by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(1000); tick = System.currentTimeMillis() } }
    val target = now ?: last
    val fresh = now != null
    val stale = !fresh && target != null && tick - lastAt < 4000
    LaunchedEffect(fresh, stale) { if (!fresh && !stale) trail.clear() }

    val ax by animateFloatAsState(target?.x ?: ROOM_M / 2, tween(500, easing = LinearEasing), label = "x")
    val ay by animateFloatAsState(target?.y ?: ROOM_M / 2, tween(500, easing = LinearEasing), label = "y")
    val pulse by rememberInfiniteTransition(label = "p").animateFloat(
        0f, 1f, infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Restart), label = "pulse")
    val tm = rememberTextMeasurer()
    val offline = l.status == Status.OFFLINE || l.status == Status.LOADING

    Canvas(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(18.dp)).background(Color(0xFF0F172A))) {
        val k = size.width / ROOM_M
        val grid = Color.White.copy(alpha = 0.08f)
        for (i in 1 until ROOM_M.toInt()) {
            drawLine(grid, Offset(i * k, 0f), Offset(i * k, size.height), 1.dp.toPx())
            drawLine(grid, Offset(0f, i * k), Offset(size.width, i * k), 1.dp.toPx())
        }
        // 레이더 거리 (1m, 2m)
        for (r in 1..2) drawCircle(Color(0xFF38BDF8).copy(alpha = 0.18f), r * k, center, style = Stroke(1.dp.toPx()))
        // 예외 구역
        v.zones.forEach { z ->
            val tl = Offset(z.x * k, z.y * k); val sz = Size(z.w * k, z.h * k)
            drawRect(Color(0xFF22C55E).copy(alpha = 0.18f), tl, sz)
            drawRect(Color(0xFF4ADE80).copy(alpha = 0.7f), tl, sz, style = Stroke(1.5.dp.toPx()))
            drawText(textMeasurer = tm, text = z.name, topLeft = tl + Offset(6.dp.toPx(), 4.dp.toPx()),
                style = TextStyle(color = Color(0xFFA7F3D0), fontSize = 12.sp, fontWeight = FontWeight.Bold))
        }
        // 레이더 위치 (가운데)
        drawCircle(Color(0xFF38BDF8), 6.dp.toPx(), center)
        if (offline) {
            val t = tm.measure(if (l.status == Status.OFFLINE) "센서 연결 끊김" else "센서 확인 중",
                TextStyle(color = Color.White.copy(alpha = 0.8f), fontSize = 18.sp, fontWeight = FontWeight.Bold))
            drawText(textLayoutResult = t, topLeft = Offset((size.width - t.size.width) / 2, size.height * 0.68f))
            return@Canvas
        }
        // 지나온 길
        if (fresh) trail.forEachIndexed { i, p ->
            drawCircle(dotColor.copy(alpha = 0.10f + 0.35f * i / trail.size), 4.dp.toPx(), Offset(p.x * k, p.y * k))
        }
        if (target != null && (fresh || stale)) {
            val c = Offset(ax * k, ay * k)
            val a = if (fresh) 1f else 0.35f
            if (fresh) drawCircle(dotColor.copy(alpha = 0.45f * (1 - pulse)), (14 + 22 * pulse).dp.toPx(), c)
            drawCircle(Color.White.copy(alpha = a), 15.dp.toPx(), c)
            drawCircle(dotColor.copy(alpha = a), 11.dp.toPx(), c)
        } else {
            val t = tm.measure("감지된 사람 없음", TextStyle(color = Color.White.copy(alpha = 0.6f), fontSize = 17.sp))
            drawText(textLayoutResult = t, topLeft =Offset((size.width - t.size.width) / 2, size.height * 0.68f))
        }
    }
    val dist = if (l.px != null && l.py != null) kotlin.math.hypot(l.px - v.offX, l.py - v.offY) else null
    Body(
        when {
            offline -> "센서가 연결되면 위치가 보여요."
            dist != null -> "레이더에서 %.1fm".format(dist) + (l.ph?.let { " · 높이 %.1fm".format(it) } ?: "")
            stale -> "방금 전 위치예요 (잠깐 놓쳤어요)"
            else -> "지금은 이 방에서 사람이 잡히지 않아요."
        }, size = 18
    )
}

// ---------------- 위쪽 작은 탭 (요약/레이더, 기록/공지) ----------------
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
