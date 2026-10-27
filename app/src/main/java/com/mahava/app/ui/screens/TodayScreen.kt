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
import androidx.compose.foundation.Image
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    val notices by vm.notices.collectAsState()
    val inbox by vm.inboxItems.collectAsState()
    val unread = notices.count { !it.read } + inbox.size
    val k = com.mahava.app.ui.kit.Kit
    Column(Modifier.fillMaxSize().background(MahavaBackground).verticalScroll(com.mahava.app.ui.kit.rememberKitScroll()).testTag("today_screen")) {
        // approved-home header: bell (start/right), centred greeting, smile button (end/left) for the account.
        com.mahava.app.ui.kit.KitHeader(
            title = "ماه، پلتفرم شناخت بدن خانم‌ها",
            titleStyle = com.mahava.app.ui.kit.Kit.title.copy(fontSize = 16.sp),
            start = { com.mahava.app.ui.kit.KitCircleButton(R.drawable.kit_bell, dot = unread > 0, tag = "open_notifications", onClick = onNotifications) },
            end = { com.mahava.app.ui.kit.KitCircleButton(R.drawable.kit_settings, tag = "open_settings", contentDescription = "تنظیمات", onClick = onSettings) }
        )
        val pred0 = pred
        val periodDays = remember(state.periods, today) {
            buildSet {
                state.periods.forEach { pe ->
                    val end = pe.endEpochDay ?: if (pe.stillOngoing) today.toEpochDay() else pe.startEpochDay
                    for (d in pe.startEpochDay..end) add(d)
                }
            }
        }
        val bleed = (state.profile?.typicalBleedLength ?: 5).coerceIn(2, 10)
        fun dotFor(d: java.time.LocalDate): androidx.compose.ui.graphics.Color = when {
            d == today -> k.lavender
            d.toEpochDay() in periodDays -> k.pink.copy(alpha = .55f)
            !pregnant && pred0 != null && !d.isBefore(pred0.nextPeriodStartCentral) && d.isBefore(pred0.nextPeriodStartCentral.plusDays(bleed.toLong())) -> k.pink.copy(alpha = .4f)
            !pregnant && pred0 != null && !d.isBefore(pred0.fertilityEarliest) && !d.isAfter(pred0.fertilityLatest) -> k.lavender.copy(alpha = .35f)
            else -> androidx.compose.ui.graphics.Color(0xFFE7E4F2)
        }
        Spacer(Modifier.height(6.dp))
        com.mahava.app.ui.kit.KitWeekStrip(today, ::dotFor, onCalendar)
        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Accounts restored at login skip onboarding: ask once here, never again after that.
            NotificationPermissionCard(NOTIFICATION_REASON_FA, onlyIfNeverAsked = true)
            if (state.profile?.qaSampleData == true) QuietInfo("این‌ها دادهٔ نمونه‌ان، نه اطلاعات واقعی.")
            val phaseGroup = com.mahava.app.content.TodaySignalContent.effectivePhaseGroup(
                ctx.subWindow, cycle?.phase, state.profile?.hormonalContraception == true, state.profile?.regularCycles
            )
            if (pregnant) {
                val p = health?.pregnancyProgress
                Box(Modifier.fillMaxWidth().clickable(onClick = onPregnancy).testTag("pregnancy_hero")) {
                    com.mahava.app.ui.kit.KitOrbit(
                        number = p?.completedWeeks?.let { PersianDigits.toPersian(it) } ?: "—",
                        caption = if (p != null) "هفته و ${PersianDigits.toPersian(p.daysIntoWeek)} روز" else "هفتهٔ بارداری",
                        sub = p?.let { (when { it.completedWeeks < 13 -> "سه‌ماههٔ اول"; it.completedWeeks < 28 -> "سه‌ماههٔ دوم"; else -> "سه‌ماههٔ سوم" }) + " · تخمینی" }
                            ?: "برای حساب کردن هفته، تاریخ مبنا رو ثبت کن",
                        progress = p?.progressFraction
                    )
                    Row(
                        Modifier.align(Alignment.TopEnd).clip(androidx.compose.foundation.shape.RoundedCornerShape(50)).background(MahavaPrimarySoft)
                            .clickable(onClick = onPregnancySetup).padding(horizontal = 10.dp, vertical = 5.dp).testTag("pregnancy_mode_chip"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        MahavaIcon(R.drawable.kit_pregnancy, MahavaPrimary, 18.dp)
                        Spacer(Modifier.width(4.dp))
                        Text("بارداری", style = k.small.copy(color = MahavaTextPrimary, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold))
                        Spacer(Modifier.width(2.dp))
                        MahavaIcon(R.drawable.kit_chevron_left, MahavaTextSecondary, 14.dp)
                    }
                }
                BodyNowCard(com.mahava.app.content.BodyNowContent.forPregnancy(p?.contentWeek), pregnant = true) {
                    p?.contentWeek?.let { onHealthInsight("preg_week_${it.coerceAtMost(42)}") } ?: onPregnancySetup()
                }
            } else {
                val length = pred?.medianCycleLength ?: state.profile?.typicalCycleLength
                val day = cycle?.cycleDay
                val until = cycle?.daysUntilCentralPeriod?.takeIf { it > 0 && cycle.isLate.not() }
                val progress = if (length != null && day != null) (day.toFloat() / length).coerceIn(0f, 1f) else null
                val marks = if (pred != null && length != null && day != null && cycle?.lastPeriodStart != null) {
                    val start = cycle.lastPeriodStart
                    (0..length).map { start.plusDays(it.toLong()) }
                        .filter { !it.isBefore(pred.fertilityEarliest) && !it.isAfter(pred.fertilityLatest) }
                        .map { (it.toEpochDay() - start.toEpochDay() + 1).toInt() }
                        .filter { it > day }.take(6).map { it.toFloat() / length }
                } else emptyList()
                Box(Modifier.fillMaxWidth().clickable { onPhaseDetail(vm.phaseTodaySelection().item.id) }.testTag("cycle_status_card")) {
                    com.mahava.app.ui.kit.KitOrbit(
                        number = (until ?: day)?.let { PersianDigits.toPersian(it) } ?: "—",
                        caption = if (until != null) "روز تا پریود بعدی" else if (day != null) "روز چرخه" else "چرخهٔ تو",
                        sub = when {
                            until != null && day != null -> "روز ${PersianDigits.toPersian(day)} چرخه · تخمینی"
                            day != null -> ctx.subWindow.titleFa
                            else -> "برای دیدن چرخه‌ت، آخرین پریودت رو ثبت کن."
                        },
                        progress = progress,
                        marks = marks
                    )
                }
                BodyNowCard(com.mahava.app.content.BodyNowContent.forCycle(phaseGroup, log, ctx.subWindow), pregnant = false, onMore = onScience)
            }
            TodayMoodSection(vm, state.dailyLogs, today, showIntimacy = !pregnant)
            com.mahava.app.ui.kit.KitPillButton("ثبت حال امروز", modifier = Modifier.testTag("today_log_button"), onClick = onDailyLog)
            health?.activeAlerts?.take(2)?.forEach { a ->
                com.mahava.app.ui.kit.KitRowCard(a.titleFa, a.recommendedActionFa, MahavaPrimarySoft, onClick = onAssistant,
                    art = { com.mahava.app.ui.kit.KitIconTile(R.drawable.kit_warning, MahavaDanger) })
            }
            // One «پیشنهاد امروز»: the same personal-first tip as its detail page and the 08:00 notification.
            val todayTip = vm.todayTip(premium, tools)
            val tip = todayTip?.body
            if (tip != null) {
                com.mahava.app.ui.kit.KitRowCard(
                    "پیشنهاد امروز", tip, k.cardPink, tag = "today_recommendation",
                    art = { if (pregnant) com.mahava.app.ui.kit.KitIconTile(R.drawable.kit_food, MahavaPrimary, size = 64.dp, iconSize = 34.dp)
                        else androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(R.drawable.kit_art_bed), null,
                            Modifier.size(64.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp)).background(androidx.compose.ui.graphics.Color.White).padding(8.dp)) },
                    onClick = { onCareDetail(todayTip?.contentId ?: vm.careToday().item.id) }
                )
            }
            com.mahava.app.ui.kit.KitRowCard(
                "اتصال به همسر", "اگه بخوای، همسرت هم می‌تونه چیزهایی رو که اینجا ثبت می‌کنی ببینه.", k.cardLavender,
                tag = "home_partner_card", art = { com.mahava.app.ui.kit.KitHearts() }, onClick = onPartner
            )
            Spacer(Modifier.height(16.dp))
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

