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
                            Text(cycle?.cycleDay?.let { "روز ${PersianDigits.toPersian(it)} چرخه" } ?: "همراه چرخهٔ تو", style = MaterialTheme.typography.headlineLarge)
                            Text(ctx.subWindow.titleFa, color = MahavaPrimaryDeep)
                            Spacer(Modifier.height(10.dp))
                            if (pred != null) {
                                Text("پریود بعدی، تخمینی")
                                Text(if (pred.nextPeriodStartEarliest == pred.nextPeriodStartLatest) "حوالی ${JalaliDate.from(pred.nextPeriodStartCentral).formatShortFa()}" else "${JalaliDate.from(pred.nextPeriodStartEarliest).formatShortFa()} تا ${JalaliDate.from(pred.nextPeriodStartLatest).formatShortFa()}")
                                QuietInfo(cycle?.limitsDescriptionFa.orEmpty())
                            } else QuietInfo(cycle?.basisDescriptionFa ?: "برای شروع، تاریخ پریودت را ثبت کن.")
                        }
                        Illustration(R.drawable.design_lavender_sprig, Modifier.width(64.dp).height(120.dp))
                    }
                    val length = pred?.medianCycleLength ?: state.profile?.typicalCycleLength
                    if (length != null && cycle?.cycleDay != null) {
                        Spacer(Modifier.height(12.dp))
                        androidx.compose.material3.LinearProgressIndicator(progress = { (cycle.cycleDay.toFloat() / length).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(6.dp), color = MahavaPrimary, trackColor = MahavaSurface)
                    }
                    Spacer(Modifier.height(8.dp))
                    QuietInfo(vm.predictionBasisShortFa())
                    if (pred != null) HealthContextNote(HealthNote.PREDICTION)
                    if (cycle?.isLate == true) androidx.compose.material3.TextButton(onClick = onLate) { Text("پریود دیر کرده؛ راهنمای بررسی") }
                }
            }
            health?.activeAlerts?.take(2)?.forEach { a ->
                MahavaCard(Modifier.clickable(onClick = onAssistant)) {
                    Text(a.titleFa, color = MahavaDanger, style = MaterialTheme.typography.titleMedium)
                    Text(a.recommendedActionFa)
                    Text("جزئیات و مبنای راهنما ‹", color = MahavaPrimaryDeep)
                }
            }
            MahavaCard(Modifier.testTag("quick_feelings")) {
                SectionLabel("حالت امروز چطوره؟")
                QuickFeelings(log, symptoms) { key ->
                    if (!premium) onAccount()
                    else vm.toggleTodayFeeling(key)
                }
                androidx.compose.material3.TextButton(onClick = onDailyLog) { Text("ثبت کامل علائم ‹", color = MahavaPrimaryDeep) }
            }
            if (!pregnant) {
                MahavaCard(Modifier.testTag("phase_science_card").clickable(onClick = onScience)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            SectionLabel("در بدنت چه می‌گذرد؟")
                            Text(science.teaserFa)
                            QuietInfo("اطلاعات عمومی دربارهٔ مرحلهٔ تخمینی؛ گزارش علائم تو نیست.")
                            Text("توضیح و منابع علمی ‹", color = MahavaPrimaryDeep)
                        }
                        Illustration(R.drawable.design_uterus_botanical, Modifier.width(90.dp).height(100.dp))
                    }
                }
                MahavaCard {
                    SectionLabel("پیشنهادهای امروز")
                    CompanionAction("ثبت خواب و حال بدنت", "ثبت‌های واقعی به شناخت الگوی خودت کمک می‌کنند.", R.drawable.design_bed, onDailyLog)
                    CompanionAction("مراقبت متناسب با حالت", if ("pain" in symptoms) "راهنمای درد و زمان مراجعه را بخوان." else "راهنمای حرکت، استراحت و مراقبت روزانه.", R.drawable.design_leaf) { onCareDetail(vm.careToday().item.id) }
                }
                HealthContextNote(HealthNote.GENERAL)
                val tomorrow = cycle?.let { com.mahava.app.cycle.CycleDayContextResolver.resolveOn(it, today.plusDays(1)) }
                MahavaCard(Modifier.testTag("phase_forecast_card").clickable(onClick = onForecast), containerColor = MahavaMenstruationSoft.copy(alpha = 0.6f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        MahavaIcon(R.drawable.design_sun, MahavaAccentPeach, 30.dp)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            SectionLabel("فردا چه خبر؟")
                            Text(tomorrow?.subWindow?.titleFa ?: "برای تخمین فردا، سابقهٔ کافی نداریم.")
                            QuietInfo("مرحله از تاریخ‌ها تخمین زده می‌شود؛ حال و علائم فردا قابل پیش‌گویی نیست.")
                        }
                    }
                }
            }
            MahavaCard(Modifier.clickable(onClick = onAssistant), containerColor = MahavaPrimarySoft.copy(alpha = 0.5f)) {
                CompanionAction("سؤالی داری؟", "راهنمای آفلاین با منابع علمی", R.drawable.design_chat, onAssistant)
            }
            PrimaryButton("＋  ثبت امروز", modifier = Modifier.testTag("today_log_button"), onClick = onDailyLog)
            Spacer(Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuickFeelings(log: com.mahava.app.data.db.DailyLogEntity?, symptoms: Set<String>, onToggle: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("happy" to "خوبم", "fatigue" to "خستگی", "pain" to "درد", "bloating" to "نفخ").forEach { (key, title) ->
            val selected = if (key == "happy") log?.moods == key else key in symptoms
            val icon = when(key) { "happy" -> R.drawable.design_smile; "fatigue" -> R.drawable.design_battery; "pain" -> R.drawable.design_energy; else -> R.drawable.design_wind }
            androidx.compose.material3.Surface(onClick = { onToggle(key) }, modifier = Modifier.weight(1f).heightIn(min = 76.dp).testTag("quick_$key"),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                color = if (selected) MahavaPrimarySoft else when(key) { "happy" -> androidx.compose.ui.graphics.Color(0xFFEAF4EF); "pain" -> MahavaMenstruationSoft; else -> MahavaPrimarySoft.copy(alpha=0.5f) },
                border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) MahavaPrimary else androidx.compose.ui.graphics.Color.Transparent)) {
                Column(Modifier.padding(8.dp), horizontalAlignment=Alignment.CenterHorizontally, verticalArrangement=Arrangement.spacedBy(6.dp)) {
                    MahavaIcon(icon, MahavaPrimaryDeep)
                    Text(title, style=MaterialTheme.typography.bodySmall, color=MahavaTextPrimary)
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
