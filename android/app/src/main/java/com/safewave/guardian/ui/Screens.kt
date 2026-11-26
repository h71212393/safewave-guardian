package com.safewave.guardian.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.safewave.guardian.Contact
import com.safewave.guardian.Live
import com.safewave.guardian.Person
import com.safewave.guardian.Me
import com.safewave.guardian.Profile
import com.safewave.guardian.Rec
import com.safewave.guardian.Status
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// ---------------- 공통 ----------------
/** 함께 사는 분이 있으면 센서가 누가 넘어졌는지 모르므로 "댁에서" 로 표현 */
fun multi() = com.safewave.guardian.Store.profile.value.people.isNotEmpty()
data class Look(val color: Color, val icon: ImageVector, val title: String, val sub: String)

fun look(l: Live, name: String): Look = when (l.status) {
    Status.NORMAL -> Look(C.ok, Icons.Default.CheckCircle, "안전해요", if (multi()) "$name 님 댁에 이상이 없어요" else "$name 님이 집에서 잘 지내고 계세요")
    Status.EMPTY -> Look(C.empty, Icons.Default.Home, "집에 안 계세요", "감지 구역에 사람이 없어요")
    Status.GRACE ->
        if (l.acking) Look(C.warn, Icons.Default.Check, "확인을 보냈어요", "센서가 정상으로 바뀌기를 기다리고 있어요")
        else Look(C.warn, Icons.Default.Warning, "넘어진 것 같아요",
            "반응을 확인하고 있어요" + (l.graceLeft?.let { " · ${it}초 남음" } ?: ""))
    Status.EMERGENCY ->
        if (l.acking) Look(C.warn, Icons.Default.Check, "확인을 보냈어요", "센서가 정상으로 바뀌기를 기다리고 있어요")
        else if (multi()) Look(C.crit, Icons.Default.Warning, "긴급! 낙상 감지", "$name 님 댁에서 넘어진 뒤 움직임이 없어요. 지금 확인해 주세요")
        else Look(C.crit, Icons.Default.Warning, "긴급! 넘어지셨어요", "$name 님이 움직이지 않으세요. 지금 확인해 주세요")
    Status.OFFLINE -> Look(C.off, Icons.Default.Info, "센서와 연결이 끊겼어요", "센서 전원과 와이파이를 확인해 주세요")
    Status.LOADING -> Look(C.off, Icons.Default.Refresh, "확인하는 중이에요", "센서 상태를 불러오고 있어요")
}

private val hm = SimpleDateFormat("a h:mm", Locale.KOREAN)
private val md = SimpleDateFormat("M월 d일", Locale.KOREAN)

fun timeText(ts: Long): String {
    if (ts <= 0) return ""
    val c = Calendar.getInstance(); val t = Calendar.getInstance().apply { timeInMillis = ts }
    val sameDay = c.get(Calendar.YEAR) == t.get(Calendar.YEAR) && c.get(Calendar.DAY_OF_YEAR) == t.get(Calendar.DAY_OF_YEAR)
    c.add(Calendar.DAY_OF_YEAR, -1)
    val yesterday = c.get(Calendar.YEAR) == t.get(Calendar.YEAR) && c.get(Calendar.DAY_OF_YEAR) == t.get(Calendar.DAY_OF_YEAR)
    val d = Date(ts)
    return when { sameDay -> "오늘 " + hm.format(d); yesterday -> "어제 " + hm.format(d); else -> md.format(d) + " " + hm.format(d) }
}

@Composable
fun Card(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(C.card)
            .border(BorderStroke(1.dp, C.line), RoundedCornerShape(20.dp)).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) { content() }
}

@Composable
fun Page(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(C.bg).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) { content(); Spacer(Modifier.height(12.dp)) }
}

private fun kindColor(kind: String) = when (kind) { "ok" -> C.ok; "wn" -> C.warn; "cr" -> C.crit; else -> C.off }

@Composable
fun RecordRow(r: Rec) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.padding(top = 8.dp).size(14.dp).clip(CircleShape).background(kindColor(r.kind)))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(timeText(r.ts), fontSize = 16.sp, color = C.sub, fontWeight = FontWeight.SemiBold)
            Text(r.msg.removePrefix("[레이더] "), fontSize = 20.sp, color = C.ink, lineHeight = 28.sp)
        }
    }
}

