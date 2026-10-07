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
    onAccount: () -> Unit = {}
) {
    val state by vm.state.collectAsState()
    val premium by vm.isPremium.collectAsState()
    val periods = state.periods.sortedBy { it.startEpochDay }
    val lengths = periods.zipWithNext { a, b -> (b.startEpochDay - a.startEpochDay).toInt() }.filter { it in 15..90 }
    val bleedLens = periods.mapNotNull { p ->
        val end = p.endEpochDay ?: return@mapNotNull null
        (end - p.startEpochDay + 1).toInt()
    }
    val pred = state.cycle?.prediction as? PredictionKind.Estimate
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag("reports_screen")) {
        ScreenHeader("گزارش‌های من", onSettings = onSettings)
        // Free: next period prediction only
        MahavaCard(Modifier.padding(horizontal = 16.dp).testTag("reports_period_pred")) {
            Text("پیش‌بینی پریود بعدی", style = MaterialTheme.typography.titleMedium)
            when {
                state.cycle?.isLate == true ->
                    Text("پریود حدود ${com.mahava.app.util.PersianDigits.toPersian(state.cycle?.daysLate ?: 0)} روز دیر کرده (تخمینی).")
                pred != null -> {
                    val d = state.cycle?.daysUntilCentralPeriod
                    Text(
                        when {
                            d == null -> "تخمین پریود بعدی آماده است."
                            d == 0 -> "پریود ممکن است از همین روزها شروع شود."
                            else -> "حدود ${com.mahava.app.util.PersianDigits.toPersian(d)} روز تا پریود بعدی."
                        }
                    )
                    QuietInfo(vm.predictionBasisShortFa())
                }
                else -> QuietInfo(state.cycle?.basisDescriptionFa ?: "با ثبت چند پریود، تخمین روشن‌تر می‌شود.")
            }
        }
        Spacer(Modifier.height(8.dp))
        if (!premium) {
            Column(Modifier.padding(horizontal = 16.dp)) {
                PremiumPaywallCard(
                    vm = vm,
                    titleFa = "گزارش‌ها و الگوها",
                    benefitFa = "روند چرخه، الگوی علائم، بینش روزانه و گزارش پزشک با اشتراک ماه باز می‌شه.",
                    onOpenAccount = onAccount
                )
            }
            Spacer(Modifier.height(24.dp))
            return@Column
        }
        if (periods.isEmpty() && state.dailyLogs.isEmpty()) {
            MahavaCard(Modifier.padding(horizontal = 16.dp).testTag("reports_empty")) {
                Text("هنوز چیزی برای گزارش نیست", style = MaterialTheme.typography.titleMedium)
                QuietInfo("وقتی پریودها و حال روزانه‌ات را ثبت کنی، خلاصه‌ها اینجا ساخته می‌شوند. نمودار ساختگی نشان نمی‌دهیم.")
            }
        }
        Row(Modifier.padding(16.dp).height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MahavaCard(Modifier.weight(1f).fillMaxHeight()) {
                MahavaIcon(R.drawable.ic_droplet, MahavaMenstruation)
                Text("میانگین مدت خون‌ریزی", color = MahavaTextSecondary)
                Text(
                    if (bleedLens.isEmpty()) "—" else "${PersianDigits.toPersian(Math.round(bleedLens.average()).toInt())} روز",
                    style = MaterialTheme.typography.headlineLarge
                )
                QuietInfo(if (bleedLens.isEmpty()) "هنوز پریودی با روز پایان ثبت نشده" else "از ${PersianDigits.toPersian(bleedLens.size)} پریود ثبت‌شده")
            }
            MahavaCard(Modifier.weight(1f).fillMaxHeight()) {
                MahavaIcon(R.drawable.ic_cycle)
                Text("میانگین طول چرخه", color = MahavaTextSecondary)
                Text(
                    if (lengths.isEmpty()) "—" else "${PersianDigits.toPersian(Math.round(lengths.average()).toInt())} روز",
                    style = MaterialTheme.typography.headlineLarge
                )
                QuietInfo(if (lengths.isEmpty()) "هنوز چرخهٔ کاملی ثبت نشده" else "از ${PersianDigits.toPersian(lengths.size)} چرخهٔ کامل")
            }
        }
        MahavaCard(Modifier.padding(horizontal = 16.dp).testTag("reports_lengths")) {
            Text("طول چرخه‌های اخیر", style = MaterialTheme.typography.titleLarge)
            if (lengths.isEmpty()) QuietInfo("وقتی دست‌کم دو پریود پشت سر هم ثبت شود، طول چرخه اینجا دیده می‌شود.")
            else {
                val maxLen = (lengths.maxOrNull() ?: 1).coerceAtLeast(1)
                lengths.takeLast(6).forEachIndexed { idx, len ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("چرخهٔ ${PersianDigits.toPersian(idx + 1)}", Modifier.width(72.dp), style = MaterialTheme.typography.bodySmall)
                        Box(Modifier.weight(1f).height(12.dp)) {
                            Box(Modifier.fillMaxWidth(len.toFloat() / maxLen).fillMaxHeight().background(MahavaPrimarySoft, RoundedCornerShape(6.dp)))
                        }
                        Spacer(Modifier.width(8.dp))
                        Text("${PersianDigits.toPersian(len)} روز", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        MahavaCard(Modifier.padding(horizontal = 16.dp).clickable(onClick = onInsight)) {
            Text("بینش روزانه", style = MaterialTheme.typography.titleMedium)
            QuietInfo("خلاصهٔ شخصی امروز بر اساس چرخه و ثبت‌هایت.")
        }
        Spacer(Modifier.height(8.dp))
        MahavaCard(Modifier.padding(horizontal = 16.dp).testTag("open_patterns").clickable(onClick = onPatterns)) {
            Text("الگوی علائم (ویژه)", style = MaterialTheme.typography.titleLarge)
            if (state.patterns.isEmpty()) QuietInfo("برای دیدن الگو، دست‌کم ۲ چرخه با ثبت علائم لازم است.")
            else {
                state.patterns.take(2).forEach { Text("• ${it.textFa}") }
                QuietInfo("برای خوشه‌بندی کامل مرحله‌ای، وارد شو.")
            }
        }
        Spacer(Modifier.height(8.dp))
        MahavaCard(Modifier.padding(horizontal = 16.dp).testTag("open_trends").clickable(onClick = onTrends)) {
            Text("روند چرخه (ویژه)", style = MaterialTheme.typography.titleMedium)
            QuietInfo("نمودار طول چرخه، مدت خون‌ریزی و علائم پرتکرار.")
        }
        Spacer(Modifier.height(8.dp))
        MahavaCard(Modifier.padding(horizontal = 16.dp).testTag("open_checker").clickable(onClick = onChecker)) {
            Text("بررسی آموزشی علائم (ویژه)", style = MaterialTheme.typography.titleMedium)
            QuietInfo("فهرست هم‌پوشانی با تنبلی تخمدان و اندومتریوز — بدون تشخیص.")
        }
        Spacer(Modifier.height(8.dp))
        MahavaCard(Modifier.padding(horizontal = 16.dp).testTag("open_doctor").clickable(onClick = onDoctor)) {
            Text("گزارش برای پزشک", style = MaterialTheme.typography.titleMedium)
            QuietInfo("بازهٔ زمانی را انتخاب کن، پیش‌نمایش را ببین و موارد خصوصی را حذف کن؛ فایل فقط وقتی خودت بخواهی ساخته می‌شود.")
        }
        Spacer(Modifier.height(8.dp))
        MahavaCard(Modifier.padding(horizontal = 16.dp).clickable(onClick = onLate)) {
            Text("پریود دیر کرده / تست بارداری", style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(24.dp))
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
            if (!includeIntimate && log.intimacyLogged) return@forEach
            sb.append("- ${JalaliDate.from(LocalDate.ofEpochDay(log.epochDay)).formatFa()}: ")
            sb.append("خون‌ریزی ${bleedFa(log.bleeding)}، درد ${log.painScore?.let { PersianDigits.toPersian(it) } ?: "-"}")
            log.moods?.takeIf { it.isNotBlank() }?.let { sb.append("، حال ${it.split(',').joinToString("/") { m -> moodFa(m) ?: m }}") }
            log.physicalSymptoms?.takeIf { it.isNotBlank() }?.let { sb.append("، علائم ${it.split(',').joinToString("/") { k -> com.mahava.app.content.PhaseHistory.labelFa(k) }}") }
            if (includeNotes && !log.note.isNullOrBlank()) sb.append("، یادداشت: ${log.note}")
            if (includeTests && log.pregnancyTest != null) sb.append("، تست بارداری ${testFa(log.pregnancyTest)}")
            sb.appendLine()
        }
        sb.appendLine()
        sb.appendLine("الگوها (فقط هم‌زمانی):")
        if (state.patterns.isEmpty()) sb.appendLine("- داده برای الگو کافی نیست")
        state.patterns.forEach { sb.appendLine("- ${it.textFa}") }
        return sb.toString()
    }

    fun writePdf(uri: android.net.Uri): Boolean = try {
        val doc = PdfDocument()
        val paint = Paint().apply { textSize = 12f; isAntiAlias = true; textAlign = Paint.Align.RIGHT }
        var pageNo = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, pageNo).create())
        var y = 40f
        buildText().lines().flatMap { it.chunked(85).ifEmpty { listOf("") } }.forEach { line ->
            if (y > 810f) {
                doc.finishPage(page); pageNo++
                page = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, pageNo).create()); y = 40f
            }
            page.canvas.drawText(line, 555f, y, paint)
            y += 18f
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
        if (preview.isNotBlank()) {
            MahavaCard { Text(preview, style = MaterialTheme.typography.bodySmall) }
        }
        PrimaryButton("ساخت فایل PDF") {
            preview = buildText()
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
        if (result == "positive") QuietInfo("با ثبت نتیجهٔ مثبت، «حالت بارداری» روشن می‌شود و پیش‌بینی پریود متوقف می‌شود. از تنظیمات می‌توانی آن را خاموش کنی.")
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
            if (result == "positive") {
                vm.updateProfile { it.copy(pregnancyMode = true, fertilityTrackingEnabled = false) }
            }
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
