package com.mahava.app.ui.screens

import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.mahava.app.MahavaApplication
import com.mahava.app.R
import com.mahava.app.cycle.PredictionKind
import com.mahava.app.ui.AppViewModel
import com.mahava.app.ui.components.*
import com.mahava.app.ui.theme.*
import com.mahava.app.util.JalaliDate
import com.mahava.app.util.PersianDigits
import java.time.LocalDate
import androidx.compose.foundation.horizontalScroll
import com.mahava.app.health.CycleStats
import com.mahava.app.health.LogLabels
import com.mahava.app.reports.DoctorReportData
import com.mahava.app.reports.DoctorReportPdf

@Composable
fun ReportsScreen(
    vm: AppViewModel,
    onSettings: () -> Unit,
    onDoctor: () -> Unit,
    onLate: () -> Unit,
    onPatterns: () -> Unit = {},
    onTrends: () -> Unit = {},
    onChecker: () -> Unit = {},
    onInsight: () -> Unit = {},
    onAccount: () -> Unit = {},
    onHealthAnalytics: () -> Unit = onTrends
) {
    val state by vm.state.collectAsState()
    val premium by vm.isPremium.collectAsState()
    val today = vm.today().toEpochDay()
    val pregnant = state.health?.appMode == com.mahava.app.health.models.AppHealthMode.PREGNANCY
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState()).testTag("reports_screen")) {
        ScreenHeader("گزارش‌های من", onSettings = onSettings)
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Default «از اولین ثبت»: from her first ever entry to today.
            val range = rememberReportRange(vm, "reports")
            val fromEpoch = range.from; val toEpoch = range.to
            val cycles = CycleStats.cycles(state.periods, today).filter { it.start in fromEpoch..toEpoch }
            val lengths = cycles.mapNotNull { it.length }
            val bleedLens = cycles.mapNotNull { it.bleedDays }
            val logs = state.dailyLogs.filter { it.epochDay in fromEpoch..toEpoch }
            // Day / week / month buckets depending on how long the range is.
            val plan = remember(fromEpoch, toEpoch) { com.mahava.app.health.TimeRange.plan(fromEpoch, toEpoch) }
            fun series(metric: (com.mahava.app.data.db.DailyLogEntity) -> Float?) = com.mahava.app.health.TimeRange.average(logs, plan, metric)
            if (!premium) {
                PremiumPaywallCard(vm, "گزارش‌ها و الگوها", "تحلیل ثبت‌های واقعی و گزارش پزشک با اشتراک ماه باز می‌شود.", onAccount)
            } else {
                if (logs.isEmpty() && cycles.isEmpty()) QuietInfo("در این بازه ثبت کافی نداریم؛ شروع و پایان پریود و علائم روزانه‌ات را ثبت کن.")
                MahavaCard(containerColor = softTint(MahavaPrimaryDeep), modifier = Modifier.clickable(onClick = onDoctor).testTag("open_doctor")) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(44.dp).background(MahavaPrimaryDeep, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) { MahavaIcon(R.drawable.design_document, androidx.compose.ui.graphics.Color.White) }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("گزارش برای پزشک (PDF)", style = MaterialTheme.typography.titleMedium)
                            QuietInfo("خلاصه، جدول چرخه‌ها، علائم و درد؛ قابل ذخیره و ارسال")
                        }
                        Text("‹", style = MaterialTheme.typography.headlineMedium, color = MahavaPrimaryDeep)
                    }
                }
                if (pregnant) {
                    MahavaCard {
                        SectionLabel("خلاصهٔ بارداری")
                        Text(state.health?.pregnancyProgress?.labelFa ?: "تاریخ مبنای بارداری را تنظیم کن.", style=MaterialTheme.typography.headlineLarge)
                        QuietInfo("${PersianDigits.toPersian(logs.size)} روز دارای ثبت در بازهٔ انتخاب‌شده؛ روزهای بدون ثبت نامشخص‌اند.")
                    }
                }
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile("میانگین چرخه", if (lengths.isEmpty()) "—" else PersianDigits.toPersian(fmt(lengths.average().toFloat())), if (lengths.isEmpty()) "سابقه کم است" else "روز، از ${PersianDigits.toPersian(lengths.size)} چرخه", MahavaPrimary, Modifier.weight(1f).fillMaxHeight())
                    StatTile("مدت پریود", if (bleedLens.isEmpty()) "—" else PersianDigits.toPersian(fmt(bleedLens.average().toFloat())), "روز", MahavaMenstruation, Modifier.weight(1f).fillMaxHeight())
                    StatTile("روزهای ثبت", PersianDigits.toPersian(logs.size), "روز", MahavaFertility, Modifier.weight(1f).fillMaxHeight())
                }
                if (!pregnant && lengths.isNotEmpty()) MahavaCard(Modifier.testTag("reports_lengths")) {
                    SectionLabel("طول چرخه‌ها")
                    val shown = cycles.filter { it.length != null }
                    ChartFrame(shown.size, minSlot = 40.dp) {
                        BarChart(shown.map { it.length!!.toFloat() }, MahavaPrimary, maxValue = (lengths.max() + 4).toFloat(), labels = shown.map { cycleLabel(it.start) }, showValues = shown.size <= 12)
                    }
                    QuietInfo("چرخهٔ جاری چون تمام نشده در میانگین نیست.")
                }
                if (!pregnant && bleedLens.isNotEmpty()) MahavaCard(Modifier.testTag("reports_period_lengths")) {
                    SectionLabel("مدت پریودها")
                    val withBleed = cycles.filter { it.bleedDays != null }
                    ChartFrame(withBleed.size, minSlot = 40.dp) {
                        BarChart(withBleed.map { it.bleedDays!!.toFloat() }, MahavaMenstruation, height = 90.dp, maxValue = 10f, labels = withBleed.map { cycleLabel(it.start) }, showValues = withBleed.size <= 12)
                    }
                }
                if (cycles.isNotEmpty()) MahavaCard(Modifier.testTag("reports_cycle_table")) {
                    SectionLabel("جدول چرخه‌ها")
                    SimpleTable(listOf(listOf("شروع", "مدت پریود", "طول چرخه", "روز درد")) + cycles.reversed().map { c ->
                        val inCycle = state.dailyLogs.filter { it.epochDay >= c.start && it.epochDay < (c.next ?: (today + 1)) }
                        listOf(JalaliDate.from(LocalDate.ofEpochDay(c.start)).formatShortFa(), c.bleedDays?.let { PersianDigits.toPersian(it) } ?: "—",
                            c.length?.let { PersianDigits.toPersian(it) } ?: "جاری", PersianDigits.toPersian(inCycle.count { (it.painScore ?: 0) > 0 }))
                    }, listOf(1.3f, 1f, 1f, 1f))
                }
                MahavaCard(Modifier.testTag("open_patterns").clickable(onClick = onPatterns)) {
                    SectionLabel("علائم پرتکرار")
                    val counts = CycleStats.symptomCounts(logs).take(8)
                    if (counts.isEmpty()) QuietInfo("در این بازه علامتی ثبت نشده.")
                    else HorizontalBars(counts.map { LogLabels.symptomFa(it.first) to it.second.toFloat() }, MahavaAccentPeach, " روز")
                    QuietInfo("${PersianDigits.toPersian(logs.size)} روز دارای ثبت؛ نبود ثبت به معنی نبود علامت نیست.")
                    Text("الگوهای تکرارشونده ‹", color=MahavaPrimaryDeep)
                }
                val painW = series { it.painScore?.toFloat() }
                if (painW.any { it != null }) MahavaCard(Modifier.testTag("reports_pain")) {
                    SectionLabel("درد (میانگین ${plan.unitFa}، ۰ تا ۱۰)")
                    ChartFrame(plan.size) { BarChart(painW, MahavaMenstruation, height = 100.dp, maxValue = 10f); TimeAxis(plan.labels) }
                    val locs = logs.flatMap { it.painLocations.orEmpty().split(',').filter { s -> s.isNotBlank() } }.groupingBy { it }.eachCount().toList().sortedByDescending { it.second }.take(4)
                    if (locs.isNotEmpty()) { Spacer(Modifier.height(8.dp)); HorizontalBars(locs.map { LogLabels.painLocationFa(it.first) to it.second.toFloat() }, MahavaMenstruation, " روز") }
                }
                val moodW = series { CycleStats.mood(it) }; val energyW = series { CycleStats.energy(it) }
                if (moodW.any { it != null } || energyW.any { it != null }) MahavaCard(Modifier.testTag("reports_mood")) {
                    SectionLabel("حال و انرژی (میانگین ${plan.unitFa})")
                    ChartFrame(plan.size) { LineChart(listOf(moodW to MahavaPrimary, energyW to MahavaAccentGold), minValue = 1f, maxValue = 5f); TimeAxis(plan.labels, centered = false) }
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) { LegendDot(MahavaPrimary, "حال"); LegendDot(MahavaAccentGold, "انرژی") }
                }
                val sleepW = series { CycleStats.sleepHours(it) }
                if (sleepW.any { it != null }) MahavaCard(Modifier.testTag("reports_sleep")) {
                    SectionLabel("خواب (میانگین ${plan.unitFa}، ساعت)")
                    ChartFrame(plan.size) { BarChart(sleepW, MahavaPrimaryDeep, height = 100.dp, maxValue = 12f); TimeAxis(plan.labels) }
                }
                if (!pregnant) {
                    val m = CycleStats.byPhase(logs, state.periods, today) { CycleStats.mood(it) }.associateBy { it.first }
                    val e = CycleStats.byPhase(logs, state.periods, today) { CycleStats.energy(it) }.associateBy { it.first }
                    val sl = CycleStats.byPhase(logs, state.periods, today) { CycleStats.sleepHours(it) }.associateBy { it.first }
                    val pn = CycleStats.byPhase(logs, state.periods, today) { it.painScore?.toFloat() }.associateBy { it.first }
                    val phases = CycleStats.PHASES.filter { it in m || it in e || it in sl || it in pn }
                    if (phases.isNotEmpty()) MahavaCard(Modifier.testTag("reports_phase_table")) {
                        SectionLabel("الگوی تو در مراحل چرخه")
                        SimpleTable(listOf(listOf("مرحله", "حال", "انرژی", "خواب", "درد")) + phases.map { ph ->
                            listOf(CycleStats.phaseTitleFa(ph), m[ph]?.let { PersianDigits.toPersian(fmt(it.second)) } ?: "—", e[ph]?.let { PersianDigits.toPersian(fmt(it.second)) } ?: "—",
                                sl[ph]?.let { PersianDigits.toPersian(fmt(it.second)) } ?: "—", pn[ph]?.let { PersianDigits.toPersian(fmt(it.second)) } ?: "—")
                        }, listOf(1.6f, 0.8f, 0.8f, 0.8f, 0.8f))
                        QuietInfo("میانگین روزهای ثبت‌شده؛ حال و انرژی از ۵، درد از ۱۰. هم‌زمانی به معنی علت نیست.")
                    }
                }
                MahavaCard {
                    CompanionAction("تحلیل کامل", "چرخه، خواب و علائم", R.drawable.design_chart, onHealthAnalytics)
                    CompanionAction("راهنمای بررسی علائم", "آموزشی، بدون تشخیص", R.drawable.design_clipboard, onChecker)
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/** Month of a cycle start, with the year when the list spans years ("مهر ۰۵"). */
private fun cycleLabel(start: Long): String {
    val j = JalaliDate.from(LocalDate.ofEpochDay(start))
    return JalaliDate.monthNameFa(j.month) + " " + PersianDigits.toPersian(j.year % 100).padStart(2, '۰')
}

private fun bleedFa(v: String?) = bleedingFa(v) ?: "-"
private fun testFa(v: String?) = when (v) { "positive" -> "مثبت"; "negative" -> "منفی"; "invalid" -> "نامعتبر"; else -> "-" }

@Composable
fun DoctorReportScreen(vm: AppViewModel, onBack: () -> Unit, onAccount: () -> Unit = {}) {
    val premium by vm.isPremium.collectAsState()
    if (!premium) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).testTag("doctor_screen")) {
            ScreenHeader("گزارش برای پزشک", onBack = onBack)
            PremiumPaywallCard(vm = vm, titleFa = "گزارش برای پزشک", benefitFa = "با اشتراک ماه باز می‌شه.", onOpenAccount = onAccount)
        }
        return
    }
    val state by vm.state.collectAsState()
    val records by vm.toolRecords.collectAsState()
    val ctx = LocalContext.current
    var includeNotes by remember { mutableStateOf(false) }
    var includeIntimate by remember { mutableStateOf(false) }
    var includeTests by remember { mutableStateOf(true) }
    var includeQuestions by remember { mutableStateOf(true) }
    var status by remember { mutableStateOf("") }
    var lastFile by remember { mutableStateOf<java.io.File?>(null) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).testTag("doctor_screen"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ScreenHeader("گزارش برای پزشک", onBack = onBack)
        // Default «از اولین ثبت»: the report covers everything she has recorded.
        val range = rememberReportRange(vm, "doctor", tagPrefix = "rep")
        val from = LocalDate.ofEpochDay(range.from)
        val to = LocalDate.ofEpochDay(range.to)

    fun data() = DoctorReportData(
        from = from, to = to, today = vm.today().toEpochDay(), allPeriods = state.periods, logs = state.dailyLogs,
        includeNotes = includeNotes, includeIntimate = includeIntimate, includeTests = includeTests,
        pregnancyLine = state.health?.pregnancy?.let { p -> "تاریخ تخمینی زایمان ${JalaliDate.from(LocalDate.ofEpochDay(p.estimatedDueEpochDay)).formatFa()}؛ مبنا ${if (p.datingMethod == "clinician_edd") "تاریخ پزشک" else "آخرین پریود"}" },
        questions = if (!includeQuestions) emptyList() else records.filter { it.type == "doctor" && it.deletedAt == null }.mapNotNull {
            runCatching { com.google.gson.JsonParser.parseString(it.payloadJson).asJsonObject.get("text")?.asString }.getOrNull()?.takeIf { t -> t.isNotBlank() }
        }
    )
    fun writeTo(uri: android.net.Uri): Boolean = try {
        ctx.contentResolver.openOutputStream(uri)?.use { DoctorReportPdf.write(ctx, data(), it) } != null
    } catch (_: Throwable) { false }
    fun buildCacheFile(): java.io.File? = try {
        val dir = java.io.File(ctx.cacheDir, "exports").apply { mkdirs() }
        java.io.File(dir, "mahava-doctor-report.pdf").also { f -> f.outputStream().use { DoctorReportPdf.write(ctx, data(), it) } }
    } catch (_: Throwable) { null }
    fun share() {
        val f = buildCacheFile() ?: run { status = "ساخت فایل ناموفق بود."; return }
        lastFile = f
        val uri = androidx.core.content.FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", f)
        val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(android.content.Intent.EXTRA_STREAM, uri)
            putExtra(android.content.Intent.EXTRA_SUBJECT, "گزارش ماه برای پزشک")
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching { ctx.startActivity(android.content.Intent.createChooser(send, "ارسال گزارش برای پزشک").addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .onFailure { status = "برنامه‌ای برای ارسال پیدا نشد." }
    }
    fun open() {
        val f = buildCacheFile() ?: run { status = "ساخت فایل ناموفق بود."; return }
        lastFile = f
        val uri = androidx.core.content.FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", f)
        val view = android.content.Intent(android.content.Intent.ACTION_VIEW).setDataAndType(uri, "application/pdf")
            .addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { ctx.startActivity(view) }.onFailure { status = "برنامهٔ نمایش PDF پیدا نشد؛ از «ارسال» یا «ذخیره» استفاده کن." }
    }
    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        status = if (writeTo(uri)) "فایل PDF ذخیره شد. اگر آن را برای کسی می‌فرستی، یادت باشد اطلاعات خصوصی دارد." else "ساخت فایل ناموفق بود."
    }
    val fromE = from.toEpochDay(); val toE = to.toEpochDay()
    val logs = state.dailyLogs.filter { it.epochDay in fromE..toE }
    val cycles = CycleStats.cycles(state.periods, vm.today().toEpochDay()).filter { it.start in fromE..toE }

        MahavaCard(Modifier.testTag("doctor_preview_card"), containerColor = softTint(MahavaPrimary)) {
            SectionLabel("چه چیزهایی در گزارش هست")
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("چرخه", PersianDigits.toPersian(cycles.size), "شروع پریود", MahavaPrimary, Modifier.weight(1f).fillMaxHeight())
                StatTile("روز ثبت", PersianDigits.toPersian(logs.size), "روز", MahavaFertility, Modifier.weight(1f).fillMaxHeight())
                StatTile("روز درد", PersianDigits.toPersian(logs.count { (it.painScore ?: 0) > 0 }), "روز", MahavaMenstruation, Modifier.weight(1f).fillMaxHeight())
            }
            Spacer(Modifier.height(6.dp))
            QuietInfo("خلاصه • خلاصهٔ ماه‌به‌ماه • جدول چرخه‌ها • علائم پرتکرار • جدول درد • حال، انرژی و خواب در مراحل چرخه • ثبت‌های روزانه")
        }
        Row(verticalAlignment = Alignment.CenterVertically) { androidx.compose.material3.Checkbox(includeQuestions, { includeQuestions = it }); Text("سؤال‌هایم از پزشک هم بیاید") }
        Row(verticalAlignment = Alignment.CenterVertically) { androidx.compose.material3.Checkbox(includeNotes, { includeNotes = it }); Text("یادداشت‌ها هم بیاید") }
        Row(verticalAlignment = Alignment.CenterVertically) { androidx.compose.material3.Checkbox(includeIntimate, { includeIntimate = it }); Text("روزهای رابطهٔ جنسی هم بیاید") }
        Row(verticalAlignment = Alignment.CenterVertically) { androidx.compose.material3.Checkbox(includeTests, { includeTests = it }); Text("نتیجهٔ تست‌ها هم بیاید") }
        PrimaryButton("ارسال برای پزشک", modifier = Modifier.testTag("doctor_share")) { share() }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SecondaryButton("دیدن PDF", modifier = Modifier.weight(1f).testTag("doctor_preview")) { open() }
            SecondaryButton("ذخیره در گوشی", modifier = Modifier.weight(1f).testTag("doctor_save")) {
                val hook = (ctx.applicationContext as? MahavaApplication)?.documentPickerOverride
                if (hook != null) hook("create", "mahava-doctor-report.pdf")?.let { status = if (writeTo(it)) "فایل PDF ذخیره شد." else "ساخت فایل ناموفق بود." }
                else saveLauncher.launch("mahava-doctor-report.pdf")
            }
        }
        if (status.isNotBlank()) Text(status, color = MahavaPrimary)
        QuietInfo("فایل فقط با انتخاب خودت ساخته و فرستاده می‌شود و ممکن است اطلاعات خصوصی داشته باشد.")
    }
}