// ---------------- 로그인 ----------------
@Composable
fun LoginScreen(busy: Boolean, error: String, onLogin: (String, String) -> Unit, onJoin: (String, String, String) -> Unit) {
    var join by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var pw by remember { mutableStateOf("") }
    var show by remember { mutableStateOf(false) }
    Page {
        Spacer(Modifier.height(12.dp))
        Box(Modifier.size(84.dp).clip(RoundedCornerShape(24.dp)).background(Color(0xFF16A34A)), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Home, null, tint = Color.White, modifier = Modifier.size(52.dp))
        }
        Text("SafeWave 보호자", fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, color = C.ink)
        Body("관제 사이트와 같은 계정으로 들어가세요.")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BigButton("로그인", { join = false }, Modifier.weight(1f), outlined = join, contentColor = if (join) C.brand else Color.White)
            BigButton("회원가입", { join = true }, Modifier.weight(1f), outlined = !join, contentColor = if (!join) C.brand else Color.White)
        }
        val fieldText = TextStyle(fontSize = 22.sp, color = C.ink)
        val colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = C.brand, unfocusedBorderColor = C.sub, focusedContainerColor = C.card, unfocusedContainerColor = C.card)
        if (join) OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("이름", fontSize = 18.sp) },
            textStyle = fieldText, singleLine = true, colors = colors)
        OutlinedTextField(email, { email = it.trim() }, Modifier.fillMaxWidth(), label = { Text("이메일", fontSize = 18.sp) },
            textStyle = fieldText, singleLine = true, colors = colors, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
        OutlinedTextField(pw, { pw = it }, Modifier.fillMaxWidth(), label = { Text("비밀번호", fontSize = 18.sp) },
            textStyle = fieldText, singleLine = true, colors = colors,
            visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = { TextButton({ show = !show }) { Text(if (show) "숨기기" else "보기", fontSize = 18.sp) } })
        if (error.isNotEmpty()) Body(error, color = C.crit)
        BigButton(
            if (busy) "잠시만요..." else if (join) "가입하기" else "로그인",
            { if (join) onJoin(name, email, pw) else onLogin(email, pw) },
            enabled = !busy && email.isNotBlank() && pw.isNotBlank() && (!join || name.isNotBlank()),
            icon = Icons.Default.Lock
        )
        if (join) Body("가입하면 관리자가 승인한 뒤부터 쓸 수 있어요.", size = 18)
    }
}

@Composable
fun WaitScreen(title: String, desc: String, onRetry: (() -> Unit)?, onLogout: () -> Unit) {
    Page {
        Spacer(Modifier.height(40.dp))
        Box(Modifier.size(96.dp).clip(CircleShape).background(C.warn), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Person, null, tint = Color.White, modifier = Modifier.size(56.dp))
        }
        Title(title)
        Body(desc)
        if (onRetry != null) BigButton("다시 확인하기", onRetry, icon = Icons.Default.Refresh)
        BigButton("로그아웃", onLogout, outlined = true, contentColor = C.sub)
    }
}

// ---------------- 홈 ----------------
@Composable
fun StatusCard(l: Live, name: String, onAck: () -> Unit, ackBusy: Boolean, goer: String? = null, onGo: (() -> Unit)? = null) {
    val k = look(l, name)
    val urgent = l.status == Status.EMERGENCY || l.status == Status.GRACE
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(k.color).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(k.icon, null, tint = Color.White, modifier = Modifier.size(72.dp))
        Text(k.title, fontSize = 40.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, lineHeight = 48.sp)
        Text(k.sub, fontSize = 23.sp, color = Color.White, lineHeight = 32.sp)
        if (urgent && l.room.isNotEmpty()) Text("장소: ${l.room}", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
        if (urgent) GoBox(goer, onGo, k.color)
        if (l.test) Text("※ 사이트에서 시험 중입니다 (실제 낙상 아님)", fontSize = 18.sp, color = Color.White, fontWeight = FontWeight.Bold)
        if (l.status == Status.OFFLINE && l.lastSeen > 0) Text("마지막 신호: ${timeText(l.lastSeen)}", fontSize = 19.sp, color = Color.White)
        if (l.connMsg.isNotEmpty() && (l.status == Status.OFFLINE || l.status == Status.LOADING)) Text(l.connMsg, fontSize = 18.sp, color = Color.White)
        if ((l.status == Status.EMERGENCY || l.status == Status.GRACE) && !l.acking) {
            Spacer(Modifier.height(4.dp))
            BigButton(if (ackBusy) "보내는 중..." else "확인했어요 (알람 끄기)", onAck, color = Color.White, contentColor = k.color,
                icon = Icons.Default.Check, enabled = !ackBusy)
        }
    }
}

