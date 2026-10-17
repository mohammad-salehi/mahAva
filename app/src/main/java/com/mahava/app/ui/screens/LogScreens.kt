package com.mahava.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.mahava.app.R
import com.mahava.app.data.db.DailyLogEntity
import com.mahava.app.ui.AppViewModel
import com.mahava.app.ui.components.*
import com.mahava.app.ui.theme.*
import com.mahava.app.util.JalaliDate
import com.mahava.app.util.PersianDigits
import java.time.LocalDate

@Composable
fun LogHubScreen(
    vm: AppViewModel,
    onDaily: () -> Unit,
    onPeriod: () -> Unit,
    onLate: () -> Unit = {},
    onSettings: () -> Unit,
    onAccount: () -> Unit = {}
) {
    DailyLogScreen(vm, vm.today(), onBack = {}, onAccount = onAccount, onPeriod = onPeriod, onTest = onLate, onSettings = onSettings)
}

@Composable
fun DailyLogScreen(vm: AppViewModel, day: LocalDate, onBack: () -> Unit, onAccount: () -> Unit = {}, onPeriod: (() -> Unit)? = null, onTest: (() -> Unit)? = null, onSettings: (() -> Unit)? = null) {
    val premium by vm.isPremium.collectAsState()
    if (!premium) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).testTag("daily_log_screen")) {
            ScreenHeader(if (day == vm.today()) "ثبت حال امروز" else "ثبت حال این روز", onBack = onBack)
            PremiumPaywallCard(
                vm = vm,
                titleFa = "ثبت حال روزانه",
                benefitFa = "ثبت حال، علائم، خواب و هوس خوراکی با اشتراک ماه باز می‌شه.",
                onOpenAccount = onAccount
            )
        }
        return
    }
    var selectedDay by androidx.compose.runtime.saveable.rememberSaveable(day) { mutableStateOf(day.toEpochDay()) }
    val date = LocalDate.ofEpochDay(selectedDay)
    key(selectedDay) {
    DailyLogForm(vm, date, onBack, onPeriod, onTest, onSettings) { selectedDay = it.toEpochDay() }
    }
}

