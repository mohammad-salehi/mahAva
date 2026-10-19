package com.mahava.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.mahava.app.health.models.AppHealthMode
import com.mahava.app.health.models.HealthInsight
import com.mahava.app.health.models.InsightReviewStatus
import com.mahava.app.ui.AppViewModel
import com.mahava.app.ui.components.MahavaCard
import com.mahava.app.ui.components.PrimaryButton
import com.mahava.app.ui.components.HealthContextNote
import com.mahava.app.ui.components.HealthNote
import com.mahava.app.ui.components.QuietInfo
import com.mahava.app.ui.components.ScreenHeader
import com.mahava.app.ui.components.SecondaryButton
import com.mahava.app.ui.theme.MahavaMenstruation
import com.mahava.app.ui.theme.MahavaPrimary
import com.mahava.app.ui.theme.MahavaTextSecondary
import com.mahava.app.util.JalaliDate
import com.mahava.app.util.PersianDigits
import com.mahava.app.ui.components.DateStepper
import java.time.LocalDate

@Composable
fun HealthMiniDashboard(
    vm: AppViewModel,
    onAnalytics: () -> Unit,
    onPregnancy: () -> Unit,
    onPregnancySetup: () -> Unit,
    onInsight: (String) -> Unit,
    onAssistant: () -> Unit,
    onDailyLog: () -> Unit
) {
    val state by vm.state.collectAsState()
    val health = state.health ?: return
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.testTag("health_mini_dashboard")) {
        if (health.appMode == AppHealthMode.PREGNANCY) {
            val p = health.pregnancyProgress
            MahavaCard(Modifier.clickable(onClick = onPregnancy).testTag("pregnancy_progress_card")) {
                Text("بارداری", style = MaterialTheme.typography.titleMedium)
                if (p != null) {
                    Text(p.labelFa, style = MaterialTheme.typography.headlineLarge)
                    Text(p.weekOrdinalLabelFa, color = MahavaTextSecondary)
                    Text(
                        "تاریخ تخمینی زایمان: ${JalaliDate.from(LocalDate.parse(p.estimatedDueDate)).formatShortFa()}",
                        color = MahavaTextSecondary
                    )
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { p.progressFraction },
                        modifier = Modifier.fillMaxWidth().height(8.dp),
                        color = MahavaPrimary,
                        trackColor = MahavaPrimary.copy(alpha = 0.15f)
                    )
                    QuietInfo("نوار پیشرفت حداکثر تا ۱۰۰٪ پر می‌شود؛ سن بارداری بعد از هفته ۴۰ هم نشان داده می‌شود.")
                } else {
                    QuietInfo("حالت بارداری روشن است، ولی تاریخ معتبر ثبت نشده.")
                    SecondaryButton("تنظیم بارداری", onClick = onPregnancySetup)
                }
                health.pregnancyProgress?.contentWeek?.let { w ->
                    val fetal = vm.fetalWeek(w.coerceAtMost(42))
                    if (fetal != null) {
                        Spacer(Modifier.height(8.dp))
                        Text("رشد این هفته (آموزشی)", style = MaterialTheme.typography.titleMedium)
                        Text(fetal.growthFa)
                        HealthContextNote(HealthNote.FETAL_GROWTH)
                        fetal.sizeHintFa?.let {
                            Text("اندازهٔ تقریبی آموزشی: $it — اندازهٔ واقعی جنین شما نیست.", color = MahavaTextSecondary)
                        }
                    }
                }
                health.todayLog?.let {
                    Spacer(Modifier.height(6.dp))
                    Text("ثبت امروز: ${if (it.moods != null || it.painScore != null) "انجام شده" else "ناقص"}", color = MahavaTextSecondary)
                } ?: Text("امروز هنوز ثبت روزانه نداری.", color = MahavaTextSecondary)
                Spacer(Modifier.height(14.dp))
                SecondaryButton("ثبت روزانه", onClick = onDailyLog)
            }
        } else {
            val sum = health.currentCycleSummary
            MahavaCard(
                modifier = Modifier.testTag("cycle_phase_summary"),
                accent = MahavaMenstruation
            ) {
                Text("خلاصهٔ امروز چرخه", style = MaterialTheme.typography.titleMedium)
                if (sum?.cycleDay != null) {
                    Text("روز ${PersianDigits.toPersian(sum.cycleDay)} چرخه", style = MaterialTheme.typography.headlineLarge)
                }
                Text(sum?.phaseLabelFa ?: "مرحله نامشخص", color = MahavaTextSecondary)
                sum?.nextPeriodLabelFa?.let { Text(it) }
                HealthContextNote(HealthNote.PREDICTION)
                Text(sum?.historySummaryFa ?: "", color = MahavaTextSecondary)
                Spacer(Modifier.height(8.dp))
                SymptomSparkline(health.homeChartData.map { it.painScore })
                Text("نمودار درد ثبت‌شدهٔ ۱۴ روز اخیر (روز بدون ثبت خالی است، صفر نیست).", color = MahavaTextSecondary)
                Spacer(Modifier.height(14.dp))
                SecondaryButton("تحلیل کامل", onClick = onAnalytics)
                Spacer(Modifier.height(12.dp))
                SecondaryButton("فعال‌سازی حالت بارداری", onClick = onPregnancySetup)
            }
        }

        health.activeAlerts.take(2).forEach { a ->
            MahavaCard(
                modifier = Modifier.clickable(onClick = onAssistant).testTag("health_alert_peek_${a.id}"),
                accent = MahavaMenstruation
            ) {
                Text(a.titleFa, style = MaterialTheme.typography.titleMedium, color = MahavaMenstruation)
                Text(a.plainLanguageExplanationFa.take(140) + "…")
                Text("ببین در دستیار", color = MahavaPrimary)
            }
        }

        // Only personal/time-varying insights (e.g. this pregnancy week) — never static encyclopedia cards.
        health.relevantInsights
            .filter { it.topic == "pregnancy" || it.id.startsWith("preg_week_") }
            .forEach { insight ->
                HealthInsightCard(insight, onClick = { onInsight(insight.id) })
            }
    }
}