@Composable
fun HomeScreen(
    l: Live, p: Profile, recs: List<Rec>, ackBusy: Boolean, ackMsg: String,
    onAck: () -> Unit, onCall: (Contact) -> Unit, on119: () -> Unit, onInfo: () -> Unit, onRecords: () -> Unit,
    newNotice: com.safewave.guardian.Notice? = null, onNotice: () -> Unit = {},
    goer: String? = null, onGo: (() -> Unit)? = null, onSite: (() -> Unit)? = null
) {
    Page {
        if (newNotice != null) NewNoticeBanner(newNotice, onNotice)
        Text("${p.displayName} 님", fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, color = C.ink)
        StatusCard(l, p.displayName, onAck, ackBusy, goer, onGo)
        if (ackMsg.isNotEmpty()) Body(ackMsg, color = if (ackMsg.startsWith("확인")) C.ok else C.crit)
        val first = p.contacts.firstOrNull { it.phone.isNotBlank() }
        if (first != null) BigButton("${first.rel.ifEmpty { first.name }}에게 전화", { onCall(first) }, color = C.ok, icon = Icons.Default.Phone)
        BigButton("119 전화", on119, color = C.crit, icon = Icons.Default.Call)
        BigButton("어르신 정보 보기", onInfo, outlined = true, contentColor = C.ink, icon = Icons.Default.Person)
        Card {
            Text("최근 기록", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = C.ink)
            if (recs.isEmpty()) Body("아직 기록이 없어요.")
            recs.take(3).forEach { RecordRow(it) }
            if (recs.size > 3) TextButton(onRecords) { Text("기록 더 보기", fontSize = 20.sp, fontWeight = FontWeight.Bold) }
        }
        if (onSite != null) Card {
            Text("관제 사이트", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = C.ink)
            Body("사이트의 모든 기능(3D 지도, 장치 설정, 예외 구역 등)을 앱 안에서 그대로 볼 수 있어요.", size = 18)
            BigButton("관제 사이트 열기", onSite, outlined = true, contentColor = C.brand, icon = Icons.Default.Home)
        }
    }
}

// ---------------- 전화 ----------------
@Composable
fun CallScreen(
    p: Profile, canEdit: Boolean, onCall: (Contact) -> Unit, on119: () -> Unit,
    onSave: (List<Contact>, (String?) -> Unit) -> Unit
) {
    var editing by remember { mutableStateOf(-2) }        // -2 없음, -1 새로 추가, 0.. 그 번호 수정
    var confirmDel by remember { mutableStateOf(-1) }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("") }
    var msgOk by remember { mutableStateOf(true) }
    val list = p.contacts
    fun save(newList: List<Contact>, done: String) {
        busy = true; msg = ""
        onSave(newList) { err ->
            busy = false
            if (err == null) { editing = -2; confirmDel = -1; msg = done; msgOk = true } else { msg = err; msgOk = false }
        }
    }
    Page {
        Title("전화하기")
        Body(if (canEdit) "이름 옆 버튼으로 전화를 걸거나, 연락처를 고칠 수 있어요." else "버튼을 누르면 전화 화면이 열려요.")
        if (msg.isNotEmpty()) Body(msg, color = if (msgOk) C.ok else C.crit)
        if (list.isEmpty() && editing != -1) Card {
            Body(if (canEdit) "등록된 연락처가 없어요. 아래 '연락처 추가'를 눌러 주세요."
                 else "등록된 연락처가 없어요. 관리자가 등록하면 여기에 나와요.")
        }
        list.forEachIndexed { i, c ->
            if (editing == i) {
                ContactForm("${i + 1}순위 연락처 고치기", c, busy,
                    onSave = { nc -> save(list.toMutableList().also { it[i] = nc.copy(sms = c.sms) }, "연락처를 고쳤어요. 관제 사이트에도 반영돼요.") },
                    onCancel = { editing = -2 })
            } else Card {
                Text("${i + 1}순위 · ${c.rel.ifEmpty { "연락처" }}", fontSize = 18.sp, color = C.sub, fontWeight = FontWeight.SemiBold)
                Text(c.name.ifEmpty { "이름 없음" }, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, color = C.ink)
                Text(c.phone.ifEmpty { "전화번호 없음" }, fontSize = 24.sp, color = C.ink)
                BigButton("전화 걸기", { onCall(c) }, color = C.ok, icon = Icons.Default.Phone, enabled = c.phone.isNotBlank())
                if (canEdit) {
                    if (confirmDel == i) {
                        Body("${c.name.ifEmpty { "이 연락처" }}를 지울까요?", color = C.crit, size = 21)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            BigButton(if (busy) "지우는 중..." else "지우기", { save(list.filterIndexed { j, _ -> j != i }, "연락처를 지웠어요.") },
                                Modifier.weight(1f), color = C.crit, enabled = !busy)
                            BigButton("아니요", { confirmDel = -1 }, Modifier.weight(1f), outlined = true, contentColor = C.ink)
                        }
                    } else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        BigButton("고치기", { editing = i; confirmDel = -1; msg = "" }, Modifier.weight(1f), outlined = true,
                            contentColor = C.brand, icon = Icons.Default.Edit)
                        BigButton("지우기", { confirmDel = i; editing = -2; msg = "" }, Modifier.weight(1f), outlined = true,
                            contentColor = C.crit, icon = Icons.Default.Delete)
                    }
                }
            }
        }
        if (canEdit) {
            if (editing == -1) ContactForm("새 연락처", Contact("", "", ""), busy,
                onSave = { nc -> save(list + nc, "연락처를 추가했어요. 관제 사이트에도 반영돼요.") },
                onCancel = { editing = -2 })
            else if (list.size < MAX_CONTACTS) BigButton("연락처 추가", { editing = -1; confirmDel = -1; msg = "" },
                color = C.brand, icon = Icons.Default.Add)
            else Body("연락처는 ${MAX_CONTACTS}명까지 등록할 수 있어요.", size = 18)
        }
        Card {
            Text("응급 신고", fontSize = 18.sp, color = C.sub, fontWeight = FontWeight.SemiBold)
            Text("119", fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, color = C.crit)
            BigButton("119 전화", on119, color = C.crit, icon = Icons.Default.Call)
        }
    }
}

