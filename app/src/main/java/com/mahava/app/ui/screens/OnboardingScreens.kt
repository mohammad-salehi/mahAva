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
import kotlinx.coroutines.launch


/**
 * Shown once, after login, for the woman's account only (the husband never sees it).
 * One screen: last period, cycle and bleed length ("don't know" allowed). Everything else
 * (goal, conditions, earlier periods) can be set later in Settings or the period log.
 */
@Composable
fun OnboardingFlow(vm: AppViewModel, onFinished: () -> Unit) {
    val today = vm.today()
    val scope = rememberCoroutineScope()
    var lastStart by remember { mutableStateOf<LocalDate?>(null) }
    var lastUnknown by remember { mutableStateOf(false) }
    var ongoing by remember { mutableStateOf<Boolean?>(null) }
    var cycleLen by remember { mutableStateOf<Int?>(null) }
    var bleedLen by remember { mutableStateOf<Int?>(null) }
    var cycleUnknown by remember { mutableStateOf(false) }
    var bleedUnknown by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().background(MahavaBackground).testTag("onboarding")) {
        CycleInfoStep(
            today = today,
            lastStart = lastStart, onLast = { lastStart = it; lastUnknown = false },
            lastUnknown = lastUnknown, onLastUnknown = { lastUnknown = it; if (it) { lastStart = null; ongoing = null } },
            ongoing = ongoing, onOngoing = { ongoing = it },
            cycleLen = cycleLen, onCycle = { cycleLen = it; cycleUnknown = false },
            bleedLen = bleedLen, onBleed = { bleedLen = it; bleedUnknown = false },
            cycleUnknown = cycleUnknown, onCycleUnknown = { cycleUnknown = it; if (it) cycleLen = null },
            bleedUnknown = bleedUnknown, onBleedUnknown = { bleedUnknown = it; if (it) bleedLen = null },
            busy = busy,
            error = error,
            onNext = {
                if (busy) return@CycleInfoStep
                busy = true
                error = null
                val now = System.currentTimeMillis()
                val profile = UserProfileEntity(
                    onboardingDone = true,
                    goal = "track_period",
                    lastPeriodStartEpochDay = lastStart?.toEpochDay(),
                    typicalCycleLength = cycleLen,
                    typicalBleedLength = bleedLen,
                    cycleLengthUnknown = cycleUnknown || cycleLen == null,
                    bleedLengthUnknown = bleedUnknown || bleedLen == null,
                    createdAt = now,
                    updatedAt = now
                )
                scope.launch {
                    val err = vm.saveOnboarding(profile, lastStart, ongoing == true, emptyList())
                    busy = false
                    if (err != null) error = err
                    else onFinished()
                }
            },
            onBack = null
        )
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
    busy: Boolean = false,
    error: String? = null,
    onNext: () -> Unit, onBack: (() -> Unit)?
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
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Spacer(Modifier.height(16.dp))
        PrimaryButton(
            if (busy) "در حال ذخیره…" else "ورود به برنامه",
            enabled = ready && !busy,
            modifier = Modifier.testTag("onb_enter"),
            onClick = onNext
        )
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

