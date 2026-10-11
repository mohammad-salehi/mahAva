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
    var months by androidx.compose.runtime.saveable.rememberSaveable { mutableIntStateOf(3) }
    var custom by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var fromEpoch by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(vm.today().minusMonths(3).toEpochDay()) }
    var toEpoch by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(vm.today().toEpochDay()) }
    val from = LocalDate.ofEpochDay(fromEpoch)
    val to = LocalDate.ofEpochDay(toEpoch)
    val periods = state.periods.filter { it.startEpochDay <= toEpoch }.sortedBy { it.startEpochDay }
    val lengths = periods.zipWithNext().filter { (_,b) -> b.startEpochDay >= fromEpoch }.map { (a,b) -> (b.startEpochDay-a.startEpochDay).toInt() }.filter { it in 15..90 }
    val bleedLens = com.mahava.app.health.CycleAnalyticsService.bleedLengths(periods.filter { it.startEpochDay >= fromEpoch })
    val logs = state.dailyLogs.filter { it.epochDay in fromEpoch..toEpoch }
    val pregnant = state.health?.appMode == com.mahava.app.health.models.AppHealthMode.PREGNANCY
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag("reports_screen")) {
        ScreenHeader("گزارش‌های من", onSettings = onSettings)
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoiceChipPill("۳ ماه اخیر", !custom && months == 3) { months=3; custom=false; fromEpoch=vm.today().minusMonths(3).toEpochDay(); toEpoch=vm.today().toEpochDay() }
                ChoiceChipPill("۶ ماه", !custom && months == 6) { months=6; custom=false; fromEpoch=vm.today().minusMonths(6).toEpochDay(); toEpoch=vm.today().toEpochDay() }
                ChoiceChipPill("بازهٔ دلخواه", custom) { custom = !custom }
            }
            if (custom) {
                DateStepper(from, max=to, min=vm.today().minusYears(10), onChange={ fromEpoch=it.toEpochDay() }, testTagPrefix="analytics_from")
                DateStepper(to, max=vm.today(), min=from, onChange={ toEpoch=it.toEpochDay() }, testTagPrefix="analytics_to")
            }
            MahavaCard(containerColor = MahavaPrimarySoft) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text("بدنت را بهتر بشناس", style = MaterialTheme.typography.headlineLarge); QuietInfo("خلاصهٔ ثبت‌های تو در یک نگاه") }
                    Illustration(R.drawable.design_lavender_sprig, Modifier.width(70.dp).height(80.dp))
                }
            }
            if (!premium) {
                PremiumPaywallCard(vm, "گزارش‌ها و الگوها", "تحلیل ثبت‌های واقعی و گزارش پزشک با اشتراک ماه باز می‌شود.", onAccount)
            } else {
                if (logs.isEmpty() && lengths.isEmpty()) QuietInfo("در این بازه ثبت کافی نداریم؛ شروع و پایان پریود و علائم روزانه‌ات را ثبت کن.")
                if (pregnant) {
                    MahavaCard {
                        SectionLabel("خلاصهٔ بارداری")
                        Text(state.health?.pregnancyProgress?.labelFa ?: "تاریخ مبنای بارداری را تنظیم کن.", style=MaterialTheme.typography.headlineLarge)
                        QuietInfo("${PersianDigits.toPersian(logs.size)} روز دارای ثبت در بازهٔ انتخاب‌شده؛ روزهای بدون ثبت نامشخص‌اند.")
                    }
                } else {
                    Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ReportStat("میانگین چرخه", lengths, R.drawable.design_calendar, Modifier.weight(1f).fillMaxHeight())
                        ReportStat("مدت خون‌ریزی", bleedLens, R.drawable.design_droplet, Modifier.weight(1f).fillMaxHeight())
                    }
                    if (lengths.isNotEmpty()) MahavaCard(Modifier.testTag("reports_lengths")) {
                        SectionLabel("طول چرخه‌های اخیر")
                        CycleColumns(lengths.takeLast(6))
                        QuietInfo("${PersianDigits.toPersian(lengths.size)} چرخهٔ کامل؛ چرخهٔ جاری در میانگین نیست.")
                    }
                }
                MahavaCard(Modifier.testTag("open_patterns").clickable(onClick = onPatterns)) {
                    SectionLabel("الگوی علائم")
                    val codes = listOf("fatigue" to "خستگی", "bloating" to "نفخ", "pain" to "درد")
                    codes.forEach { (code,label) ->
                        val count = logs.count { code in it.physicalSymptoms.orEmpty().split(',') }
                        Row(Modifier.fillMaxWidth().padding(vertical=6.dp), horizontalArrangement=Arrangement.SpaceBetween) {
                            Text(label); Text("${PersianDigits.toPersian(count)} روز ثبت‌شده", color=MahavaTextSecondary)
                        }
                    }
                    QuietInfo("${PersianDigits.toPersian(logs.size)} روز دارای ثبت؛ نبود ثبت به معنی نبود علامت نیست.")
                    Text("مشاهدهٔ روند و الگوها ‹", color=MahavaPrimaryDeep)
                }
                MahavaCard {
                    CompanionAction("تحلیل کامل", "چرخه، خواب و علائم", R.drawable.design_chart, onHealthAnalytics)
                    CompanionAction("راهنمای بررسی علائم", "آموزشی، بدون تشخیص", R.drawable.design_clipboard, onChecker)
                }
                SecondaryButton("آماده‌سازی گزارش برای پزشک", modifier=Modifier.testTag("open_doctor"), onClick=onDoctor)
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ReportStat(title: String, values: List<Int>, icon: Int, modifier: Modifier) {
    MahavaCard(modifier) {
        MahavaIcon(icon)
        Text(title, style=MaterialTheme.typography.bodySmall)
        Text(if (values.isEmpty()) "—" else "${PersianDigits.toPersian(kotlin.math.round(values.average()).toInt())} روز", style=MaterialTheme.typography.headlineLarge)
        QuietInfo(if (values.isEmpty()) "سابقهٔ کافی ثبت نشده" else "از ${PersianDigits.toPersian(values.size)} سابقهٔ کامل")
    }
}