const val MAX_CONTACTS = 4   // 관제 사이트와 같게

@Composable
private fun ContactForm(title: String, init: Contact, busy: Boolean, onSave: (Contact) -> Unit, onCancel: () -> Unit) {
    var name by remember { mutableStateOf(init.name) }
    var rel by remember { mutableStateOf(init.rel) }
    var phone by remember { mutableStateOf(init.phone) }
    Card {
        Text(title, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = C.ink)
        BigField("이름", name) { name = it }
        BigField("관계 (예: 아들, 딸, 복지관)", rel) { rel = it }
        BigField("전화번호", phone, KeyboardType.Phone) { phone = it }
        BigButton(if (busy) "저장하는 중..." else "저장", {
            onSave(Contact(name.trim(), rel.trim(), phone.trim()))
        }, color = C.ok, icon = Icons.Default.Check, enabled = !busy && name.isNotBlank() && phone.any { it.isDigit() })
        BigButton("취소", onCancel, outlined = true, contentColor = C.sub, enabled = !busy)
    }
}

@Composable
fun BigField(label: String, value: String, type: KeyboardType = KeyboardType.Text, singleLine: Boolean = true, onChange: (String) -> Unit) {
    OutlinedTextField(
        value, onChange, Modifier.fillMaxWidth(),
        label = { Text(label, fontSize = 18.sp) },
        textStyle = TextStyle(fontSize = 22.sp, color = C.ink),
        singleLine = singleLine,
        keyboardOptions = KeyboardOptions(keyboardType = type),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = C.brand, unfocusedBorderColor = C.sub,
            focusedContainerColor = C.card, unfocusedContainerColor = C.card
        )
    )
}

// ---------------- 기록 ----------------
@Composable
fun RecordsScreen(recs: List<Rec>, onRefresh: () -> Unit) {
    Page {
        Title("기록")
        BigButton("새로 고침", onRefresh, outlined = true, contentColor = C.brand, icon = Icons.Default.Refresh)
        Card {
            if (recs.isEmpty()) Body("아직 기록이 없어요.")
            recs.forEach { RecordRow(it) }
        }
    }
}

// ---------------- 어르신 정보 ----------------
const val MAX_PEOPLE = 3   // 대표 어르신 외에 함께 사는 분

