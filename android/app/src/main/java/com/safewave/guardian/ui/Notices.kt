package com.safewave.guardian.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.safewave.guardian.Notice

/**
 * 공지사항 게시판. 모두 읽을 수 있고, 관리자만 쓰고 고치고 지울 수 있음.
 * onRead: 화면을 열면 가장 새 공지 시각을 "읽음" 으로 저장
 */
@Composable
fun NoticeScreen(
    list: List<Notice>, canEdit: Boolean, lastRead: Long,
    onRead: (Long) -> Unit, onRefresh: () -> Unit,
    onSave: (Notice?, String, String, Boolean, (String?) -> Unit) -> Unit,
    onDelete: (Notice, (String?) -> Unit) -> Unit
) {
    var openId by remember { mutableStateOf<String?>(null) }
    var writing by remember { mutableStateOf(false) }
    var editTarget by remember { mutableStateOf<Notice?>(null) }
    var confirmDel by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("") }
    var msgOk by remember { mutableStateOf(true) }
    // 이 화면을 연 순간 읽기 전 기준을 기억해 두고, 읽음으로 저장
    val unreadSince = remember { lastRead }
    LaunchedEffect(list) { list.maxOfOrNull { it.ts }?.let { if (it > lastRead) onRead(it) } }

    if (writing) {
        NoticeForm(editTarget, busy, if (msgOk) "" else msg,
            onSave = { t, b, pin ->
                busy = true; msg = ""
                onSave(editTarget, t, b, pin) { err ->
                    busy = false
                    if (err == null) { writing = false; msg = if (editTarget == null) "공지를 올렸어요." else "공지를 고쳤어요."; msgOk = true; editTarget = null }
                    else { msg = err; msgOk = false }
                }
            },
            onCancel = { writing = false; editTarget = null; msg = "" })
        return
    }

    val open = list.firstOrNull { it.id == openId }
    if (open != null) {
        Page {
            BigButton("목록으로", { openId = null; confirmDel = false }, outlined = true, contentColor = C.ink, icon = Icons.Default.ArrowBack)
            Card {
                if (open.pinned) Tag("중요", C.crit)
                Text(open.title, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, color = C.ink, lineHeight = 38.sp)
                Body(listOf(timeText(open.ts), open.by).filter { it.isNotEmpty() }.joinToString(" · "), size = 18)
                Text(open.body.ifEmpty { "(내용 없음)" }, fontSize = 23.sp, color = C.ink, lineHeight = 34.sp)
            }
            if (msg.isNotEmpty()) Body(msg, color = if (msgOk) C.ok else C.crit)
            if (canEdit) {
                if (confirmDel) {
                    Body("이 공지를 지울까요?", color = C.crit, size = 21)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        BigButton(if (busy) "지우는 중..." else "지우기", {
                            busy = true
                            onDelete(open) { err ->
                                busy = false; confirmDel = false
                                if (err == null) { openId = null; msg = "공지를 지웠어요."; msgOk = true } else { msg = err; msgOk = false }
                            }
                        }, Modifier.weight(1f), color = C.crit, enabled = !busy)
                        BigButton("아니요", { confirmDel = false }, Modifier.weight(1f), outlined = true, contentColor = C.ink)
                    }
                } else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    BigButton("고치기", { editTarget = open; writing = true; msg = "" }, Modifier.weight(1f), outlined = true,
                        contentColor = C.brand, icon = Icons.Default.Edit)
                    BigButton("지우기", { confirmDel = true; msg = "" }, Modifier.weight(1f), outlined = true,
                        contentColor = C.crit, icon = Icons.Default.Delete)
                }
            }
        }
        return
    }

    Page {
        Title("공지사항")
        if (msg.isNotEmpty()) Body(msg, color = if (msgOk) C.ok else C.crit)
        if (canEdit) BigButton("공지 쓰기", { editTarget = null; writing = true; msg = "" }, color = C.brand, icon = Icons.Default.Add)
        BigButton("새로 고침", onRefresh, outlined = true, contentColor = C.brand, icon = Icons.Default.Refresh)
        if (list.isEmpty()) Card { Body(if (canEdit) "아직 공지가 없어요. '공지 쓰기'로 첫 공지를 올려 보세요." else "아직 공지가 없어요.") }
        list.forEach { n ->
            val isNew = n.ts > unreadSince
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(C.card)
                    .clickable { openId = n.id; msg = "" }.heightIn(min = 88.dp).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (n.pinned) { Tag("중요", C.crit); Spacer(Modifier.width(8.dp)) }
                    if (isNew) { Tag("새 글", C.ok); Spacer(Modifier.width(8.dp)) }
                    Text(timeText(n.ts), fontSize = 17.sp, color = C.sub, fontWeight = FontWeight.SemiBold)
                }
                Text(n.title, fontSize = 25.sp, fontWeight = FontWeight.Bold, color = C.ink, lineHeight = 32.sp)
                if (n.body.isNotEmpty()) Text(n.body, fontSize = 19.sp, color = C.sub, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 27.sp)
                Text("눌러서 자세히 보기 ›", fontSize = 18.sp, color = C.brand, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun Tag(text: String, color: Color) {
    Box(Modifier.clip(RoundedCornerShape(10.dp)).background(color).padding(horizontal = 10.dp, vertical = 3.dp)) {
        Text(text, fontSize = 16.sp, color = Color.White, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun NoticeForm(old: Notice?, busy: Boolean, err: String, onSave: (String, String, Boolean) -> Unit, onCancel: () -> Unit) {
    var title by remember { mutableStateOf(old?.title.orEmpty()) }
    var body by remember { mutableStateOf(old?.body.orEmpty()) }
    var pinned by remember { mutableStateOf(old?.pinned ?: false) }
    Page {
        Title(if (old == null) "공지 쓰기" else "공지 고치기")
        Card {
            BigField("제목", title) { title = it.take(60) }
            BigField("내용", body, singleLine = false) { body = it.take(2000) }
            Row(Modifier.fillMaxWidth().heightIn(min = 64.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("중요 공지", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = C.ink)
                    Text("목록 맨 위에 빨간 표시로 보여요", fontSize = 17.sp, color = C.sub)
                }
                Switch(pinned, { pinned = it }, colors = SwitchDefaults.colors(checkedTrackColor = C.crit))
            }
        }
        if (err.isNotEmpty()) Body(err, color = C.crit)
        BigButton(if (busy) "올리는 중..." else if (old == null) "올리기" else "저장", { onSave(title.trim(), body.trim(), pinned) },
            color = C.ok, icon = Icons.Default.Check, enabled = !busy && title.isNotBlank())
        BigButton("취소", onCancel, outlined = true, contentColor = C.sub, enabled = !busy)
        Body("올린 공지는 이 센서를 쓰는 모든 보호자 앱에 보이고, 새 공지 알림이 가요.", size = 17)
    }
}

/** 홈 화면 맨 위에 보이는 새 공지 알림 줄 */
@Composable
fun NewNoticeBanner(n: Notice, onOpen: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color(0xFFFFF7D6))
            .clickable(onClick = onOpen).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text("새 공지사항이 있어요", fontSize = 18.sp, color = Color(0xFF854D0E), fontWeight = FontWeight.Bold)
        Text(n.title, fontSize = 23.sp, color = C.ink, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text("눌러서 보기 ›", fontSize = 18.sp, color = C.brand, fontWeight = FontWeight.Bold)
    }
}
