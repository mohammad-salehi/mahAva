package com.mahava.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.mahava.app.R
import com.mahava.app.data.db.*
import com.mahava.app.health.*
import com.mahava.app.ui.AppViewModel
import com.mahava.app.ui.components.*
import com.mahava.app.ui.theme.*
import com.mahava.app.util.JalaliDate
import com.mahava.app.util.PersianDigits
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID

/**
 * One entry per tool. [route] set = opens that screen directly (no in-between page);
 * otherwise the tool page "tools/<id>".
 */
data class ToolEntry(val id: String, val title: String, val subtitle: String, val icon: Int, val color: Color, val route: String? = null)

object ToolCatalog {
    val groups: List<Pair<String, List<ToolEntry>>> = listOf(
        "بدن و چرخه" to listOf(
            ToolEntry("cycle", "الگوی چرخه", "طول چرخه‌ها و پریودها", R.drawable.design_cycle, MahavaPrimary),
            ToolEntry("pain", "ردیاب درد", "جا، شدت، نوع و نتیجه", R.drawable.design_pain, MahavaMenstruation),
            ToolEntry("discharge", "ردیاب ترشحات", "نوع و علائم همراه", R.drawable.design_droplet, MahavaAccentSky),
            ToolEntry("symptoms", "تحلیل علائم", "پرتکرارترین علائمت", R.drawable.design_chart, MahavaAccentPeach),
            ToolEntry("pms", "PMS من", "روزهای پیش از پریود", R.drawable.design_calendar, MahavaMenstruation),
            ToolEntry("comparison", "مقایسهٔ چرخه‌ها", "هر چرخه کنار بقیه", R.drawable.design_chart, MahavaFertility)
        ),
        "حال و خواب" to listOf(
            ToolEntry("mood_energy", "حال و انرژی، همگام با چرخه", "ثبت و نمودار حال و انرژی", R.drawable.design_smile, MahavaAccentGold),
            ToolEntry("sleep", "خواب و چرخه", "ساعت و کیفیت خواب", R.drawable.design_moon, MahavaPrimaryDeep),
            ToolEntry("insights", "بینش‌های من", "الگوهایی که از ثبت‌هایت پیدا شد", R.drawable.design_sun, MahavaFertility),
            ToolEntry("recommendation", "توصیهٔ روزانه", "هر روز ساعت ۸ صبح", R.drawable.design_leaf, MahavaFertility)
        ),
        "پزشک و مراقبت" to listOf(
            ToolEntry("doctor", "سؤال‌های پزشک", "سؤال‌هایت را جمع کن", R.drawable.design_chat, MahavaPrimary),
            ToolEntry("appointments", "مراجعه‌های من", "قرارهای پزشکی", R.drawable.design_calendar, MahavaAccentSky),
            ToolEntry("doctor_report", "گزارش برای پزشک", "فایل PDF با جدول و خلاصه", R.drawable.design_document, MahavaPrimaryDeep, route = "doctor_pdf"),
            ToolEntry("test", "زمان تست بارداری", "اگر پریود دیر کرده", R.drawable.design_pregnancy_test, MahavaMenstruation, route = "late"),
            ToolEntry("pregnancy", "بارداری هفته‌به‌هفته", "اگر باردار هستی", R.drawable.design_sprout, MahavaFertility, route = "pregnancy")
        ),
        "بیشتر" to listOf(
            ToolEntry("body", "شناخت بدن", "مطالب علمی ساده با منبع", R.drawable.design_book, MahavaPrimary, route = "body"),
            ToolEntry("reminders", "یادآورها", "ثبت روزانه، پریود، آب، دارو", R.drawable.design_notes, MahavaAccentPeach),
            ToolEntry("partner", "همسر", "اتصال یا قطع اتصال", R.drawable.design_heart, MahavaMenstruation, route = "partner_hub")
        )
    )
    val all = groups.flatMap { it.second }
    fun find(id: String) = all.firstOrNull { it.id == id }
    fun routeFor(e: ToolEntry) = e.route ?: "tools/${e.id}"
}

private fun ToolRecord.fields(): JsonObject = runCatching { JsonParser.parseString(payloadJson).asJsonObject }.getOrDefault(JsonObject())
private fun JsonObject.text(key: String) = get(key)?.takeUnless { it.isJsonNull }?.asString.orEmpty()
private fun JsonObject.flag(key: String) = runCatching { get(key)?.asBoolean == true }.getOrDefault(false)
private fun shortDay(d: Long) = JalaliDate.from(LocalDate.ofEpochDay(d)).formatShortFa()
private fun monthYear(d: Long) = JalaliDate.from(LocalDate.ofEpochDay(d)).let { JalaliDate.monthNameFa(it.month) + " " + PersianDigits.toPersian(it.year % 100).padStart(2, '۰') }

