package com.mahava.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.mahava.app.checker.CheckerBand
import com.mahava.app.checker.CheckerTopic
import com.mahava.app.checker.SymptomChecker
import com.mahava.app.content.CravingContent
import com.mahava.app.content.PhaseForecast
import com.mahava.app.content.FoodCravingKeys
import com.mahava.app.ui.AppViewModel
import com.mahava.app.ui.components.*
import com.mahava.app.ui.theme.*
import com.mahava.app.util.PersianDigits

@Composable
fun DailyInsightScreen(vm: AppViewModel, onBack: () -> Unit, onAccount: () -> Unit = {}) {
    val premium by vm.isPremium.collectAsState()
    val state by vm.state.collectAsState()
    val insight = remember(state.dailyLogs, state.cycle) {
        vm.dailyInsight()
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).testTag("daily_insight_screen")) {
        ScreenHeader("بینش روزانه", onBack = onBack)
        if (!premium) {
            PremiumPaywallCard(
                vm = vm,
                titleFa = "بینش روزانه",
                benefitFa = "متن شخصی‌سازی‌شده بر اساس مرحلهٔ چرخه و ثبت‌هایت با اشتراک ماه باز می‌شه.",
                onOpenAccount = onAccount
            )
        } else {
            PremiumBadge()
            MahavaCard(Modifier.testTag("insight_full")) {
                Text(insight.titleFa, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                Text(insight.bodyFa, style = MaterialTheme.typography.bodyLarge)
                insight.cravingLineFa?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = MahavaPrimary)
                }
                Spacer(Modifier.height(12.dp))
                Text("چه کار می‌توانی بکنی", style = MaterialTheme.typography.titleMedium)
                Bullets(insight.tipsFa)
                Spacer(Modifier.height(8.dp))
                QuietInfo(insight.dataBasisFa)
                QuietInfo(CravingContent.disclaimerFa())
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun CravingDetailScreen(vm: AppViewModel, onBack: () -> Unit, onAccount: () -> Unit = {}) {
    val premium by vm.isPremium.collectAsState()
    val state by vm.state.collectAsState()
    val ctx = vm.dayContext()
    val phaseGroup = CravingContent.phaseGroupOf(ctx.subWindow, state.cycle?.phase)
    val todayCravings = vm.logFor(vm.today())?.foodCravings
        ?.split(',')?.filter { it.isNotBlank() }.orEmpty()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).testTag("craving_detail_screen")) {
        ScreenHeader("هوس خوراکی و معنی‌اش", onBack = onBack)
        if (!premium) {
            PremiumPaywallCard(
                vm = vm,
                titleFa = "هوس خوراکی و معنی‌اش",
                benefitFa = "با اشتراک ماه باز می‌شه.",
                onOpenAccount = onAccount
            )
            return@Column
        }
        QuietInfo("مرحلهٔ تقریبی امروز: ${ctx.subWindow.titleFa}")
        QuietInfo(CravingContent.disclaimerFa())
        Spacer(Modifier.height(8.dp))
        if (todayCravings.isNotEmpty()) {
            MahavaCard {
                Text("امروز ثبت کرده‌ای", style = MaterialTheme.typography.titleMedium)
                todayCravings.forEach { key ->
                    val m = CravingContent.meaning(key, phaseGroup)
                    Spacer(Modifier.height(8.dp))
                    Text(FoodCravingKeys.labelFa(key), color = MahavaPrimary, style = MaterialTheme.typography.titleSmall)
                    Text(m?.meaningFa ?: "")
                    m?.tipFa?.let { QuietInfo("پیشنهاد: $it") }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        Text("هوس‌های رایج در این دوره", style = MaterialTheme.typography.titleMedium)
        CravingContent.meaningsForPhase(phaseGroup).forEach { m ->
            MahavaCard(Modifier.padding(vertical = 4.dp)) {
                Text(m.titleFa, style = MaterialTheme.typography.titleSmall, color = MahavaPrimary)
                Text(m.meaningFa)
                QuietInfo("پیشنهاد: ${m.tipFa}")
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun SymptomPatternsScreen(vm: AppViewModel, onBack: () -> Unit, onAccount: () -> Unit = {}) {
    val premium by vm.isPremium.collectAsState()
    val state by vm.state.collectAsState()
    val clusters = remember(state.dailyLogs, state.periods) {
        vm.phaseClusters()
    }
    val classic = state.patterns
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).testTag("symptom_patterns_screen")) {
        ScreenHeader("الگوی علائم", onBack = onBack)
        if (!premium) {
            PremiumPaywallCard(
                vm = vm,
                titleFa = "الگوی علائم",
                benefitFa = "با اشتراک ماه باز می‌شه.",
                onOpenAccount = onAccount
            )
        } else {
            PremiumBadge()
            if (clusters.isEmpty() && classic.isEmpty()) {
                MahavaCard(Modifier.testTag("patterns_empty")) {
                    Text("هنوز دادهٔ کافی نیست", style = MaterialTheme.typography.titleMedium)
                    QuietInfo("دست‌کم ۲ چرخه با چند ثبت علائم لازم است. برنامه چیز ساختگی نشان نمی‌دهد.")
                }
            } else {
                if (clusters.isNotEmpty()) {
                    MahavaCard {
                        Text("خوشه‌بندی بر اساس مرحله", style = MaterialTheme.typography.titleLarge)
                        clusters.forEach {
                            Spacer(Modifier.height(6.dp))
                            Text("• ${it.textFa}")
                        }
                        QuietInfo("فقط هم‌زمانی را نشان می‌دهد، نه علت را.")
                    }
                    Spacer(Modifier.height(8.dp))
                }
                if (classic.isNotEmpty()) {
                    MahavaCard {
                        Text("الگوی پیش از پریود", style = MaterialTheme.typography.titleLarge)
                        classic.forEach { Text("• ${it.textFa}") }
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun CycleTrendsScreen(vm: AppViewModel, onBack: () -> Unit, onAccount: () -> Unit = {}) {
    val premium by vm.isPremium.collectAsState()
    val state by vm.state.collectAsState()
    val trends = remember(state.periods, state.dailyLogs) {
        vm.cycleTrends()
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).testTag("cycle_trends_screen")) {
        ScreenHeader("روند چرخه", onBack = onBack)
        if (!premium) {
            PremiumPaywallCard(
                vm = vm,
                titleFa = "روند چرخه",
                benefitFa = "با اشتراک ماه باز می‌شه.",
                onOpenAccount = onAccount
            )
        } else {
            PremiumBadge()
            if (trends.emptyReasonFa != null && trends.cycleLengths.isEmpty() && trends.bleedLengths.isEmpty()) {
                MahavaCard { QuietInfo(trends.emptyReasonFa!!) }
            } else {
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MahavaCard(Modifier.weight(1f).fillMaxHeight()) {
                        Text("میانهٔ طول چرخه", color = MahavaTextSecondary)
                        Text(
                            trends.medianCycle?.let { "${PersianDigits.toPersian(it)} روز" } ?: "—",
                            style = MaterialTheme.typography.headlineLarge
                        )
                    }
                    MahavaCard(Modifier.weight(1f).fillMaxHeight()) {
                        Text("میانهٔ خون‌ریزی", color = MahavaTextSecondary)
                        Text(
                            trends.medianBleed?.let { "${PersianDigits.toPersian(it)} روز" } ?: "—",
                            style = MaterialTheme.typography.headlineLarge
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                MahavaCard {
                    Text("طول چرخه‌ها", style = MaterialTheme.typography.titleLarge)
                    if (trends.cycleLengths.isEmpty()) QuietInfo("با ثبت دو پریود پشت‌سرهم اینجا پر می‌شود.")
                    else {
                        val maxLen = (trends.cycleLengths.maxOfOrNull { it.lengthDays } ?: 1).coerceAtLeast(1)
                        trends.cycleLengths.takeLast(8).forEach { pt ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("چرخهٔ ${PersianDigits.toPersian(pt.index)}", Modifier.width(72.dp), style = MaterialTheme.typography.bodySmall)
                                Box(Modifier.weight(1f).height(12.dp)) {
                                    Box(
                                        Modifier.fillMaxWidth(pt.lengthDays.toFloat() / maxLen)
                                            .fillMaxHeight()
                                            .background(MahavaPrimarySoft, RoundedCornerShape(6.dp))
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                                Text("${PersianDigits.toPersian(pt.lengthDays)} روز", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                MahavaCard {
                    Text("مدت خون‌ریزی", style = MaterialTheme.typography.titleLarge)
                    if (trends.bleedLengths.isEmpty()) QuietInfo("پریودهایی که روز پایان دارند اینجا می‌آیند.")
                    else {
                        val maxB = (trends.bleedLengths.maxOfOrNull { it.lengthDays } ?: 1).coerceAtLeast(1)
                        trends.bleedLengths.takeLast(8).forEach { pt ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("پریود ${PersianDigits.toPersian(pt.index)}", Modifier.width(72.dp), style = MaterialTheme.typography.bodySmall)
                                Box(Modifier.weight(1f).height(12.dp)) {
                                    Box(
                                        Modifier.fillMaxWidth(pt.lengthDays.toFloat() / maxB)
                                            .fillMaxHeight()
                                            .background(MahavaMenstruation.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                                Text("${PersianDigits.toPersian(pt.lengthDays)} روز", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                val dp = trends.dayPercents
                if (dp.totalLoggedDays > 0) {
                    Spacer(Modifier.height(8.dp))
                    MahavaCard(Modifier.testTag("trends_percents")) {
                        Text("درصد روزهای ثبت‌شده", style = MaterialTheme.typography.titleLarge)
                        QuietInfo("از ${PersianDigits.toPersian(dp.totalLoggedDays)} روز ثبت‌شده")
                        Spacer(Modifier.height(6.dp))
                        dp.painPercent?.let { Text("• درد قابل‌توجه: ${PersianDigits.toPersian(it)}٪") }
                        dp.lowMoodPercent?.let { Text("• خلق پایین/نگران/زودرنج: ${PersianDigits.toPersian(it)}٪") }
                        dp.cravingPercent?.let { Text("• هوس خوراکی: ${PersianDigits.toPersian(it)}٪") }
                        dp.lowEnergyPercent?.let { Text("• انرژی کم: ${PersianDigits.toPersian(it)}٪") }
                        QuietInfo("این‌ها آمار ثبت‌های خودت است، نه تشخیص.")
                    }
                }
                if (trends.phaseSymptomFreqs.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    MahavaCard {
                        Text("علائم پرتکرار در هر مرحله", style = MaterialTheme.typography.titleLarge)
                        trends.phaseSymptomFreqs.take(8).forEach { f ->
                            Text("• ${f.phaseLabelFa}: ${f.symptomLabelFa} (${PersianDigits.toPersian(f.count)} بار)")
                        }
                        QuietInfo("هم‌زمانی است، نه علت.")
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun SymptomCheckerScreen(vm: AppViewModel, onBack: () -> Unit, onAccount: () -> Unit = {}) {
    val premium by vm.isPremium.collectAsState()
    var topic by remember { mutableStateOf<CheckerTopic?>(null) }
    val answers = remember { mutableStateMapOf<String, Boolean>() }
    var result by remember { mutableStateOf(vm.lastCheckerResult()) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).testTag("symptom_checker_screen")) {
        ScreenHeader("بررسی آموزشی علائم", onBack = onBack)
        if (!premium) {
            PremiumPaywallCard(
                vm = vm,
                titleFa = "بررسی آموزشی علائم",
                benefitFa = "با اشتراک ماه باز می‌شه.",
                onOpenAccount = onAccount
            )
            QuietInfo(SymptomChecker.DISCLAIMER_FA)
            return@Column
        }
        PremiumBadge()
        MahavaCard {
            Text("هشدار مهم", color = MahavaDanger, style = MaterialTheme.typography.titleMedium)
            Text(SymptomChecker.DISCLAIMER_FA)
        }
        Spacer(Modifier.height(8.dp))

        if (topic == null && result == null) {
            Text("کدام فهرست را می‌خواهی ببینی؟", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            PrimaryButton(CheckerTopic.PCOS.titleFa, modifier = Modifier.testTag("checker_pcos")) {
                topic = CheckerTopic.PCOS
                answers.clear()
            }
            Spacer(Modifier.height(8.dp))
            SecondaryButton(CheckerTopic.ENDO.titleFa, modifier = Modifier.testTag("checker_endo")) {
                topic = CheckerTopic.ENDO
                answers.clear()
            }
        } else if (topic != null && result == null) {
            val t = topic!!
            Text(t.titleFa, style = MaterialTheme.typography.titleLarge)
            QuietInfo("برای هر مورد بگو بله یا خیر. مجبور نیستی به همه جواب بدهی؛ بدون جواب «خیر» حساب می‌شود.")
            SymptomChecker.questions(t).forEach { q ->
                Spacer(Modifier.height(8.dp))
                MahavaCard {
                    Text(q.textFa)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ChoiceChipPill("بله", answers[q.id] == true, tag = "ans_${q.id}_yes") {
                            answers[q.id] = true
                        }
                        ChoiceChipPill("خیر", answers[q.id] == false, tag = "ans_${q.id}_no") {
                            answers[q.id] = false
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            PrimaryButton("دیدن نتیجهٔ آموزشی", modifier = Modifier.testTag("checker_submit")) {
                val ans = SymptomChecker.questions(t).map {
                    com.mahava.app.checker.CheckerAnswer(it.id, answers[it.id] == true)
                }
                val r = SymptomChecker.score(t, ans, vm.today().toEpochDay())
                result = r
                vm.saveCheckerResult(r)
                topic = null
            }
            TextButton(onClick = { topic = null; answers.clear() }) { Text("انصراف") }
        } else if (result != null) {
            val r = result!!
            MahavaCard(Modifier.testTag("checker_result")) {
                Text(r.topic.titleFa, style = MaterialTheme.typography.titleLarge)
                Text(
                    SymptomChecker.bandLabelFa(r.band),
                    color = when (r.band) {
                        CheckerBand.LOW -> MahavaFertility
                        CheckerBand.MODERATE -> MahavaPrimary
                        CheckerBand.HIGHER -> MahavaMenstruation
                    },
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(Modifier.height(8.dp))
                Text(r.summaryFa)
                Spacer(Modifier.height(8.dp))
                Text("چه چیزهایی را با پزشک مطرح کنی", style = MaterialTheme.typography.titleMedium)
                Bullets(r.discussFa)
                Spacer(Modifier.height(8.dp))
                QuietInfo(r.disclaimerFa)
            }
            Spacer(Modifier.height(8.dp))
            SecondaryButton("شروع دوباره") { result = null; topic = null; answers.clear() }
        }
        Spacer(Modifier.height(24.dp))
    }
}


@Composable
fun PhaseForecastScreen(vm: AppViewModel, onBack: () -> Unit, onCravings: () -> Unit = {}, onAccount: () -> Unit = {}) {
    val premium by vm.isPremium.collectAsState()
    val state by vm.state.collectAsState()
    val forecast = remember(state.dailyLogs, state.cycle) {
        vm.phaseForecast()
    }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .testTag("phase_forecast_screen")
    ) {
        ScreenHeader("پیش‌بینی فردا", onBack = onBack)
        if (!premium) {
            PremiumPaywallCard(
                vm = vm,
                titleFa = "پیش‌بینی خلق و هوس فردا",
                benefitFa = "با اشتراک ماه باز می‌شه.",
                onOpenAccount = onAccount
            )
            return@Column
        }
        QuietInfo("بر اساس مرحلهٔ تخمینی چرخه برای فردا — ${forecast.phaseTitleFa}")
        Spacer(Modifier.height(8.dp))
        MahavaCard {
            Text("وضعیت هورمونی (ساده‌شده)", style = MaterialTheme.typography.titleMedium, color = MahavaPrimary)
            Text(forecast.hormoneSnapshotFa)
        }
        Spacer(Modifier.height(8.dp))
        MahavaCard {
            Text("حال روحی فردا", style = MaterialTheme.typography.titleMedium, color = MahavaPrimary)
            Text(forecast.moodLineFa)
            Spacer(Modifier.height(8.dp))
            Text(forecast.detailMoodFa)
            Spacer(Modifier.height(8.dp))
            Text("اگر این حال را دیدی، دلیل علمی‌اش چیست؟", style = MaterialTheme.typography.titleSmall, color = MahavaPrimary)
            Text(forecast.scienceMoodFa)
        }
        Spacer(Modifier.height(8.dp))
        MahavaCard {
            Text("هوس خوراکی فردا", style = MaterialTheme.typography.titleMedium, color = MahavaPrimary)
            Text(forecast.cravingLineFa)
            Spacer(Modifier.height(8.dp))
            Text(forecast.detailCravingFa)
            Spacer(Modifier.height(8.dp))
            Text("اگر این هوس را دیدی، دلیل علمی‌اش چیست؟", style = MaterialTheme.typography.titleSmall, color = MahavaPrimary)
            Text(forecast.scienceCravingFa)
        }
        forecast.personalNoteFa?.let {
            Spacer(Modifier.height(8.dp))
            MahavaCard {
                Text("از ثبت‌های خودت", style = MaterialTheme.typography.titleMedium)
                Text(it)
            }
        }
        Spacer(Modifier.height(8.dp))
        MahavaCard {
            Text("چه کار می‌توانی بکنی", style = MaterialTheme.typography.titleMedium)
            Text(forecast.tipFa)
        }
        Spacer(Modifier.height(8.dp))
        SecondaryButton("معنی هوس‌های رایج در این دوره", onClick = onCravings)
        QuietInfo(forecast.disclaimerFa)
        Spacer(Modifier.height(24.dp))
    }
}