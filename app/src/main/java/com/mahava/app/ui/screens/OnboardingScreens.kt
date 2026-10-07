package com.mahava.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.mahava.app.R
import com.mahava.app.data.db.UserProfileEntity
import com.mahava.app.ui.AppViewModel
import com.mahava.app.ui.components.*
import com.mahava.app.ui.theme.*
import com.mahava.app.util.JalaliDate
import com.mahava.app.util.PersianDigits
import java.time.LocalDate

@Composable
fun OnboardingFlow(vm: AppViewModel, onFinished: () -> Unit) {
    val today = vm.today()
    var step by remember { mutableIntStateOf(0) }
    var goal by remember { mutableStateOf("track_period") }
    var lastStart by remember { mutableStateOf<LocalDate?>(null) }
    var lastUnknown by remember { mutableStateOf(false) }
    var ongoing by remember { mutableStateOf<Boolean?>(null) }
    var cycleLen by remember { mutableStateOf<Int?>(null) }
    var bleedLen by remember { mutableStateOf<Int?>(null) }
    var cycleUnknown by remember { mutableStateOf(false) }
    var bleedUnknown by remember { mutableStateOf(false) }
    var regular by remember { mutableStateOf<Boolean?>(null) }
    var hormonal by remember { mutableStateOf(false) }
    var postpartum by remember { mutableStateOf(false) }
    var peri by remember { mutableStateOf(false) }
    var history by remember { mutableStateOf(listOf<LocalDate>()) }

    Column(Modifier.fillMaxSize().background(MahavaBackground).testTag("onboarding")) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(4) { i ->
                Box(Modifier.size(8.dp).clip(CircleShape).background(if (i == step) MahavaPrimary else MahavaBorder))
            }
        }
        when (step) {
            0 -> WelcomeGoalStep(goal = goal, onGoal = { goal = it }, onNext = { step = 1 })
            1 -> CycleInfoStep(
                today = today,
                lastStart = lastStart, onLast = { lastStart = it; lastUnknown = false },
                lastUnknown = lastUnknown, onLastUnknown = { lastUnknown = it; if (it) { lastStart = null; ongoing = null } },
                ongoing = ongoing, onOngoing = { ongoing = it },
                cycleLen = cycleLen, onCycle = { cycleLen = it; cycleUnknown = false },
                bleedLen = bleedLen, onBleed = { bleedLen = it; bleedUnknown = false },
                cycleUnknown = cycleUnknown, onCycleUnknown = { cycleUnknown = it; if (it) cycleLen = null },
                bleedUnknown = bleedUnknown, onBleedUnknown = { bleedUnknown = it; if (it) bleedLen = null },
                onNext = { step = 2 }, onBack = { step = 0 }
            )
            2 -> ConditionsStep(
                regular, { regular = it }, hormonal, { hormonal = it }, postpartum, { postpartum = it }, peri, { peri = it },
                onNext = { step = 3 }, onBack = { step = 1 }
            )
            else -> HistoryStep(
                today = today,
                firstSuggestion = (lastStart ?: today).minusDays(1),
                history = history,
                onAdd = { d -> if (d !in history && !d.isAfter(today) && (lastStart == null || d.isBefore(lastStart))) history = (history + d).sortedDescending() },
                onRemove = { d -> history = history - d },
                onBack = { step = 2 },
                onEnter = {
                    val now = System.currentTimeMillis()
                    val profile = UserProfileEntity(
                        onboardingDone = true,
                        goal = goal,
                        lastPeriodStartEpochDay = lastStart?.toEpochDay(),
                        typicalCycleLength = cycleLen,
                        typicalBleedLength = bleedLen,
                        cycleLengthUnknown = cycleUnknown || cycleLen == null,
                        bleedLengthUnknown = bleedUnknown || bleedLen == null,
                        regularCycles = regular,
                        hormonalContraception = hormonal,
                        postpartumOrBreastfeeding = postpartum,
                        perimenopause = peri,
                        fertilityTrackingEnabled = goal == "ttc",
                        createdAt = now,
                        updatedAt = now
                    )
                    vm.saveOnboarding(profile, lastStart, ongoing == true, history)
                    onFinished()
                }
            )
        }
    }
}