@Composable
fun ToolTile(e: ToolEntry, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(20.dp)).background(softTint(e.color)).clickable(onClick = onClick)
            .padding(14.dp).heightIn(min = 112.dp).testTag("tool_${e.id}"),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(e.color.copy(alpha = 0.9f)), contentAlignment = Alignment.Center) {
            MahavaIcon(e.icon, Color.White, 24.dp)
        }
        Text(e.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        Text(e.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** The Tools tab: every tool as a colourful tile, grouped. */
@Composable
fun ToolsHomeScreen(onBack: (() -> Unit)?, onOpen: (String) -> Unit) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState()).testTag("tools_screen")) {
        ScreenHeader("ابزارها", onBack = onBack)
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            QuietInfo("هر ابزار یک کار مشخص دارد؛ ثبت کامل روزانه از دکمهٔ «ثبت» در پایین صفحه است.")
            ToolCatalog.groups.forEach { (group, items) ->
                SectionLabel(group)
                items.chunked(2).forEach { row ->
                    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { e -> ToolTile(e, Modifier.weight(1f).fillMaxHeight()) { onOpen(ToolCatalog.routeFor(e)) } }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
fun ToolsDetailScreen(vm: AppViewModel, tool: String, onBack: () -> Unit, onRoute: (String) -> Unit) {
    // Old links (checkin, mood, energy, sharing, privacy, education) land on their replacement.
    val id = when (tool) { "mood", "energy" -> "mood_energy"; else -> tool }
    when (id) {
        "checkin" -> { LaunchedEffect(Unit) { onBack(); onRoute("daily_log?day=${vm.today().toEpochDay()}") }; return }
        "sharing" -> { LaunchedEffect(Unit) { onBack(); onRoute("partner_hub") }; return }
        "privacy" -> { LaunchedEffect(Unit) { onBack(); onRoute("settings") }; return }
        "education" -> { LaunchedEffect(Unit) { onBack(); onRoute("body") }; return }
    }
    ToolCatalog.find(id)?.route?.let { r -> LaunchedEffect(Unit) { onBack(); onRoute(r) }; return }
    val state by vm.state.collectAsState()
    val records by vm.toolRecords.collectAsState()
    val live = records.filter { it.deletedAt == null }
    val notificationPermission = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) {}
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
    val scope = rememberCoroutineScope()
    var error by remember { mutableStateOf<String?>(null) }
    val run: (suspend () -> Unit) -> Unit = { action -> scope.launch { try { action(); error = null } catch (e: Exception) { error = "ذخیره نشد؛ دوباره تلاش کن." } } }
    LaunchedEffect(id, state.dailyLogs, state.periods) {
        if (id in setOf("cycle", "pms", "sleep", "mood_energy", "insights", "symptoms", "comparison")) runCatching { vm.tools.regenerateInsights() }
        if (id == "recommendation") runCatching { vm.tools.ensureRecommendation() }
    }
    val today = vm.today().toEpochDay()
    val entry = ToolCatalog.find(id)
    val color = entry?.color ?: MahavaPrimary
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState()).testTag("tool_screen_$id")) {
        ScreenHeader(entry?.title ?: "ابزارها", onBack = onBack)
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            when (id) {
                "mood_energy" -> {
                    MoodEnergyForm(vm, run)
                    MahavaCard(Modifier.testTag("tool_chart_mood_energy")) {
                        val r = rememberReportRange(vm, "tool_mood_energy", months = listOf(1, 3, 6, 12), allowCustom = false, tagPrefix = "tool_range")
                        val plan = remember(r.from, r.to) { TimeRange.plan(r.from, r.to) }
                        val mood = TimeRange.average(state.dailyLogs, plan) { CycleStats.mood(it) }
                        val energy = TimeRange.average(state.dailyLogs, plan) { CycleStats.energy(it) }
                        SectionLabel("حال و انرژی (میانگین ${plan.unitFa})")
                        if (mood.all { it == null } && energy.all { it == null }) EmptyChartNote("هنوز حال یا انرژی‌ای ثبت نشده؛ از فرم بالا شروع کن.")
                        else {
                            ChartFrame(plan.size) { LineChart(listOf(mood to MahavaPrimary, energy to MahavaAccentGold), minValue = 1f, maxValue = 5f); TimeAxis(plan.labels, centered = false) }
                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) { LegendDot(MahavaPrimary, "حال (۱ تا ۵)"); LegendDot(MahavaAccentGold, "انرژی (۱ تا ۵)") }
                            QuietInfo("راست = قدیمی‌تر، چپ = امروز. بازه‌های بدون ثبت خالی مانده‌اند.")
                        }
                    }
                    PhaseBars("میانگین انرژی در هر مرحلهٔ چرخه", CycleStats.byPhase(state.dailyLogs, state.periods, today) { CycleStats.energy(it) }, MahavaAccentGold, 5f)
                    PhaseBars("میانگین حال در هر مرحلهٔ چرخه", CycleStats.byPhase(state.dailyLogs, state.periods, today) { CycleStats.mood(it) }, MahavaPrimary, 5f)
                    InsightCards(live, setOf("insight_mood", "insight_energy"))
                }
                "sleep" -> {
                    SleepForm(vm, run)
                    MahavaCard(Modifier.testTag("tool_chart_sleep")) {
                        val r = rememberReportRange(vm, "tool_sleep", months = listOf(1, 3, 6, 12), allowCustom = false, tagPrefix = "tool_range")
                        val plan = remember(r.from, r.to) { TimeRange.plan(r.from, r.to) }
                        val sleep = TimeRange.average(state.dailyLogs, plan) { CycleStats.sleepHours(it) }
                        SectionLabel("ساعت خواب (میانگین ${plan.unitFa})")
                        if (sleep.any { it != null }) ChartFrame(plan.size) { BarChart(sleep, MahavaPrimaryDeep, maxValue = 12f); TimeAxis(plan.labels) }
                        val vals = state.dailyLogs.filter { it.epochDay in r.from..r.to }.mapNotNull { CycleStats.sleepHours(it) }
                        QuietInfo(if (vals.isEmpty()) "هنوز خوابی ثبت نشده." else "میانگین ${PersianDigits.toPersian(fmt(vals.average().toFloat()))} ساعت در ${PersianDigits.toPersian(vals.size)} شب ثبت‌شده")
                    }
                    PhaseBars("میانگین خواب در هر مرحلهٔ چرخه (ساعت)", CycleStats.byPhase(state.dailyLogs, state.periods, today) { CycleStats.sleepHours(it) }, MahavaPrimaryDeep, 10f)
                    InsightCards(live, setOf("insight_sleep"))
                }
                "pain" -> {
                    PainForm(vm, run)
                    MahavaCard(Modifier.testTag("tool_chart_pain")) {
                        val r = rememberReportRange(vm, "tool_pain", months = listOf(1, 3, 6, 12), allowCustom = false, tagPrefix = "tool_range")
                        val plan = remember(r.from, r.to) { TimeRange.plan(r.from, r.to) }
                        val pain = TimeRange.average(state.dailyLogs, plan) { l -> CycleStats.pain(l)?.toFloat() }
                        SectionLabel("شدت درد (میانگین ${plan.unitFa}، ۰ تا ۱۰)")
                        if (pain.any { it != null }) ChartFrame(plan.size) { BarChart(pain, MahavaMenstruation, maxValue = 10f); TimeAxis(plan.labels) }
                        val painDays = state.dailyLogs.filter { it.epochDay in r.from..r.to && (it.painScore ?: 0) > 0 }
                        QuietInfo("${PersianDigits.toPersian(painDays.size)} روز با درد ثبت شده${if (painDays.isNotEmpty()) "؛ میانگین شدت ${PersianDigits.toPersian(fmt(painDays.mapNotNull { it.painScore }.average().toFloat()))}" else ""}")
                    }
                    PhaseBars("میانگین درد در هر مرحله", CycleStats.byPhase(state.dailyLogs, state.periods, today) { l -> CycleStats.pain(l)?.toFloat() }, MahavaMenstruation, 10f)
                    val locs = state.dailyLogs.flatMap { it.painLocations.orEmpty().split(',').filter { s -> s.isNotBlank() } }.groupingBy { it }.eachCount().toList().sortedByDescending { it.second }
                    if (locs.isNotEmpty()) MahavaCard { SectionLabel("جای درد"); HorizontalBars(locs.take(6).map { LogLabels.painLocationFa(it.first) to it.second.toFloat() }, MahavaMenstruation, " روز") }
                }
                "discharge" -> {
                    DischargeForm(vm, run)
                    val recent = state.dailyLogs.filter { !it.discharge.isNullOrBlank() }.sortedByDescending { it.epochDay }.take(10)
                    if (recent.isNotEmpty()) MahavaCard {
                        SectionLabel("ثبت‌های اخیر")
                        SimpleTable(listOf(listOf("روز", "ترشحات", "همراه")) + recent.map { l ->
                            listOf(shortDay(l.epochDay), LogLabels.labelOf(LogLabels.DISCHARGE, l.discharge),
                                l.dischargeConcerns.orEmpty().split(',').filter { it.isNotBlank() }.joinToString("، ") { LogLabels.labelOf(LogLabels.DISCHARGE_CONCERNS, it) }.ifBlank { "—" })
                        }, listOf(1f, 1.2f, 1.2f))
                    }
                }
                "doctor", "appointments" -> {
                    var draft by rememberSaveable(id) { mutableStateOf("") }
                    var date by rememberSaveable(id) { mutableStateOf(vm.today().toString()) }
                    OutlinedTextField(draft, { if (it.length <= 1500) draft = it }, label = { Text(if (id == "doctor") "سؤال من از پزشک" else "عنوان و محل مراجعه") }, modifier = Modifier.fillMaxWidth())
                    if (id == "appointments") DateStepper(LocalDate.parse(date), onChange = { date = it.toString() }, min = vm.today().minusYears(1), max = vm.today().plusYears(2))
                    PrimaryButton("ذخیره", enabled = draft.isNotBlank()) { val value = draft; run { vm.tools.save("${id}_${UUID.randomUUID()}", id, mapOf("text" to value, "date" to date, "done" to false), LocalDate.parse(date).toEpochDay()); draft = "" } }
                    live.filter { it.type == id }.forEach { item ->
                        val fields = item.fields()
                        MahavaCard {
                            Text(fields.text("text"))
                            if (id == "appointments") QuietInfo(item.epochDay?.let { shortDay(it) } ?: fields.text("date"))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(fields.flag("done"), { value -> run { fields.addProperty("done", value); vm.tools.save(item.id, id, fields, item.epochDay) } })
                                Text(if (fields.flag("done")) "انجام شد" else "در انتظار")
                            }
                            if (id == "doctor") {
                                var answer by rememberSaveable(item.id, item.updatedAt) { mutableStateOf(fields.text("answer")) }
                                OutlinedTextField(answer, { if (it.length <= 1500) answer = it }, label = { Text("پاسخ پزشک یا یادداشت") }, modifier = Modifier.fillMaxWidth())
                                TextButton(onClick = { run { fields.addProperty("answer", answer); vm.tools.save(item.id, id, fields, item.epochDay) } }) { Text("ذخیرهٔ پاسخ") }
                            }
                            TextButton(onClick = { run { vm.tools.delete(item.id) } }) { Text("حذف") }
                        }
                    }
                }
                "recommendation" -> {
                    MahavaCard(containerColor = softTint(MahavaFertility)) {
                        Text("هر روز ساعت ۸ صبح یک توصیهٔ کوتاه برایت می‌آید.", style = MaterialTheme.typography.titleMedium)
                        QuietInfo("ممکن است اندروید برای صرفه‌جویی باتری چند دقیقه دیرتر نشانش بدهد.")
                        if (android.os.Build.VERSION.SDK_INT >= 33) TextButton(onClick = { notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS) }) { Text("اجازهٔ نمایش اعلان") }
                    }
                    live.filter { it.type == "recommendation" }.sortedByDescending { it.epochDay }.forEach { item ->
                        val rec = runCatching { Gson().fromJson(item.payloadJson, DailyRecommendation::class.java) }.getOrNull() ?: return@forEach
                        MahavaCard {
                            Text(item.epochDay?.let { shortDay(it) } ?: "", style = MaterialTheme.typography.titleMedium)
                            Text(rec.body)
                            SectionLabel("چرا این توصیه را دیدم؟")
                            Text(rec.reasonText)
                            rec.medicalSourceIds.split(',').filter { it.isNotBlank() }.forEach { sid -> TextButton(onClick = { vm.scienceSource(sid)?.url?.let { uriHandler.openUri(it) } }) { Text(vm.scienceSource(sid)?.titleFa ?: sid) } }
                            if (!rec.opened) TextButton(onClick = { run { vm.tools.save(item.id, item.type, rec.copy(opened = true), item.epochDay) } }) { Text("خواندم") }
                        }
                    }
                }
                "reminders" -> {
                    listOf("daily_log" to "ثبت روزانه", "period" to "نزدیک شدن پریود", "water" to "نوشیدن آب", "sleep" to "خواب", "medication" to "داروی خودم").forEach { (rid, label) ->
                        val current = state.reminders.find { it.id == rid } ?: ReminderPrefEntity(id = rid)
                        MahavaCard {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(label, Modifier.weight(1f)); Switch(current.enabled, { enabled ->
                                    if (enabled && android.os.Build.VERSION.SDK_INT >= 33) notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                                    vm.saveReminder(current.copy(enabled = enabled))
                                })
                            }
                            if (current.enabled) {
                                SectionLabel("ساعت: ${PersianDigits.toPersian(current.hour)}:${PersianDigits.toPersian(current.minute.toString().padStart(2, '0'))}")
                                Slider(current.hour.toFloat(), { vm.saveReminder(current.copy(hour = it.toInt())) }, valueRange = 6f..23f, steps = 16)
                                if (rid == "medication") {
                                    var medication by rememberSaveable(rid, current.medicationLabel) { mutableStateOf(current.medicationLabel.orEmpty()) }
                                    OutlinedTextField(medication, { if (it.length <= 200) medication = it }, label = { Text("نام دارو (همان که پزشک گفته)") }, modifier = Modifier.fillMaxWidth())
                                    TextButton(onClick = { vm.saveReminder(current.copy(medicationLabel = medication.ifBlank { null })) }) { Text("ذخیره") }
                                }
                            }
                        }
                    }
                }
                "cycle", "comparison" -> CycleTool(vm, id == "comparison")
                "symptoms" -> {
                    val r = rememberReportRange(vm, "tool_symptoms", months = listOf(1, 3, 6, 12), allowCustom = false, tagPrefix = "tool_range")
                    val logged = state.dailyLogs.filter { it.epochDay in r.from..r.to }
                    MahavaCard {
                        SectionLabel("علائم پرتکرار")
                        val counts = CycleStats.symptomCounts(logged).take(8)
                        if (counts.isEmpty()) QuietInfo("هنوز علامتی ثبت نشده.") else HorizontalBars(counts.map { LogLabels.symptomFa(it.first) to it.second.toFloat() }, MahavaAccentPeach, " روز")
                        QuietInfo("${PersianDigits.toPersian(logged.size)} روز دارای ثبت؛ روزهای بدون ثبت حساب نشده‌اند.")
                    }
                    MahavaCard {
                        SectionLabel("حال‌های ثبت‌شده")
                        val counts = CycleStats.moodCounts(logged).take(8)
                        if (counts.isEmpty()) QuietInfo("هنوز حالی ثبت نشده.") else HorizontalBars(counts.map { LogLabels.moodFa(it.first) to it.second.toFloat() }, MahavaPrimary, " روز")
                    }
                    SecondaryButton("الگوهای تکرارشونده") { onRoute("symptom_patterns") }
                }
                "pms" -> {
                    val pre = state.dailyLogs.filter { CycleStats.phaseOf(it.epochDay, state.periods, today) == "premenstrual" }
                    val other = state.dailyLogs.filter { CycleStats.phaseOf(it.epochDay, state.periods, today).let { p -> p != null && p != "premenstrual" } }
                    MahavaCard {
                        SectionLabel("علائم در ۵ روز پیش از پریود")
                        val counts = CycleStats.symptomCounts(pre).take(6)
                        if (counts.isEmpty()) QuietInfo("هنوز در روزهای پیش از پریود علامتی ثبت نشده.")
                        else HorizontalBars(counts.map { (k, c) -> LogLabels.symptomFa(k) to (100f * c / pre.size.coerceAtLeast(1)) }, MahavaMenstruation, "٪", 100f)
                        QuietInfo("درصد روزهای ثبت‌شده در این بازه (${PersianDigits.toPersian(pre.size)} روز).")
                    }
                    MahavaCard {
                        SectionLabel("همین علائم در بقیهٔ روزها")
                        val counts = CycleStats.symptomCounts(other).take(6)
                        if (counts.isEmpty()) QuietInfo("دادهٔ کافی نیست.")
                        else HorizontalBars(counts.map { (k, c) -> LogLabels.symptomFa(k) to (100f * c / other.size.coerceAtLeast(1)) }, MahavaPrimary, "٪", 100f)
                    }
                    InsightCards(live, null, prefix = "insight_pms_")
                    QuietInfo("این مقایسه تشخیص PMS نیست؛ اگر علائم زندگی روزمره‌ات را مختل می‌کند با پزشک صحبت کن.")
                }
                "insights" -> {
                    val list = live.filter { it.type == "insight" }
                    if (list.isEmpty()) QuietInfo("با ثبت چند چرخهٔ کامل، اینجا الگوهای خودت را می‌بینی.")
                    InsightCards(live, null)
                }
                else -> QuietInfo("این ابزار پیدا نشد.")
            }
            HealthContextNote(HealthNote.GENERAL)
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun PhaseBars(title: String, data: List<Triple<String, Float, Int>>, color: Color, max: Float) {
    if (data.isEmpty()) return
    MahavaCard {
        SectionLabel(title)
        HorizontalBars(data.map { CycleStats.phaseTitleFa(it.first) to it.second }, color, max = max)
        QuietInfo("از ${PersianDigits.toPersian(data.sumOf { it.third })} روز ثبت‌شده. هم‌زمانی به معنی علت نیست.")
    }
}

