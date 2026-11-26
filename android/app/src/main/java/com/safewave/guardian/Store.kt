package com.safewave.guardian

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

enum class Status { LOADING, NORMAL, EMPTY, GRACE, EMERGENCY, OFFLINE }
enum class Gate { CHECKING, LOGGED_OUT, PENDING, REJECTED, KICKED, READY, ERROR }

data class Live(
    val status: Status = Status.LOADING,
    val graceLeft: Int? = null,
    val msg: String = "",
    val test: Boolean = false,
    val lastSeen: Long = 0L,
    val acking: Boolean = false,     // 확인 버튼을 눌러서 센서가 정상으로 바뀌기를 기다리는 중
    val connMsg: String = "",
    val node: String = "",           // 어느 센서 (daegu-102-1 …)
    val room: String = "",           // 방 이름 (거실·큰방)
    val px: Float? = null,           // 레이더가 잡은 사람 위치 (m, 레이더 기준 좌우·앞뒤). 최근 2초 안에 잡혔을 때만
    val py: Float? = null,
    val ph: Float? = null            // 바닥에서 높이 (m)
) {
    /** 경보를 울려야 하는 상태 (확인을 보낸 직후는 제외) */
    val alarming get() = status == Status.EMERGENCY && !acking
}

/** sms: 긴급이면 이 번호로 자동 문자 (관제 사이트 대상자 정보의 "자동 문자" 체크와 같음) */
data class Contact(val name: String, val rel: String, val phone: String, val sms: Boolean = true)

/** 함께 사는 분 (같은 센서를 씀) */
data class Person(
    val name: String = "", val age: String = "", val sex: String = "여", val blood: String = "",
    val cond: String = "", val meds: String = "", val mob: String = "", val note: String = ""
)

data class Profile(
    val name: String = "어르신", val age: String = "", val sex: String = "", val blood: String = "",
    val addr: String = "", val cond: String = "", val meds: String = "", val mob: String = "", val note: String = "",
    val contacts: List<Contact> = emptyList(), val loaded: Boolean = false,
    val people: List<Person> = emptyList()
) {
    /** 경보·상태에 쓰는 이름: 한 분이면 "김영순", 두 분이면 "김영순·박철수", 더 많으면 "김영순 외 2명" */
    val displayName: String get() {
        val names = listOf(name) + people.map { it.name }.filter { it.isNotBlank() }
        return when (names.size) { 1 -> names[0]; 2 -> names.joinToString("·"); else -> "${names[0]} 외 ${names.size - 1}명" }
    }
}

data class Me(val name: String, val email: String, val role: String) {
    /** 관제 사이트와 같이 관리자만 어르신 정보·연락처를 고칠 수 있음 */
    val canEdit get() = role == "admin"
    val roleText get() = when (role) { "admin" -> "관리자"; "member" -> "보호자"; "pending" -> "승인 대기"; else -> role }
}

/** 공지사항 (shared/<노드>/notices/<id>) */
data class Notice(val id: String, val title: String, val body: String, val by: String, val ts: Long, val pinned: Boolean)

/** kind: ok(초록) wn(주황) cr(빨강) sys(회색) */
data class Rec(val id: String, val ts: Long, val msg: String, val kind: String)

/** 예외 구역 (지도 위 m 단위, 방 5m×5m 기준 — 관제 사이트와 같음) */
data class Zone(val x: Float, val y: Float, val w: Float, val h: Float, val name: String)

/** 방 지도를 그리는 설정 (관제 사이트의 가운데 맞추기·뒤집기·예외 구역을 그대로 씀) */
data class RoomView(
    val offX: Float = 0f, val offY: Float = 0f,
    val flipX: Boolean = false, val flipY: Boolean = false, val swap: Boolean = false,
    val zones: List<Zone> = emptyList()
)

/** 집에 있는 레이더 센서 (관제 사이트와 같은 이름) */
object Rooms {
    val ids = listOf("daegu-102-1", "daegu-102-2")
    /** 관제 사이트 장치 설정에서 바꾼 이름 (shared/<노드>/name). 없으면 센서1·센서2 */
    @Volatile var names: Map<String, String> = emptyMap()
    val list: List<Pair<String, String>> get() = ids.mapIndexed { i, id -> id to (names[id] ?: "센서${i + 1}") }
    fun name(id: String) = list.firstOrNull { it.first == id }?.second ?: id