@Composable
private fun DailyLogForm(vm: AppViewModel, day: LocalDate, onBack: () -> Unit, onPeriod: (() -> Unit)?, onTest: (() -> Unit)?, onSettings: (() -> Unit)?, onDate: (LocalDate) -> Unit) {
    val state by vm.state.collectAsState()
    val pregnant = state.health?.appMode == com.mahava.app.health.models.AppHealthMode.PREGNANCY
    val existing = remember(day) { vm.logFor(day) }
    var bleeding by remember { mutableStateOf(existing?.bleeding) }
    var mood by remember { mutableStateOf(existing?.moods?.split(',')?.firstOrNull { it.isNotBlank() }) }
    val initialMood = existing?.moods?.split(',')?.firstOrNull { it.isNotBlank() }
    var feeling by remember { mutableStateOf(existing?.overallFeeling) }
    var energyScore by remember { mutableStateOf(existing?.let { com.mahava.app.health.CycleStats.energy(it)?.toInt()?.toString() }) }
    var appetite by remember { mutableStateOf(existing?.appetite) }
    var symptoms by remember { mutableStateOf(existing?.physicalSymptoms?.split(',')?.filter { it.isNotBlank() }?.toSet() ?: emptySet()) }
    var noSymptoms by remember { mutableStateOf(existing?.noSymptoms == true) }
    var pain by remember { mutableIntStateOf(existing?.painScore ?: -1) }
    var note by remember { mutableStateOf(existing?.note ?: "") }
    var discharge by remember { mutableStateOf(existing?.discharge) }
    var sleep by remember { mutableStateOf(existing?.sleepQuality) }
    // Sleep is chosen in hours (0–14, half-hour steps) and stored as minutes + hours.
    var sleepHours by remember { mutableStateOf(existing?.let { com.mahava.app.health.CycleStats.sleepHours(it) }?.let { Math.round(it * 2f) / 2f }) }
    var waterMl by remember { mutableIntStateOf(existing?.waterIntakeMl ?: -1) }
    var cravings by remember {
        mutableStateOf(existing?.foodCravings?.split(',')?.filter { it.isNotBlank() }?.toSet() ?: emptySet())
    }
    val isFuture = day.isAfter(vm.today())

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).testTag("daily_log_screen")) {
        ScreenHeader(if (day == vm.today()) "ثبت امروز" else "ثبت روزانه", onBack = if (onSettings == null) onBack else null, onSettings = onSettings)
        MahavaCard(containerColor = MahavaPrimarySoft.copy(alpha = 0.5f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { SectionLabel("یک دقیقه برای خودت"); QuietInfo("هرچه ثبت کنی، شناختت دقیق‌تر می‌شود.") }
                Illustration(R.drawable.design_lavender_sprig, Modifier.width(60.dp).height(70.dp))
            }
        }
        Spacer(Modifier.height(20.dp))
        DateStepper(day, max = vm.today(), min = vm.today().minusDays(3650), onChange = onDate)
        if (existing != null) QuietInfo("برای این روز قبلاً ثبت کرده‌ای؛ تغییرها همان ثبت را به‌روز می‌کند.")
        if (isFuture) {
            Spacer(Modifier.height(12.dp))
            Text("برای روزهای آینده نمی‌توان چیزی ثبت کرد.", color = MahavaDanger)
            Spacer(Modifier.height(12.dp))
            SecondaryButton("بازگشت", onClick = onBack)
            return@Column
        }
        Spacer(Modifier.height(12.dp))
        MahavaCard {
        SectionLabel(if (pregnant) "خون‌ریزی در بارداری" else "خون‌ریزی")
        ChipsFlow(
            listOf("none" to "ندارم", "spotting" to "لکه‌بینی", "light" to "کم", "medium" to "متوسط", "heavy" to "زیاد"),
            bleeding, "bleed"
        ) { bleeding = if (bleeding == it) null else it }

        if (pregnant && bleeding != null && bleeding != "none") Text("خون‌ریزی در بارداری را با پزشک یا ماما مطرح کن؛ درد شدید، غش یا خون‌ریزی زیاد به بررسی فوری نیاز دارد.", color = MahavaDanger)
        }
        Spacer(Modifier.height(12.dp))
        MahavaCard {
        SectionLabel("امروز در کل چطوری؟")
        ChipsFlow(com.mahava.app.health.LogLabels.FEELINGS, feeling, "feel") { feeling = if (feeling == it) null else it }
        SectionLabel("حالت چطور است؟")
        MoodIconRow(
            listOf(
                Triple("happy", "خوشحال", R.drawable.ic_mood_happy),
                Triple("calm", "آرام", R.drawable.ic_mood_calm),
                Triple("anxious", "نگران", R.drawable.ic_mood_anxious),
                Triple("irritable", "زودرنج", R.drawable.ic_mood_irritable),
                Triple("sad", "غمگین", R.drawable.ic_mood_sad)
            ),
            mood
        ) { mood = if (mood == it) null else it }

        SectionLabel("انرژی از ۱ تا ۵")
        ChipsFlow((1..5).map { it.toString() to PersianDigits.toPersian(it) }, energyScore, "energy") { energyScore = if (energyScore == it) null else it }
        SectionLabel("اشتها")
        ChipsFlow(listOf("low" to "کم", "normal" to "معمولی", "high" to "زیاد"), appetite, "appetite") { appetite = if (appetite == it) null else it }

        }
        Spacer(Modifier.height(12.dp))
        MahavaCard {
        SectionLabel("بدنت چه حسی دارد؟")
        ChoiceChipPill("هیچ علامتی ندارم", noSymptoms, tag = "sym_none") {
            noSymptoms = !noSymptoms
            if (noSymptoms) { symptoms = emptySet(); pain = -1 }
        }
        if (!noSymptoms) {
            MultiChipsFlow(
                listOf("pain" to "درد", "bloating" to "نفخ", "headache" to "سردرد", "breast_tenderness" to "حساسیت سینه", "fatigue" to "خستگی", "nausea" to "حالت تهوع", "acne" to "جوش") + if (pregnant) listOf("severe_headache" to "سردرد شدید", "vision_changes" to "تغییر دید", "reduced_movement" to "کاهش حرکت جنین", "fluid_leak" to "خروج مایع", "fainting" to "غش") else emptyList(),
                symptoms, "sym"
            ) { k -> symptoms = if (k in symptoms) symptoms - k else symptoms + k }
            if (pregnant && symptoms.any { it in setOf("severe_headache", "vision_changes", "reduced_movement", "fluid_leak", "fainting") }) Text("برای این علامت همین حالا با تیم مراقبت بارداری تماس بگیر؛ ثبت در برنامه جای تماس را نمی‌گیرد.", color = MahavaDanger)
            SectionLabel("شدت درد از ۰ تا ۱۰ (اختیاری)")
            Slider(
                value = if (pain < 0) 0f else pain.toFloat(),
                onValueChange = { pain = it.toInt() },
                valueRange = 0f..10f,
                steps = 9,
                modifier = Modifier.testTag("pain_slider")
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (pain < 0) "انتخاب نشده" else PersianDigits.toPersian(pain), Modifier.weight(1f))
                if (pain >= 0) TextButton(onClick = { pain = -1 }) { Text("پاک کردن") }
            }
        }
        }
        Spacer(Modifier.height(12.dp))
        MahavaCard {
        SectionLabel("خواب دیشب (اختیاری)")
        ChipsFlow(listOf("poor" to "بد", "ok" to "معمولی", "good" to "خوب"), sleep, "sleep") { sleep = if (sleep == it) null else it }
        SectionLabel("چند ساعت خوابیدی؟ (اختیاری)")
        SleepHoursPicker(sleepHours) { sleepHours = it }
        }
        Spacer(Modifier.height(12.dp))
        SectionLabel("آب امروز (میلی‌لیتر — اختیاری؛ خالی = ثبت‌نشده، نه صفر)")
        Slider(
            value = if (waterMl < 0) 0f else waterMl.toFloat().coerceIn(0f, 4000f),
            onValueChange = { waterMl = it.toInt() },
            valueRange = 0f..4000f,
            steps = 15,
            modifier = Modifier.testTag("water_ml_slider")
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (waterMl < 0) "ثبت نشده" else "${PersianDigits.toPersian(waterMl)} ml", Modifier.weight(1f))
            if (waterMl >= 0) TextButton(onClick = { waterMl = -1 }) { Text("پاک کردن") }
        }
        SectionLabel("هوس خوراکی (اختیاری — چند مورد)")
        QuietInfo("اگر چیزی هوس کردی ثبت کن؛ معنی رایج‌اش را نسبت به مرحلهٔ چرخه‌ات می‌گوییم.")
        MultiChipsFlow(com.mahava.app.content.FoodCravingKeys.ALL, cravings, "crave") { k ->
            cravings = if (k in cravings) cravings - k else cravings + k
        }
        SectionLabel("ترشحات واژن (اختیاری)")
        ChipsFlow(
            listOf("dry" to "خشک", "sticky" to "چسبناک", "creamy" to "کرمی", "watery" to "آبکی", "eggwhite" to "شفاف و کش‌دار"),
            discharge, "dis"
        ) { discharge = if (discharge == it) null else it }
        OutlinedTextField(
            value = note,
            onValueChange = { note = it },
            modifier = Modifier.fillMaxWidth().testTag("note_field"),
            label = { Text("یادداشت (اختیاری)") }
        )
        Spacer(Modifier.height(12.dp))
        if (onPeriod != null && !pregnant) SecondaryButton(if (state.cycle?.periodOngoing == true) "ثبت پایان خون‌ریزی" else "ثبت شروع پریود", onClick = onPeriod)
        if (onTest != null) SecondaryButton("ثبت تست بارداری", onClick = onTest)
        QuietInfo("گزینهٔ انتخاب‌نشده نامشخص است؛ خون‌ریزی روزانه به‌تنهایی شروع چرخه نیست.")
        Spacer(Modifier.height(16.dp))
        PrimaryButton("ذخیرهٔ ثبت روزانه", modifier = Modifier.testTag("save_daily")) {
            val now = System.currentTimeMillis()
            vm.saveDailyLog(
                (existing ?: DailyLogEntity(epochDay = day.toEpochDay(), createdAt = now, updatedAt = now)).copy(
                    epochDay = day.toEpochDay(),
                    bleeding = bleeding,
                    noSymptoms = noSymptoms,
                    painScore = pain.takeIf { it >= 0 },
                    physicalSymptoms = symptoms.joinToString(",").ifBlank { null },
                    // Keep the extra moods the mood tool saved unless the main mood changed.
                    moods = if (mood == initialMood) existing?.moods else mood,
                    overallFeeling = feeling,
                    energyScore = energyScore?.toIntOrNull(),
                    energy = energyScore?.toIntOrNull()?.let { if (it <= 2) "low" else if (it == 3) "medium" else "high" },
                    appetite = appetite,
                    sleepQuality = sleep,
                    sleepMinutes = sleepHours?.let { Math.round(it * 60) },
                    sleepHours = sleepHours,
                    waterIntakeMl = waterMl.takeIf { it >= 0 },
                    discharge = discharge,
                    foodCravings = cravings.joinToString(",").ifBlank { null },
                    note = note.ifBlank { null },
                    updatedAt = now
                )
            )
            onBack()
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun MoodIconRow(
    options: List<Triple<String, String, Int>>,
    selected: String?,
    onSelect: (String) -> Unit
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { (key, label, icon) ->
            val isOn = selected == key
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (isOn) MahavaPrimarySoft else Color(0xFFF4F0F8))
                    .clickable(role = Role.Button) { onSelect(key) }
                    .testTag("mood_$key")
                    .padding(vertical = 10.dp, horizontal = 2.dp)
            ) {
                Icon(
                    painter = painterResource(icon),
                    contentDescription = label,
                    tint = if (isOn) MahavaPrimary else MahavaTextSecondary,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(Modifier.height(4.dp))
                Text(label, style = MaterialTheme.typography.bodySmall, color = if (isOn) MahavaPrimaryDeep else MahavaTextSecondary)
            }
        }
    }
}