@Composable
private fun InsightCards(live: List<ToolRecord>, ids: Set<String>?, prefix: String? = null) {
    live.filter { it.type == "insight" && (ids == null || it.id in ids) && (prefix == null || it.id.startsWith(prefix)) }.forEach { item ->
        val insight = runCatching { Gson().fromJson(item.payloadJson, StoredInsight::class.java) }.getOrNull() ?: return@forEach
        MahavaCard(containerColor = softTint(MahavaFertility)) {
            Text(insight.title, style = MaterialTheme.typography.titleMedium)
            Text(PersianDigits.toPersian(insight.description))
            QuietInfo("بر اساس ${PersianDigits.toPersian(insight.sampleCount)} نمونه")
        }
    }
}

@Composable
private fun CycleTool(vm: AppViewModel, comparison: Boolean) {
    val state by vm.state.collectAsState()
    val today = vm.today().toEpochDay()
    val cycles = CycleStats.cycles(state.periods, today)
    val complete = cycles.filter { it.length != null }
    if (cycles.isEmpty()) { QuietInfo("اول تاریخ پریودهایت را ثبت کن."); return }
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        val lens = complete.mapNotNull { it.length }
        val bleeds = cycles.mapNotNull { it.bleedDays }
        StatTile("میانگین چرخه", if (lens.isEmpty()) "—" else PersianDigits.toPersian(fmt(lens.average().toFloat())), "روز", MahavaPrimary, Modifier.weight(1f).fillMaxHeight())
        StatTile("میانگین پریود", if (bleeds.isEmpty()) "—" else PersianDigits.toPersian(fmt(bleeds.average().toFloat())), "روز", MahavaMenstruation, Modifier.weight(1f).fillMaxHeight())
        StatTile("دامنهٔ تغییر", if (lens.size < 2) "—" else PersianDigits.toPersian(lens.max() - lens.min()), "روز", MahavaFertility, Modifier.weight(1f).fillMaxHeight())
    }
    if (complete.isNotEmpty()) MahavaCard {
        SectionLabel("طول چرخه‌ها")
        QuietInfo("همهٔ چرخه‌ها از اولین پریود ثبت‌شده (${shortDay(cycles.first().start)})")
        ChartFrame(complete.size, minSlot = 40.dp) {
            BarChart(complete.map { it.length!!.toFloat() }, MahavaPrimary, maxValue = (complete.maxOf { it.length!! } + 4).toFloat(), labels = complete.map { monthYear(it.start) }, showValues = complete.size <= 12)
        }
    }
    val withBleed = cycles.filter { it.bleedDays != null }
    if (withBleed.isNotEmpty()) MahavaCard {
        SectionLabel("مدت پریودها")
        ChartFrame(withBleed.size, minSlot = 40.dp) {
            BarChart(withBleed.map { it.bleedDays!!.toFloat() }, MahavaMenstruation, height = 90.dp, maxValue = 10f, labels = withBleed.map { monthYear(it.start) }, showValues = withBleed.size <= 12)
        }
    }
    val last = cycles.last()
    MahavaCard { Text("چرخهٔ فعلی: روز ${PersianDigits.toPersian((today - last.start + 1).toInt())}", style = MaterialTheme.typography.titleMedium); QuietInfo("طول چرخهٔ فعلی هنوز معلوم نیست و در میانگین حساب نمی‌شود.") }
    if (comparison) MahavaCard {
        SectionLabel("مقایسهٔ چرخه‌ها")
        val rows = cycles.reversed().map { c ->
            val logs = state.dailyLogs.filter { it.epochDay >= c.start && it.epochDay < (c.next ?: (today + 1)) }
            val painAvg = logs.mapNotNull { it.painScore?.takeIf { p -> p > 0 } }.takeIf { it.isNotEmpty() }?.average()?.toFloat()
            listOf(shortDay(c.start), c.length?.let { PersianDigits.toPersian(it) } ?: "جاری", c.bleedDays?.let { PersianDigits.toPersian(it) } ?: "—",
                PersianDigits.toPersian(logs.count { !it.physicalSymptoms.isNullOrBlank() }), painAvg?.let { PersianDigits.toPersian(fmt(it)) } ?: "—")
        }
        SimpleTable(listOf(listOf("شروع", "طول", "پریود", "روز علامت", "میانگین درد")) + rows, listOf(1.3f, 0.8f, 0.8f, 1f, 1f))
    }
}