    /** 앱을 다시 켜도 바로 그 이름이 보이게 휴대폰에 기억 */
    fun load(ctx: Context) {
        val s = Prefs(ctx).roomNames ?: return
        runCatching { val j = JSONObject(s); names = j.keys().asSequence().associateWith { j.getString(it) } }
    }
    fun save(ctx: Context, m: Map<String, String>) {
        if (m == names) return
        names = m
        Prefs(ctx).roomNames = JSONObject(m as Map<*, *>).toString()
    }
}

object Store {
    val gate = MutableStateFlow(Gate.CHECKING)
    val gateMsg = MutableStateFlow("")
    val me = MutableStateFlow<Me?>(null)
    val live = MutableStateFlow(Live())
    val profile = MutableStateFlow(Profile())
    val records = MutableStateFlow<List<Rec>>(emptyList())
    val fontLevel = MutableStateFlow(1)
    val testAlarm = MutableStateFlow(false)
    val silenced = MutableStateFlow(false)
    val notices = MutableStateFlow<List<Notice>>(emptyList())
    /** 방별 상태 (거실·큰방). live 는 이 중 가장 급한 상태 */
    val rooms = MutableStateFlow<Map<String, Live>>(emptyMap())
    val views = MutableStateFlow<Map<String, RoomView>>(emptyMap())
    /** 방별 "긴급 문자 자동 발송" 스위치 (관제 사이트 장치 설정 · shared/<노드>/settings/sms). 없으면 켜짐 */
    @Volatile var smsOn: Map<String, Boolean> = emptyMap()
    /** 이번 경보에서 "내가 갈게요" 를 누른 보호자 이름 ("나" 면 이 휴대폰) */
    val goer = MutableStateFlow<String?>(null)
    /** AI 탭 (탭을 오가도 남아 있게 여기 둠) */
    val aiSummary = MutableStateFlow("")
    val aiChat = MutableStateFlow<List<Pair<Boolean, String>>>(emptyList())   // (내가 보낸 것, 글)

    /** 센서 하나의 실시간 데이터 */
    private class Buf {
        var node: JSONObject? = null
        var lastUp: String? = null
        var lastUpAt = 0L
        var ackUntil = 0L
        var connMsg = ""
    }
    private val bufs = HashMap<String, Buf>()
    private fun buf(id: String) = bufs.getOrPut(id) { Buf() }
    private var incidentAt = 0L

    fun reset() {
        synchronized(this) { bufs.clear(); incidentAt = 0L }
        me.value = null; live.value = Live(); rooms.value = emptyMap(); profile.value = Profile(); records.value = emptyList()
        goer.value = null; aiSummary.value = ""; aiChat.value = emptyList()
    }

    // ---------------- 실시간 센서 상태 ----------------
    /** nodes/<노드> 실시간 스트림 이벤트 (put / patch) */
    fun onStreamEvent(nodeId: String, type: String, data: String) {
        val m = runCatching { JSONObject(data) }.getOrNull() ?: return
        val path = m.optString("path", "/")
        val d = m.opt("data")
        synchronized(this) {
            val b = buf(nodeId)
            if (path == "/" && type == "put") {
                b.node = d as? JSONObject ?: JSONObject()
            } else {
                val n = b.node ?: JSONObject().also { b.node = it }
                val keys = path.trim('/').split('/').filter { it.isNotEmpty() }
                if (keys.isEmpty()) {
                    if (d is JSONObject) d.keys().forEach { k -> n.put(k, d.get(k)) }
                } else {
                    var cur = n
                    for (k in keys.dropLast(1)) {
                        cur = cur.optJSONObject(k) ?: JSONObject().also { cur.put(k, it) }
                    }
                    val last = keys.last()
                    if (type == "patch" && d is JSONObject) {
                        val target = cur.optJSONObject(last) ?: JSONObject().also { cur.put(last, it) }
                        d.keys().forEach { k -> target.put(k, d.get(k)) }
                    } else if (d == null || d == JSONObject.NULL) cur.remove(last)
                    else cur.put(last, d)
                }
            }
            // 센서는 몇 초마다 up 값을 바꿈. 처음 받은 값은 오래된 것일 수 있어 다음 변화부터 살아 있다고 봄 (사이트와 같은 방식)
            val up = b.node?.opt("up")?.toString()
            if (up != null && up != b.lastUp) {
                b.lastUpAt = if (b.lastUp == null) 0L else System.currentTimeMillis()
                b.lastUp = up
            }
        }
        recompute()
    }

