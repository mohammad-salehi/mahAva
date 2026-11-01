package com.mahava.app.ui.extra

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.mahava.app.MahavaApplication
import com.mahava.app.cycle.CyclePhase
import com.mahava.app.network.MahApiException
import com.mahava.app.ui.AppViewModel
import com.mahava.app.ui.components.MahavaCard
import com.mahava.app.ui.components.PrimaryButton
import com.mahava.app.ui.theme.MahavaBackground
import com.mahava.app.ui.theme.MahavaPrimary
import com.mahava.app.ui.theme.MahavaPrimaryDeep
import com.mahava.app.ui.theme.MahavaPrimarySoft
import com.mahava.app.ui.theme.MahavaTextPrimary
import com.mahava.app.ui.theme.MahavaTextSecondary
import com.mahava.app.util.PersianDigits
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object ExtraRoutes {
    const val PARTNER_CHAT = "partner_chat"
    const val SKIN = "skin_routine"
    const val STORIES = "tip_stories?start={start}"
    const val QUIZZES = "health_quizzes"
    const val QUIZ = "health_quiz/{id}"
    fun stories(start: Int = 0) = "tip_stories?start=$start"
    fun quiz(id: String) = "health_quiz/$id"
}

private fun fa(n: Any) = PersianDigits.toPersian(n.toString())
private fun JsonObject.s(k: String) = get(k)?.takeUnless { it.isJsonNull }?.asString.orEmpty()
private fun JsonObject.arr(k: String): JsonArray = get(k)?.takeIf { it.isJsonArray }?.asJsonArray ?: JsonArray()
private fun JsonArray.objs() = mapNotNull { it.takeIf { e -> e.isJsonObject }?.asJsonObject }
private fun JsonArray.strings() = mapNotNull { runCatching { it.asString }.getOrNull() }

fun phaseKey(p: CyclePhase?): String = when (p) {
    CyclePhase.MENSTRUATION -> "menstrual"
    CyclePhase.FOLLICULAR -> "follicular"
    CyclePhase.OVULATION_WINDOW -> "ovulation"
    CyclePhase.LUTEAL -> "luteal"
    else -> ""
}

/** Admin-served content (skin / stories / quizzes) with an on-device cache for offline use. */
object ExtraContent {
    private const val PREFS = "mah_extra_content"

    fun cached(context: Context, kind: String): JsonObject? = runCatching {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(kind, null)?.let { JsonParser.parseString(it).asJsonObject }
    }.getOrNull()

    suspend fun load(context: Context, kind: String): JsonObject {
        val app = context.applicationContext as MahavaApplication
        return try {
            val r = app.apiClient.extraCall("GET", "/api/mah/content/$kind", null, null)
            val data = r.get("data")?.takeIf { it.isJsonObject }?.asJsonObject ?: throw MahApiException(500, "محتوا نامعتبر است")
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(kind, data.toString()).apply()
            data
        } catch (e: Exception) {
            cached(context, kind) ?: throw e
        }
    }
}

@Composable
private fun rememberContent(kind: String): Pair<JsonObject?, String?> {
    val context = LocalContext.current
    var data by remember { mutableStateOf(ExtraContent.cached(context, kind)) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(kind) {
        runCatching { ExtraContent.load(context, kind) }
            .onSuccess { data = it; error = null }
            .onFailure { if (data == null) error = (it as? MahApiException)?.message ?: "اتصال به اینترنت را بررسی کن" }
    }
    return data to error
}

@Composable
private fun Page(title: String, onBack: () -> Unit, scroll: Boolean = true, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().background(MahavaBackground)) {
        com.mahava.app.ui.kit.KitHeader(title, start = { com.mahava.app.ui.kit.KitBack(onBack) })
        Column(
            Modifier.fillMaxSize().then(if (scroll) Modifier.verticalScroll(rememberScrollState()) else Modifier).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) { content() }
    }
}