// ---------- Focused entry forms (each tool has its own; the full day is the "ثبت" tab) ----------

@Composable
private fun DayPicker(vm: AppViewModel, date: LocalDate, onChange: (LocalDate) -> Unit) =
    DateStepper(date, onChange = onChange, max = vm.today(), min = vm.today().minusYears(5))

@Composable
private fun SaveRow(vm: AppViewModel, day: LocalDate, enabled: Boolean = true, saved: Boolean, onSave: () -> Unit) {
    PrimaryButton("ذخیره برای ${if (day == vm.today()) "امروز" else shortDay(day.toEpochDay())}", enabled = enabled, onClick = onSave)
    if (saved) QuietInfo("ذخیره شد ✓")
}

@Composable
private fun MoodEnergyForm(vm: AppViewModel, run: (suspend () -> Unit) -> Unit) {
    val state by vm.state.collectAsState()
    var date by rememberSaveable { mutableStateOf(vm.today().toString()) }
    val day = LocalDate.parse(date)
    val old = state.dailyLogs.find { it.epochDay == day.toEpochDay() }
    var feeling by rememberSaveable(date, old?.updatedAt) { mutableStateOf(old?.overallFeeling) }
    var moods by rememberSaveable(date, old?.updatedAt) { mutableStateOf(old?.moods.orEmpty()) }
    var energy by rememberSaveable(date, old?.updatedAt) { mutableStateOf(old?.let { CycleStats.energy(it)?.toInt()?.toString() }) }
    var saved by remember(date) { mutableStateOf(false) }
    MahavaCard(Modifier.testTag("form_mood_energy")) {
        DayPicker(vm, day) { date = it.toString() }
        SectionLabel("امروز چطوری؟"); ChipsFlow(LogLabels.FEELINGS, feeling, "feel") { feeling = if (feeling == it) null else it }
        SectionLabel("حس و حال (چند مورد)")
        val sel = moods.split(',').filter { it.isNotBlank() }.toSet()
        MultiChipsFlow(LogLabels.MOODS, sel, "moods") { k -> moods = (if (k in sel) sel - k else sel + k).joinToString(",") }
        SectionLabel("انرژی از ۱ تا ۵"); ChipsFlow((1..5).map { it.toString() to PersianDigits.toPersian(it) }, energy, "en") { energy = if (energy == it) null else it }
        SaveRow(vm, day, saved = saved) {
            run {
                val e = energy?.toIntOrNull()
                vm.tools.updateDaily(day.toEpochDay()) { it.copy(overallFeeling = feeling, moods = moods.ifBlank { null }, energyScore = e,
                    energy = e?.let { v -> if (v <= 2) "low" else if (v == 3) "medium" else "high" } ?: it.energy) }
                saved = true
            }
        }
    }
}