    fun setConnection(nodeId: String, ok: Boolean, msg: String?) {
        synchronized(this) { buf(nodeId).connMsg = if (ok) "" else (msg ?: "") }
        recompute()
    }

    private fun liveOf(id: String, room: String, b: Buf, now: Long): Live {
        val n = b.node ?: return Live(status = Status.LOADING, connMsg = b.connMsg, node = id, room = room)
        val online = b.lastUpAt > 0 && now - b.lastUpAt < Config.OFFLINE_AFTER_MS
        val st = n.optString("state", "normal")
        val status = when {
            !online && b.lastUp != null && now - startedAt < Config.OFFLINE_AFTER_MS -> Status.LOADING
            !online -> Status.OFFLINE
            st == "emergency" -> Status.EMERGENCY
            st == "grace" -> Status.GRACE
            st == "empty" -> Status.EMPTY
            else -> Status.NORMAL
        }
        if (b.ackUntil > 0 && (status == Status.NORMAL || status == Status.EMPTY || now > b.ackUntil)) b.ackUntil = 0L
        val gl = n.opt("graceLeft")
        fun f(k: String): Float? = (n.opt(k) as? Number)?.toFloat()?.takeIf { it.isFinite() }
        val hasPos = online && f("px") != null && f("py") != null
        return Live(
            px = if (hasPos) f("px") else null, py = if (hasPos) f("py") else null, ph = if (online) f("ph") else null,
            status = status,
            graceLeft = (gl as? Number)?.toInt() ?: gl?.toString()?.toDoubleOrNull()?.toInt(),
            msg = n.optString("msg", ""),
            test = n.optBoolean("sim", false) || n.optBoolean("test", false),
            lastSeen = b.lastUpAt,
            acking = b.ackUntil > 0 && (status == Status.EMERGENCY || status == Status.GRACE),
            connMsg = b.connMsg, node = id, room = room
        )
    }

    /** 여러 방 중 어느 상태를 대표로 보여 줄지 (급한 것 먼저, 그다음 사람이 있는 방) */
    private fun rank(l: Live) = when (l.status) {
        Status.EMERGENCY -> if (l.acking) 9 else 10
        Status.GRACE -> if (l.acking) 7 else 8
        Status.NORMAL -> 5
        Status.EMPTY -> 4
        Status.LOADING -> 3
        Status.OFFLINE -> 2
    }

    @Synchronized
    fun recompute() {
        val now = System.currentTimeMillis()
        val map = LinkedHashMap<String, Live>()
        for ((id, room) in Rooms.list) map[id] = liveOf(id, room, buf(id), now)
        rooms.value = map
        val best = map.values.maxByOrNull { rank(it) } ?: Live()
        // 경보가 시작된 시각 ("내가 갈게요" 는 이 뒤의 것만 봄)
        val urgent = best.status == Status.EMERGENCY || best.status == Status.GRACE
        if (urgent && incidentAt == 0L) incidentAt = now
        if (!urgent && incidentAt != 0L) { incidentAt = 0L; goer.value = null }
        live.value = best
    }
    private val startedAt = System.currentTimeMillis()

    // ---------------- 내가 갈게요 ----------------
    /** 경보 중에 "내가 갈게요" → 공유 기록에 남겨서 다른 보호자 앱·관제 사이트에 보임 */
    suspend fun go(ctx: Context): Boolean = withContext(Dispatchers.IO) {
        try {
            val l = live.value
            val nodeId = l.node.ifEmpty { Prefs(ctx).node }
            val name = me.value?.name.orEmpty()
            val body = JSONObject()
                .put("msg", "내가 갈게요" + (if (l.room.isNotEmpty()) " (${l.room})" else "") + (if (name.isNotEmpty()) " · $name" else "") + " (앱)")
                .put("cls", "ok").put("go", true)
                .put("uid", Auth.uid(ctx) ?: JSONObject.NULL)
                .put("by", if (name.isEmpty()) JSONObject.NULL else name)
                .put("ts", JSONObject().put(".sv", "timestamp"))
            Db.post(ctx, "/logs/$nodeId", body.toString())
            goer.value = "나"
            silenced.value = true
            true
        } catch (e: Exception) { false }
    }

