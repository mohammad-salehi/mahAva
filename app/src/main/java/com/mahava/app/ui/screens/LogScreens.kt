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
                benefitFa = "ثبت حال، علائم، خواب و هوس خوراکی با اشتراک ماه باز می‌شود.",
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
    var intimacy by remember { mutableStateOf(com.mahava.app.content.IntimacyLog.of(existing)) }
    var intimacyTouched by remember { mutableStateOf(false) }
    var discharge by remember { mutableStateOf(existing?.discharge) }
    var sleep by remember { mutableStateOf(existing?.sleepQuality) }
    // Sleep is chosen in hours (0–14, half-hour steps) and stored as minutes + hours.
    var sleepHours by remember { mutableStateOf(existing?.let { com.mahava.app.health.CycleStats.sleepHours(it) }?.let { Math.round(it * 2f) / 2f }) }
    var waterMl by remember { mutableIntStateOf(existing?.waterIntakeMl ?: -1) }
    var cravings by remember {
        mutableStateOf(existing?.foodCravings?.split(',')?.filter { it.isNotBlank() }?.toSet() ?: emptySet())
    }
    val isFuture = day.isAfter(vm.today())

    val k = com.mahava.app.ui.kit.Kit
    Column(Modifier.fillMaxSize().background(MahavaBackground).verticalScroll(com.mahava.app.ui.kit.rememberKitScroll()).testTag("daily_log_screen")) {
        // A-main-screens «ثبت امروز»: header + date, 5 faces, bleeding tiles, symptom chips, sleep/water cards, capsule save.
        com.mahava.app.ui.kit.KitHeader(
            if (day == vm.today()) "ثبت امروز" else "ثبت روزانه",
            start = if (onSettings == null) { { com.mahava.app.ui.kit.KitBack(onBack) } } else null,
            end = if (onSettings != null) { { com.mahava.app.ui.kit.KitCircleButton(R.drawable.kit_settings, contentDescription = "تنظیمات", onClick = onSettings) } } else null
        )
        com.mahava.app.ui.kit.KitDateRow(day, max = vm.today(), min = vm.today().minusDays(3650), onChange = onDate,
            extra = if (existing != null) "برای این روز قبلاً ثبت کردی؛ تغییرها همون ثبت رو به‌روز می‌کنه." else null)
        Column(Modifier.padding(horizontal = 20.dp)) {
        if (isFuture) {
            Spacer(Modifier.height(12.dp))
            Text("برای روزهای آینده نمی‌شه چیزی ثبت کرد.", color = MahavaDanger)
            Spacer(Modifier.height(12.dp))
            SecondaryButton("بازگشت", onClick = onBack)
            return@Column
        }
        com.mahava.app.ui.kit.KitSectionTitle("حال امروزت چطوره؟")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val faces = mapOf("great" to (R.drawable.redesign_mood_good to Color(0xFFFCEEF4)), "good" to (R.drawable.redesign_mood_good to Color(0xFFFDF3F7)),
                "ordinary" to (R.drawable.redesign_mood_ordinary to Color(0xFFEDF1FC)), "bad" to (R.drawable.redesign_mood_bad to Color(0xFFF1F0FA)),
                "very_bad" to (R.drawable.redesign_mood_bad to Color(0xFFEFEDF8)))
            com.mahava.app.health.LogLabels.FEELINGS.forEach { (key, label) ->
                val (icon, bg) = faces.getValue(key)
                com.mahava.app.ui.kit.KitOptionTile(label, feeling == key, bg, "feel_$key", Modifier.weight(1f),
                    art = { androidx.compose.foundation.Image(painterResource(icon), null, Modifier.size(30.dp)) }) { feeling = if (feeling == key) null else key }
            }
        }
        com.mahava.app.ui.kit.KitSectionTitle(if (pregnant) "خون‌ریزی در بارداری" else "میزان خون‌ریزی", info = true)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("none" to "ندارم", "spotting" to "لکه‌بینی", "light" to "کم", "medium" to "متوسط", "heavy" to "زیاد").forEach { (key, label) ->
                com.mahava.app.ui.kit.KitOptionTile(label, bleeding == key, if (key == "none") Color(0xFFF4F3F8) else Color(0xFFFCEEF4), "bleed_$key", Modifier.weight(1f), art = {
                    when (key) {
                        "none" -> MahavaIcon(R.drawable.kit_none, MahavaTextSecondary, 22.dp)
                        "spotting" -> Box(Modifier.size(8.dp).clip(androidx.compose.foundation.shape.CircleShape).background(k.pink))
                        "light" -> com.mahava.app.ui.kit.KitDrops(1)
                        "medium" -> com.mahava.app.ui.kit.KitDrops(2)
                        else -> com.mahava.app.ui.kit.KitDrops(3)
                    }
                }) { bleeding = if (bleeding == key) null else key }
            }
        }
        if (pregnant && bleeding != null && bleeding != "none") Text("خون‌ریزی در بارداری رو با پزشک یا ماما در میون بذار؛ درد شدید، غش یا خون‌ریزی زیاد بررسی فوری لازم داره.", color = MahavaDanger)
        com.mahava.app.ui.kit.KitSectionTitle("علائم امروزت رو انتخاب کن")
        val symIcons = mapOf("pain" to R.drawable.kit_energy, "bloating" to R.drawable.kit_food, "headache" to R.drawable.kit_warning, "breast_tenderness" to R.drawable.kit_heart,
            "fatigue" to R.drawable.kit_moon, "nausea" to R.drawable.kit_water, "acne" to R.drawable.kit_smile)
        val symOptions = listOf("pain" to "درد", "bloating" to "نفخ", "breast_tenderness" to "حساسیت سینه", "headache" to "سردرد", "acne" to "جوش", "fatigue" to "خستگی", "nausea" to "حالت تهوع") +
            if (pregnant) listOf("severe_headache" to "سردرد شدید", "vision_changes" to "تغییر دید", "reduced_movement" to "کاهش حرکت جنین", "fluid_leak" to "خروج مایع", "fainting" to "غش") else emptyList()
        // Two equal columns per row (RTL: first item on the right).
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            (listOf("none_all" to "هیچ علامتی ندارم") + symOptions).chunked(2).forEach { pair ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    pair.forEach { (key, label) ->
                        val on = if (key == "none_all") noSymptoms else key in symptoms
                        com.mahava.app.ui.kit.KitSymptomChip(label, if (key == "none_all") R.drawable.kit_check else symIcons[key] ?: R.drawable.kit_warning, on,
                            if (key == "none_all") "sym_none" else "sym_$key", Modifier.weight(1f)) {
                            if (key == "none_all") { noSymptoms = !noSymptoms; if (noSymptoms) { symptoms = emptySet(); pain = -1 } }
                            else { symptoms = if (key in symptoms) symptoms - key else symptoms + key; if (symptoms.isNotEmpty()) noSymptoms = false }
                        }
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
        if (pregnant && symptoms.any { it in setOf("severe_headache", "vision_changes", "reduced_movement", "fluid_leak", "fainting") }) Text("برای این علامت همین حالا با تیم مراقبت بارداری تماس بگیر؛ ثبت توی برنامه جای تماس رو نمی‌گیره.", color = MahavaDanger)
        if (!noSymptoms && ("pain" in symptoms || pain >= 0)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FieldLabel("شدت درد: " + if (pain < 0) "انتخاب نشده" else PersianDigits.toPersian(pain) + " از ۱۰")
                Spacer(Modifier.weight(1f))
                if (pain >= 0) TextButton(onClick = { pain = -1 }) { Text("پاک کردن") }
            }
            Slider(value = if (pain < 0) 0f else pain.toFloat(), onValueChange = { pain = it.toInt() }, valueRange = 0f..10f, steps = 9, modifier = Modifier.testTag("pain_slider"))
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            com.mahava.app.ui.kit.KitStepperCard("مدت خواب دیشب", sleepHours?.let { com.mahava.app.ui.kit.faFmt(it) } ?: "—", "ساعت",
                R.drawable.kit_moon, MahavaLavender, "sleep", Modifier.weight(1f).fillMaxHeight(),
                onMinus = { sleepHours = ((sleepHours ?: 7.5f) - .5f).coerceIn(0f, 14f) }, onPlus = { sleepHours = ((sleepHours ?: 7f) + .5f).coerceIn(0f, 14f) })
            val glasses = if (waterMl < 0) null else Math.round(waterMl / 250f)
            com.mahava.app.ui.kit.KitStepperCard("میزان آب امروز", glasses?.let { PersianDigits.toPersian(it) } ?: "—", "لیوان",
                R.drawable.kit_water, Color(0xFF7FA6F0), "water", Modifier.weight(1f).fillMaxHeight(),
                onMinus = { waterMl = (((glasses ?: 1) - 1).coerceIn(0, 20)) * 250 }, onPlus = { waterMl = (((glasses ?: 0) + 1).coerceIn(0, 20)) * 250 })
        }
        Spacer(Modifier.height(12.dp))
        FieldLabel("کیفیت خواب دیشب")
        ScaleRow(listOf("poor" to "بد", "ok" to "معمولی", "good" to "خوب"), sleep, "sleep") { sleep = if (sleep == it) null else it }
        Spacer(Modifier.height(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        FormSection("حال و انرژی", tag = "log_section_mood") {
            FieldLabel("حال")
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
            FieldLabel("انرژی (۱ کم، ۵ زیاد)")
            ScaleRow((1..5).map { it.toString() to PersianDigits.toPersian(it) }, energyScore, "energy") { energyScore = if (energyScore == it) null else it }
            FieldLabel("اشتها")
            ScaleRow(listOf("low" to "کم", "normal" to "معمولی", "high" to "زیاد"), appetite, "appetite") { appetite = if (appetite == it) null else it }
        }
        FormSection("هوس خوراکی و ترشحات", tag = "log_section_more") {
            FieldLabel("هوس خوراکی (چند مورد)")
            MultiChipsFlow(com.mahava.app.content.FoodCravingKeys.ALL, cravings, "crave") { k ->
                cravings = if (k in cravings) cravings - k else cravings + k
            }
            FieldLabel("ترشحات واژن")
            ChipsFlow(
                listOf("dry" to "خشک", "sticky" to "چسبناک", "creamy" to "کرمی", "watery" to "آبکی", "eggwhite" to "شفاف و کش‌دار"),
                discharge, "dis"
            ) { discharge = if (discharge == it) null else it }
        }
        FormSection("رابطه", tag = "log_section_intimacy") {
            FieldLabel("امروز رابطه داشتی؟")
            ChipsFlow(com.mahava.app.content.IntimacyLog.OPTIONS, intimacy, "intim") { intimacy = if (intimacy == it) null else it; intimacyTouched = true }
            Text("فقط برای خودت و گزارش پزشکه؛ به همسرت نشون داده نمی‌شه.", style = MaterialTheme.typography.bodySmall, color = MahavaTextSecondary)
        }
        FormSection("یادداشت", tag = "log_section_note") {
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                modifier = Modifier.fillMaxWidth().testTag("note_field"),
                label = { Text("یادداشت (اختیاری)") }
            )
        }
        if (onPeriod != null && !pregnant) SecondaryButton(if (state.cycle?.periodOngoing == true) "ثبت پایان خون‌ریزی" else "ثبت شروع پریود", onClick = onPeriod)
        if (onTest != null) SecondaryButton("ثبت تست بارداری", onClick = onTest)
        }
        Spacer(Modifier.height(16.dp))
        com.mahava.app.ui.kit.KitPillButton("ذخیرهٔ امروز", icon = R.drawable.kit_check, modifier = Modifier.testTag("save_daily")) {
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
                ).let { if (intimacyTouched) com.mahava.app.content.IntimacyLog.apply(it, intimacy) else it }
            )
            onBack()
        }
        Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun MoodIconRow(
    options: List<Triple<String, String, Int>>,
    selected: String?,
    onSelect: (String) -> Unit
) {
    // Fewer per row when five would break the words (small phones / large system font).
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
    val perRow = if (maxWidth < 50.dp * options.size * androidx.compose.ui.platform.LocalDensity.current.fontScale) 3 else options.size
    @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
    androidx.compose.foundation.layout.FlowRow(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), maxItemsInEachRow = perRow) {
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
                Text(label, style = MaterialTheme.typography.bodySmall, color = if (isOn) MahavaPrimaryDeep else MahavaTextSecondary, maxLines = 1, softWrap = false)
            }
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