/** Hours picker 0–14 in half-hour steps; stored as minutes (and hours) for older readers. */
@Composable
fun SleepHoursPicker(hours: Float?, onChange: (Float?) -> Unit) {
    Text(if (hours == null) "ثبت نشده" else "${PersianDigits.toPersian(fmt(hours))} ساعت", style = MaterialTheme.typography.headlineMedium, color = MahavaPrimaryDeep)
    Slider(value = hours ?: 7f, onValueChange = { onChange((Math.round(it * 2f) / 2f).coerceIn(0f, 14f)) }, valueRange = 0f..14f, steps = 27, modifier = Modifier.testTag("sleep_hours_slider"))
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(6f, 7f, 8f).forEach { h -> ChoiceChipPill("${PersianDigits.toPersian(fmt(h))} ساعت", hours == h) { onChange(h) } }
        if (hours != null) TextButton(onClick = { onChange(null) }) { Text("پاک کردن") }
    }
}

@Composable
private fun SleepForm(vm: AppViewModel, run: (suspend () -> Unit) -> Unit) {
    val state by vm.state.collectAsState()
    var date by rememberSaveable { mutableStateOf(vm.today().toString()) }
    val day = LocalDate.parse(date)
    val old = state.dailyLogs.find { it.epochDay == day.toEpochDay() }
    var hours by remember(date, old?.updatedAt) { mutableStateOf(old?.let { CycleStats.sleepHours(it) }?.let { Math.round(it * 2f) / 2f }) }
    var quality by rememberSaveable(date, old?.updatedAt) { mutableStateOf(old?.sleepQuality) }
    var saved by remember(date) { mutableStateOf(false) }
    MahavaCard(Modifier.testTag("form_sleep")) {
        DayPicker(vm, day) { date = it.toString() }
        SectionLabel("خواب دیشب چند ساعت بود؟")
        SleepHoursPicker(hours) { hours = it }
        SectionLabel("کیفیت خواب"); ChipsFlow(listOf("poor" to "بد", "ok" to "معمولی", "good" to "خوب"), quality, "sq") { quality = if (quality == it) null else it }
        SaveRow(vm, day, saved = saved) {
            run {
                vm.tools.updateDaily(day.toEpochDay()) { it.copy(sleepMinutes = hours?.let { h -> Math.round(h * 60) }, sleepHours = hours, sleepQuality = quality) }
                saved = true
            }
        }
    }
}