@Composable
fun LateTestScreen(
    vm: AppViewModel,
    onBack: () -> Unit,
    onLogPeriod: () -> Unit,
    onOpenItem: (String) -> Unit = {},
    onAccount: () -> Unit = {}
) {
    val state by vm.state.collectAsState()
    val premium by vm.isPremium.collectAsState()
    var result by remember { mutableStateOf<String?>(null) }
    var testDate by remember { mutableStateOf(vm.today()) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).testTag("late_screen"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ScreenHeader("پریود دیر کرده و تست بارداری", onBack = onBack)
        // Free: period prediction + period logging
        MahavaCard {
            Text("پریود شروع شده ولی ثبت نکرده‌ای؟", style = MaterialTheme.typography.titleMedium)
            val pred = state.cycle?.prediction
            if (pred is PredictionKind.Estimate) {
                QuietInfo("تاریخ تخمینی پریود: حدود ${JalaliDate.from(pred.nextPeriodStartCentral).formatFa()}")
            } else QuietInfo("برای تو تاریخ تخمینی پریود حساب نشده است.")
            SecondaryButton("ثبت پریود", onClick = onLogPeriod)
        }
        if (!premium) {
            PremiumPaywallCard(
                vm = vm,
                titleFa = "راهنما و ثبت تست بارداری",
                benefitFa = "با اشتراک ماه باز می‌شه.",
                onOpenAccount = onAccount
            )
            return@Column
        }
        Illustration(R.drawable.ill_test_guide_woman, Modifier.height(140.dp))
        MahavaCard(Modifier.clickable { onOpenItem("pregnancy_test_timing") }) {
            Text("کِی تست بدهم؟", style = MaterialTheme.typography.titleMedium)
            QuietInfo("بیشتر تست‌های خانگی از روز اول عقب افتادن پریود قابل اعتمادترند. اگر نمی‌دانی پریود کی باید می‌آمد، دست‌کم ۲۱ روز بعد از آخرین رابطهٔ بدون محافظت تست بده. (منبع: NHS)")
            Text("بیشتر بخوان", color = MahavaPrimary, style = MaterialTheme.typography.labelLarge)
        }
        SectionLabel("نتیجهٔ تست (اختیاری)")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("negative" to "منفی", "positive" to "مثبت", "invalid" to "نامعتبر").forEach { (k, l) ->
                ChoiceChipPill(l, result == k, tag = "test_$k") { result = k }
            }
        }
        SectionLabel("روز تست")
        DateStepper(testDate, max = vm.today(), min = vm.today().minusDays(60), onChange = { testDate = it }, testTagPrefix = "test_date")
        if (result == "positive") QuietInfo("نتیجهٔ مثبت را با مراقب سلامت پیگیری کن؛ برای فعال‌سازی بارداری، تاریخ مبنا را از تنظیمات ثبت کن.")
        PrimaryButton("ثبت نتیجه", enabled = result != null, modifier = Modifier.testTag("save_test")) {
            val now = System.currentTimeMillis()
            val existing = vm.logFor(testDate)
            vm.saveDailyLog(
                (existing ?: com.mahava.app.data.db.DailyLogEntity(epochDay = testDate.toEpochDay(), createdAt = now, updatedAt = now)).copy(
                    pregnancyTest = result,
                    pregnancyTestEpochDay = testDate.toEpochDay(),
                    updatedAt = now
                )
            )

            onBack()
        }
        QuietInfo("جواب منفیِ زود همیشه درست نیست؛ اگر پریود نیامد، چند روز بعد دوباره تست بده.")
        SecondaryButton("جلوگیری اضطراری چیست؟", modifier = Modifier.testTag("open_ec")) { onOpenItem("emergency_contraception") }
        SecondaryButton("علائم هشدار بارداری خارج از رحم") { onOpenItem("ectopic_warning_edu") }
    }
}

