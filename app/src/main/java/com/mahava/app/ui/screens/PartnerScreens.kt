package com.mahava.app.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mahava.app.content.FoodCravingKeys
import com.mahava.app.content.PartnerAdvice
import com.mahava.app.content.PartnerLogFormat
import com.mahava.app.content.TodaySignalContent
import com.mahava.app.util.JalaliDate
import com.google.gson.JsonObject
import java.time.LocalDate
import com.mahava.app.cycle.CyclePhase
import com.mahava.app.network.PartnerSnapshotDto
import com.mahava.app.ui.AppViewModel
import com.mahava.app.ui.components.MahavaCard
import com.mahava.app.ui.components.PeriodCycleWidget
import com.mahava.app.ui.components.PremiumPaywallCard
import com.mahava.app.ui.components.PrimaryButton
import com.mahava.app.ui.components.QuietInfo
import com.mahava.app.ui.components.ScreenHeader
import com.mahava.app.ui.components.SecondaryButton
import com.mahava.app.ui.theme.MahavaDanger
import com.mahava.app.ui.theme.MahavaFertility
import com.mahava.app.ui.theme.MahavaFertilitySoft
import com.mahava.app.ui.theme.MahavaMenstruation
import com.mahava.app.ui.theme.MahavaMenstruationSoft
import com.mahava.app.ui.theme.MahavaPrimary
import com.mahava.app.ui.theme.MahavaPrimarySoft
import com.mahava.app.ui.theme.MahavaSurface
import com.mahava.app.ui.theme.MahavaTextPrimary
import com.mahava.app.ui.theme.MahavaTextSecondary
import com.mahava.app.util.PersianDigits
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Shown wherever she approves a request (hub card and the in-app dialog). */
const val PARTNER_APPROVE_FA =
    "اگر تأیید کنی، همسرت همهٔ چیزهایی را که ثبت می‌کنی می‌بیند: پریودها، حال، علائم، هوس‌ها، درد، خون‌ریزی، یادداشت‌ها، رابطه و بقیهٔ ثبت‌ها. هر وقت بخواهی می‌توانی اتصال را قطع کنی."