@Composable
fun InfoScreen(
    p: Profile, canEdit: Boolean, onBack: () -> Unit,
    onSave: (Profile, (String?) -> Unit) -> Unit,
    onSavePeople: (List<Person>, String, (String?) -> Unit) -> Unit
) {
    var editing by remember { mutableStateOf(-3) }     // -3 없음, -2 대표 어르신, -1 새로 추가, 0.. 함께 사는 분 번호
    var confirmDel by remember { mutableStateOf(-1) }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("") }
    var msgOk by remember { mutableStateOf(true) }
    val done: (String?, String) -> Unit = { err, ok ->
        busy = false
        if (err == null) { editing = -3; confirmDel = -1; msg = ok; msgOk = true } else { msg = err; msgOk = false }
    }
    fun savePeople(list: List<Person>, what: String, ok: String) {
        busy = true; msg = ""
        onSavePeople(list, what) { done(it, ok) }
    }

    when {
        editing == -2 -> {
            ProfileForm(p, busy, if (msgOk) "" else msg,
                onSave = { np -> busy = true; msg = ""; onSave(np) { done(it, "저장했어요. 관제 사이트에도 반영돼요.") } },
                onCancel = { editing = -3; msg = "" })
            return
        }
        editing == -1 -> {
            PersonForm("함께 사는 분 추가", Person(), busy, if (msgOk) "" else msg,
                onSave = { np -> savePeople(p.people + np, "함께 사는 분 추가", "${np.name} 님을 추가했어요.") },
                onCancel = { editing = -3; msg = "" })
            return
        }
        editing >= 0 && editing < p.people.size -> {
            val i = editing
            PersonForm("${p.people[i].name} 님 정보 고치기", p.people[i], busy, if (msgOk) "" else msg,
                onSave = { np -> savePeople(p.people.toMutableList().also { it[i] = np }, "함께 사는 분 정보 저장", "저장했어요.") },
                onCancel = { editing = -3; msg = "" })
            return
        }
    }

    Page {
        BigButton("돌아가기", onBack, outlined = true, contentColor = C.ink, icon = Icons.Default.ArrowBack)
        Title("어르신 정보")
        if (msg.isNotEmpty()) Body(msg, color = if (msgOk) C.ok else C.crit)

        // 대표 어르신 (관제 사이트에 나오는 분)
        Card {
            Text(if (p.people.isEmpty()) "어르신" else "대표 어르신", fontSize = 18.sp, color = C.sub, fontWeight = FontWeight.SemiBold)
            Text(p.name, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, color = C.ink)
            val meta = metaText(p.age, p.sex, p.blood)
            if (meta.isNotEmpty()) Body(meta, size = 22)
            InfoRow("주소", p.addr); InfoRow("질환", p.cond); InfoRow("복용약", p.meds)
            InfoRow("거동", p.mob); InfoRow("특이사항", p.note)
            if (canEdit) BigButton("고치기", { editing = -2; msg = "" }, outlined = true, contentColor = C.brand, icon = Icons.Default.Edit)
        }

        // 함께 사는 분
        p.people.forEachIndexed { i, x ->
            Card {
                Text("함께 사는 분", fontSize = 18.sp, color = C.sub, fontWeight = FontWeight.SemiBold)
                Text(x.name, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, color = C.ink)
                val meta = metaText(x.age, x.sex, x.blood)
                if (meta.isNotEmpty()) Body(meta, size = 22)
                InfoRow("질환", x.cond); InfoRow("복용약", x.meds); InfoRow("거동", x.mob); InfoRow("특이사항", x.note)
                if (canEdit) {
                    if (confirmDel == i) {
                        Body("${x.name} 님을 목록에서 지울까요?", color = C.crit, size = 21)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            BigButton(if (busy) "지우는 중..." else "지우기",
                                { savePeople(p.people.filterIndexed { j, _ -> j != i }, "함께 사는 분 삭제", "${x.name} 님을 지웠어요.") },
                                Modifier.weight(1f), color = C.crit, enabled = !busy)
                            BigButton("아니요", { confirmDel = -1 }, Modifier.weight(1f), outlined = true, contentColor = C.ink)
                        }
                    } else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        BigButton("고치기", { editing = i; confirmDel = -1; msg = "" }, Modifier.weight(1f), outlined = true,
                            contentColor = C.brand, icon = Icons.Default.Edit)
                        BigButton("지우기", { confirmDel = i; msg = "" }, Modifier.weight(1f), outlined = true,
                            contentColor = C.crit, icon = Icons.Default.Delete)
                    }
                }
            }
        }

        if (canEdit) {
            if (p.people.size < MAX_PEOPLE) BigButton("함께 사는 분 추가", { editing = -1; confirmDel = -1; msg = "" },
                color = C.brand, icon = Icons.Default.Add)
            else Body("함께 사는 분은 ${MAX_PEOPLE}명까지 등록할 수 있어요.", size = 18)
            Body("센서는 집 전체를 지켜봐서, 여러 분이 계시면 경보는 \"○○ 님 댁에서 낙상 감지\"로 알려 드려요.", size = 17)
        } else Body("정보는 관리자 계정에서 고칠 수 있어요.", size = 18)
        if (!p.loaded) Body("관제 사이트에 등록된 정보가 아직 없어요.")
    }
}