@Composable
private fun WelcomeGoalStep(goal: String, onGoal: (String) -> Unit, onNext: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Text("خوش آمدی", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(8.dp))
        Illustration(R.drawable.ill_welcome_woman, Modifier.height(180.dp))
        Text("بدنت را بهتر بشناس", style = MaterialTheme.typography.headlineLarge)
        QuietInfo("همراه روزانهٔ تو در طول چرخهٔ قاعدگی")
        Spacer(Modifier.height(12.dp))
        MahavaCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MahavaIcon(R.drawable.ic_lock)
                Spacer(Modifier.width(8.dp))
                Text("داده‌های پریود و چرخه‌ات فقط روی همین گوشی می‌ماند و به سرور فرستاده نمی‌شود. اگر برای اشتراک حساب بسازی، فقط همان حساب است — اطلاعات سلامتت روی دستگاهت می‌ماند.", style = MaterialTheme.typography.bodyMedium)
            }
        }
        Spacer(Modifier.height(16.dp))
        SectionLabel("بیشتر برای چه می‌خواهی از ماه استفاده کنی؟")
        GoalRow("track_period", goal, "پیگیری پریود", R.drawable.ic_droplet, MahavaMenstruation, onGoal)
        GoalRow("body_awareness", goal, "شناخت بهتر بدنم", R.drawable.ic_leaf, MahavaFertility, onGoal)
        GoalRow("ttc", goal, "اقدام برای بارداری", R.drawable.ic_heart, MahavaMenstruation, onGoal)
        Spacer(Modifier.height(20.dp))
        PrimaryButton("شروع کنیم", modifier = Modifier.testTag("onb_start"), onClick = onNext)
    }
}

@Composable
private fun GoalRow(id: String, selected: String, label: String, icon: Int, tint: Color, onGoal: (String) -> Unit) {
    val sel = id == selected
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp).clip(RoundedCornerShape(16.dp))
            .border(1.dp, if (sel) MahavaPrimary else MahavaBorder, RoundedCornerShape(16.dp))
            .background(if (sel) MahavaPrimarySoft else MahavaSurface)
            .clickable { onGoal(id) }.testTag("goal_$id").padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MahavaIcon(icon, tint)
        Spacer(Modifier.width(12.dp))
        Text(label, modifier = Modifier.weight(1f))
        RadioButton(selected = sel, onClick = { onGoal(id) })
    }
}