@Composable
private fun Loading(error: String?) {
    if (error != null) Text(error, color = MaterialTheme.colorScheme.error)
    else Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = MahavaPrimary) }
}

@Composable
private fun Tag(text: String, color: Color = MahavaPrimary) {
    Box(Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.12f)).padding(horizontal = 10.dp, vertical = 4.dp)) {
        Text(text, color = color, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
    }
}

// ---------------------------------------------------------------- partner chat

private data class ChatMsg(val id: String, val text: String, val mine: Boolean, val at: Long, val read: Boolean)

@Composable
fun PartnerChatScreen(vm: AppViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as MahavaApplication
    val status by vm.partnerStatus.collectAsState()
    val messages = remember { mutableStateListOf<ChatMsg>() }
    var input by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var sending by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    fun parse(r: JsonObject) = r.arr("messages").objs().map {
        ChatMsg(it.s("id"), it.s("text"), it.get("mine")?.asBoolean == true, it.get("createdAt")?.asLong ?: 0L, (it.get("readAt")?.asLong ?: 0L) > 0)
    }

    LaunchedEffect(Unit) {
        while (true) {
            try {
                val after = messages.lastOrNull()?.at ?: 0L
                val r = app.authRepository.withToken { t -> app.apiClient.extraCall("GET", "/api/mah/partner/chat" + if (after > 0) "?after=$after" else "", null, t) }
                val fresh = parse(r).filter { m -> messages.none { it.id == m.id } }
                if (after == 0L) { messages.clear(); messages.addAll(parse(r)) } else messages.addAll(fresh)
                error = null
            } catch (e: MahApiException) {
                error = e.message
            } catch (_: Exception) {
                error = "اتصال برقرار نیست"
            }
            loaded = true
            delay(5000)
        }
    }
    LaunchedEffect(messages.size) { if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1) }

    Column(Modifier.fillMaxSize().background(MahavaBackground).imePadding()) {
        val phone = PersianDigits.toPersian(status?.pair?.partner?.phoneMasked.orEmpty())
        com.mahava.app.ui.kit.KitHeader("گفت‌وگو با همسر", subtitle = phone.ifBlank { null }, start = { com.mahava.app.ui.kit.KitBack(onBack) })
        Text(
            "🔒 فقط شما دو نفر این پیام‌ها را می‌بینید · پیام‌ها پس از ۶ ماه پاک می‌شوند",
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            style = MaterialTheme.typography.labelSmall, color = MahavaTextSecondary, textAlign = TextAlign.Center
        )
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            item { Spacer(Modifier.height(8.dp)) }
            if (loaded && messages.isEmpty() && error == null) {
                item { Text("هنوز پیامی نیست. اولین پیام را بفرست 💌", Modifier.fillMaxWidth().padding(24.dp), textAlign = TextAlign.Center, color = MahavaTextSecondary) }
            }
            items(messages, key = { it.id }) { m -> Bubble(m) }
        }
        error?.let { Text(it, Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                input, { input = it.take(1000) }, Modifier.weight(1f),
                placeholder = { Text("پیام بنویس…") }, maxLines = 4, shape = RoundedCornerShape(24.dp)
            )
            Box(
                Modifier.size(48.dp).clip(CircleShape).background(if (input.isBlank() || sending) MahavaPrimary.copy(alpha = 0.4f) else MahavaPrimary)
                    .clickable(enabled = input.isNotBlank() && !sending) {
                        val text = input.trim()
                        sending = true
                        scope.launch {
                            try {
                                val body = JsonObject().apply { addProperty("text", text) }
                                val r = app.authRepository.withToken { t -> app.apiClient.extraCall("POST", "/api/mah/partner/chat", body, t) }
                                r.get("message")?.asJsonObject?.let { o ->
                                    val m = ChatMsg(o.s("id"), o.s("text"), true, o.get("createdAt")?.asLong ?: System.currentTimeMillis(), false)
                                    if (messages.none { it.id == m.id }) messages.add(m)
                                }
                                input = ""
                                error = null
                            } catch (e: MahApiException) { error = e.message } catch (_: Exception) { error = "ارسال نشد" }
                            sending = false
                        }
                    },
                contentAlignment = Alignment.Center
            ) { Text("➤", color = Color.White, style = MaterialTheme.typography.titleMedium) }
        }
    }
}