@Composable
fun CareScreen(vm: AppViewModel, onBack: () -> Unit, onOpenItem: (String) -> Unit = {}, onAccount: () -> Unit = {}) {
    val premium by vm.isPremium.collectAsState()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).testTag("care_screen"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ScreenHeader("راهنمای مراقبت و علائم هشدار", onBack = onBack)
        if (!premium) {
            PremiumPaywallCard(
                vm = vm,
                titleFa = "راهنمای مراقبت",
                benefitFa = "با اشتراک ماه باز می‌شه.",
                onOpenAccount = onAccount
            )
            return@Column
        }
        Illustration(R.drawable.ill_care_woman, Modifier.height(140.dp))
        MahavaCard(Modifier.clickable { onOpenItem("period_pain_care") }) {
            Text("برای درد خفیف پریود", style = MaterialTheme.typography.titleMedium, color = MahavaFertility)
            QuietInfo("کیسهٔ آب گرم روی شکم، دوش گرم، ماساژ، ورزش سبک و مسکن رایج طبق دستور بسته. (منبع: NHS)")
        }
        MahavaCard(Modifier.clickable { onOpenItem("when_to_seek_care") }) {
            Text("کی با پزشک صحبت کنم؟", style = MaterialTheme.typography.titleMedium, color = MahavaMenstruation)
            QuietInfo("اگر درد جلوی کارهای روزانه‌ات را می‌گیرد، خون‌ریزی خیلی زیاد است یا بیشتر از ۷ روز طول می‌کشد، بین پریودها خون‌ریزی داری، یا الگوی پریودت عوض شده. (منبع: NHS)")
        }
        MahavaCard(Modifier.clickable { onOpenItem("ectopic_warning_edu") }) {
            Text("کمک فوری لازم است اگر…", style = MaterialTheme.typography.titleMedium, color = MahavaDanger)
            QuietInfo("درد ناگهانی و شدید شکم داری، سرگیجهٔ شدید یا غش داری، یا خون‌ریزی در زمان کوتاه خیلی زیاد است. با اورژانس محلی تماس بگیر.")
        }
        QuietInfo("این راهنما آموزشی است و جای معاینه و نظر پزشک را نمی‌گیرد.")
    }
}
