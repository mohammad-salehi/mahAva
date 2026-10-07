package com.mahava.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.mahava.app.content.PhaseScienceBank
import com.mahava.app.cycle.PredictionKind
import com.mahava.app.data.db.DailyLogEntity
import com.mahava.app.ui.AppViewModel
import com.mahava.app.ui.components.*
import com.mahava.app.ui.theme.*
import com.mahava.app.util.JalaliDate
import com.mahava.app.util.PersianDigits

@Composable
fun PhaseScienceScreen(vm: AppViewModel, onBack: () -> Unit, onAccount: () -> Unit = {}) {
    val premium by vm.isPremium.collectAsState()
    val state by vm.state.collectAsState()
    val ctx = vm.dayContext()
    val science = PhaseScienceBank.forContext(ctx.subWindow, state.cycle?.phase)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).testTag("phase_science_screen")) {
        ScreenHeader("در بدنم چه می‌گذرد؟", onBack = onBack)
        if (!premium) {
            PremiumPaywallCard(vm = vm, "توضیح علمی فاز", onOpenAccount = onAccount)
            return@Column
        }
        QuietInfo(ctx.subWindow.titleFa)
        MahavaCard {
            Text(science.titleFa, style = MaterialTheme.typography.titleLarge, color = MahavaPrimary)
            Spacer(Modifier.height(8.dp))
            Text(science.bodyFa)
        }
        Spacer(Modifier.height(8.dp))
        MahavaCard {
            Text("هورمون‌ها (ساده‌شده)", style = MaterialTheme.typography.titleMedium, color = MahavaPrimary)
            Text(science.hormoneFa)
        }
        Spacer(Modifier.height(8.dp))
        MahavaCard {
            Text("روحیه و خلق", style = MaterialTheme.typography.titleMedium, color = MahavaPrimary)
            Text(science.moodFa)
        }
        Spacer(Modifier.height(8.dp))
        MahavaCard {
            Text("هوس خوراکی", style = MaterialTheme.typography.titleMedium, color = MahavaPrimary)
            Text(science.cravingFa)
        }
        Spacer(Modifier.height(8.dp))
        MahavaCard {
            Text("انرژی و خواب", style = MaterialTheme.typography.titleMedium, color = MahavaPrimary)
            Text(science.energySleepFa)
        }
        Spacer(Modifier.height(8.dp))
        MahavaCard {
            Text("پوست", style = MaterialTheme.typography.titleMedium, color = MahavaPrimary)
            Text(science.skinFa)
        }
        Spacer(Modifier.height(8.dp))
        MahavaCard {
            Text("اگر این حس را دیدی", style = MaterialTheme.typography.titleMedium, color = MahavaPrimary)
            Text(science.ifYouFeelFa)
        }
        QuietInfo(science.disclaimerFa)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun QuickSymptomLogScreen(vm: AppViewModel, onBack: () -> Unit, onAccount: () -> Unit = {}) {
    val premium by vm.isPremium.collectAsState()
    if (!premium) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).testTag("quick_log_screen")) {
            ScreenHeader("ثبت سریع علائم", onBack = onBack)
            PremiumPaywallCard(vm = vm, "ثبت سریع علائم", onOpenAccount = onAccount)
        }
        return
    }
    val day = vm.today()
    val existing = remember(day) { vm.logFor(day) }
    var mood by remember { mutableStateOf(existing?.moods?.split(',')?.firstOrNull { it.isNotBlank() }) }
    var energy by remember { mutableStateOf(existing?.energy) }
    var symptoms by remember {
        mutableStateOf(existing?.physicalSymptoms?.split(',')?.filter { it.isNotBlank() }?.toSet() ?: emptySet())
    }
    var pain by remember { mutableIntStateOf(existing?.painScore ?: -1) }
    var sleep by remember { mutableStateOf(existing?.sleepQuality) }
    var cravings by remember {
        mutableStateOf(existing?.foodCravings?.split(',')?.filter { it.isNotBlank() }?.toSet() ?: emptySet())
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).testTag("quick_log_screen")) {
        ScreenHeader("ثبت سریع علائم", onBack = onBack)
        QuietInfo(JalaliDate.from(day).formatFa())
        SectionLabel("حال")
        ChipsFlow(
            listOf("happy" to "خوشحال", "calm" to "آرام", "anxious" to "نگران", "irritable" to "زودرنج", "sad" to "غمگین"),
            mood, "q_mood"
        ) { mood = if (mood == it) null else it }
        SectionLabel("انرژی")
        ChipsFlow(listOf("low" to "کم", "medium" to "متوسط", "high" to "زیاد"), energy, "q_energy") {
            energy = if (energy == it) null else it
        }
        SectionLabel("خواب")
        ChipsFlow(listOf("poor" to "بد", "ok" to "متوسط", "good" to "خوب"), sleep, "q_sleep") {
            sleep = if (sleep == it) null else it
        }
        SectionLabel("علائم")
        MultiChipsFlow(
            listOf("pain" to "درد", "bloating" to "نفخ", "headache" to "سردرد", "breast_tenderness" to "حساسیت سینه", "nausea" to "تهوع", "acne" to "جوش"),
            symptoms, "q_sym"
        ) { k -> symptoms = if (k in symptoms) symptoms - k else symptoms + k }
        SectionLabel("درد ۰ تا ۱۰")
        Slider(
            value = if (pain < 0) 0f else pain.toFloat(),
            onValueChange = { pain = it.toInt() },
            valueRange = 0f..10f,
            steps = 9,
            modifier = Modifier.testTag("q_pain")
        )
        Text(if (pain < 0) "انتخاب نشده" else PersianDigits.toPersian(pain))
        SectionLabel("هوس خوراکی")
        MultiChipsFlow(
            listOf("chocolate" to "شکلات", "sweet" to "شیرینی", "salty" to "شور", "carbs" to "نان و کربوهیدرات", "red_meat" to "گوشت قرمز"),
            cravings, "q_crave"
        ) { k -> cravings = if (k in cravings) cravings - k else cravings + k }
        Spacer(Modifier.height(12.dp))
        PrimaryButton("ذخیره", modifier = Modifier.testTag("q_save")) {
            val now = System.currentTimeMillis()
            val base = existing ?: DailyLogEntity(epochDay = day.toEpochDay(), createdAt = now, updatedAt = now)
            vm.saveDailyLog(
                base.copy(
                    moods = mood,
                    energy = energy,
                    sleepQuality = sleep,
                    physicalSymptoms = symptoms.takeIf { it.isNotEmpty() }?.joinToString(","),
                    painScore = pain.takeIf { it >= 0 },
                    foodCravings = cravings.takeIf { it.isNotEmpty() }?.joinToString(","),
                    noSymptoms = symptoms.isEmpty() && pain < 0,
                    updatedAt = now
                )
            )
            onBack()
        }
        QuietInfo("برای جزئیات بیشتر از «ثبت حال امروز» استفاده کن.")
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun PhaseFoodTipsScreen(vm: AppViewModel, onBack: () -> Unit, onAccount: () -> Unit = {}) {
    val premium by vm.isPremium.collectAsState()
    val state by vm.state.collectAsState()
    val ctx = vm.dayContext()
    val science = PhaseScienceBank.forContext(ctx.subWindow, state.cycle?.phase)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).testTag("food_tips_screen")) {
        ScreenHeader("چه بخورم امروز؟", onBack = onBack)
        if (!premium) {
            PremiumPaywallCard(vm = vm, "نکته‌های غذایی فاز", onOpenAccount = onAccount)
            return@Column
        }
        QuietInfo("بر اساس مرحلهٔ تخمینی: ${science.titleFa}")
        MahavaCard {
            Text("پیشنهادهای غذایی", style = MaterialTheme.typography.titleMedium, color = MahavaPrimary)
            Bullets(science.foodTipsFa)
        }
        Spacer(Modifier.height(8.dp))
        MahavaCard {
            Text("هوس رایج این دوره", style = MaterialTheme.typography.titleMedium)
            Text(science.cravingFa)
        }
        QuietInfo(science.disclaimerFa)
        QuietInfo("این‌ها پیشنهاد آموزشی‌اند، نه رژیم درمانی.")
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun FertilityWindowScreen(vm: AppViewModel, onBack: () -> Unit, onAccount: () -> Unit = {}) {
    val premium by vm.isPremium.collectAsState()
    val state by vm.state.collectAsState()
    val pred = state.cycle?.prediction as? PredictionKind.Estimate
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).testTag("fertility_screen")) {
        ScreenHeader("پنجرهٔ باروری (آموزشی)", onBack = onBack)
        if (!premium) {
            PremiumPaywallCard(vm = vm, "ماشین‌حساب پنجره باروری", onOpenAccount = onAccount)
            return@Column
        }
        MahavaCard {
            Text("هشدار مهم", color = MahavaDanger, style = MaterialTheme.typography.titleMedium)
            Text("این بازه فقط تخمینی و آموزشی است و روش جلوگیری از بارداری نیست. برای پیشگیری از روش مطمئن استفاده کن.")
        }
        Spacer(Modifier.height(8.dp))
        if (pred == null) {
            MahavaCard {
                Text("هنوز تخمین نداریم", style = MaterialTheme.typography.titleMedium)
                QuietInfo(state.cycle?.basisDescriptionFa ?: "با ثبت چند پریود، بازهٔ تخمینی ساخته می‌شود.")
            }
        } else {
            MahavaCard {
                Text("بازهٔ تخمینی تخمک‌گذاری", style = MaterialTheme.typography.titleMedium, color = MahavaFertility)
                Text(
                    "از ${JalaliDate.from(pred.ovulationEarliest).formatFa()} تا ${JalaliDate.from(pred.ovulationLatest).formatFa()}"
                )
                QuietInfo("معمولاً چند روز قبل از تخمک‌گذاری هم امکان بارداری هست (اسپرم می‌تواند چند روز زنده بماند).")
            }
            Spacer(Modifier.height(8.dp))
            MahavaCard {
                Text("پریود بعدی (تخمینی)", style = MaterialTheme.typography.titleMedium)
                Text(JalaliDate.from(pred.nextPeriodStartCentral).formatFa())
                state.cycle?.daysUntilCentralPeriod?.let {
                    QuietInfo("حدود ${PersianDigits.toPersian(it)} روز مانده")
                }
            }
            Spacer(Modifier.height(8.dp))
            MahavaCard {
                Text("چطور حساب شده؟", style = MaterialTheme.typography.titleMedium)
                Text("تخمک‌گذاری معمولاً حدود ۱۰ تا ۱۶ روز قبل از پریود بعدی است (نه الزاماً روز ۱۴). این برنامه از روی تاریخ تخمینی پریود بعدی‌ات بازه را می‌سازد.")
            }
        }
        QuietInfo(vm.predictionBasisShortFa())
        Spacer(Modifier.height(24.dp))
    }
}