@Composable
private fun Bubble(m: ChatMsg) {
    val time = java.time.Instant.ofEpochMilli(m.at).atZone(java.time.ZoneId.systemDefault()).toLocalTime()
    val t = fa("%02d:%02d".format(time.hour, time.minute))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (m.mine) Arrangement.Start else Arrangement.End) {
        Column(
            Modifier.widthIn(max = 280.dp)
                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = if (m.mine) 4.dp else 20.dp, bottomEnd = if (m.mine) 20.dp else 4.dp))
                .background(if (m.mine) MahavaPrimary else MahavaPrimarySoft)
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            Text(m.text, color = if (m.mine) Color.White else MahavaTextPrimary)
            Text(t + if (m.mine && m.read) " ✓✓" else if (m.mine) " ✓" else "", style = MaterialTheme.typography.labelSmall, color = if (m.mine) Color.White.copy(alpha = 0.8f) else MahavaTextSecondary)
        }
    }
}

/** Card shown on the partner screens when the pair is active. */
@Composable
fun PartnerChatEntry(onOpen: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Brush.horizontalGradient(listOf(MahavaPrimary, MahavaPrimaryDeep)))
            .clickable(onClick = onOpen).padding(18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("💬", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text("گفت‌وگو با همسر", color = Color.White, fontWeight = FontWeight.Bold)
            Text("پیام خصوصی، فقط بین شما دو نفر", color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodySmall)
        }
        Text("‹", color = Color.White, style = MaterialTheme.typography.headlineSmall)
    }
}

// ---------------------------------------------------------------- skin & hair routine

@Composable
fun SkinRoutineScreen(vm: AppViewModel, onBack: () -> Unit) {
    val state by vm.state.collectAsState()
    val current = phaseKey(state.cycle?.phase)
    val (data, error) = rememberContent("skin")
    var selected by remember { mutableStateOf<String?>(null) }
    Page("روتین پوست و مو", onBack) {
        if (data == null) { Loading(error); return@Page }
        val phases = data.arr("phases").objs()
        val key = selected ?: current.ifBlank { phases.firstOrNull()?.s("key").orEmpty() }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            phases.forEach { p ->
                val on = p.s("key") == key
                Box(
                    Modifier.clip(RoundedCornerShape(50)).background(if (on) MahavaPrimary else MahavaPrimarySoft)
                        .clickable { selected = p.s("key") }.padding(horizontal = 14.dp, vertical = 8.dp)
                ) { Text(p.s("title") + if (p.s("key") == current) " · امروز" else "", color = if (on) Color.White else MahavaPrimaryDeep, fontWeight = FontWeight.SemiBold) }
            }
        }
        val p = phases.firstOrNull { it.s("key") == key } ?: return@Page
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Brush.linearGradient(listOf(MahavaPrimary, MahavaPrimaryDeep))).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(p.s("title"), color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(p.s("days"), color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodySmall)
            Text(p.s("summary"), color = Color.White)
        }
        RoutineCard("✨ پوست", p.arr("skin").strings())
        RoutineCard("💇‍♀️ مو", p.arr("hair").strings())
        Text(data.s("disclaimer"), style = MaterialTheme.typography.bodySmall, color = MahavaTextSecondary)
    }
}

@Composable
private fun RoutineCard(title: String, steps: List<String>) {
    if (steps.isEmpty()) return
    MahavaCard {
        Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        steps.forEachIndexed { i, s ->
            Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
                Box(Modifier.size(24.dp).clip(CircleShape).background(MahavaPrimarySoft), contentAlignment = Alignment.Center) {
                    Text(fa(i + 1), color = MahavaPrimaryDeep, style = MaterialTheme.typography.labelMedium)
                }
                Spacer(Modifier.size(10.dp))
                Text(s, Modifier.weight(1f))
            }
        }
    }
}