@Composable
private fun CycleColumns(values: List<Int>) {
    val max=values.maxOrNull()?.coerceAtLeast(1) ?: 1
    Row(Modifier.fillMaxWidth().height(150.dp), horizontalArrangement=Arrangement.spacedBy(12.dp), verticalAlignment=Alignment.Bottom) {
        values.forEachIndexed { i,v ->
            Column(Modifier.weight(1f), horizontalAlignment=Alignment.CenterHorizontally) {
                Text(PersianDigits.toPersian(v), style=MaterialTheme.typography.bodySmall)
                Box(Modifier.fillMaxWidth().height((100f*v/max).dp).background(MahavaPrimary.copy(alpha=0.65f), RoundedCornerShape(topStart=8.dp,topEnd=8.dp)))
                QuietInfo("چرخهٔ ${PersianDigits.toPersian(i+1)}")
            }
        }
    }
}

private fun bleedFa(v: String?) = bleedingFa(v) ?: "-"
private fun testFa(v: String?) = when (v) { "positive" -> "مثبت"; "negative" -> "منفی"; "invalid" -> "نامعتبر"; else -> "-" }

@Composable
fun DoctorReportScreen(vm: AppViewModel, onBack: () -> Unit, onAccount: () -> Unit = {}) {
    val premium by vm.isPremium.collectAsState()
    if (!premium) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).testTag("doctor_screen")) {
            ScreenHeader("گزارش برای پزشک", onBack = onBack)
            PremiumPaywallCard(
                vm = vm,
                titleFa = "گزارش برای پزشک",
                benefitFa = "با اشتراک ماه باز می‌شه.",
                onOpenAccount = onAccount
            )
        }
        return
    }
    val state by vm.state.collectAsState()
    val ctx = LocalContext.current
    var from by remember { mutableStateOf(vm.today().minusDays(90)) }
    var to by remember { mutableStateOf(vm.today()) }
    var includeNotes by remember { mutableStateOf(false) }
    var includeIntimate by remember { mutableStateOf(false) }
    var includeTests by remember { mutableStateOf(true) }
    var preview by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }

    fun buildText(): String {
        val sb = StringBuilder()
        sb.appendLine("گزارش ماه برای پزشک")
        sb.appendLine("بازه: ${JalaliDate.from(from).formatFa()} تا ${JalaliDate.from(to).formatFa()}")
        sb.appendLine("این گزارش از ثبت‌های خود فرد ساخته شده و تشخیص پزشکی نیست.")
        sb.appendLine()
        sb.appendLine("پریودها:")
        val ps = state.periods.filter { it.startEpochDay in from.toEpochDay()..to.toEpochDay() }
        if (ps.isEmpty()) sb.appendLine("- در این بازه پریودی ثبت نشده")
        ps.forEach {
            val end = it.endEpochDay?.let { d -> JalaliDate.from(LocalDate.ofEpochDay(d)).formatFa() } ?: if (it.stillOngoing) "هنوز ادامه دارد" else "ثبت نشده"
            sb.appendLine("- شروع ${JalaliDate.from(LocalDate.ofEpochDay(it.startEpochDay)).formatFa()}، پایان: $end")
        }
        sb.appendLine()
        sb.appendLine("ثبت‌های روزانه:")
        val logs = state.dailyLogs.filter { it.epochDay in from.toEpochDay()..to.toEpochDay() }
        if (logs.isEmpty()) sb.appendLine("- در این بازه ثبتی نیست")
        logs.forEach { log ->
            // Private intimacy fields are omitted without dropping other data from the day.
            sb.append("- ${JalaliDate.from(LocalDate.ofEpochDay(log.epochDay)).formatFa()}: ")
            sb.append("خون‌ریزی ${bleedFa(log.bleeding)}، درد ${log.painScore?.let { PersianDigits.toPersian(it) } ?: "-"}")
            log.moods?.takeIf { it.isNotBlank() }?.let { sb.append("، حال ${it.split(',').joinToString("/") { m -> moodFa(m) ?: m }}") }
            log.physicalSymptoms?.takeIf { it.isNotBlank() }?.let { sb.append("، علائم ${it.split(',').joinToString("/") { k -> com.mahava.app.content.PhaseHistory.labelFa(k) }}") }
            if (includeIntimate && log.intimacyLogged) sb.append("، رابطه: ثبت‌شده؛ محافظت: ${when (log.intimacyProtected) { "yes" -> "بله"; "no" -> "خیر"; else -> "نامشخص" }}")
            if (includeNotes && !log.note.isNullOrBlank()) sb.append("، یادداشت: ${log.note}")
            if (includeTests && log.pregnancyTest != null) sb.append("، تست بارداری ${testFa(log.pregnancyTest)}")
            sb.appendLine()
        }
        sb.appendLine()
        sb.appendLine("روزهای بدون ثبت: نامشخص؛ به معنی نبود علامت نیست.")
        state.health?.pregnancy?.let { p ->
            sb.appendLine("بارداری: تاریخ تخمینی زایمان ${JalaliDate.from(LocalDate.ofEpochDay(p.estimatedDueEpochDay)).formatFa()}؛ مبنا ${if (p.datingMethod == "clinician_edd") "تاریخ پزشک" else "آخرین پریود"}")
        }
        return sb.toString()
    }

    fun writePdf(uri: android.net.Uri): Boolean = try {
        val doc = PdfDocument()
        val paint = android.text.TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 12f
            typeface = androidx.core.content.res.ResourcesCompat.getFont(ctx, R.font.vazirmatn_regular)
            color = android.graphics.Color.rgb(48,39,62)
        }
        var pageNo=1
        var page=doc.startPage(PdfDocument.PageInfo.Builder(595,842,pageNo).create())
        var y=40
        buildText().lines().forEach { line ->
            val layout = android.text.StaticLayout.Builder.obtain(line.ifEmpty { " " },0,line.ifEmpty { " " }.length,paint,515)
                .setAlignment(android.text.Layout.Alignment.ALIGN_NORMAL)
                .setTextDirection(android.text.TextDirectionHeuristics.RTL)
                .setLineSpacing(4f,1f).setIncludePad(false).build()
            // Render each shaped line independently to allow long private notes to span pages.
            for (i in 0 until layout.lineCount) {
                val top=layout.getLineTop(i)
                val bottom=layout.getLineBottom(i)
                val height=bottom-top
                if (y+height>802) { doc.finishPage(page); pageNo++; page=doc.startPage(PdfDocument.PageInfo.Builder(595,842,pageNo).create()); y=40 }
                page.canvas.save()
                page.canvas.translate(40f,(y-top).toFloat())
                page.canvas.clipRect(0,top,515,bottom)
                layout.draw(page.canvas)
                page.canvas.restore()
                y+=height
            }
            y+=4
        }
        doc.finishPage(page)
        ctx.contentResolver.openOutputStream(uri)?.use { doc.writeTo(it) }
        doc.close()
        true
    } catch (_: Throwable) { false }

    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        status = if (writePdf(uri)) "فایل PDF ذخیره شد. اگر آن را برای کسی می‌فرستی، یادت باشد اطلاعات خصوصی دارد." else "ساخت فایل ناموفق بود."
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).testTag("doctor_screen")) {
        ScreenHeader("گزارش برای پزشک", onBack = onBack)
        QuietInfo("از ${JalaliDate.from(from).formatFa()} تا ${JalaliDate.from(to).formatFa()}")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChoiceChipPill("۳ ماه اخیر", from == vm.today().minusDays(90)) { from = vm.today().minusDays(90); to = vm.today() }
            ChoiceChipPill("۶ ماه اخیر", from == vm.today().minusDays(180)) { from = vm.today().minusDays(180); to = vm.today() }
        }
        SectionLabel("یا بازهٔ دلخواه")
        Text("از:", style = MaterialTheme.typography.bodyMedium)
        DateStepper(from, max = to, min = vm.today().minusDays(3650), onChange = { from = it }, testTagPrefix = "rep_from")
        Text("تا:", style = MaterialTheme.typography.bodyMedium)
        DateStepper(to, max = vm.today(), min = from, onChange = { to = it }, testTagPrefix = "rep_to")
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.Checkbox(includeNotes, { includeNotes = it }); Text("یادداشت‌ها هم بیاید")
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.Checkbox(includeIntimate, { includeIntimate = it }); Text("روزهای مربوط به رابطهٔ جنسی هم بیاید")
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.Checkbox(includeTests, { includeTests = it }); Text("نتیجهٔ تست‌ها هم بیاید")
        }
        SecondaryButton("پیش‌نمایش", modifier = Modifier.testTag("doctor_preview")) { preview = buildText() }
        if (preview.isNotBlank() && preview != buildText()) QuietInfo("بازه یا گزینه‌ها تغییر کرده؛ پیش‌نمایش را دوباره ببین.")
        if (preview.isNotBlank()) {
            MahavaCard { Text(preview, style = MaterialTheme.typography.bodySmall) }
        }
        PrimaryButton("ساخت فایل PDF", enabled = preview.isNotBlank() && preview == buildText()) {
            val hook = (ctx.applicationContext as? MahavaApplication)?.documentPickerOverride
            if (hook != null) hook("create", "mahava-doctor-report.pdf")?.let { status = if (writePdf(it)) "فایل PDF ذخیره شد." else "ساخت فایل ناموفق بود." }
            else saveLauncher.launch("mahava-doctor-report.pdf")
        }
        if (status.isNotBlank()) Text(status, color = MahavaPrimary)
        QuietInfo("فایل فقط با انتخاب خودت ساخته و ذخیره می‌شود. این فایل ممکن است اطلاعات خصوصی داشته باشد.")
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