@Composable
private fun PainForm(vm: AppViewModel, run: (suspend () -> Unit) -> Unit) {
    val state by vm.state.collectAsState()
    var date by rememberSaveable { mutableStateOf(vm.today().toString()) }
    val day = LocalDate.parse(date)
    val old = state.dailyLogs.find { it.epochDay == day.toEpochDay() }
    var score by remember(date, old?.updatedAt) { mutableIntStateOf(old?.painScore ?: -1) }
    var locs by rememberSaveable(date, old?.updatedAt) { mutableStateOf(old?.painLocations.orEmpty()) }
    var type by rememberSaveable(date, old?.updatedAt) { mutableStateOf(old?.painType) }
    var duration by rememberSaveable(date, old?.updatedAt) { mutableStateOf(old?.painDurationMinutes?.let { m -> LogLabels.PAIN_DURATIONS.minByOrNull { kotlin.math.abs(it.first.toInt() - m) }?.first }) }
    var actions by rememberSaveable(date, old?.updatedAt) { mutableStateOf(old?.painAction.orEmpty()) }
    var result by rememberSaveable(date, old?.updatedAt) { mutableStateOf(old?.painResult) }
    var saved by remember(date) { mutableStateOf(false) }
    MahavaCard(Modifier.testTag("form_pain")) {
        DayPicker(vm, day) { date = it.toString() }
        SectionLabel("شدت درد: ${if (score < 0) "ثبت نشده" else PersianDigits.toPersian(score) + " از ۱۰"}")
        Slider(if (score < 0) 0f else score.toFloat(), { score = it.toInt() }, valueRange = 0f..10f, steps = 9, colors = SliderDefaults.colors(thumbColor = MahavaMenstruation, activeTrackColor = MahavaMenstruation))
        SectionLabel("کجا درد داری؟")
        val sel = locs.split(',').filter { it.isNotBlank() }.toSet()
        MultiChipsFlow(LogLabels.PAIN_LOCATIONS, sel, "ploc") { k -> locs = (if (k in sel) sel - k else sel + k).joinToString(",") }
        SectionLabel("چه نوع دردی؟"); ChipsFlow(LogLabels.PAIN_TYPES, type, "ptype") { type = if (type == it) null else it }
        SectionLabel("چقدر طول کشید؟"); ChipsFlow(LogLabels.PAIN_DURATIONS, duration, "pdur") { duration = if (duration == it) null else it }
        SectionLabel("چه کار کردی؟")
        val asel = actions.split(',').filter { it.isNotBlank() }.toSet()
        MultiChipsFlow(LogLabels.PAIN_ACTIONS, asel, "pact") { k -> actions = (if (k in asel) asel - k else asel + k).joinToString(",") }
        SectionLabel("نتیجه"); ChipsFlow(LogLabels.PAIN_RESULTS, result, "pres") { result = if (result == it) null else it }
        SaveRow(vm, day, saved = saved) {
            run {
                vm.tools.updateDaily(day.toEpochDay()) {
                    it.copy(painScore = score.takeIf { s -> s >= 0 }, painLocations = locs.ifBlank { null }, painType = type,
                        painDurationMinutes = duration?.toIntOrNull(), painAction = actions.ifBlank { null }, painResult = result,
                        physicalSymptoms = if (score > 0) (it.physicalSymptoms.orEmpty().split(',').filter { s -> s.isNotBlank() }.toSet() + "pain").joinToString(",") else it.physicalSymptoms,
                        noSymptoms = if (score > 0) false else it.noSymptoms)
                }
                saved = true
            }
        }
        if (score >= 7) Text("اگر درد خیلی شدید، ناگهانی یا همراه با تب است، زودتر به پزشک مراجعه کن.", color = MahavaDanger)
    }
}