@Composable
fun HealthInsightCard(insight: HealthInsight, onClick: () -> Unit) {
    MahavaCard(Modifier.clickable(onClick = onClick).testTag("insight_card_${insight.id}")) {
        Text(insight.titleFa, style = MaterialTheme.typography.titleMedium)
        Text(insight.summaryFa)
        val src = insight.sourceIds.firstOrNull()
        if (src != null) Text("منبع: $src", color = MahavaTextSecondary)
        Text("بیشتر بخوان", color = MahavaPrimary)
    }
}

@Composable
fun SymptomSparkline(values: List<Int?>) {
    Canvas(Modifier.fillMaxWidth().height(56.dp).testTag("symptom_sparkline")) {
        val pts = values.mapIndexedNotNull { i, v ->
            if (v == null) null else i.toFloat() to v.toFloat()
        }
        if (pts.size < 2) return@Canvas
        val maxX = (values.size - 1).coerceAtLeast(1).toFloat()
        val path = Path()
        pts.forEachIndexed { idx, (x, y) ->
            val px = (x / maxX) * size.width
            val py = size.height - (y / 10f) * size.height
            if (idx == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        drawPath(path, color = MahavaPrimary, style = Stroke(width = 4f, cap = StrokeCap.Round))
        pts.forEach { (x, y) ->
            val px = (x / maxX) * size.width
            val py = size.height - (y / 10f) * size.height
            drawCircle(MahavaMenstruation, radius = 5f, center = Offset(px, py))
        }
    }
}

@Composable
fun PregnancySetupPage(vm: AppViewModel, onBack: () -> Unit, onDone: () -> Unit) {
    val today = vm.today()
    val state by vm.state.collectAsState()
    val saved = state.health?.pregnancy
    var method by remember(saved?.id) { mutableStateOf(saved?.datingMethod ?: "lmp") }
    var lmp by remember(saved?.id) { mutableStateOf(saved?.lmpEpochDay?.let { LocalDate.ofEpochDay(it) } ?: state.periods.maxByOrNull { it.startEpochDay }?.let { LocalDate.ofEpochDay(it.startEpochDay) } ?: today) }
    var edd by remember(saved?.id) { mutableStateOf(saved?.estimatedDueEpochDay?.let { LocalDate.ofEpochDay(it) } ?: today.plusDays(280)) }
    var confirmed by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ScreenHeader("تنظیم بارداری", onBack = onBack)
        QuietInfo("فعال‌سازی فقط با انتخاب خودت است. از روی تأخیر پریود بارداری را قطعی فرض نمی‌کنیم.")
        SecondaryButton("از روز اول آخرین پریود") { method = "lmp" }
        SecondaryButton("تاریخ زایمانی که پزشک گفته") { method = "clinician_edd" }
        Text(if (method == "lmp") "روش: آخرین پریود" else "روش: تاریخ پزشک (اولویت دارد)", color = MahavaPrimary)
        if (method == "lmp") {
            Text("اولین روز آخرین پریود")
            DateStepper(date = lmp, min = today.minusDays(300), max = today, onChange = { lmp = it })
        } else {
            Text("تاریخ تخمینی زایمان پزشک")
            DateStepper(date = edd, min = today.minusDays(30), max = today.plusDays(300), onChange = { edd = it })
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.Checkbox(confirmed, { confirmed = it })
            Text("تاریخ مبنا را بررسی کردم و می‌خواهم بارداری فعال شود.")
        }
        Spacer(Modifier.height(8.dp))
        PrimaryButton("ذخیره و ورود به حالت بارداری", enabled = confirmed) {
            vm.savePregnancySetup(
                datingMethod = method,
                lmp = if (method == "lmp") lmp else null,
                clinicianEdd = if (method == "clinician_edd") edd else null,
                clinicianConfirmed = method == "clinician_edd"
            )
            onDone()
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun PregnancyPage(
    vm: AppViewModel,
    onBack: () -> Unit,
    onSetup: () -> Unit,
    onDailyLog: () -> Unit,
    onInsight: (String) -> Unit
) {
    val state by vm.state.collectAsState()
    val health = state.health
    val progress = health?.pregnancyProgress
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).testTag("pregnancy_page")) {
        ScreenHeader("بارداری", onBack = onBack)
        if (progress == null) {
            QuietInfo("هنوز بارداری فعالی تنظیم نشده.")
            PrimaryButton("تنظیم بارداری", onClick = onSetup)
            return
        }
        Text(progress.labelFa, style = MaterialTheme.typography.headlineLarge)
        Text(progress.weekOrdinalLabelFa, color = MahavaTextSecondary)
        Text("زمان تخمینی زایمان: ${JalaliDate.from(LocalDate.parse(progress.estimatedDueDate)).formatFa()}")
        LinearProgressIndicator(progress = { progress.progressFraction }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
        val week = progress.contentWeek.coerceAtMost(42)
        val fetal = vm.fetalWeek(week)
        if (fetal != null) {
            MahavaCard(Modifier.clickable { onInsight("preg_week_${fetal.week}") }) {
                Text("رشد هفتهٔ ${PersianDigits.toPersian(fetal.week)}", style = MaterialTheme.typography.titleMedium)
                Text(fetal.growthFa)
                Text(fetal.bodyChangesFa)
                HealthContextNote(HealthNote.FETAL_GROWTH)
                fetal.sizeHintFa?.let { Text("مقایسهٔ آموزشی: $it", color = MahavaTextSecondary) }
            }
        } else if (progress.contentWeek > 40) {
            QuietInfo("سن بارداری از هفته ۴۰ گذشته؛ محتوای هفته‌ای قفل نشده و سن واقعی بالا نشان داده می‌شود.")
        }
        // Moved from Today (the approved pregnancy Today shows only the hero, body card, tip and partner).
        PregnancyCompanion(vm, onPregnancy = {}, onSetup = onSetup, onDaily = onDailyLog, onInsight = onInsight, part = PregnancyPart.REST)
        Text("نقاط عطف عمومی", style = MaterialTheme.typography.titleMedium)
        vm.pregnancyMilestones().forEach { m ->
            MahavaCard {
                Text("هفتهٔ ${PersianDigits.toPersian(m.week)} · ${m.titleFa}", style = MaterialTheme.typography.titleMedium)
                Text(m.bodyFa)
            }
        }
        SecondaryButton("ثبت روزانه سلامت", onClick = onDailyLog)
        SecondaryButton("پایان حالت بارداری") { vm.endPregnancyMode("user_ended") }
    }
}

@Composable
fun HealthAnalyticsPage(vm: AppViewModel, onBack: () -> Unit) {
    val state by vm.state.collectAsState()
    val a = state.health?.fullAnalyticsData
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).testTag("health_analytics")) {
        ScreenHeader("تحلیل سلامت", onBack = onBack)
        val months by vm.analyticsRangeMonths.collectAsState()
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(3, 6, 12).forEach { m ->
                com.mahava.app.ui.components.ChoiceChipPill("${PersianDigits.toPersian(m)} ماه اخیر", months == m) { vm.setAnalyticsRangeMonths(m) }
            }
        }
        Spacer(Modifier.height(8.dp))
        if (a == null) {
            QuietInfo("در حال آماده‌سازی…")
            return
        }
        a.emptyReasonFa?.let { QuietInfo(it) }
        Text("چرخه‌های کامل در بازه: ${PersianDigits.toPersian(a.completedCycleCount)}", style = MaterialTheme.typography.titleMedium)
        QuietInfo("چرخهٔ جاری در میانگین نیست. لکه‌بینی (خون‌ریزی خیلی کم) به‌تنهایی شروع چرخه حساب نمی‌شود.")
        BarChartBlock("طول چرخه‌های کامل", a.cycleLengths.map { it.toFloat() }, "روز")
        QuietInfo("میانگین ${a.meanCycle?.let { PersianDigits.toPersian(it.toInt()) } ?: "—"}؛ بازه ${a.minCycle?.let { PersianDigits.toPersian(it) } ?: "—"} تا ${a.maxCycle?.let { PersianDigits.toPersian(it) } ?: "—"}.")
        BarChartBlock("طول خون‌ریزی", a.bleedLengths.map { it.toFloat() }, "روز")
        QuietInfo("میانگین خون‌ریزی: ${a.meanBleed?.let { PersianDigits.toPersian(it.toInt()) } ?: "—"}.")
        Text("روزهایی با درد ۴ یا بیشتر (از ۱۰): ${PersianDigits.toPersian(a.symptomPainDays)} روز از ${PersianDigits.toPersian(a.loggedDaysInRange)} روز ثبت‌شده")
        if (a.sleepTrend.isNotEmpty()) {
            BarChartBlock("خواب شبانه", a.sleepTrend.map { it.second / 60f }, "ساعت")
            QuietInfo("فقط روزهایی که خواب ثبت شده‌اند.")
        }
        if (a.waterTrend.isNotEmpty()) {
            BarChartBlock("آب روزانه", a.waterTrend.map { it.second.toFloat() }, "میلی‌لیتر")
        }
        QuietInfo("رابطهٔ بین علائم به‌عنوان علت‌ومعلول معرفی نمی‌شود و درصد اطمینان ساختگی نشان داده نمی‌شود.")
    }
}

