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
    Column(Modifier.fillMaxSize().background(MahavaBackground).verticalScroll(rememberScrollState()).testTag("today_screen")) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("سلام، امروز چطوری؟", style = MaterialTheme.typography.titleLarge, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                QuietInfo(JalaliDate.from(today).formatShortFa())
            }
            NotificationBell(vm, onNotifications)
            androidx.compose.material3.IconButton(onClick = onSettings, modifier = Modifier.testTag("open_settings")) {
                MahavaIcon(R.drawable.design_settings, MahavaTextPrimary)
            }
        }
        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            HomeWeekStrip(today, onCalendar)
            // Accounts restored at login skip onboarding: ask once here, never again after that.
            NotificationPermissionCard(NOTIFICATION_REASON_FA, onlyIfNeverAsked = true)
            if (state.profile?.qaSampleData == true) QuietInfo("این‌ها دادهٔ نمونه‌ان، نه اطلاعات واقعی.")
            val phaseGroup = com.mahava.app.content.TodaySignalContent.effectivePhaseGroup(
                ctx.subWindow, cycle?.phase, state.profile?.hormonalContraception == true, state.profile?.regularCycles
            )
            if (pregnant) {
                PregnancyCompanion(vm, onPregnancy, onPregnancySetup, onDailyLog, onHealthInsight, part = PregnancyPart.HERO)
                BodyNowCard(com.mahava.app.content.BodyNowContent.forPregnancy(health?.pregnancyProgress?.contentWeek), pregnant = true) {
                    health?.pregnancyProgress?.contentWeek?.let { onHealthInsight("preg_week_${it.coerceAtMost(42)}") } ?: onPregnancySetup()
                }
            } else {
                Column(Modifier.fillMaxWidth().testTag("cycle_status_card"), horizontalAlignment = Alignment.CenterHorizontally) {
                    val length = pred?.medianCycleLength ?: state.profile?.typicalCycleLength
                    val day = cycle?.cycleDay
                    HomeOrbit(day, "روز چرخه", if (length != null && day != null) day.toFloat() / length else null)
                    Text(ctx.subWindow.titleFa, color = MahavaPrimaryDeep, style = MaterialTheme.typography.titleMedium)
                    if (pred != null) {
                        Text("پریود بعدیت حدود ${JalaliDate.from(pred.nextPeriodStartCentral).formatShortFa()} · تخمینی", color = MahavaTextSecondary)
                    } else QuietInfo("برای دیدن چرخه‌ت، آخرین پریودت رو ثبت کن.")
                }
                BodyNowCard(com.mahava.app.content.BodyNowContent.forCycle(phaseGroup, log), pregnant = false, onMore = onScience)
            }
            TodayMoodSection(vm, state.dailyLogs, today)
            PrimaryButton("ثبت علائم امروز", modifier = Modifier.testTag("today_log_button"), onClick = onDailyLog)
            TodayCheckIn(
                log = log,
                phaseGroup = if (pregnant) "general" else phaseGroup,
                onSave = vm::saveTodaySignals,
                onDetail = onSignal
            )
            health?.activeAlerts?.take(2)?.forEach { a ->
                MahavaCard(Modifier.clickable(onClick = onAssistant)) {
                    Text(a.titleFa, color = MahavaDanger, style = MaterialTheme.typography.titleMedium)
                    Text(a.recommendedActionFa)
                    MoreLink("بیشتر بدون")
                }
            }
            // One «پیشنهاد امروز»: the subscriber's daily tip, else a simple care tip for this time of the cycle.
            val rec = if (premium) tools.find { it.id == "recommendation_${today.toEpochDay()}" && it.deletedAt == null }?.let { record ->
                runCatching { com.google.gson.Gson().fromJson(record.payloadJson, com.mahava.app.health.DailyRecommendation::class.java).body }.getOrNull()
            } else null
            val tip = rec ?: if (!pregnant) science.careTipsFa.firstOrNull() else null
            if (tip != null) {
                Row(
                    Modifier.fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(24.dp)).background(MahavaPrimarySoft)
                        .clickable { if (rec != null) onTool("tools/recommendation") else onCareDetail(vm.careToday().item.id) }
                        .padding(16.dp).testTag("today_recommendation"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("پیشنهاد امروز", style = MaterialTheme.typography.titleMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text(tip, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    }
                    Spacer(Modifier.width(8.dp))
                    MahavaIcon(R.drawable.design_chevron_left, MahavaTextSecondary, 18.dp)
                }
            }
            if (pregnant) PregnancyCompanion(vm, onPregnancy, onPregnancySetup, onDailyLog, onHealthInsight, part = PregnancyPart.REST)
            Row(
                Modifier.fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(24.dp)).background(MahavaLavenderSoft)
                    .clickable(onClick = onPartner).padding(16.dp).testTag("home_partner_card"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("اتصال به همسر", style = MaterialTheme.typography.titleMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text("اگه بخوای، همسرت هم می‌تونه چیزهایی رو که اینجا ثبت می‌کنی ببینه.", style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.width(8.dp))
                MahavaIcon(R.drawable.design_heart, MahavaPrimary, 30.dp)
                Spacer(Modifier.width(6.dp))
                MahavaIcon(R.drawable.design_chevron_left, MahavaTextSecondary, 18.dp)
            }
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