// ---------------------------------------------------------------- daily tip stories

private fun storyKey(s: JsonObject): String = s.s("id").ifBlank { s.s("title") }

/**
 * Which tip stories were viewed today (Instagram-style "seen" ring). Kept per day in a synced
 * tool record (`story_seen_<epochDay>`), so it is saved on the server with the user's data.
 */
object StorySeen {
    private const val TYPE = "story_seen"
    val seenToday = kotlinx.coroutines.flow.MutableStateFlow<Set<String>>(emptySet())
    private var loadedDay = Long.MIN_VALUE

    private fun idFor(day: Long) = "story_seen_$day"

    suspend fun load(app: MahavaApplication) {
        val day = app.clock.today().toEpochDay()
        val rec = app.database.toolRecordDao().get(idFor(day))
        val ids = rec?.takeIf { it.deletedAt == null }?.let {
            runCatching { JsonParser.parseString(it.payloadJson).asJsonObject.get("ids")?.asString.orEmpty() }.getOrDefault("")
        }.orEmpty().split('\u001F').filter { it.isNotBlank() }.toSet()
        loadedDay = day
        seenToday.value = ids
    }

    suspend fun markSeen(app: MahavaApplication, key: String) {
        if (key.isBlank()) return
        val day = app.clock.today().toEpochDay()
        if (loadedDay != day) load(app)
        if (key in seenToday.value) return
        val ids = seenToday.value + key
        seenToday.value = ids
        val dao = app.database.toolRecordDao()
        val old = dao.get(idFor(day))
        val now = maxOf(app.clock.nowMillis(), (old?.updatedAt ?: 0) + 1)
        val payload = JsonObject().apply { addProperty("ids", ids.joinToString("\u001F")) }.toString()
        dao.upsert(com.mahava.app.data.db.ToolRecord(idFor(day), TYPE, day, payload, old?.createdAt ?: now, now))
        runCatching { com.mahava.app.sync.DataSync.syncNow(app) }
    }
}

private fun orderedStories(data: JsonObject, phase: String) =
    data.arr("items").objs().sortedBy { val p = it.s("phase"); if (p == phase) 0 else if (p == "any" || p.isBlank()) 1 else 2 }