@Composable
private fun BarChartBlock(title: String, values: List<Float>, unit: String) {
    MahavaCard {
        Text(title, style = MaterialTheme.typography.titleMedium)
        if (values.isEmpty()) {
            Text("هنوز چیزی برای این نمودار ثبت نشده.", color = MahavaTextSecondary)
            return@MahavaCard
        }
        // Long series are averaged into at most 24 bars so they stay readable.
        val bars = com.mahava.app.health.TimeRange.compress(values, 24)
        Spacer(Modifier.height(8.dp))
        com.mahava.app.ui.components.BarChart(bars, MahavaPrimary, showValues = bars.size <= 8)
        val avg = values.average().toFloat()
        val f = { v: Float -> PersianDigits.toPersian(com.mahava.app.ui.components.fmt(Math.round(v * 10f) / 10f)) }
        QuietInfo("میانگین ${f(avg)} $unit · کمترین ${f(values.min())} · بیشترین ${f(values.max())} · ${PersianDigits.toPersian(values.size)} مورد ثبت‌شده")
    }
}

@Composable
fun HealthFeedPage(vm: AppViewModel, onBack: () -> Unit, onOpen: (String) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).testTag("health_feed")) {
        ScreenHeader("محتوای علمی", onBack = onBack)
        QuietInfo("خلاصهٔ آموزشی با منبع؛ متن مقالات کامل کپی نشده. بازبینی بالینی مستقل روی محتوای جدید انجام نشده.")
        vm.healthFeed().forEach { insight ->
            HealthInsightCard(insight) { onOpen(insight.id) }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
fun HealthInsightDetailPage(vm: AppViewModel, id: String, onBack: () -> Unit) {
    val insight = vm.healthInsight(id)
    val ctx = LocalContext.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).testTag("insight_detail")) {
        ScreenHeader("جزئیات", onBack = onBack)
        if (insight == null) {
            QuietInfo("این مطلب پیدا نشد یا حذف شده.")
            return
        }
        Text(insight.titleFa, style = MaterialTheme.typography.headlineLarge)
        Text(insight.summaryFa)
        QuietInfo(insight.applicabilityNoteFa)
        if (insight.topic == "pregnancy") {
            com.mahava.app.ui.components.Illustration(com.mahava.app.R.drawable.design_fetus_educational, Modifier.height(120.dp))
            QuietInfo("تصویر نمادین آموزشی است؛ شکل دقیق جنین در هفتهٔ انتخاب‌شده یا نتیجهٔ سونوگرافی نیست.")
        }
        QuietInfo("وضعیت بازبینی: ${reviewFa(insight.reviewStatus)}")
        insight.sections.forEach { s ->
            MahavaCard {
                Text(s.headingFa, style = MaterialTheme.typography.titleMedium)
                Text(s.bodyFa)
            }
            Spacer(Modifier.height(8.dp))
        }
        HealthContextNote(if (id.startsWith("preg_week_")) HealthNote.FETAL_GROWTH else HealthNote.GENERAL)
        Text("منابع", style = MaterialTheme.typography.titleMedium)
        insight.sourceIds.forEach { sid ->
            val src = vm.scienceSource(sid)
            if (src != null) {
                MahavaCard(Modifier.clickable {
                    ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(src.url)))
                }) {
                    Text(src.publisherFa + " · " + src.titleFa)
                    Text(src.url, color = MahavaPrimary)
                    Text("تاریخ مراجعه به منبع: ${com.mahava.app.util.DateFa.iso(src.accessedAt.toString())}", color = MahavaTextSecondary)
                }
            } else {
                Text(sid, color = MahavaTextSecondary)
            }
            Spacer(Modifier.height(6.dp))
        }
        Text("بازبینی محتوا: ${insight.reviewedAt?.let { com.mahava.app.util.DateFa.iso(it) } ?: "ثبت نشده"} · هنوز پزشک مستقلی آن را بازبینی نکرده است", color = MahavaTextSecondary)
    }
}

