package com.mahava.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.mahava.app.R
import com.mahava.app.content.PhaseScienceBank
import com.mahava.app.cycle.PredictionKind
import com.mahava.app.ui.AppViewModel
import com.mahava.app.ui.components.*
import com.mahava.app.ui.theme.*
import com.mahava.app.util.JalaliDate
import com.mahava.app.util.PersianDigits
import kotlinx.coroutines.delay

@Composable
fun TodayScreen(
    vm: AppViewModel,
    onSettings: () -> Unit,
    onDailyLog: () -> Unit,
    onPeriod: () -> Unit,
    onBody: () -> Unit,
    onLate: () -> Unit,
    onPhaseDetail: (String) -> Unit,
    onCareDetail: (String) -> Unit,
    onDailyInsight: () -> Unit = {},
    onCravings: () -> Unit = {},
    onPatterns: () -> Unit = {},
    onTrends: () -> Unit = {},
    onChecker: () -> Unit = {},
    onForecast: () -> Unit = {},
    onCalendar: () -> Unit = {},
    onAccount: () -> Unit = {},
    onQuickLog: () -> Unit = onDailyLog,
    onFoodTips: () -> Unit = {},
    onFertility: () -> Unit = {},
    onDoctor: () -> Unit = {},
    onScience: () -> Unit = {},
    onSignal: (String, String) -> Unit = { _, _ -> },
    onPartner: () -> Unit = {},
    onNotifications: () -> Unit = {},
    onMore: () -> Unit = onDailyLog,
    onTool: (String) -> Unit = {},
    onHealthAnalytics: () -> Unit = onTrends,
    onPregnancy: () -> Unit = {},
    onPregnancySetup: () -> Unit = {},
    onHealthInsight: (String) -> Unit = {},
    onAssistant: () -> Unit = {},
    onHealthFeed: () -> Unit = {}
) {
    val state by vm.state.collectAsState()
    val premium by vm.isPremium.collectAsState()
    val cycle = state.cycle
    val health = state.health
    val pregnant = health?.appMode == com.mahava.app.health.models.AppHealthMode.PREGNANCY
    val today = vm.today()
    val ctx = vm.dayContext()
    val pred = cycle?.prediction as? PredictionKind.Estimate
    val science = PhaseScienceBank.forContext(ctx.subWindow, cycle?.phase)
    val log = state.dailyLogs.find { it.epochDay == today.toEpochDay() }
    val symptoms = log?.physicalSymptoms?.split(',')?.filter { it.isNotBlank() }?.toSet().orEmpty()
    val tools by vm.toolRecords.collectAsState()
    LaunchedEffect(today) { runCatching { vm.tools.ensureRecommendation() } }
    val food = log?.foodCravings?.split(',')?.filter { it.isNotBlank() }?.toSet().orEmpty()
    Column(Modifier.fillMaxSize().background(MahavaBackground).verticalScroll(rememberScrollState()).testTag("today_screen")) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("امروزِ تو", style = MaterialTheme.typography.headlineLarge)
                QuietInfo(JalaliDate.from(today).formatShortFa())
            }
            NotificationBell(vm, onNotifications)
            androidx.compose.material3.IconButton(onClick = onSettings, modifier = Modifier.testTag("open_settings")) {
                MahavaIcon(R.drawable.design_settings, MahavaTextPrimary)
            }
        }
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (state.profile?.qaSampleData == true) QuietInfo("دادهٔ نمونه برای آزمایش؛ اطلاعات واقعی نیست.")
            if (pregnant) {
                PregnancyCompanion(vm, onPregnancy, onPregnancySetup, onDailyLog, onHealthInsight)
            } else {
                MahavaCard(Modifier.testTag("cycle_status_card"), containerColor = MahavaPrimarySoft) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(cycle?.cycleDay?.let { "روز ${PersianDigits.toPersian(it)} از چرخه‌ات" } ?: "چرخهٔ تو", style = MaterialTheme.typography.headlineLarge)
                            Text(ctx.subWindow.titleFa, color = MahavaPrimaryDeep)
                            Spacer(Modifier.height(10.dp))
                            if (pred != null) {
                                Text("پریود بعدی (حدودی)")
                                Text(
                                    if (pred.nextPeriodStartEarliest == pred.nextPeriodStartLatest)
                                        "حدود ${JalaliDate.from(pred.nextPeriodStartCentral).formatShortFa()}"
                                    else
                                        "از ${JalaliDate.from(pred.nextPeriodStartEarliest).formatShortFa()} تا ${JalaliDate.from(pred.nextPeriodStartLatest).formatShortFa()}"
                                )
                                QuietInfo("این فقط یک حدس است؛ تاریخ دقیق ممکن است فرق کند.")
                            } else QuietInfo(cycle?.basisDescriptionFa ?: "اول تاریخ آخرین پریودت را ثبت کن.")
                        }
                        Illustration(R.drawable.design_lavender_sprig, Modifier.width(64.dp).height(120.dp))
                    }
                    val length = pred?.medianCycleLength ?: state.profile?.typicalCycleLength
                    if (length != null && cycle?.cycleDay != null) {
                        Spacer(Modifier.height(12.dp))
                        androidx.compose.material3.LinearProgressIndicator(progress = { (cycle.cycleDay.toFloat() / length).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(6.dp), color = MahavaPrimary, trackColor = MahavaSurface)
                    }
                    if (cycle?.isLate == true) androidx.compose.material3.TextButton(onClick = onLate) { Text("پریود دیر کرده؟ اینجا را ببین") }
                }
            }
            health?.activeAlerts?.take(2)?.forEach { a ->
                MahavaCard(Modifier.clickable(onClick = onAssistant)) {
                    Text(a.titleFa, color = MahavaDanger, style = MaterialTheme.typography.titleMedium)
                    Text(a.recommendedActionFa)
                    Text("جزئیات و مبنای راهنما ‹", color = MahavaPrimaryDeep)
                }
            }
            tools.find { it.id == "recommendation_${today.toEpochDay()}" && it.deletedAt == null }?.let { record ->
                val recommendation = com.google.gson.Gson().fromJson(record.payloadJson, com.mahava.app.health.DailyRecommendation::class.java)
                MahavaCard(Modifier.clickable { onTool("tools/recommendation") }.testTag("today_recommendation"), containerColor = softTint(MahavaFertility)) {
                    SectionLabel("توصیهٔ امروز")
                    Text(recommendation.body)
                    Text("چرا این توصیه؟ و توصیه‌های قبلی ‹", color = MahavaPrimaryDeep)
                }
            }
            MahavaCard(Modifier.testTag("quick_feelings")) {
                SectionLabel("امروز حالت چطور است؟")
                QuickFeelings(log, symptoms) { key ->
                    if (!premium) onAccount()
                    else vm.toggleTodayFeeling(key)
                }
            }
            if (!pregnant) HomeToolCards(vm, onTool)
            if (!pregnant) {
                MahavaCard(Modifier.testTag("phase_science_card").clickable(onClick = onScience)) {
                    // Only the title shares the row with the picture, so the text keeps full width with large fonts.
                    Row(verticalAlignment = Alignment.Top) {
                        Column(Modifier.weight(1f)) {
                            SectionLabel("وضعیت امروزت")
                            Text(science.teaserFa, style = MaterialTheme.typography.titleMedium, color = MahavaPrimaryDeep)
                        }
                        Illustration(R.drawable.design_uterus_botanical, Modifier.width(72.dp).height(80.dp))
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(science.homePracticalFa.ifBlank { science.bodyFa.take(140) })
                    Spacer(Modifier.height(8.dp))
                    QuietInfo("این راهنمای کلی است؛ جای پزشک را نمی‌گیرد.")
                    Text("بیشتر بخوان ‹", color = MahavaPrimaryDeep)
                }
                MahavaCard {
                    SectionLabel("پیشنهاد امروز")
                    science.foodTipsFa.firstOrNull()?.let { tip ->
                        CompanionAction("غذای امروز", tip, R.drawable.design_leaf, onFoodTips)
                    }
                    science.careTipsFa.firstOrNull()?.let { tip ->
                        CompanionAction(
                            if ("pain" in symptoms) "کمک به درد" else "مراقبت امروز",
                            tip,
                            R.drawable.design_calm
                        ) { onCareDetail(vm.careToday().item.id) }
                    }
                }
                val tomorrowCtx = cycle?.let { com.mahava.app.cycle.CycleDayContextResolver.resolveOn(it, today.plusDays(1)) }
                val forecast = remember(tomorrowCtx, state.dailyLogs) {
                    tomorrowCtx?.let {
                        com.mahava.app.content.PhaseForecastEngine.build(it.subWindow, cycle?.phase, state.dailyLogs.takeLast(14))
                    }
                }
                MahavaCard(Modifier.testTag("phase_forecast_card").clickable(onClick = onForecast), containerColor = MahavaMenstruationSoft.copy(alpha = 0.55f)) {
                    Row(verticalAlignment = Alignment.Top) {
                        MahavaIcon(R.drawable.design_sun, MahavaAccentPeach, 30.dp)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            SectionLabel("فردا چه خبر؟")
                            Text(forecast?.phaseTitleFa ?: tomorrowCtx?.subWindow?.titleFa ?: "هنوز برای حدس فردا دادهٔ کافی نداریم.", color = MahavaPrimaryDeep)
                            Spacer(Modifier.height(4.dp))
                            if (forecast != null) {
                                Text(forecast.moodLineFa)
                                Spacer(Modifier.height(4.dp))
                                QuietInfo(forecast.tipFa)
                                forecast.cravingLineFa.takeIf { it.isNotBlank() }?.let {
                                    Spacer(Modifier.height(4.dp))
                                    QuietInfo(it)
                                }
                            } else {
                                QuietInfo("با ثبت چند پریود، حدس فردا بهتر می‌شود.")
                            }
                            Spacer(Modifier.height(6.dp))
                            Text("بیشتر ببین ‹", color = MahavaPrimaryDeep)
                        }
                    }
                }
            }
            PrimaryButton("ثبت امروز", modifier = Modifier.testTag("today_log_button"), onClick = onDailyLog)
            Spacer(Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuickFeelings(log: com.mahava.app.data.db.DailyLogEntity?, symptoms: Set<String>, onToggle: (String) -> Unit) {
    // Two per row when four would clip the words (small phones / large system font).
    androidx.compose.foundation.layout.BoxWithConstraints {
    val perRow = if (maxWidth < 60.dp * 4 * androidx.compose.ui.platform.LocalDensity.current.fontScale) 2 else 4
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), maxItemsInEachRow = perRow) {
        listOf("happy" to "خوبم", "fatigue" to "خستگی", "pain" to "درد", "bloating" to "نفخ").forEach { (key, title) ->
            val selected = if (key == "happy") log?.moods == key else key in symptoms
            val icon = when(key) { "happy" -> R.drawable.design_smile; "fatigue" -> R.drawable.design_battery; "pain" -> R.drawable.design_energy; else -> R.drawable.design_wind }
            androidx.compose.material3.Surface(onClick = { onToggle(key) }, modifier = Modifier.weight(1f).heightIn(min = 76.dp).testTag("quick_$key"),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                color = if (selected) MahavaPrimarySoft else when(key) { "happy" -> androidx.compose.ui.graphics.Color(0xFFEAF4EF); "pain" -> MahavaMenstruationSoft; else -> MahavaPrimarySoft.copy(alpha=0.5f) },
                shadowElevation = if (selected) 2.dp else 0.dp) {
                Column(Modifier.padding(8.dp), horizontalAlignment=Alignment.CenterHorizontally, verticalArrangement=Arrangement.spacedBy(6.dp)) {
                    MahavaIcon(icon, MahavaPrimaryDeep)
                    Text(title, style=MaterialTheme.typography.bodySmall, color=MahavaTextPrimary, maxLines = 1, softWrap = false)
                }
            }
        }
    }
    }
}

