package com.mahava.app.ui.screens

import androidx.compose.foundation.clickable
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
    onCareDetail: (String) -> Unit
) {
    val state by vm.state.collectAsState()
    val cycle = state.cycle
    val today = vm.today()
    val j = JalaliDate.from(today)
    val log = vm.logFor(today)
    val phaseSel = vm.phaseTodaySelection()
    val care = vm.careToday()
    val dayCtx = vm.dayContext()
    val pred = cycle?.prediction as? PredictionKind.Estimate

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag("today_screen")) {
        if (state.profile?.qaSampleData == true) {
            MahavaCard(Modifier.padding(16.dp)) {
                Text("دادهٔ نمونه برای آزمایش", color = MahavaMenstruation, style = MaterialTheme.typography.titleMedium)
                QuietInfo("این اطلاعات ساختگی و فقط برای آزمایش برنامه است، نه سابقهٔ واقعی تو. از تنظیمات می‌توانی همه را پاک کنی.")
            }
        }
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("امروز", style = MaterialTheme.typography.headlineLarge)
                Text(j.formatShortFa(), color = MahavaTextSecondary)
            }
            Illustration(R.drawable.ill_daily_woman, Modifier.width(110.dp).height(80.dp))
            androidx.compose.material3.IconButton(onClick = onSettings, modifier = Modifier.size(48.dp).testTag("open_settings")) {
                MahavaIcon(R.drawable.ic_settings, MahavaTextPrimary)
            }
        }
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            CycleRing(
                cycleDay = cycle?.cycleDay,
                cycleLengthHint = pred?.medianCycleLength,
                subtitle = dayCtx.subWindow.titleFa
            )
        }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (cycle?.isLate == true) {
                MahavaCard(Modifier.testTag("late_card")) {
                    Text("پریود دیر کرده", style = MaterialTheme.typography.titleLarge, color = MahavaMenstruation)
                    Text("حدود ${PersianDigits.toPersian(cycle.daysLate ?: 0)} روز از تاریخ تخمینی گذشته. چند روز جابه‌جایی رایج است.")
                    Spacer(Modifier.height(8.dp))
                    SecondaryButton("راهنمای تأخیر و تست بارداری", modifier = Modifier.testTag("late_open")) { onLate() }
                }
            } else if (pred != null) {
                MahavaCard(Modifier.testTag("period_estimate_card")) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        MahavaIcon(R.drawable.ic_calendar, MahavaMenstruation)
                        Spacer(Modifier.width(8.dp))
                        Column {
                            val d = cycle.daysUntilCentralPeriod
                            Text(
                                when {
                                    d == null -> "پریود بعدی (تخمینی)"
                                    d == 0 -> "پریود ممکن است از همین روزها شروع شود"
                                    else -> "حدود ${PersianDigits.toPersian(d)} روز تا پریود بعدی"
                                },
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                "بین ${JalaliDate.from(pred.nextPeriodStartEarliest).formatFa(withYear = false)} تا ${JalaliDate.from(pred.nextPeriodStartLatest).formatFa(withYear = false)}",
                                color = MahavaTextSecondary
                            )
                            QuietInfo(vm.predictionBasisShortFa())
                        }
                    }
                }
                if (cycle.showFertility) {
                    MahavaCard(Modifier.testTag("fertility_card")) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            MahavaIcon(R.drawable.ic_leaf, MahavaFertility)
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text("روزهای احتمالی باروری", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "${JalaliDate.from(pred.fertilityEarliest).formatFa(withYear = false)} تا ${JalaliDate.from(pred.fertilityLatest).formatFa(withYear = false)}",
                                    color = MahavaTextSecondary
                                )
                                QuietInfo("فقط یک تخمین از روی تاریخ‌هاست و روش جلوگیری نیست.")
                            }
                        }
                    }
                }
            } else {
                MahavaCard(Modifier.testTag("no_estimate_card")) {
                    Text("تاریخ پریود بعدی را فعلاً تخمین نمی‌زنیم", style = MaterialTheme.typography.titleMedium)
                    QuietInfo(cycle?.basisDescriptionFa ?: "")
                }
            }

            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QuickStat(R.drawable.ic_pain, "درد و ناراحتی", log?.painScore?.let { "${PersianDigits.toPersian(it)} از ۱۰" } ?: if (log?.noSymptoms == true) "ندارم" else "ثبت نشده", Modifier.weight(1f))
                val mood = log?.moods?.split(',')?.firstOrNull { it.isNotBlank() }
                QuickStat(moodIcon(mood), "حال روحی", moodFa(mood) ?: "ثبت نشده", Modifier.weight(1f))
                QuickStat(R.drawable.ic_energy, "سطح انرژی", energyFa(log?.energy), Modifier.weight(1f))
            }
            if (log == null) QuietInfo("«ثبت نشده» یعنی هنوز چیزی وارد نکرده‌ای، نه این‌که علامتی نداری.")

            PrimaryButton(if (log == null) "ثبت حال امروز" else "ویرایش حال امروز", modifier = Modifier.testTag("today_log_button"), onClick = onDailyLog)
            SecondaryButton(
                if (cycle?.periodOngoing == true) "ثبت پایان پریود" else "پریودم شروع شد",
                modifier = Modifier.testTag("today_period_button"),
                onClick = onPeriod
            )

            // Lower educational card (board-02): what is happening in my body
            MahavaCard(Modifier.testTag("phase_card").clickable { onPhaseDetail(phaseSel.item.id) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("در بدنم چه می‌گذرد؟", style = MaterialTheme.typography.titleMedium)
                        Text(phaseSel.item.phase?.cardTitleFa ?: phaseSel.item.titleFa, color = MahavaPrimary, style = MaterialTheme.typography.titleSmall)
                        Text(phaseSel.item.summaryFa, style = MaterialTheme.typography.bodyMedium)
                        phaseSel.restrictionNoteFa?.let { QuietInfo(it) }
                        if (dayCtx.nearBoundary) QuietInfo("امروز نزدیک مرز دو مرحله است.")
                        Text("بیشتر بخوان", color = MahavaPrimary, style = MaterialTheme.typography.labelLarge)
                    }
                    Spacer(Modifier.width(8.dp))
                    Illustration(R.drawable.ill_rest_woman, Modifier.width(96.dp).height(96.dp))
                }
            }

            MahavaCard(Modifier.testTag("care_card").clickable { onCareDetail(care.item.id) }) {
                Text("پیشنهاد مراقبت برای امروز", style = MaterialTheme.typography.titleMedium)
                Text(care.item.titleFa, color = MahavaPrimary)
                care.reasonFa?.let { QuietInfo("$it.") }
                QuietInfo(care.item.summaryFa)
            }

            if (state.patterns.isNotEmpty()) {
                MahavaCard {
                    Text("الگوی تو", style = MaterialTheme.typography.titleMedium)
                    state.patterns.take(2).forEach { Text("• ${it.textFa}") }
                    QuietInfo("فقط از ثبت‌های خودت؛ علت را نشان نمی‌دهد.")
                }
            }
            cycle?.limitsDescriptionFa?.takeIf { it.isNotBlank() }?.let { QuietInfo(it) }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable private fun QuickStat(icon: Int, title: String, value: String, modifier: Modifier) {
    MahavaCard(modifier.fillMaxHeight()) {
        MahavaIcon(icon, MahavaPrimary)
        Spacer(Modifier.height(4.dp))
        Text(title, color = MahavaTextSecondary, style = MaterialTheme.typography.bodySmall)
        Text(value, style = MaterialTheme.typography.titleSmall, color = MahavaPrimary)
    }
}

fun moodIcon(mood: String?): Int = when (mood) {
    "happy" -> R.drawable.ic_mood_happy
    "calm" -> R.drawable.ic_mood_calm
    "sad" -> R.drawable.ic_mood_sad
    "anxious" -> R.drawable.ic_mood_anxious
    "irritable" -> R.drawable.ic_mood_irritable
    else -> R.drawable.ic_mood_happy
}

fun energyFa(v: String?) = when (v) {
    "low" -> "کم"; "medium" -> "متوسط"; "high" -> "زیاد"; else -> "ثبت نشده"
}