/**
 * Start a new period, or (when the latest period is still ongoing) record its end.
 * Optional flow/pain details are merged into that day's daily log only if the user picks them.
 */
@Composable
fun PeriodLogScreen(vm: AppViewModel, onBack: () -> Unit) {
    val state by vm.state.collectAsState()
    val today = vm.today()
    val last = remember { state.periods.maxByOrNull { it.startEpochDay } }
    val editing = last != null && last.endEpochDay == null && last.stillOngoing
    var start by remember { mutableStateOf(if (editing) LocalDate.ofEpochDay(last!!.startEpochDay) else today) }
    var ongoing by remember { mutableStateOf(!editing) }
    var end by remember { mutableStateOf(today) }
    var flow by remember { mutableStateOf<String?>(null) }
    var pain by remember { mutableIntStateOf(-1) }
    var clots by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).testTag("period_log_screen")) {
        ScreenHeader(if (editing) "پایان پریود" else "ثبت شروع پریود", onBack = onBack)
        SectionLabel("روز شروع خون‌ریزی")
        DateStepper(start, max = today, min = today.minusDays(365), onChange = { start = it }, testTagPrefix = "period_start")
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("هنوز ادامه دارد", Modifier.weight(1f))
            Switch(checked = ongoing, onCheckedChange = { ongoing = it }, modifier = Modifier.testTag("period_ongoing_switch"))
        }
        if (!ongoing) {
            SectionLabel("روز پایان خون‌ریزی")
            LaunchedEffect(start) { if (end.isBefore(start)) end = start }
            DateStepper(end, max = today, min = start, onChange = { end = it }, testTagPrefix = "period_end")
        }
        SectionLabel("میزان خون‌ریزی (اختیاری)")
        ChipsFlow(listOf("light" to "کم", "medium" to "متوسط", "heavy" to "زیاد"), flow, "flow") { flow = if (flow == it) null else it }
        SectionLabel("شدت درد از ۰ تا ۱۰ (اختیاری)")
        Slider(value = if (pain < 0) 0f else pain.toFloat(), onValueChange = { pain = it.toInt() }, valueRange = 0f..10f, steps = 9)
        Text(if (pain < 0) "انتخاب نشده" else PersianDigits.toPersian(pain))
        SectionLabel("لختهٔ خون (اختیاری)")
        ChipsFlow(listOf("none" to "ندارم", "sometimes" to "گاهی", "frequent" to "زیاد"), clots, "clots") { clots = if (clots == it) null else it }
        Spacer(Modifier.height(12.dp))
        PrimaryButton("ذخیره پریود", modifier = Modifier.testTag("save_period")) {
            vm.savePeriod(start, if (ongoing) null else end, ongoing, existingId = if (editing) last!!.id else 0L)
            if (flow != null || pain >= 0 || clots != null) {
                val day = if (!ongoing && editing) end else start
                val now = System.currentTimeMillis()
                val ex = vm.logFor(day)
                vm.saveDailyLog(
                    (ex ?: DailyLogEntity(epochDay = day.toEpochDay(), createdAt = now, updatedAt = now)).copy(
                        bleeding = flow ?: ex?.bleeding,
                        painScore = pain.takeIf { it >= 0 } ?: ex?.painScore,
                        clots = clots ?: ex?.clots,
                        noSymptoms = false,
                        updatedAt = now
                    )
                )
            }
            onBack()
        }
        if (editing) {
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = { confirmDelete = true }, modifier = Modifier.testTag("delete_period")) { Text("حذف این پریود", color = MahavaDanger) }
        }
        QuietInfo("تاریخ‌های پیش‌بینی‌شده هیچ‌وقت خودبه‌خود به‌عنوان پریود واقعی ذخیره نمی‌شوند. لکه‌بینی هم چرخهٔ جدید حساب نمی‌شود.")
    }
    if (confirmDelete && last != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("این پریود حذف شود؟") },
            text = { Text("ثبت این پریود پاک می‌شود. ثبت‌های روزانه دست نمی‌خورند.") },
            confirmButton = { TextButton(onClick = { vm.deletePeriod(last.id); confirmDelete = false; onBack() }) { Text("حذف", color = MahavaDanger) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("انصراف") } }
        )
    }
}