private fun metaText(age: String, sex: String, blood: String) =
    listOf(age.takeIf { it.isNotEmpty() }?.let { "${it}세" }, sex.ifEmpty { null },
        blood.takeIf { it.isNotEmpty() }?.let { "혈액형 $it" }).filterNotNull().joinToString(" · ")

@Composable
private fun PersonForm(title: String, init: Person, busy: Boolean, err: String, onSave: (Person) -> Unit, onCancel: () -> Unit) {
    var name by remember { mutableStateOf(init.name) }
    var age by remember { mutableStateOf(init.age) }
    var sex by remember { mutableStateOf(init.sex.ifEmpty { "여" }) }
    var blood by remember { mutableStateOf(init.blood) }
    var cond by remember { mutableStateOf(init.cond) }
    var meds by remember { mutableStateOf(init.meds) }
    var mob by remember { mutableStateOf(init.mob) }
    var note by remember { mutableStateOf(init.note) }
    Page {
        Title(title)
        Card {
            BigField("이름", name) { name = it }
            BigField("나이", age, KeyboardType.Number) { v -> age = v.filter { it.isDigit() }.take(3) }
            Text("성별", fontSize = 18.sp, color = C.sub, fontWeight = FontWeight.SemiBold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf("여", "남").forEach { s ->
                    BigButton(s, { sex = s }, Modifier.weight(1f), outlined = sex != s, contentColor = if (sex == s) Color.White else C.brand)
                }
            }
            BigField("혈액형 (예: A+)", blood) { blood = it.take(6) }
        }
        Card {
            BigField("질환", cond, singleLine = false) { cond = it }
            BigField("복용약", meds, singleLine = false) { meds = it }
            BigField("거동 (예: 지팡이 사용)", mob, singleLine = false) { mob = it }
            BigField("특이사항", note, singleLine = false) { note = it }
        }
        if (err.isNotEmpty()) Body(err, color = C.crit)
        BigButton(if (busy) "저장하는 중..." else "저장", {
            onSave(Person(name.trim(), age.trim(), sex, blood.trim(), cond.trim(), meds.trim(), mob.trim(), note.trim()))
        }, color = C.ok, icon = Icons.Default.Check, enabled = !busy && name.isNotBlank())
        BigButton("취소", onCancel, outlined = true, contentColor = C.sub, enabled = !busy)
    }
}