@Composable
private fun CycleInfoStep(
    today: LocalDate,
    lastStart: LocalDate?, onLast: (LocalDate) -> Unit,
    lastUnknown: Boolean, onLastUnknown: (Boolean) -> Unit,
    ongoing: Boolean?, onOngoing: (Boolean) -> Unit,
    cycleLen: Int?, onCycle: (Int) -> Unit,
    bleedLen: Int?, onBleed: (Int) -> Unit,
    cycleUnknown: Boolean, onCycleUnknown: (Boolean) -> Unit,
    bleedUnknown: Boolean, onBleedUnknown: (Boolean) -> Unit,
    onNext: () -> Unit, onBack: () -> Unit
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        ScreenHeader("دربارهٔ چرخه‌ات", onBack = onBack)
        QuietInfo("هر جا مطمئن نیستی «نمی‌دانم» را بزن؛ چیزی را به‌جای تو حدس نمی‌زنیم.")
        Spacer(Modifier.height(8.dp))
        SectionLabel("آخرین پریودت از چه روزی شروع شد؟")
        MahavaCard {
            if (lastStart != null) {
                DateStepper(lastStart, max = today, min = today.minusDays(365), onChange = onLast, testTagPrefix = "onb_last")
            } else if (!lastUnknown) {
                Text("هنوز انتخاب نکرده‌ای", color = MahavaTextSecondary)
            }
            if (lastStart == null) {
                var picking by remember { mutableStateOf(false) }
                PrimaryButton(
                    "انتخاب تاریخ از تقویم",
                    modifier = Modifier.testTag("onb_pick_date").heightIn(min = 64.dp)
                ) { picking = true }
                if (picking) MonthDatePickerDialog(today, today.minusDays(365), today, onPick = { onLast(it); picking = false }, onDismiss = { picking = false })
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { onLastUnknown(!lastUnknown) }) {
                Checkbox(lastUnknown, onLastUnknown, modifier = Modifier.testTag("onb_last_unknown")); Text("نمی‌دانم / یادم نیست")
            }
            if (lastStart != null && !lastStart.isBefore(today.minusDays(10))) {
                Text("خون‌ریزی هنوز ادامه دارد؟", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChoiceChipPill("بله، هنوز ادامه دارد", ongoing == true, tag = "onb_ongoing_yes") { onOngoing(true) }
                    ChoiceChipPill("نه، تمام شده", ongoing == false, tag = "onb_ongoing_no") { onOngoing(false) }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        SectionLabel("هر چرخه معمولاً چند روز طول می‌کشد؟")
        QuietInfo("از روز اول یک پریود تا روز اول پریود بعدی.")
        StepperRow(cycleLen, 28, 15, 60, "روز", "cycle", onCycle)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(cycleUnknown, onCycleUnknown, modifier = Modifier.testTag("onb_cycle_unknown")); Text("نمی‌دانم")
        }
        SectionLabel("خون‌ریزی معمولاً چند روز طول می‌کشد؟")
        StepperRow(bleedLen, 5, 1, 15, "روز", "bleed", onBleed)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(bleedUnknown, onBleedUnknown, modifier = Modifier.testTag("onb_bleed_unknown")); Text("نمی‌دانم")
        }
        val ready = (lastStart != null || lastUnknown) && (cycleLen != null || cycleUnknown) && (bleedLen != null || bleedUnknown)
        if (!ready) QuietInfo("برای ادامه، به هر سؤال جواب بده یا «نمی‌دانم» را بزن.")
        Spacer(Modifier.height(16.dp))
        PrimaryButton("ادامه", enabled = ready, modifier = Modifier.testTag("onb_next_cycle"), onClick = onNext)
    }
}


/** Shows "not chosen" until the user taps; the first tap starts from [start]. */
@Composable
private fun StepperRow(value: Int?, start: Int, min: Int, max: Int, unit: String, tag: String, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        IconButton(onClick = { onChange(((value ?: start + 1) - 1).coerceIn(min, max)) }, modifier = Modifier.testTag("${tag}_minus")) { MahavaIcon(R.drawable.ic_minus) }
        Text(
            if (value == null) "انتخاب نشده" else "${PersianDigits.toPersian(value)} $unit",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.testTag("${tag}_value")
        )
        IconButton(onClick = { onChange(((value ?: start - 1) + 1).coerceIn(min, max)) }, modifier = Modifier.testTag("${tag}_plus")) { MahavaIcon(R.drawable.ic_plus) }
    }
}

