package com.mahava.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    onMore: () -> Unit = onDailyLog
) {
    val state by vm.state.collectAsState()
    val premium by vm.isPremium.collectAsState()
    val cycle = state.cycle
    val today = vm.today()
    val j = JalaliDate.from(today)
    val phaseSel = vm.phaseTodaySelection()
    val dayCtx = vm.dayContext()
    val pred = cycle?.prediction as? PredictionKind.Estimate
    val forecast = vm.phaseForecast()
    val science = PhaseScienceBank.forContext(dayCtx.subWindow, cycle?.phase)
    val lengthHint = pred?.medianCycleLength ?: state.profile?.typicalCycleLength

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag("today_screen")) {
        if (state.profile?.qaSampleData == true) {
            MahavaCard(Modifier.padding(16.dp)) {
                Text("دادهٔ نمونه برای آزمایش", color = MahavaMenstruation, style = MaterialTheme.typography.titleMedium)
                QuietInfo("این اطلاعات ساختگی و فقط برای آزمایش برنامه است. از تنظیمات می‌توانی همه را پاک کنی.")
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("امروز", style = MaterialTheme.typography.headlineLarge)
                Text(j.formatShortFa(), color = MahavaTextSecondary)
            }
            androidx.compose.material3.IconButton(onClick = onSettings, modifier = Modifier.size(48.dp).testTag("open_settings")) {
                MahavaIcon(R.drawable.ic_settings, MahavaTextPrimary)
            }
        }

        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            PeriodCycleWidget(
                cycleDay = cycle?.cycleDay,
                cycleLengthHint = lengthHint,
                phase = cycle?.phase,
                phaseTitleFa = if (premium) dayCtx.subWindow.titleFa else "پیش‌بینی پریود",
                daysUntilPeriod = cycle?.daysUntilCentralPeriod,
                isLate = cycle?.isLate == true
            )
        }

        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Free: next-period prediction
            when {
                cycle?.isLate == true -> {
                    MahavaCard(Modifier.testTag("late_card").clickable {
                        if (premium) onLate() else onAccount()
                    }) {
                        Text("پریود دیر کرده", style = MaterialTheme.typography.titleMedium, color = MahavaMenstruation)
                        Text("حدود ${PersianDigits.toPersian(cycle.daysLate ?: 0)} روز از تاریخ تخمینی گذشته. چند روز جابه‌جایی رایج است.")
                        if (premium) Text("راهنما", color = MahavaPrimary, style = MaterialTheme.typography.labelLarge)
                        else Text("با اشتراک ماه باز می‌شه", color = MahavaPrimary, style = MaterialTheme.typography.labelLarge)
                    }
                }
                pred != null -> {
                    MahavaCard(Modifier.testTag("period_estimate_card")) {
                        val d = cycle?.daysUntilCentralPeriod
                        Text(
                            when {
                                d == null -> "پریود بعدی (تخمینی)"
                                d == 0 -> "پریود ممکن است از همین روزها شروع شود"
                                else -> "حدود ${PersianDigits.toPersian(d)} روز تا پریود بعدی"
                            },
                            style = MaterialTheme.typography.titleMedium
                        )
                        if (cycle?.cycleDay != null && lengthHint != null) {
                            QuietInfo("روز ${PersianDigits.toPersian(cycle.cycleDay)} از حدود ${PersianDigits.toPersian(lengthHint)}")
                        }
                        QuietInfo(vm.predictionBasisShortFa())
                    }
                }
                else -> {
                    MahavaCard(Modifier.testTag("no_estimate_card")) {
                        Text("پریود بعدی را فعلاً تخمین نمی‌زنیم", style = MaterialTheme.typography.titleMedium)
                        QuietInfo(cycle?.basisDescriptionFa ?: "با ثبت چند پریود، تخمین روشن‌تر می‌شود.")
                    }
                }
            }

            if (premium) {
                // Phase science card
                MahavaCard(Modifier.testTag("phase_science_card").clickable { onScience() }) {
                    Text("در بدنم چه می‌گذرد؟", style = MaterialTheme.typography.titleMedium)
                    Text(science.titleFa, color = MahavaPrimary, style = MaterialTheme.typography.titleSmall)
                    Text(science.bodyFa.let { if (it.length > 140) it.take(140).trimEnd() + "…" else it })
                    Spacer(Modifier.height(6.dp))
                    QuietInfo("هورمون‌ها: ${science.hormoneFa.let { if (it.length > 90) it.take(90).trimEnd() + "…" else it }}")
                    Text("جزئیات علمی", color = MahavaPrimary, style = MaterialTheme.typography.labelLarge)
                }

                // Tomorrow forecast
                MahavaCard(Modifier.testTag("phase_forecast_card").clickable { onForecast() }) {
                    Text("فردا ممکنه چی حس کنی", style = MaterialTheme.typography.titleMedium)
                    QuietInfo(forecast.hormoneSnapshotFa)
                    forecast.summaryLinesFa.forEach { line ->
                        Text("• $line", style = MaterialTheme.typography.bodyMedium)
                    }
                    Text("دلیل علمی و جزئیات", color = MahavaPrimary, style = MaterialTheme.typography.labelLarge)
                }

                // 3 care tips
                MahavaCard(Modifier.testTag("care_tips_card").clickable { onCareDetail(vm.careToday().item.id) }) {
                    Text("امروز چیکار کنی", style = MaterialTheme.typography.titleMedium)
                    science.careTipsFa.take(3).forEach { tip ->
                        Text("• $tip")
                    }
                    Text("بیشتر", color = MahavaPrimary, style = MaterialTheme.typography.labelLarge)
                }

                // Tool shortcuts row
                Text("ابزارها", style = MaterialTheme.typography.titleMedium)
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ToolChip("ثبت سریع", onQuickLog, "tool_quick")
                    ToolChip("روند و نمودار", onTrends, "tool_trends")
                    ToolChip("چه بخورم", onFoodTips, "tool_food")
                    ToolChip("پنجره باروری", onFertility, "tool_fertility")
                    ToolChip("گزارش پزشک", onDoctor, "tool_doctor")
                    ToolChip("الگوها", onPatterns, "tool_patterns")
                    ToolChip("بینش امروز", onDailyInsight, "tool_insight")
                }
            } else {
                PremiumTeaserCard("در بدنم چه می‌گذرد؟", onClick = onAccount)
                PremiumTeaserCard("فردا ممکنه چی حس کنی", onClick = onAccount)
                PremiumTeaserCard("نکته‌های مراقبت و ابزارها", onClick = onAccount)
            }

            // Free: period start/end
            SecondaryButton(
                if (cycle?.periodOngoing == true) "ثبت پایان پریود" else "پریودم شروع شد",
                modifier = Modifier.testTag("today_period_button"),
                onClick = onPeriod
            )

            // Free: calendar shortcut
            SecondaryButton("تقویم", modifier = Modifier.testTag("today_calendar_button"), onClick = onCalendar)

            if (premium) {
                Text(
                    "ثبت حال و امکانات بیشتر",
                    color = MahavaPrimary,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier
                        .testTag("today_more_link")
                        .clickable { onMore() }
                        .padding(vertical = 4.dp)
                )
            } else {
                QuietInfo("بدون اشتراک فقط ثبت پریود، تقویم و پیش‌بینی پریود بعدی آزاد است.")
                Text(
                    "حساب و اشتراک",
                    color = MahavaPrimary,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier
                        .testTag("today_account_link")
                        .clickable { onAccount() }
                        .padding(vertical = 4.dp)
                )
            }

            cycle?.limitsDescriptionFa?.takeIf { it.isNotBlank() }?.let { QuietInfo(it) }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ToolChip(label: String, onClick: () -> Unit, tag: String) {
    ChoiceChipPill(label, selected = false, tag = tag, onClick = onClick)
}