private fun reviewFa(s: InsightReviewStatus) = when (s) {
    InsightReviewStatus.DRAFT -> "پیش‌نویس"
    InsightReviewStatus.SOURCE_VERIFIED -> "منبع بررسی‌شده (بدون بازبینی بالینی مستقل)"
    InsightReviewStatus.CLINICALLY_REVIEWED -> "بازبینی بالینی"
}

@Composable
fun SmartAssistantPage(vm: AppViewModel, onBack: () -> Unit, onAlert: (String) -> Unit) {
    val state by vm.state.collectAsState()
    val alerts = state.health?.activeAlerts.orEmpty()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).testTag("smart_assistant")) {
        ScreenHeader("دستیار سلامت", onBack = onBack)
        QuietInfo("راهنمای آفلاین بر اساس ثبت‌های تو و کتابخانهٔ ذخیره‌شده؛ تشخیص پزشکی یا پاسخ آنلاین نیست.")
        Text(state.health?.pregnancyProgress?.let { "مبنای شخصی‌سازی: ${it.labelFa} از تاریخ ثبت‌شده" } ?: "مبنای شخصی‌سازی: ثبت‌ها و مرحلهٔ تخمینی چرخه")
        var question by remember { mutableStateOf("") }
        var asked by remember { mutableStateOf("") }
        androidx.compose.material3.OutlinedTextField(question, { question = it }, modifier = Modifier.fillMaxWidth(), label = { Text("موضوع یا سؤال کوتاهت را بنویس") })
        PrimaryButton("جست‌وجو در راهنمای آفلاین", enabled = question.isNotBlank()) { asked = question.trim() }
        if (asked.isNotBlank()) {
            val terms = asked.split(' ').filter { it.length > 2 }
            val matches = vm.healthFeed().map { item -> item to terms.count { term -> (item.titleFa + item.summaryFa + item.sections.joinToString { it.bodyFa }).contains(term) } }.filter { it.second > 0 }.sortedByDescending { it.second }.take(3)
            if (matches.isEmpty()) QuietInfo("در بانک ذخیره‌شده پاسخ مرتبطی پیدا نشد؛ سؤال کوتاه‌تر یا موضوع مشخص‌تری بنویس.")
            matches.forEach { (item, _) ->
                MahavaCard {
                    Text(item.titleFa, style = MaterialTheme.typography.titleMedium)
                    Text(item.summaryFa)
                    QuietInfo("مبنای پاسخ: اطلاعات عمومی ذخیره‌شده، نه تشخیص وضعیت تو.")
                    HealthContextNote(if (item.id.startsWith("preg_week_")) HealthNote.FETAL_GROWTH else HealthNote.GENERAL)
                    item.sourceIds.mapNotNull { vm.scienceSource(it) }.take(2).forEach { source ->
                        val context = LocalContext.current
                        Text(source.publisherFa + " · " + source.titleFa, color = MahavaPrimary, modifier = Modifier.clickable { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(source.url))) })
                    }
                }
            }
        }
        if (alerts.isEmpty()) {
            QuietInfo("الان هشدار فعالی از روی داده‌هایت ساخته نشده.")
        }
        alerts.forEach { a ->
            MahavaCard(Modifier.clickable { onAlert(a.id) }.testTag("alert_card_${a.id}")) {
                Text(a.titleFa, style = MaterialTheme.typography.titleMedium)
                Text(a.plainLanguageExplanationFa.take(180))
                Text("جزئیات", color = MahavaPrimary)
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
fun AlertDetailPage(vm: AppViewModel, id: String, onBack: () -> Unit) {
    val state by vm.state.collectAsState()
    val alert = state.health?.activeAlerts?.find { it.id == id }
    val ctx = LocalContext.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).testTag("alert_detail")) {
        ScreenHeader("جزئیات هشدار", onBack = onBack)
        if (alert == null) {
            QuietInfo("این هشدار پیدا نشد یا دیگر فعال نیست.")
            return
        }
        LaunchedEffect(id) { vm.markAlertRead(id) }
        Text(alert.titleFa, style = MaterialTheme.typography.headlineLarge)
        Text("چه داده‌ای باعث شد؟", style = MaterialTheme.typography.titleMedium)
        Text(alert.plainLanguageExplanationFa)
        Text("روزهایی که دیده شد: ${alert.observedDates.joinToString("، ") { com.mahava.app.util.DateFa.iso(it) }}")
        Text("اقدام مناسب", style = MaterialTheme.typography.titleMedium)
        Text(alert.recommendedActionFa)
        QuietInfo("این پیام تشخیص قطعی نیست و دارویی تجویز نمی‌کند.")
        HealthContextNote(if (id.startsWith("preg_week_")) HealthNote.FETAL_GROWTH else HealthNote.GENERAL)
        Text("منابع", style = MaterialTheme.typography.titleMedium)
        alert.evidenceReferences.forEach { sid ->
            val src = vm.scienceSource(sid)
            if (src != null) {
                Text(src.titleFa, color = MahavaPrimary, modifier = Modifier.clickable {
                    ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(src.url)))
                })
            }
        }
        SecondaryButton("نادیده بگیر") {
            vm.dismissAlert(id)
            onBack()
        }
    }
}