@Composable
private fun ConditionsStep(
    regular: Boolean?, onRegular: (Boolean?) -> Unit,
    hormonal: Boolean, onHormonal: (Boolean) -> Unit,
    postpartum: Boolean, onPost: (Boolean) -> Unit,
    peri: Boolean, onPeri: (Boolean) -> Unit,
    onNext: () -> Unit, onBack: () -> Unit
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        ScreenHeader("شرایط بدن من", onBack = onBack)
        SectionLabel("پریودهایت معمولاً منظم است؟")
        QuietInfo("منظم یعنی فاصلهٔ پریودها تقریباً هر بار یکی است.")
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ConditionCard("منظم", regular == true, R.drawable.ic_calendar, Modifier.weight(1f).testTag("onb_regular")) { onRegular(true) }
            ConditionCard("نامنظم", regular == false, R.drawable.ic_cycle, Modifier.weight(1f).testTag("onb_irregular")) { onRegular(false) }
            ConditionCard("نمی‌دانم", regular == null, R.drawable.ic_info, Modifier.weight(1f).testTag("onb_regular_unknown")) { onRegular(null) }
        }
        Spacer(Modifier.height(12.dp))
        SectionLabel("هر کدام که برایت صدق می‌کند (اختیاری)")
        ToggleLine("روش هورمونی جلوگیری (مثل قرص، آمپول یا آی‌یو‌دی هورمونی)", R.drawable.ic_pill, hormonal, onHormonal, "onb_hormonal")
        ToggleLine("زایمان اخیر یا شیردهی", R.drawable.ic_heart, postpartum, onPost, "onb_postpartum")
        ToggleLine("نزدیک یائسگی", R.drawable.ic_info, peri, onPeri, "onb_peri")
        QuietInfo("این‌ها کمک می‌کنند توضیح‌ها و تخمین‌ها برای تو درست‌تر باشند. بعداً در تنظیمات هم قابل تغییرند.")
        Spacer(Modifier.height(16.dp))
        PrimaryButton("ادامه", modifier = Modifier.testTag("onb_next_conditions"), onClick = onNext)
    }
}

@Composable private fun ConditionCard(title: String, selected: Boolean, icon: Int, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.fillMaxHeight().clip(RoundedCornerShape(16.dp)).border(1.dp, if (selected) MahavaPrimary else MahavaBorder, RoundedCornerShape(16.dp))
            .background(if (selected) MahavaPrimarySoft else MahavaSurface).clickable(onClick = onClick).padding(12.dp).heightIn(min = 88.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        MahavaIcon(icon); Spacer(Modifier.height(8.dp)); Text(title)
    }
}

@Composable private fun ToggleLine(label: String, icon: Int, checked: Boolean, onChange: (Boolean) -> Unit, tag: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        MahavaIcon(icon); Spacer(Modifier.width(10.dp)); Text(label, Modifier.weight(1f)); Switch(checked, onChange, modifier = Modifier.testTag(tag))
    }
}

@Composable
private fun HistoryStep(
    today: LocalDate,
    firstSuggestion: LocalDate,
    history: List<LocalDate>, onAdd: (LocalDate) -> Unit, onRemove: (LocalDate) -> Unit,
    onBack: () -> Unit, onEnter: () -> Unit
) {
    var picking by remember { mutableStateOf(false) }
    var pick by remember { mutableStateOf(firstSuggestion) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        ScreenHeader("پریودهای قبلی", onBack = onBack)
        QuietInfo("اگر روز شروع پریودهای قبلی را یادت هست، اضافه کن تا تخمین‌ها زودتر دقیق‌تر شوند. این مرحله اختیاری است.")
        history.forEach { d ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(JalaliDate.from(d).formatFa(), Modifier.weight(1f))
                IconButton(onClick = { onRemove(d) }) { MahavaIcon(R.drawable.ic_trash, MahavaDanger) }
            }
        }
        if (picking) {
            MahavaCard {
                Text("روز شروع یک پریود قبلی", style = MaterialTheme.typography.titleSmall)
                DateStepper(pick, max = firstSuggestion, min = today.minusDays(365), onChange = { pick = it }, testTagPrefix = "onb_hist")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChoiceChipPill("افزودن", true, tag = "onb_hist_add") { onAdd(pick); picking = false; pick = pick.minusDays(1) }
                    ChoiceChipPill("انصراف", false) { picking = false }
                }
            }
        } else {
            SecondaryButton("+ افزودن پریود قبلی", modifier = Modifier.testTag("onb_add_history")) { picking = true }
        }
        QuietInfo("اگر یادت نیست، اشکالی ندارد؛ از این به بعد ثبت کن.")
        Spacer(Modifier.height(20.dp))
        PrimaryButton("ورود به برنامه", modifier = Modifier.testTag("onb_enter"), onClick = onEnter)
    }
}