    /** 다른 보호자가 "내가 갈게요" 를 눌렀는지 확인 (경보 중에만 부름) */
    suspend fun checkGo(ctx: Context) = withContext(Dispatchers.IO) {
        val since = incidentAt
        if (since == 0L) return@withContext
        val myUid = Auth.uid(ctx)
        runCatching {
            var best: Pair<Long, String>? = null
            for ((id, _) in Rooms.list) {
                val b = Db.get(ctx, "/logs/$id", "&orderBy=%22%24key%22&limitToLast=8")
                if (b.trim() == "null") continue
                val j = JSONObject(b)
                j.keys().forEach { k ->
                    val d = j.optJSONObject(k) ?: return@forEach
                    if (!d.optBoolean("go", false)) return@forEach
                    val ts = d.optLong("ts", 0L)
                    if (ts < since - 60_000L) return@forEach   // 이번 경보 전 것은 무시 (시계 차이 1분 여유)
                    val who = if (myUid != null && d.optString("uid") == myUid) "나" else d.optString("by").takeIf { it.isNotEmpty() && it != "null" } ?: "다른 보호자"
                    if (best == null || ts < best!!.first) best = ts to who   // 먼저 누른 사람
                }
            }
            best?.let { if (incidentAt != 0L) goer.value = it.second }
        }
    }

    // ---------------- 계정 ----------------
    /** 로그인 상태와 권한 확인. 처음 로그인한 계정은 users/<uid> 에 승인 대기로 등록 (사이트와 같은 방식) */
    suspend fun loadMe(ctx: Context, joinName: String? = null): Gate = withContext(Dispatchers.IO) {
        val p = Prefs(ctx)
        val g: Gate = try {
            val uid = Auth.uid(ctx)
            if (uid == null || !Auth.hasSaved(ctx)) Gate.LOGGED_OUT
            else {
                Auth.token(ctx)
                val body = Db.get(ctx, "/users/$uid")
                val found: JSONObject? = if (body.trim() == "null") null else JSONObject(body)
                val j: JSONObject = found ?: run {
                    val email = Auth.email(ctx)
                    val name = joinName?.takeIf { it.isNotBlank() } ?: p.name ?: email.substringBefore('@')
                    val nj = JSONObject().put("name", name).put("email", email).put("role", "pending")
                        .put("at", JSONObject().put(".sv", "timestamp"))
                    Db.put(ctx, "/users/$uid", nj.toString())
                    nj
                }
                val m = Me(j.optString("name"), j.optString("email"), j.optString("role"))
                me.value = m
                p.name = m.name
                gateMsg.value = ""
                when (m.role) {
                    "admin", "member" -> Gate.READY
                    "rejected" -> Gate.REJECTED
                    "kicked" -> Gate.KICKED
                    else -> Gate.PENDING
                }
            }
        } catch (e: AuthException) {
            gateMsg.value = e.message ?: ""
            if (e.message?.contains("만료") == true || e.message?.contains("로그인이 필요") == true) {
                Auth.signOut(ctx); Gate.LOGGED_OUT
            } else Gate.ERROR
        } catch (e: IOException) {
            gateMsg.value = "인터넷 연결을 확인해 주세요."
            Gate.ERROR
        } catch (e: Exception) {
            gateMsg.value = "오류가 발생했습니다: ${e.message}"
            Gate.ERROR
        }
        gate.value = g
        g
    }

    // ---------------- 대상자 정보 (사이트의 shared/<노드>/profile) ----------------
    suspend fun refreshShared(ctx: Context) = withContext(Dispatchers.IO) {
        val mainId = Prefs(ctx).node
        val vm = HashMap(views.value)
        val nm = HashMap<String, String>()
        val so = HashMap<String, Boolean>()
        fun nameOf(id: String, doc: JSONObject) {
            doc.optString("name", "").trim().takeIf { it.isNotEmpty() && it != "null" }?.let { nm[id] = it }
            doc.optJSONObject("settings")?.let { if (it.has("sms")) so[id] = it.optBoolean("sms", true) }
        }
        runCatching {
            val body = Db.get(ctx, "/shared/$mainId")
            if (body.trim() == "null") return@runCatching
            val doc = JSONObject(body)
            notices.value = parseNotices(doc.optJSONObject("notices"))
            val people = parsePeople(doc.opt("people"))
            val pj = doc.optJSONObject("profile")
            profile.value = (if (pj != null) parseProfile(pj) else profile.value).copy(people = people)
            vm[mainId] = parseView(doc)
            nameOf(mainId, doc)
        }
        // 다른 방은 지도 설정·이름만
        var ok = true
        for (id in Rooms.ids) if (id != mainId) runCatching {
            val body = Db.get(ctx, "/shared/$id")
            if (body.trim() == "null") vm[id] = RoomView()
            else { val d = JSONObject(body); vm[id] = parseView(d); nameOf(id, d) }
        }.onFailure { ok = false }
        views.value = vm
        if (ok) { Rooms.save(ctx, nm); smsOn = so; recompute() }   // 이름이 바뀌면 방 상태에도 바로
    }