@Composable
private fun DischargeForm(vm: AppViewModel, run: (suspend () -> Unit) -> Unit) {
    val state by vm.state.collectAsState()
    var date by rememberSaveable { mutableStateOf(vm.today().toString()) }
    val day = LocalDate.parse(date)
    val old = state.dailyLogs.find { it.epochDay == day.toEpochDay() }
    var discharge by rememberSaveable(date, old?.updatedAt) { mutableStateOf(old?.discharge?.let { if (it == "stretchy") "eggwhite" else it }) }
    var concerns by rememberSaveable(date, old?.updatedAt) { mutableStateOf(old?.dischargeConcerns.orEmpty()) }
    var saved by remember(date) { mutableStateOf(false) }
    MahavaCard(Modifier.testTag("form_discharge")) {
        DayPicker(vm, day) { date = it.toString() }
        SectionLabel("ترشحات امروز"); ChipsFlow(LogLabels.DISCHARGE, discharge, "dis") { discharge = if (discharge == it) null else it }
        SectionLabel("علائم همراه")
        val sel = concerns.split(',').filter { it.isNotBlank() }.toSet()
        MultiChipsFlow(LogLabels.DISCHARGE_CONCERNS, sel, "disc") { k -> concerns = (if (k in sel) sel - k else sel + k).joinToString(",") }
        SaveRow(vm, day, saved = saved) {
            run { vm.tools.updateDaily(day.toEpochDay()) { it.copy(discharge = discharge, dischargeConcerns = concerns.ifBlank { null }) }; saved = true }
        }
        QuietInfo("ثبت ترشحات تشخیص نیست. اگر بو، خارش یا سوزش ادامه داشت، با پزشک مشورت کن.")
    }
}

@Composable
fun ToolsTheme(content: @Composable () -> Unit) {
    if (!androidx.compose.foundation.isSystemInDarkTheme()) { MaterialTheme(colorScheme = MaterialTheme.colorScheme.copy(outline = MahavaBorder, outlineVariant = MahavaBorder)) { content() }; return }
    val colors = darkColorScheme(primary = Color(0xFFD0B8EF), surface = Color(0xFF292330), background = Color(0xFF19151F), onSurface = Color(0xFFF2EAF7), onBackground = Color(0xFFF2EAF7), onSurfaceVariant = Color(0xFFBDB1C8))
    val type = MaterialTheme.typography
    MaterialTheme(colorScheme = colors, typography = type.copy(bodyLarge = type.bodyLarge.copy(color = colors.onSurface), bodyMedium = type.bodyMedium.copy(color = colors.onSurface), bodySmall = type.bodySmall.copy(color = colors.onSurfaceVariant), titleMedium = type.titleMedium.copy(color = colors.onSurface), titleLarge = type.titleLarge.copy(color = colors.onSurface), headlineLarge = type.headlineLarge.copy(color = colors.onSurface))) { content() }
}