@Composable
private fun ProfileForm(p: Profile, busy: Boolean, msg: String, onSave: (Profile) -> Unit, onCancel: () -> Unit) {
    var name by remember { mutableStateOf(if (p.loaded) p.name else "") }
    var age by remember { mutableStateOf(p.age) }
    var sex by remember { mutableStateOf(p.sex.ifEmpty { "여" }) }
    var blood by remember { mutableStateOf(p.blood) }
    var addr by remember { mutableStateOf(p.addr) }
    var cond by remember { mutableStateOf(p.cond) }
    var meds by remember { mutableStateOf(p.meds) }
    var mob by remember { mutableStateOf(p.mob) }
    var note by remember { mutableStateOf(p.note) }
    Page {
        Title("어르신 정보 고치기")
        Card {
            BigField("이름", name) { name = it }
            BigField("나이", age, KeyboardType.Number) { v -> age = v.filter { it.isDigit() }.take(3) }
            Text("성별", fontSize = 18.sp, color = C.sub, fontWeight = FontWeight.SemiBold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf("여", "남").forEach { s ->
                    BigButton(s, { sex = s }, Modifier.weight(1f), outlined = sex != s, contentColor = if (sex == s) Color.White else C.brand)
                }
            }
            BigField("혈액형 (예: A+)", blood) { blood = it.take(6) }
        }
        Card {
            BigField("주소", addr, singleLine = false) { addr = it }
            BigField("질환", cond, singleLine = false) { cond = it }
            BigField("복용약", meds, singleLine = false) { meds = it }
            BigField("거동 (예: 보행 보조기 사용)", mob, singleLine = false) { mob = it }
            BigField("특이사항", note, singleLine = false) { note = it }
        }
        if (msg.isNotEmpty()) Body(msg, color = C.crit)
        BigButton(if (busy) "저장하는 중..." else "저장", {
            onSave(p.copy(name = name.trim(), age = age.trim(), sex = sex, blood = blood.trim(), addr = addr.trim(),
                cond = cond.trim(), meds = meds.trim(), mob = mob.trim(), note = note.trim()))
        }, color = C.ok, icon = Icons.Default.Check, enabled = !busy && name.isNotBlank())
        BigButton("취소", onCancel, outlined = true, contentColor = C.sub, enabled = !busy)
        Body("저장하면 관제 사이트와 다른 보호자 앱에도 바뀐 정보가 보여요.", size = 17)
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column {
        Text(label, fontSize = 18.sp, color = C.sub, fontWeight = FontWeight.SemiBold)
        Text(value.ifEmpty { "—" }, fontSize = 22.sp, color = C.ink, lineHeight = 30.sp)
    }
}

// ---------------- 설정 ----------------
@Composable
fun SettingsScreen(
    me: Me?, fontLevel: Int, voice: Boolean, sound: Boolean, node: String, flipGuide: Boolean, onFlipGuide: (Boolean) -> Unit,
    needNotify: Boolean, needFullScreen: Boolean, needBattery: Boolean,
    onFont: (Int) -> Unit, onVoice: (Boolean) -> Unit, onSound: (Boolean) -> Unit,
    onTest: () -> Unit, onNotify: () -> Unit, onFullScreen: () -> Unit, onBattery: () -> Unit, onLogout: () -> Unit,
    smsSender: Boolean = false, smsTargets: List<Contact> = emptyList(),
    onSmsSender: (Boolean) -> Unit = {}, onSmsTest: ((String) -> Unit) -> Unit = {}
) {
    var smsArm by remember { mutableStateOf(false) }
    var smsBusy by remember { mutableStateOf(false) }
    var smsMsg by remember { mutableStateOf("") }
    Page {
        Title("설정")
        Card {
            Text("글자 크기", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = C.ink)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("보통", "크게", "아주 크게").forEachIndexed { i, t ->
                    BigButton(t, { onFont(i) }, Modifier.weight(1f), outlined = fontLevel != i,
                        contentColor = if (fontLevel == i) Color.White else C.brand)
                }
            }
        }
        if (needNotify || needFullScreen || needBattery) Card {
            Text("꼭 해 주세요", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = C.crit)
            if (needNotify) { Body("알림이 꺼져 있어서 경보가 안 와요."); BigButton("알림 켜기", onNotify, color = C.crit) }
            if (needFullScreen) { Body("화면이 꺼져 있을 때도 경보 화면이 뜨게 해 주세요."); BigButton("경보 화면 허용하기", onFullScreen, color = C.crit) }
            if (needBattery) { Body("배터리 절약 때문에 앱이 멈추지 않게 해 주세요."); BigButton("배터리 제한 풀기", onBattery, color = C.warn) }
        }
        Card {
            SwitchRow("음성으로 알려주기", "넘어지면 말로 알려 드려요", voice, onVoice)
            SwitchRow("경보음 울리기", "긴급일 때 큰 소리로 울려요", sound, onSound)
            BigButton("경보 시험하기", onTest, outlined = true, contentColor = C.crit, icon = Icons.Default.Notifications)
            Body("시험 경보는 이 휴대폰에서만 울리고, 다른 사람에게는 가지 않아요.", size = 17)
        }
        Card {
            Text("긴급 문자 자동 발송", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = C.ink)
            SwitchRow("이 폰에서 문자 보내기", "긴급이면 이 폰 번호로 비상연락처에 문자를 보내요", smsSender) { smsMsg = ""; onSmsSender(it) }
            Body(
                if (smsTargets.isEmpty()) "받을 사람이 없어요. 비상연락처에 휴대폰 번호를 넣고 '자동 문자'를 켜 주세요."
                else "받는 사람: " + smsTargets.joinToString(", ") { it.name.ifEmpty { it.phone } },
                color = if (smsTargets.isEmpty()) C.crit else C.ink, size = 19
            )
            Body("팀에서 안드로이드 폰 한 대만 켜 주세요. 문자 요금은 이 폰 요금제로 나가요. 관제 사이트 장치 설정의 '긴급 문자 자동 발송'이 꺼져 있으면 보내지 않아요.", size = 16)
            if (smsSender) {
                BigButton(
                    when { smsBusy -> "보내는 중..."; smsArm -> "한 번 더 누르면 보내요"; else -> "시험 문자 보내기" },
                    {
                        if (!smsArm) smsArm = true
                        else { smsArm = false; smsBusy = true; onSmsTest { smsBusy = false; smsMsg = it } }
                    },
                    outlined = !smsArm, color = C.crit, contentColor = if (smsArm) Color.White else C.crit,
                    icon = Icons.Default.Phone, enabled = !smsBusy && smsTargets.isNotEmpty()
                )
            }
            if (smsMsg.isNotEmpty()) Body(smsMsg, color = if (smsMsg.startsWith("시험 문자를 보냈")) C.ok else C.crit, size = 18)
        }
        if (flipGuide) Card {
            Text("갤럭시 플립 커버 화면", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = C.ink)
            Body("접은 채로도 상태를 보고 경보를 받으려면 커버 화면에 SafeWave 위젯을 놓아 주세요.", size = 18)
            Body("1. 설정 → 커버 화면 → 위젯\n2. SafeWave 상태 위젯 켜기\n3. 설정 → 커버 화면 → 알림 표시 켜기", color = C.ink, size = 19)
            Body("긴급이면 위젯이 빨갛게 바뀌고, 누르면 커버 화면에서 바로 확인할 수 있어요.", size = 17)
            BigButton("이 안내 숨기기", { onFlipGuide(false) }, outlined = true, contentColor = C.sub)
        } else BigButton("갤럭시 플립 커버 화면 안내 보기", { onFlipGuide(true) }, outlined = true, contentColor = C.brand,
            icon = Icons.Default.Info)
        Card {
            Text("내 계정", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = C.ink)
            if (me != null) { Body("${me.name} (${me.roleText})", color = C.ink, size = 22); Body(me.email, size = 18) }
            Body("센서 이름: $node", size = 18)
            BigButton("로그아웃", onLogout, outlined = true, contentColor = C.sub)
        }
    }
}