    private fun parseView(doc: JSONObject): RoomView {
        val off = doc.optJSONArray("posOff")
        val zs = mutableListOf<Zone>()
        val za = doc.opt("zones")
        val list = when (za) {
            is JSONArray -> (0 until za.length()).mapNotNull { za.optJSONObject(it) }
            is JSONObject -> za.keys().asSequence().mapNotNull { za.optJSONObject(it) }.toList()
            else -> emptyList()
        }
        list.forEach { z ->
            val w = z.optDouble("w", Double.NaN); val h = z.optDouble("h", Double.NaN)
            val x = z.optDouble("x", Double.NaN); val y = z.optDouble("y", Double.NaN)
            if (!x.isNaN() && !y.isNaN() && !w.isNaN() && !h.isNaN())
                zs += Zone(x.toFloat(), y.toFloat(), w.toFloat(), h.toFloat(), z.optString("name", "구역"))
        }
        return RoomView(
            offX = off?.optDouble(0, 0.0)?.toFloat() ?: 0f, offY = off?.optDouble(1, 0.0)?.toFloat() ?: 0f,
            flipX = doc.optBoolean("flipX", false), flipY = doc.optBoolean("flipY", false), swap = doc.optBoolean("swapXY", false),
            zones = zs
        )
    }

    private fun parseNotices(j: JSONObject?): List<Notice> {
        if (j == null) return emptyList()
        return j.keys().asSequence().mapNotNull { k ->
            val o = j.optJSONObject(k) ?: return@mapNotNull null
            val title = o.optString("title").trim()
            if (title.isEmpty()) null
            else Notice(k, title, o.optString("body").trim(), o.optString("by").takeIf { it != "null" }.orEmpty(),
                o.optLong("ts", 0L), o.optBoolean("pinned", false))
        }.sortedWith(compareByDescending<Notice> { it.pinned }.thenByDescending { it.ts }).toList()
    }

    /** 공지 쓰기·고치기 (id 가 없으면 새 글). 성공하면 null */
    suspend fun saveNotice(ctx: Context, old: Notice?, title: String, text: String, pinned: Boolean): String? =
        writeNotice(ctx, old?.id ?: "n${System.currentTimeMillis()}",
            JSONObject().put("title", title).put("body", text).put("pinned", pinned)
                .put("by", me.value?.name?.takeIf { it.isNotEmpty() } ?: JSONObject.NULL)
                .put("ts", if (old != null && old.ts > 0) old.ts else JSONObject().put(".sv", "timestamp")),
            if (old == null) "공지사항 등록: $title" else "공지사항 수정: $title")

    suspend fun deleteNotice(ctx: Context, n: Notice): String? = writeNotice(ctx, n.id, JSONObject.NULL, "공지사항 삭제: ${n.title}")

    private suspend fun writeNotice(ctx: Context, id: String, value: Any, what: String): String? = withContext(Dispatchers.IO) {
        try {
            val nodeId = Prefs(ctx).node
            Db.patch(ctx, "/shared/$nodeId", JSONObject().put("notices/$id", value).toString())
            val who = me.value?.name.orEmpty()
            val log = JSONObject().put("msg", "$what (앱)" + if (who.isNotEmpty()) " · $who" else "")
                .put("cls", "sys").put("by", if (who.isEmpty()) JSONObject.NULL else who).put("ts", JSONObject().put(".sv", "timestamp"))
            runCatching { Db.post(ctx, "/logs/$nodeId", log.toString()) }
            refreshShared(ctx)
            null
        } catch (e: AuthException) {
            "공지를 쓸 권한이 없어요. 관리자 계정으로 로그인해 주세요."
        } catch (e: Exception) {
            "저장하지 못했어요. 인터넷을 확인하고 다시 해 주세요."
        }
    }