/** Woman's side: make a code, approve, remove. */
@Composable
fun PartnerHubScreen(vm: AppViewModel, onBack: () -> Unit, onAccount: () -> Unit) {
    val status by vm.partnerStatus.collectAsState()
    val shared by vm.sharedFromPartner.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var code by remember { mutableStateOf<String?>(null) }
    var codeTtl by remember { mutableStateOf(30) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { vm.partnerRefresh() }

    val pair = status?.pair
    val phone = PersianDigits.toPersian(pair?.partner?.phoneMasked.orEmpty())

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag("partner_hub_screen")) {
        ScreenHeader("اتصال به همسر", onBack = onBack)
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when {
                pair == null -> {
                    PartnerHero("💞", "به همسرت وصل شو", "کد را بساز و برای همسرت بفرست. او کد را در اپ ماه می‌زند و تو تأیید می‌کنی؛ تمام.")
                    MahavaCard(Modifier.testTag("partner_code_card")) {
                        val c = code
                        if (c == null) {
                            PrimaryButton(if (busy) "صبر کن…" else "ساختن کد اتصال", enabled = !busy, modifier = Modifier.testTag("partner_make_code")) {
                                busy = true; error = null
                                scope.launch {
                                    val (res, err) = vm.partnerCreateCode()
                                    busy = false
                                    if (res != null) {
                                        code = res.code
                                        codeTtl = vm.partnerStatus.value?.codeTtlMinutes ?: 30
                                    } else error = err
                                }
                            }
                        } else {
                            Surface(shape = RoundedCornerShape(18.dp), color = MahavaPrimarySoft, modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    c.chunked(3).joinToString("  "),
                                    fontSize = 34.sp, fontWeight = FontWeight.Bold, color = MahavaPrimary,
                                    letterSpacing = 4.sp, textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp).testTag("partner_code_value")
                                )
                            }
                            QuietInfo("این کد ${PersianDigits.toPersian(codeTtl)} دقیقه معتبر است. وقتی همسرت کد را بزند، همین‌جا از تو تأیید می‌خواهیم.")
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                SecondaryButton("کپی", Modifier.weight(1f)) {
                                    clipboard.setText(AnnotatedString(c)); info = "کد کپی شد."
                                }
                                PrimaryButton("فرستادن", Modifier.weight(1f)) {
                                    val send = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, "کد اتصال من در اپ ماه: $c")
                                    }
                                    context.startActivity(Intent.createChooser(send, "فرستادن کد").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                }
                            }
                        }
                    }
                }

                pair.status == "pending" -> MahavaCard(Modifier.testTag("partner_request_card")) {
                    CardTitle("درخواست اتصال همسر")
                    Text("همسرت${if (phone.isNotBlank()) " ($phone)" else ""} می‌خواهد به تو وصل شود.", style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(6.dp))
                    Text(PARTNER_APPROVE_FA, style = MaterialTheme.typography.bodyMedium, color = MahavaTextSecondary)
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SecondaryButton("رد", Modifier.weight(1f).testTag("partner_reject")) {
                            scope.launch { error = vm.partnerRespond(pair.id, false) }
                        }
                        PrimaryButton(if (busy) "صبر کن…" else "تأیید", Modifier.weight(1f).testTag("partner_approve"), enabled = !busy) {
                            busy = true
                            scope.launch { error = vm.partnerRespond(pair.id, true); busy = false }
                        }
                    }
                }

                else -> MahavaCard(Modifier.testTag("partner_active_card")) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Dot(MahavaFertility)
                        Spacer(Modifier.width(8.dp))
                        CardTitle("به همسرت وصلی${if (phone.isNotBlank()) " ($phone)" else ""}")
                    }
                    Text("همسرت همهٔ چیزهایی را که ثبت می‌کنی می‌بیند.", style = MaterialTheme.typography.bodyMedium)
                    if (shared) {
                        Spacer(Modifier.height(4.dp))
                        Text("اشتراک ویژه‌ات از اشتراک همسرت است؛ تا وقتی وصلید هر دو استفاده می‌کنید.", style = MaterialTheme.typography.bodyMedium, color = MahavaTextSecondary)
                    }
                }
            }

            if (pair != null) RemovePartnerButton(vm, iAmFemale = true) { msg -> info = msg }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            info?.let { Text(it, color = MahavaPrimary) }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** Husband's home: enter her code, wait for her approval, then see everything she logs. */