@Composable
private fun SwitchRow(title: String, desc: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = C.ink)
            Text(desc, fontSize = 17.sp, color = C.sub)
        }
        Switch(checked, onChange, colors = SwitchDefaults.colors(checkedTrackColor = C.ok))
    }
}

// ---------------- 긴급 경보 화면 (잠금 화면 위에도 뜸) ----------------
@Composable
fun EmergencyScreen(
    name: String, test: Boolean, ackBusy: Boolean, first: Contact?,
    onAck: () -> Unit, onSilence: () -> Unit, onCall: (Contact) -> Unit, on119: () -> Unit,
    room: String = "", goer: String? = null, onGo: (() -> Unit)? = null
) {
    Column(
        Modifier.fillMaxSize().background(C.crit).verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(Modifier.height(16.dp))
        Icon(Icons.Default.Warning, null, tint = Color.White, modifier = Modifier.size(96.dp))
        Text(if (test) "시험 경보" else "긴급!", fontSize = 52.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
        Text(if (test) "실제 상황이 아니에요. 경보가 잘 울리는지 시험하는 중이에요."
             else if (multi()) "$name 님 댁에서\n낙상이 감지됐어요.\n움직임이 없어요." else "$name 님이 넘어지셨어요.\n움직이지 않으세요.", fontSize = 28.sp, color = Color.White, lineHeight = 38.sp)
        if (!test && room.isNotEmpty()) Text("장소: $room", fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
        if (!test) GoBox(goer, onGo, C.crit)
        BigButton(if (ackBusy) "보내는 중..." else "확인했어요 (알람 끄기)", onAck, Modifier.heightIn(min = 96.dp),
            color = Color.White, contentColor = C.crit, icon = Icons.Default.Check, enabled = !ackBusy)
        if (first != null) BigButton("${first.rel.ifEmpty { first.name }}에게 전화", { onCall(first) }, color = Color(0xFF7F1D1D), icon = Icons.Default.Phone)
        BigButton("119 전화", on119, color = Color(0xFF7F1D1D), icon = Icons.Default.Call)
        BigButton("소리만 끄기", onSilence, outlined = true, contentColor = Color.White, outlinedContainer = Color.Transparent)
        Text("확인을 누르면 관제 사이트와 다른 보호자에게도 확인했다고 알려져요.", fontSize = 18.sp,
            color = Color.White, textAlign = TextAlign.Start, lineHeight = 26.sp)
    }
}