    private fun parsePeople(v: Any?): List<Person> {
        val arr = when (v) {
            is JSONArray -> (0 until v.length()).mapNotNull { v.optJSONObject(it) }
            is JSONObject -> v.keys().asSequence().mapNotNull { v.optJSONObject(it) }.toList()   // 배열이 객체로 올 때
            else -> emptyList()
        }
        return arr.map { o ->
            fun s(k: String) = o.opt(k)?.takeIf { it != JSONObject.NULL }?.toString()?.trim().orEmpty()
            Person(s("name"), s("age"), s("sex").ifEmpty { "여" }, s("blood"), s("cond"), s("meds"), s("mob"), s("note"))
        }.filter { it.name.isNotEmpty() }
    }

    private fun parseProfile(j: JSONObject): Profile {
        val cs = mutableListOf<Contact>()
        val arr: JSONArray? = j.optJSONArray("contacts")
        if (arr != null) for (i in 0 until arr.length()) {
            val c = arr.optJSONObject(i) ?: continue
            val name = c.optString("name").trim()
            val phone = c.optString("phone").trim()
            if (name.isNotEmpty() || phone.isNotEmpty()) cs += Contact(name, c.optString("rel").trim(), phone, c.optBoolean("sms", true))
        }
        fun s(k: String) = j.opt(k)?.takeIf { it != JSONObject.NULL }?.toString()?.trim().orEmpty()
        return Profile(
            name = s("name").ifEmpty { "어르신" }, age = s("age"), sex = s("sex"), blood = s("blood"),
            addr = s("addr"), cond = s("cond"), meds = s("meds"), mob = s("mob"), note = s("note"),
            contacts = cs, loaded = true
        )
    }

    /**
     * 어르신 정보·연락처 저장. 사이트와 같은 shared/<노드>/profile 에 저장해서 사이트 화면에도 바로 반영됨.
     * 성공하면 null, 실패하면 안내 문구
     */
    suspend fun saveProfile(ctx: Context, np: Profile, what: String): String? = withContext(Dispatchers.IO) {
        try {
            val nodeId = Prefs(ctx).node
            val contacts = JSONArray()
            np.contacts.forEach { contacts.put(JSONObject().put("name", it.name).put("rel", it.rel).put("phone", it.phone).put("sms", it.sms)) }
            val pj = JSONObject()
                .put("name", np.name).put("age", np.age.trim().toIntOrNull() ?: np.age.trim())
                .put("sex", np.sex).put("blood", np.blood).put("addr", np.addr).put("cond", np.cond)
                .put("meds", np.meds).put("mob", np.mob).put("note", np.note).put("contacts", contacts)
            val who = me.value?.name.orEmpty()
            val whoJson: Any = if (who.isEmpty()) JSONObject.NULL else who
            val body = JSONObject().put("profile", pj).put("by", whoJson).put("at", JSONObject().put(".sv", "timestamp"))
            Db.patch(ctx, "/shared/$nodeId", body.toString())
            profile.value = np.copy(loaded = true, people = profile.value.people)
            val log = JSONObject().put("msg", "$what 저장 (앱)" + if (who.isNotEmpty()) " · $who" else "")
                .put("cls", "sys").put("by", whoJson).put("ts", JSONObject().put(".sv", "timestamp"))
            runCatching { Db.post(ctx, "/logs/$nodeId", log.toString()) }
            null
        } catch (e: AuthException) {
            "수정할 권한이 없어요. 관리자 계정으로 로그인해 주세요."
        } catch (e: Exception) {
            "저장하지 못했어요. 인터넷을 확인하고 다시 해 주세요."
        }
    }