@Composable
fun PartnerHomeScreen(vm: AppViewModel, onAccount: () -> Unit, onNotifications: () -> Unit = {}) {
    val status by vm.partnerStatus.collectAsState()
    val share by vm.partnerShare.collectAsState()
    val premium by vm.isPremium.collectAsState()
    val scope = rememberCoroutineScope()
    var codeInput by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { vm.partnerRefresh() }

    val pair = status?.pair

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag("partner_home_screen")) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("همسرم", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.weight(1f))
            NotificationBell(vm, onNotifications)
            androidx.compose.material3.IconButton(onClick = onAccount, modifier = Modifier.size(48.dp).testTag("partner_open_account")) {
                com.mahava.app.ui.components.MahavaIcon(com.mahava.app.R.drawable.ic_settings, MahavaTextPrimary)
            }
        }
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (pair != null) NotificationPermissionCard()
            when {
                pair == null -> MahavaCard(Modifier.testTag("partner_redeem_card")) {
                    CardTitle("کد همسرت را بزن")
                    QuietInfo("همسرت در اپ ماه، از «اتصال به همسر» یک کد ۶ حرفی می‌سازد.")
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = codeInput,
                        onValueChange = { v -> codeInput = v.uppercase().filter { it.isLetterOrDigit() }.take(6) },
                        label = { Text("مثلاً AB3K7Q") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                        modifier = Modifier.fillMaxWidth().testTag("partner_code_input")
                    )
                    Spacer(Modifier.height(10.dp))
                    PrimaryButton(if (busy) "صبر کن…" else "اتصال", enabled = !busy && codeInput.length == 6,
                        modifier = Modifier.testTag("partner_redeem")) {
                        busy = true; error = null
                        scope.launch { error = vm.partnerRedeem(codeInput); busy = false }
                    }
                }

                pair.status == "pending" -> MahavaCard(Modifier.testTag("partner_pending_card")) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = MahavaPrimary)
                        Spacer(Modifier.width(10.dp))
                        CardTitle("منتظر تأیید همسرت")
                    }
                    QuietInfo("وقتی تأیید کند، همین‌جا باز می‌شود.")
                }

                else -> {
                    val snap = share?.snapshot
                    if (snap == null) {
                        MahavaCard { Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = MahavaPrimary)
                            Spacer(Modifier.width(10.dp))
                            CardTitle("در حال گرفتن اطلاعات همسرت…")
                        } }
                    } else {
                        PartnerDashboard(vm, snap, share?.logs.orEmpty(), share?.periods.orEmpty(), share?.updatedAt, premium, onAccount)
                    }
                }
            }
            if (pair != null) RemovePartnerButton(vm, iAmFemale = false) { msg -> info = msg }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            info?.let { Text(it, color = MahavaPrimary) }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PartnerDashboard(
    vm: AppViewModel,
    snap: PartnerSnapshotDto,
    logs: List<JsonObject>,
    periods: List<JsonObject>,
    updatedAt: String?,
    premium: Boolean,
    onAccount: () -> Unit
) {
    val advice = PartnerAdvice.forSnapshot(snap)
    val group = if (snap.generalOnly) "general" else snap.phaseGroup
    val (accent, soft) = phaseColors(group)
    val todayEpoch = vm.today().toEpochDay()
    val todayLog = snap.today?.log?.takeIf { snap.today?.epochDay == todayEpoch }
        ?: logs.firstOrNull { PartnerLogFormat.epochDay(it) == todayEpoch }
    val todayLines = PartnerLogFormat.lines(todayLog)

    // Hero
    Surface(shape = RoundedCornerShape(24.dp), color = Color.Transparent, modifier = Modifier.fillMaxWidth()) {
        Column(
            Modifier.background(Brush.verticalGradient(listOf(soft, MahavaSurface))).padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(advice.headlineFa, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                color = MahavaTextPrimary, textAlign = TextAlign.Center, modifier = Modifier.testTag("partner_headline"))
            if (advice.statusFa.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(advice.statusFa, color = accent, style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(8.dp))
            PeriodCycleWidget(
                cycleDay = snap.cycleDay,
                cycleLengthHint = snap.cycleLength,
                phase = phaseOf(group),
                phaseTitleFa = PartnerAdvice.phaseNameFa(group),
                daysUntilPeriod = snap.daysUntilPeriod,
                isLate = snap.isLate
            )
            lastUpdatedFa(updatedAt)?.let { QuietInfo("به‌روزرسانی: $it") }
        }
    }

    // Everything she logged today
    if (todayLines.isNotEmpty()) {
        MahavaCard(Modifier.testTag("partner_today_card")) {
            CardTitle("امروز ثبت کرده")
            val today = snap.today
            val pills = mutableListOf<Pair<String, Color>>()
            today?.moods.orEmpty().forEach { pills += TodaySignalContent.label("mood", it) to MahavaFertility }
            today?.symptoms.orEmpty().forEach { pills += TodaySignalContent.label("body", it) to MahavaPrimary }
            (today?.painScore ?: 0).takeIf { it > 0 }?.let { pills += "درد ${PersianDigits.toPersian(it)} از ۱۰" to MahavaDanger }
            today?.cravings.orEmpty().forEach { pills += "هوس ${FoodCravingKeys.labelFa(it)}" to MahavaMenstruation }
            if (pills.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    pills.forEach { (label, color) -> Pill(label, color) }
                }
                Spacer(Modifier.height(8.dp))
            }
            LogLines(todayLines)
        }
    }

    if (!premium) {
        PremiumPaywallCard(
            vm,
            titleFa = "راهنمای علمی امروز",
            benefitFa = "با یک اشتراک فعال (از تو یا همسرت) باز می‌شود؛ هر دو استفاده می‌کنید.",
            onOpenAccount = onAccount
        )
    } else {
        advice.sections.forEach { s -> AdviceCard(s.icon, s.titleFa, s.bodyFa, soft) }
        val sourceIds = advice.sourceIds.toMutableList()
        if (todayLines.isEmpty()) {
            // Nothing logged today: the same sourced content her Today screen shows for this phase.
            MahavaCard(Modifier.testTag("partner_phase_science")) {
                CardTitle("این روزها در بدنش")
                Text(TodaySignalContent.bodyTodayFa(group), style = MaterialTheme.typography.bodyLarge)
            }
            PartnerAdvice.phaseScience(group).forEach { item ->
                val e = item.explanation
                sourceIds += e.sourceIds
                MahavaCard(Modifier.testTag("partner_science_${item.kind}_${item.key}")) {
                    Text("${TodaySignalContent.kindTitleFa(item.kind)} · ${TodaySignalContent.label(item.kind, item.key)}",
                        style = MaterialTheme.typography.labelLarge, color = accent)
                    Spacer(Modifier.height(2.dp))
                    Text(e.teaserFa, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MahavaTextPrimary)
                    e.sections.take(2).forEach { sec ->
                        Spacer(Modifier.height(6.dp))
                        Text(sec.titleFa, fontWeight = FontWeight.Bold, color = MahavaPrimary, style = MaterialTheme.typography.bodyMedium)
                        Text(sec.bodyFa, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
        MahavaCard(Modifier.testTag("partner_do_card")) {
            CardTitle("امروز چه کار کنی؟")
            advice.doFa.forEach { Text("• $it", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(vertical = 2.dp)) }
        }
        EvidenceCard(advice.evidence)
        SourcesCard(sourceIds.distinct())
    }

    // Everything she logged recently
    val older = logs.filter { PartnerLogFormat.epochDay(it) != todayEpoch && PartnerLogFormat.lines(it).isNotEmpty() }
    if (older.isNotEmpty()) {
        var showAll by remember { mutableStateOf(false) }
        MahavaCard(Modifier.testTag("partner_history_card")) {
            CardTitle("ثبت‌های روزهای قبل")
            (if (showAll) older else older.take(5)).forEach { log ->
                val day = PartnerLogFormat.epochDay(log) ?: return@forEach
                Spacer(Modifier.height(8.dp))
                Text(dayFa(day), fontWeight = FontWeight.Bold, color = MahavaTextPrimary, style = MaterialTheme.typography.bodyMedium)
                LogLines(PartnerLogFormat.lines(log))
            }
            if (older.size > 5) {
                TextButton(onClick = { showAll = !showAll }) { Text(if (showAll) "کمتر" else "همه (${PersianDigits.toPersian(older.size)} روز)") }
            }
        }
    }
    if (periods.isNotEmpty()) {
        MahavaCard(Modifier.testTag("partner_periods_card")) {
            CardTitle("پریودهایش")
            periods.take(6).forEach { p ->
                val start = try { p.get("startEpochDay")?.asLong } catch (_: Throwable) { null } ?: return@forEach
                val end = try { p.get("endEpochDay")?.takeIf { !it.isJsonNull }?.asLong } catch (_: Throwable) { null }
                val ongoing = end == null || (try { p.get("stillOngoing")?.asBoolean } catch (_: Throwable) { null } == true)
                val text = if (ongoing && (end == null || end >= todayEpoch)) "از ${shortFa(start)} (ادامه دارد)"
                else "${shortFa(start)} تا ${shortFa(end ?: start)} · ${PersianDigits.toPersian(((end ?: start) - start + 1).toInt())} روز"
                Text("• $text", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 2.dp))
                val note = try { p.get("note")?.takeIf { !it.isJsonNull }?.asString } catch (_: Throwable) { null }
                if (!note.isNullOrBlank()) Text("   یادداشت: $note", style = MaterialTheme.typography.bodySmall, color = MahavaTextSecondary)
            }
        }
    }
}

@Composable
private fun LogLines(lines: List<PartnerLogFormat.Line>) {
    lines.forEach { l ->
        Row(Modifier.padding(vertical = 2.dp)) {
            Text("${l.label}: ", fontWeight = FontWeight.Bold, color = MahavaTextSecondary, style = MaterialTheme.typography.bodyMedium)
            Text(l.value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun AdviceCard(icon: String, title: String, body: String, soft: Color) {
    MahavaCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(34.dp).clip(CircleShape).background(soft), contentAlignment = Alignment.Center) {
                Text(icon, fontSize = 16.sp)
            }
            Spacer(Modifier.width(10.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MahavaTextPrimary)
        }
        Spacer(Modifier.height(6.dp))
        Text(body, style = MaterialTheme.typography.bodyLarge)
    }
}

private fun shortFa(epochDay: Long): String = JalaliDate.from(LocalDate.ofEpochDay(epochDay)).formatShortFa()

private fun dayFa(epochDay: Long): String {
    val d = LocalDate.ofEpochDay(epochDay)
    return "${JalaliDate.weekdayNameFa(d)}، ${JalaliDate.from(d).formatShortFa()}"
}

/** Clear 'remove spouse' action with one confirm dialog. Works from both sides. */
@Composable
fun RemovePartnerButton(vm: AppViewModel, iAmFemale: Boolean, onResult: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var ask by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    TextButton(onClick = { ask = true }, modifier = Modifier.fillMaxWidth().testTag("partner_remove")) {
        Text("حذف همسر", color = MahavaDanger, fontWeight = FontWeight.Bold)
    }
    if (ask) {
        AlertDialog(
            onDismissRequest = { if (!busy) ask = false },
            title = { Text("حذف همسر؟") },
            text = {
                Text(
                    (if (iAmFemale) "همسرت دیگر اطلاعاتت را نمی‌بیند و اطلاعات مشترک از سرور پاک می‌شود."
                    else "دیگر اطلاعات همسرت را نمی‌بینی.") + " به او هم خبر می‌دهیم. هر کس اشتراک خودش را نگه می‌دارد."
                )
            },
            confirmButton = {
                TextButton(enabled = !busy, modifier = Modifier.testTag("partner_remove_confirm"), onClick = {
                    busy = true
                    scope.launch {
                        val err = vm.partnerUnpair()
                        busy = false; ask = false
                        onResult(err ?: "اتصال قطع شد.")
                    }
                }) { Text(if (busy) "صبر کن…" else "حذف", color = MahavaDanger) }
            },
            dismissButton = { TextButton(enabled = !busy, onClick = { ask = false }) { Text("انصراف") } }
        )
    }
}

@Composable
private fun PartnerHero(icon: String, title: String, body: String) {
    Surface(shape = RoundedCornerShape(24.dp), color = MahavaPrimarySoft, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(52.dp).clip(CircleShape).background(MahavaSurface), contentAlignment = Alignment.Center) {
                Text(icon, fontSize = 26.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MahavaTextPrimary)
                Spacer(Modifier.height(2.dp))
                Text(body, style = MaterialTheme.typography.bodyMedium, color = MahavaTextSecondary)
            }
        }
    }
}

@Composable
private fun CardTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MahavaPrimary)
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun Dot(color: Color) {
    Box(Modifier.size(10.dp).clip(CircleShape).background(color))
}

@Composable
private fun Pill(label: String, color: Color) {
    Surface(shape = RoundedCornerShape(50), color = color.copy(alpha = 0.12f)) {
        Text(label, color = color, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp))
    }
}

private fun phaseOf(group: String): CyclePhase = when (group) {
    "menstrual" -> CyclePhase.MENSTRUATION
    "follicular" -> CyclePhase.FOLLICULAR
    "fertile" -> CyclePhase.OVULATION_WINDOW
    "early_luteal", "late_luteal" -> CyclePhase.LUTEAL
    else -> CyclePhase.UNKNOWN
}

private fun phaseColors(group: String): Pair<Color, Color> = when (group) {
    "menstrual", "late_luteal" -> MahavaMenstruation to MahavaMenstruationSoft
    "fertile" -> MahavaFertility to MahavaFertilitySoft
    else -> MahavaPrimary to MahavaPrimarySoft
}

/** Server ISO time -> "امروز ساعت ۲۱:۳۰" style, in the phone's zone. */
fun lastUpdatedFa(iso: String?): String? {
    if (iso.isNullOrBlank()) return null
    return try {
        val zone = ZoneId.systemDefault()
        val t = Instant.parse(iso).atZone(zone)
        val today = java.time.LocalDate.now(zone)
        val hm = PersianDigits.toPersian(t.format(DateTimeFormatter.ofPattern("HH:mm")))
        val days = (today.toEpochDay() - t.toLocalDate().toEpochDay()).toInt()
        when (days) {
            0 -> "امروز ساعت $hm"
            1 -> "دیروز ساعت $hm"
            else -> "${PersianDigits.toPersian(days)} روز پیش"
        }
    } catch (_: Throwable) { null }
}