/** Instagram-like circles row for the Today screen. */
@Composable
fun StoryRow(vm: AppViewModel, onOpen: (Int) -> Unit) {
    val state by vm.state.collectAsState()
    val phase = phaseKey(state.cycle?.phase)
    val (data, _) = rememberContent("stories")
    val app = LocalContext.current.applicationContext as MahavaApplication
    LaunchedEffect(Unit) { StorySeen.load(app) }
    val seen by StorySeen.seenToday.collectAsState()
    val items = data?.let { orderedStories(it, phase) }.orEmpty()
        // Unseen first, like Instagram.
        .sortedBy { if (storyKey(it) in seen) 1 else 0 }
    if (items.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("نکته‌های امروز", fontWeight = FontWeight.Bold, color = MahavaTextPrimary)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items.forEach { s ->
                val isSeen = storyKey(s) in seen
                // Open by the story's position in the viewer's (unsorted) order.
                val openIndex = data?.let { orderedStories(it, phase) }.orEmpty().indexOfFirst { storyKey(it) == storyKey(s) }.coerceAtLeast(0)
                Column(Modifier.clickable { onOpen(openIndex) }, horizontalAlignment = Alignment.CenterHorizontally) {
                    val c = runCatching { Color(android.graphics.Color.parseColor(s.s("color"))) }.getOrDefault(MahavaPrimary)
                    Box(
                        Modifier.size(64.dp)
                            .border(
                                if (isSeen) 1.5.dp else 3.dp,
                                if (isSeen) Brush.linearGradient(listOf(Color(0xFFCFCBDD), Color(0xFFCFCBDD)))
                                else Brush.linearGradient(listOf(MahavaPrimary, Color(0xFFFFB86B))),
                                CircleShape
                            )
                            .padding(5.dp).clip(CircleShape).background(if (isSeen) c.copy(alpha = 0.45f) else c),
                        contentAlignment = Alignment.Center
                    ) { Text(s.s("title").take(6), color = Color.White.copy(alpha = if (isSeen) 0.8f else 1f), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center) }
                    Text(
                        s.s("title"), style = MaterialTheme.typography.labelSmall, maxLines = 1,
                        color = if (isSeen) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
fun StoriesScreen(vm: AppViewModel, start: Int, onClose: () -> Unit) {
    val state by vm.state.collectAsState()
    val phase = phaseKey(state.cycle?.phase)
    val (data, error) = rememberContent("stories")
    if (data == null) { Page("نکته‌های روزانه", onClose) { Loading(error) }; return }
    val stories = orderedStories(data, phase)
    if (stories.isEmpty()) { Page("نکته‌های روزانه", onClose) { Text("نکته‌ای موجود نیست") }; return }
    var si by remember { mutableIntStateOf(start.coerceIn(0, stories.size - 1)) }
    var fi by remember { mutableIntStateOf(0) }
    var paused by remember { mutableStateOf(false) }
    val story = stories[si]
    val frames = story.arr("frames").objs()
    val appCtx = LocalContext.current.applicationContext as MahavaApplication
    LaunchedEffect(si) { StorySeen.markSeen(appCtx, storyKey(story)) }
    fun next() {
        if (fi < frames.size - 1) fi++ else if (si < stories.size - 1) { si++; fi = 0 } else onClose()
    }
    fun prev() { if (fi > 0) fi-- else if (si > 0) { si--; fi = 0 } }
    var progress by remember { mutableStateOf(0f) }
    LaunchedEffect(si, fi, paused) {
        if (paused) return@LaunchedEffect
        progress = 0f
        val steps = 50
        repeat(steps) { delay(100); progress = (it + 1) / steps.toFloat() }
        next()
    }
    val c = runCatching { Color(android.graphics.Color.parseColor(story.s("color"))) }.getOrDefault(MahavaPrimary)
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(c, MahavaPrimaryDeep)))) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                frames.indices.forEach { i ->
                    LinearProgressIndicator(
                        progress = { if (i < fi) 1f else if (i == fi) progress else 0f },
                        modifier = Modifier.weight(1f).height(3.dp).clip(RoundedCornerShape(2.dp)),
                        color = Color.White, trackColor = Color.White.copy(alpha = 0.35f)
                    )
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("ماه · " + story.s("title"), color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton(onClick = onClose) { Text("✕", color = Color.White, style = MaterialTheme.typography.titleLarge) }
            }
            Spacer(Modifier.weight(1f))
            frames.getOrNull(fi)?.let { f ->
                Text(f.s("title"), color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                Text(f.s("body"), color = Color.White, style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.weight(1.2f))
            Text("نکتهٔ آموزشی است، نه توصیهٔ پزشکی", color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        }
        // RTL: right half = previous, left half = next (like reading direction).
        Row(Modifier.fillMaxSize().padding(top = 80.dp)) {
            Box(Modifier.weight(1f).fillMaxSize().clickable { prev() })
            Box(Modifier.weight(1f).fillMaxSize().clickable { next() })
        }
    }
}

// ---------------------------------------------------------------- health quizzes

/** Quiz results: synced tool records (`quiz_<id>`) on the server + a prefs cache for instant reads. */
private object QuizResults {
    private const val PREFS = "mah_quiz_results"
    private const val TYPE = "quiz_result"
    private fun recId(id: String) = "quiz_" + id.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(58)

    fun save(context: Context, id: String, score: Int, level: String) {
        val now = System.currentTimeMillis()
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(id, "$score|$level|$now").apply()
        val app = context.applicationContext as MahavaApplication
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            runCatching {
                val dao = app.database.toolRecordDao()
                val old = dao.get(recId(id))
                val ts = maxOf(app.clock.nowMillis(), (old?.updatedAt ?: 0) + 1)
                val payload = JsonObject().apply {
                    addProperty("quizId", id); addProperty("score", score); addProperty("level", level); addProperty("at", now)
                }.toString()
                dao.upsert(com.mahava.app.data.db.ToolRecord(recId(id), TYPE, null, payload, old?.createdAt ?: ts, ts))
                com.mahava.app.sync.DataSync.syncNow(app)
            }
        }
    }

    /** Pull server-synced results (e.g. after reinstall/login) into the prefs cache. */
    suspend fun hydrate(context: Context) {
        val app = context.applicationContext as MahavaApplication
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val e = sp.edit()
        app.database.toolRecordDao().getAll().filter { it.type == TYPE && it.deletedAt == null }.forEach { r ->
            runCatching {
                val o = JsonParser.parseString(r.payloadJson).asJsonObject
                val qid = o.get("quizId").asString
                val at = o.get("at")?.asLong ?: r.updatedAt
                val cur = sp.getString(qid, null)?.split("|")?.getOrNull(2)?.toLongOrNull() ?: 0L
                if (at >= cur) e.putString(qid, "${o.get("score").asInt}|${o.get("level")?.asString.orEmpty()}|$at")
            }
        }
        e.apply()
    }
    fun last(context: Context, id: String): Triple<Int, String, Long>? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(id, null)?.split("|")?.takeIf { it.size == 3 }?.let {
            Triple(it[0].toIntOrNull() ?: 0, it[1], it[2].toLongOrNull() ?: 0L)
        }
}

private fun levelFa(l: String) = when (l) { "low" -> "کم"; "mid" -> "متوسط"; "high" -> "زیاد"; else -> l }
private fun levelColor(l: String) = when (l) { "low" -> Color(0xFF16A34A); "mid" -> Color(0xFFD97706); "high" -> Color(0xFFDC2626); else -> MahavaPrimary }

@Composable
fun QuizListScreen(onBack: () -> Unit, onOpen: (String) -> Unit) {
    val context = LocalContext.current
    val (data, error) = rememberContent("quizzes")
    var hydrated by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { runCatching { QuizResults.hydrate(context) }; hydrated++ }
    Page("آزمون‌های سلامت", onBack) {
        if (hydrated < 0) return@Page // re-read cached results after hydrate
        if (data == null) { Loading(error); return@Page }
        MahavaCard(containerColor = MahavaPrimarySoft) {
            Text(data.s("disclaimer"), style = MaterialTheme.typography.bodySmall, color = MahavaPrimaryDeep)
        }
        data.arr("items").objs().forEach { q ->
            val last = QuizResults.last(context, q.s("id"))
            MahavaCard(Modifier.clickable { onOpen(q.s("id")) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(q.s("title"), fontWeight = FontWeight.Bold)
                        Text("${fa(q.arr("questions").size())} پرسش · حدود ${fa(q.get("minutes")?.asInt ?: 2)} دقیقه", style = MaterialTheme.typography.bodySmall, color = MahavaTextSecondary)
                    }
                    if (last != null) Tag("آخرین: " + levelFa(last.second), levelColor(last.second))
                }
            }
        }
        Text("بازبینی منابع: ${fa(data.s("reviewedAt"))}", style = MaterialTheme.typography.labelSmall, color = MahavaTextSecondary)
    }
}

@Composable
fun QuizScreen(id: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val (data, error) = rememberContent("quizzes")
    val quiz = data?.arr("items")?.objs()?.firstOrNull { it.s("id") == id }
    var answers by remember { mutableStateOf<Map<Int, Int>>(emptyMap()) }
    var index by remember { mutableIntStateOf(0) }
    var done by remember { mutableStateOf(false) }
    if (quiz == null) { Page("آزمون", onBack) { Loading(error ?: if (data != null) "آزمون پیدا نشد" else null) }; return }
    val questions = quiz.arr("questions").strings()
    val redFlags = quiz.arr("redFlags").strings()
    val total = questions.size + redFlags.size
    val options = quiz.arr("options").objs()
    val yesNo = listOf("نه" to 0, "بله" to 1)

    Page(quiz.s("title"), onBack) {
        if (!done) {
            LinearProgressIndicator(progress = { index / total.toFloat() }, Modifier.fillMaxWidth(), color = MahavaPrimary, trackColor = MahavaPrimarySoft)
            Text("پرسش ${fa(index + 1)} از ${fa(total)}", style = MaterialTheme.typography.labelMedium, color = MahavaTextSecondary)
            if (index == 0) Text(quiz.s("intro"), style = MaterialTheme.typography.bodySmall, color = MahavaTextSecondary)
            val isFlag = index >= questions.size
            val text = if (isFlag) redFlags[index - questions.size] else questions[index]
            MahavaCard { Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
            val opts = if (isFlag) yesNo else options.map { it.s("label") to (it.get("score")?.asInt ?: 0) }
            opts.forEach { (label, score) ->
                val on = answers[index] == score
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(if (on) MahavaPrimary else MahavaPrimarySoft)
                        .clickable {
                            answers = answers + (index to score)
                            if (index < total - 1) index++ else {
                                done = true
                                val s = questions.indices.sumOf { answers[it] ?: 0 }
                                val band = quiz.arr("bands").objs().firstOrNull { s <= (it.get("max")?.asInt ?: Int.MAX_VALUE) }
                                QuizResults.save(context, id, s, band?.s("level").orEmpty())
                            }
                        }.padding(16.dp)
                ) { Text(label, color = if (on) Color.White else MahavaPrimaryDeep, fontWeight = FontWeight.SemiBold) }
            }
            if (index > 0) TextButton(onClick = { index-- }) { Text("پرسش قبلی") }
            return@Page
        }
        val score = questions.indices.sumOf { answers[it] ?: 0 }
        val flagged = redFlags.indices.any { (answers[questions.size + it] ?: 0) > 0 }
        if (flagged) {
            MahavaCard(containerColor = Color(0xFFFEE2E2)) {
                Text("⚠️ لطفاً هر چه زودتر با پزشک صحبت کن", fontWeight = FontWeight.Bold, color = Color(0xFFB91C1C))
                Text("پاسخ‌هایت نشانه‌ای دارد که نیاز به بررسی فوری دارد. اگر در خطر آسیب به خود هستی، با اورژانس ۱۱۵ یا صدای مشاور بهزیستی ۱۴۸۰ تماس بگیر.", color = Color(0xFF7F1D1D))
            }
        }
        val band = quiz.arr("bands").objs().firstOrNull { score <= (it.get("max")?.asInt ?: Int.MAX_VALUE) }
        if (band != null && !flagged) {
            val col = levelColor(band.s("level"))
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(col.copy(alpha = 0.1f)).padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Tag("سطح: " + levelFa(band.s("level")), col)
                Text(band.s("title"), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = col)
                Text("امتیاز ${fa(score)}", style = MaterialTheme.typography.bodySmall, color = MahavaTextSecondary)
            }
            Section("یعنی چه؟", band.s("meaning"))
            Section("قدم بعدی", band.s("next"))
        }
        Section("کی به پزشک مراجعه کنم؟", quiz.s("doctor"))
        MahavaCard {
            Text("منابع علمی", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            quiz.arr("sources").strings().forEach { Text("• $it", style = MaterialTheme.typography.bodySmall, color = MahavaTextSecondary) }
        }
        Text(data.s("disclaimer"), style = MaterialTheme.typography.bodySmall, color = MahavaPrimaryDeep)
        PrimaryButton("انجام دوباره") { answers = emptyMap(); index = 0; done = false }
    }
}

@Composable
private fun Section(title: String, body: String) {
    if (body.isBlank()) return
    MahavaCard {
        Text(title, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(body)
    }
}