@Composable
fun CompanionAction(title: String, subtitle: String, icon: Int, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp).heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        MahavaIcon(icon, MahavaPrimaryDeep, 26.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            QuietInfo(subtitle)
        }
        MahavaIcon(R.drawable.design_chevron_left, MahavaTextSecondary, 18.dp)
    }
}

/** Four colourful home cards for the tools that read best as pictures. */
@Composable
private fun HomeToolCards(vm: AppViewModel, onTool: (String) -> Unit) {
    val state by vm.state.collectAsState()
    val records by vm.toolRecords.collectAsState()
    val today = vm.today().toEpochDay()
    // Every mini chart covers her whole history (first entry → today), squeezed into ≤ 12 points.
    val TR = com.mahava.app.health.TimeRange
    val allLengths = com.mahava.app.health.CycleStats.completeLengths(state.periods, today)
    val lengths = TR.compress(allLengths.map { it.toFloat() }, 12)
    val first = rememberFirstEntryDay(vm)
    val plan = remember(first, today) { TR.plan(first ?: today, today, maxPoints = 12) }
    val mood = TR.average(state.dailyLogs, plan) { com.mahava.app.health.CycleStats.mood(it) }
    val energy = TR.average(state.dailyLogs, plan) { com.mahava.app.health.CycleStats.energy(it) }
    val pain = TR.average(state.dailyLogs, plan) { it.painScore?.toFloat() }
    val painDays = state.dailyLogs.count { it.epochDay in (first ?: today)..today && (it.painScore ?: 0) > 0 }
    val sinceFa = first?.let { f ->
        val j = JalaliDate.from(java.time.LocalDate.ofEpochDay(f))
        "از " + j.formatFa(withYear = j.year != JalaliDate.from(java.time.LocalDate.ofEpochDay(today)).year)
    } ?: ""
    val insights = records.filter { it.type == "insight" && it.deletedAt == null }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.testTag("home_tool_cards")) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HomeCard("الگوی چرخه", if (allLengths.isEmpty()) "با ثبت پریودها کامل می‌شود" else "میانگین ${PersianDigits.toPersian(Math.round(allLengths.average()).toInt())} روز، ${PersianDigits.toPersian(allLengths.size)} چرخه",
                MahavaPrimary, R.drawable.design_cycle, Modifier.weight(1f).fillMaxHeight(), "home_card_cycle", { onTool("tools/cycle") }) {
                BarChart(lengths, MahavaPrimary, height = 56.dp, maxValue = (lengths.maxOrNull() ?: 30f) + 3f)
            }
            HomeCard("حال و انرژی", if (mood.all { it == null } && energy.all { it == null }) "هنوز ثبتی نیست" else "$sinceFa تا امروز", MahavaAccentGold, R.drawable.design_smile, Modifier.weight(1f).fillMaxHeight(), "home_card_mood", { onTool("tools/mood_energy") }) {
                LineChart(listOf(mood to MahavaPrimary, energy to MahavaAccentGold), height = 56.dp, minValue = 1f, maxValue = 5f)
            }
        }
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HomeCard("ردیاب درد", if (painDays == 0) "از اولین ثبت دردی ثبت نشده" else "${PersianDigits.toPersian(painDays)} روز درد، $sinceFa",
                MahavaMenstruation, R.drawable.design_pain, Modifier.weight(1f).fillMaxHeight(), "home_card_pain", { onTool("tools/pain") }) {
                BarChart(pain, MahavaMenstruation, height = 56.dp, maxValue = 10f)
            }
            HomeCard("بینش‌های من", if (insights.isEmpty()) "با ثبت بیشتر پیدا می‌شوند" else "${PersianDigits.toPersian(insights.size)} الگوی تازه",
                MahavaFertility, R.drawable.design_sun, Modifier.weight(1f).fillMaxHeight(), "home_card_insights", { onTool("tools/insights") }) {
                val first = insights.firstOrNull()?.let { runCatching { com.google.gson.Gson().fromJson(it.payloadJson, com.mahava.app.health.StoredInsight::class.java).title }.getOrNull() }
                Text(first ?: "هر چه بیشتر ثبت کنی، الگوهای خودت را بهتر می‌بینی.", style = MaterialTheme.typography.bodySmall, maxLines = 3)
            }
        }
    }
}

@Composable
private fun HomeCard(title: String, subtitle: String, color: androidx.compose.ui.graphics.Color, icon: Int, modifier: Modifier, tag: String,
                     onClick: () -> Unit, content: @Composable () -> Unit) {
    Column(modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(22.dp)).background(softTint(color)).clickable(onClick = onClick).padding(14.dp).testTag(tag),
        verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(32.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp)).background(color), contentAlignment = Alignment.Center) {
                MahavaIcon(icon, androidx.compose.ui.graphics.Color.White, 18.dp)
            }
            Spacer(Modifier.width(8.dp))
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
        }
        content()
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