    /** 함께 사는 분 목록 저장 (shared/<노드>/people). 사이트가 profile 을 저장해도 지워지지 않게 따로 둠 */
    suspend fun savePeople(ctx: Context, list: List<Person>, what: String): String? = withContext(Dispatchers.IO) {
        try {
            val nodeId = Prefs(ctx).node
            val arr = JSONArray()
            list.forEach {
                arr.put(JSONObject().put("name", it.name).put("age", it.age.toIntOrNull() ?: it.age).put("sex", it.sex)
                    .put("blood", it.blood).put("cond", it.cond).put("meds", it.meds).put("mob", it.mob).put("note", it.note))
            }
            val who = me.value?.name.orEmpty()
            val whoJson: Any = if (who.isEmpty()) JSONObject.NULL else who
            val body = JSONObject().put("people", if (list.isEmpty()) JSONObject.NULL else arr)
                .put("by", whoJson).put("at", JSONObject().put(".sv", "timestamp"))
            Db.patch(ctx, "/shared/$nodeId", body.toString())
            profile.value = profile.value.copy(people = list)
            val log = JSONObject().put("msg", "$what (앱)" + if (who.isNotEmpty()) " · $who" else "")
                .put("cls", "sys").put("by", whoJson).put("ts", JSONObject().put(".sv", "timestamp"))
            runCatching { Db.post(ctx, "/logs/$nodeId", log.toString()) }
            null
        } catch (e: AuthException) {
            "수정할 권한이 없어요. 관리자 계정으로 로그인해 주세요."
        } catch (e: Exception) {
            "저장하지 못했어요. 인터넷을 확인하고 다시 해 주세요."
        }
    }

    // ---------------- 기록 (레이더 이벤트 + 사이트 공유 기록) ----------------
    /** 거실·큰방 기록을 합쳐서 최신순으로 (앞에 방 이름을 붙임) */
    suspend fun refreshRecords(ctx: Context) = withContext(Dispatchers.IO) {
        val ids = Rooms.list.map { it.first }
        val main = ids.first()
        val q = "&orderBy=%22%24key%22&limitToLast=40"
        val out = mutableListOf<Rec>()
        runCatching {
            val b = Db.get(ctx, "/events", "&orderBy=%22%24key%22&limitToLast=60")
            if (b.trim() != "null") {
                val j = JSONObject(b)
                j.keys().forEach { k ->
                    val d = j.optJSONObject(k) ?: return@forEach
                    val n = d.optString("node", "").ifEmpty { main }
                    if (n !in ids) return@forEach
                    val msg = d.optString("msg", "").removePrefix("[레이더] ")
                    if (msg.isEmpty()) return@forEach
                    val kind = when (d.optString("tag")) {
                        "grace" -> "wn"; "emergency" -> "cr"
                        "response", "ack", "clear", "enter" -> "ok"
                        else -> "sys"
                    }
                    out += Rec("ev:$k", d.optLong("ts", 0L), "${Rooms.name(n)} · $msg", kind)
                }
            }
        }
        for (id in ids) runCatching {
            val b = Db.get(ctx, "/logs/$id", q)
            if (b.trim() != "null") {
                val j = JSONObject(b)
                j.keys().forEach { k ->
                    val d = j.optJSONObject(k) ?: return@forEach
                    val msg = d.optString("msg", "")
                    if (msg.isEmpty()) return@forEach
                    out += Rec("lg:$id:$k", d.optLong("ts", 0L), "${Rooms.name(id)} · $msg", d.optString("cls", "sys"))
                }
            }
        }
        if (out.isNotEmpty() || records.value.isEmpty()) records.value = out.sortedByDescending { it.ts }.take(80)
    }

    // ---------------- 확인(알람 끄기) ----------------
    /** 사이트의 "보호자 확인" 과 똑같이 cmd/<노드> 에 "ACK" 를 보내고 공유 기록을 남김 */
    /** 경보 중인 방(들)에 모두 확인을 보냄. 하나라도 보내지면 true */
    suspend fun ack(ctx: Context): Boolean = withContext(Dispatchers.IO) {
        val targets = rooms.value.values.filter { it.status == Status.EMERGENCY || it.status == Status.GRACE }.map { it.node }
            .ifEmpty { listOf(live.value.node.ifEmpty { Prefs(ctx).node }) }
        var any = false
        for (nodeId in targets) {
            try {
                Db.put(ctx, "/cmd/$nodeId", "\"ACK\"")
                synchronized(this@Store) { buf(nodeId).ackUntil = System.currentTimeMillis() + 8_000L }
                any = true
                val name = me.value?.name.orEmpty()
                val body = JSONObject()
                    .put("msg", "보호자 확인 버튼 누름 (앱)" + if (name.isNotEmpty()) " · $name" else "")
                    .put("cls", "ok")
                    .put("by", if (name.isEmpty()) JSONObject.NULL else name)
                    .put("ts", JSONObject().put(".sv", "timestamp"))
                runCatching { Db.post(ctx, "/logs/$nodeId", body.toString()) }
            } catch (e: Exception) { }
        }
        if (any) { silenced.value = true; recompute() }
        any
    }
}
