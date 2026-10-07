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
import com.mahava.app.content.TodaySignalContent
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

private const val SHARED_LIST_FA =
    "• مرحلهٔ چرخه و روز چرخه\n• تخمین پریود بعدی\n• حال، علائم جسمی، هوس‌ها و شدت دردی که امروز ثبت می‌کنی"
private const val NOT_SHARED_FA =
    "یادداشت‌ها، رابطهٔ جنسی، تست‌ها، وزن، داروها و تاریخچهٔ روزهای قبل فرستاده نمی‌شوند."

/** Woman's side: pair, approve, see what is shared, remove partner. */
@Composable
fun PartnerHubScreen(vm: AppViewModel, onBack: () -> Unit, onAccount: () -> Unit) {
    val status by vm.partnerStatus.collectAsState()
    val consent by vm.partnerConsent.collectAsState()
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
    val partnerName = PartnerAdvice.name(pair?.partner?.name)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag("partner_hub_screen")) {
        ScreenHeader("همراه من", onBack = onBack)
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            PartnerHero(
                "💞",
                "حال این روزهایت را با همراهت شریک شو",
                "همسر یا همراهت در اپ خودش می‌بیند در کدام مرحلهٔ چرخه‌ای و امروز چه حالی داری؛ با راهنمای ساده که چطور کنارت باشد."
            )
            BackgroundReliabilityCards(active = pair?.status == "active")

            when {
                pair == null && !consent -> ConsentCard(onAccept = { vm.setPartnerConsent(true) })

                pair == null -> MahavaCard(Modifier.testTag("partner_code_card")) {
                    CardTitle("۱. کد اتصال بساز")
                    QuietInfo("کد را برای همراهت بفرست. او اپ ماه را نصب می‌کند، با حساب «آقا» وارد می‌شود و کد را می‌زند. بعد همین‌جا درخواستش را تأیید می‌کنی.")
                    Spacer(Modifier.height(10.dp))
                    val c = code
                    if (c != null) {
                        Surface(shape = RoundedCornerShape(18.dp), color = MahavaPrimarySoft, modifier = Modifier.fillMaxWidth()) {
                            Text(
                                c.chunked(3).joinToString("  "),
                                fontSize = 34.sp, fontWeight = FontWeight.Bold, color = MahavaPrimary,
                                letterSpacing = 4.sp, textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp).testTag("partner_code_value")
                            )
                        }
                        QuietInfo("این کد ${PersianDigits.toPersian(codeTtl)} دقیقه معتبر است و فقط یک بار کار می‌کند.")
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SecondaryButton("کپی", Modifier.weight(1f)) {
                                clipboard.setText(AnnotatedString(c)); info = "کد کپی شد."
                            }
                            PrimaryButton("فرستادن", Modifier.weight(1f)) {
                                val send = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, "کد اتصال من در اپ ماه: $c\nدر بخش «همراه» واردش کن. تا ${PersianDigits.toPersian(codeTtl)} دقیقه معتبر است.")
                                }
                                context.startActivity(Intent.createChooser(send, "فرستادن کد"))
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        TextButton(onClick = { code = null }) { Text("ساخت کد تازه") }
                    } else {
                        PrimaryButton(if (busy) "صبر کن…" else "ساخت کد اتصال", enabled = !busy, modifier = Modifier.testTag("partner_make_code")) {
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
                    }
                    Spacer(Modifier.height(4.dp))
                    TextButton(onClick = { vm.setPartnerConsent(false) }) { Text("پس گرفتن موافقت", color = MahavaTextSecondary) }
                }

                pair.status == "pending" -> MahavaCard(Modifier.testTag("partner_request_card")) {
                    CardTitle("درخواست اتصال")
                    Text(
                        "$partnerName (${PersianDigits.toPersian(pair.partner?.phoneMasked ?: "")}) می‌خواهد همراهت شود.",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    if (!consent) {
                        Spacer(Modifier.height(8.dp))
                        ConsentCard(onAccept = { vm.setPartnerConsent(true) })
                    } else {
                        QuietInfo("با تأیید، از همین حالا وضعیتت برایش فرستاده می‌شود.")
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SecondaryButton("رد", Modifier.weight(1f)) {
                                scope.launch { error = vm.partnerRespond(pair.id, false) }
                            }
                            PrimaryButton(if (busy) "صبر کن…" else "تأیید", Modifier.weight(1f).testTag("partner_approve"), enabled = !busy) {
                                busy = true
                                scope.launch { error = vm.partnerRespond(pair.id, true); busy = false }
                            }
                        }
                    }
                }

                else -> {
                    MahavaCard(Modifier.testTag("partner_active_card")) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Dot(MahavaFertility)
                            Spacer(Modifier.width(8.dp))
                            CardTitle("به $partnerName وصلی")
                        }
                        QuietInfo("هر تغییری که ثبت کنی، چند ثانیه بعد برایش فرستاده می‌شود (اگر اینترنت داشته باشی).")
                        lastUpdatedFa(status?.snapshotUpdatedAt)?.let { QuietInfo("آخرین ارسال: $it") }
                        Spacer(Modifier.height(10.dp))
                        Text("چه چیزهایی را می‌بیند؟", fontWeight = FontWeight.Bold, color = MahavaPrimary)
                        Text(SHARED_LIST_FA, style = MaterialTheme.typography.bodyMedium)
                        QuietInfo(NOT_SHARED_FA)
                        Spacer(Modifier.height(10.dp))
                        SecondaryButton("ارسال دوباره الان") { vm.partnerRefresh(); info = "فرستاده شد." }
                    }
                    MahavaCard {
                        CardTitle("اشتراک مشترک")
                        Text(
                            if (shared) "اشتراک ویژه‌ات از اشتراک $partnerName است. تا وقتی وصلید، هر دو از امکانات ویژه استفاده می‌کنید."
                            else "با یک اشتراک فعال، هر دوی شما از امکانات ویژه استفاده می‌کنید.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        QuietInfo("اگر اتصال قطع شود، اشتراک برای کسی که خریده تا پایان مدتش می‌ماند.")
                    }
                }
            }

            if (pair != null) {
                RemovePartnerButton(vm, partnerName, iAmFemale = true) { msg -> info = msg }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            info?.let { Text(it, color = MahavaPrimary) }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** Shown before anything is ever sent to the server. */
@Composable
fun ConsentCard(onAccept: () -> Unit) {
    MahavaCard(Modifier.testTag("partner_consent_card")) {
        CardTitle("قبل از شروع، این را بخوان")
        Text("اطلاعاتت روی سرور امن ماه ذخیره می‌شود تا گم نشود، ولی همراهت فقط چیزی را می‌بیند که خودت اجازه بدهی. با اجازه‌ات، او فقط این‌ها را می‌بیند:", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(6.dp))
        Text(SHARED_LIST_FA, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(6.dp))
        QuietInfo(NOT_SHARED_FA)
        QuietInfo("هر وقت اتصال را قطع کنی (یا او قطع کند)، او دیگر چیزی نمی‌بیند و بخش مشترک از سرور پاک می‌شود. یادداشت‌ها و بقیهٔ ثبت‌هایت هیچ‌وقت به او نشان داده نمی‌شود.")
        Spacer(Modifier.height(10.dp))
        PrimaryButton("موافقم، ادامه بده", modifier = Modifier.testTag("partner_consent_accept"), onClick = onAccept)
    }
}

/** Man's home: connect with a code, then see her day with simple, sourced advice. */
@Composable
fun PartnerHomeScreen(vm: AppViewModel, onAccount: () -> Unit) {
    val status by vm.partnerStatus.collectAsState()
    val share by vm.partnerShare.collectAsState()
    val premium by vm.isPremium.collectAsState()
    val role by vm.accountRole.collectAsState()
    val scope = rememberCoroutineScope()
    var codeInput by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { vm.partnerRefresh() }

    val pair = status?.pair
    val partnerName = PartnerAdvice.name(share?.partnerName?.ifBlank { null } ?: pair?.partner?.name)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag("partner_home_screen")) {
        ScreenHeader("همراه", onSettings = onAccount)
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            BackgroundReliabilityCards(active = role == "male" && pair != null)
            when {
                role != "male" -> MahavaCard {
                    CardTitle("این حساب برای کیست؟")
                    QuietInfo("بخش همراه برای آقایانی است که می‌خواهند وضعیت همسر یا همراهشان را ببینند.")
                    Spacer(Modifier.height(8.dp))
                    PrimaryButton("حساب من برای آقا (همراه) است") {
                        scope.launch { error = vm.partnerSetRole("male") }
                    }
                }

                pair == null -> {
                    PartnerHero(
                        "🤝",
                        "به همراهت وصل شو",
                        "وقتی وصل شوی، می‌بینی در کدام مرحلهٔ چرخه است، امروز چه حالی دارد و چطور می‌توانی کنارش باشی."
                    )
                    MahavaCard(Modifier.testTag("partner_redeem_card")) {
                        CardTitle("کد اتصال")
                        QuietInfo("همراهت در اپ ماه از بخش «همراه من» یک کد ۶ حرفی می‌سازد. آن را این‌جا بزن.")
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
                        PrimaryButton(if (busy) "صبر کن…" else "فرستادن درخواست", enabled = !busy && codeInput.length == 6,
                            modifier = Modifier.testTag("partner_redeem")) {
                            busy = true; error = null
                            scope.launch { error = vm.partnerRedeem(codeInput); busy = false }
                        }
                    }
                }

                pair.status == "pending" -> MahavaCard(Modifier.testTag("partner_pending_card")) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = MahavaPrimary)
                        Spacer(Modifier.width(10.dp))
                        CardTitle("منتظر تأیید $partnerName")
                    }
                    QuietInfo("وقتی درخواستت را تأیید کند، به تو خبر می‌دهیم.")
                    Spacer(Modifier.height(8.dp))
                    SecondaryButton("بررسی دوباره") { vm.partnerRefresh() }
                }

                else -> {
                    val snap = share?.snapshot
                    if (snap == null) {
                        MahavaCard {
                            CardTitle("به $partnerName وصلی")
                            QuietInfo("هنوز وضعیتی فرستاده نشده. وقتی اپ ماه روی گوشی او باز شود یا چیزی ثبت کند، این‌جا می‌آید.")
                            Spacer(Modifier.height(8.dp))
                            SecondaryButton("به‌روزرسانی") { vm.partnerRefresh() }
                        }
                    } else {
                        PartnerDashboard(vm, partnerName, snap, share?.updatedAt, premium, onAccount)
                    }
                }
            }
            if (pair != null) RemovePartnerButton(vm, partnerName, iAmFemale = false) { msg -> info = msg }
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
    name: String,
    snap: PartnerSnapshotDto,
    updatedAt: String?,
    premium: Boolean,
    onAccount: () -> Unit
) {
    val advice = PartnerAdvice.forSnapshot(name, snap)
    val group = if (snap.generalOnly) "general" else snap.phaseGroup
    val (accent, soft) = phaseColors(group)

    // Hero
    Surface(shape = RoundedCornerShape(24.dp), color = Color.Transparent, modifier = Modifier.fillMaxWidth()) {
        Column(
            Modifier.background(Brush.verticalGradient(listOf(soft, MahavaSurface))).padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(advice.headlineFa, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                color = MahavaTextPrimary, textAlign = TextAlign.Center, modifier = Modifier.testTag("partner_headline"))
            Spacer(Modifier.height(4.dp))
            Text(advice.statusFa, color = accent, style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center)
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

    // What she logged today
    MahavaCard(Modifier.testTag("partner_today_card")) {
        CardTitle("امروزش")
        val today = snap.today
        val isToday = today?.epochDay == vm.today().toEpochDay()
        val pills = mutableListOf<Pair<String, Color>>()
        if (isToday) {
            today?.moods.orEmpty().forEach { pills += TodaySignalContent.label("mood", it) to MahavaFertility }
            today?.symptoms.orEmpty().forEach { pills += TodaySignalContent.label("body", it) to MahavaPrimary }
            (today?.painScore ?: 0).takeIf { it > 0 }?.let { pills += "درد ${PersianDigits.toPersian(it)} از ۱۰" to MahavaDanger }
            today?.cravings.orEmpty().forEach { pills += "هوس ${FoodCravingKeys.labelFa(it)}" to MahavaMenstruation }
        }
        if (pills.isEmpty()) {
            QuietInfo("امروز هنوز چیزی ثبت نکرده. بهترین راه دانستن حالش، پرسیدن از خودش است.")
        } else {
            Spacer(Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                pills.forEach { (label, color) -> Pill(label, color) }
            }
        }
    }

    if (!premium) {
        PremiumPaywallCard(
            vm,
            titleFa = "راهنمای امروز برای همراهی",
            benefitFa = "با یک اشتراک فعال (از تو یا همراهت) باز می‌شود؛ هر دو استفاده می‌کنید.",
            onOpenAccount = onAccount
        )
        return
    }

    advice.sections.forEach { s ->
        MahavaCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(34.dp).clip(CircleShape).background(soft), contentAlignment = Alignment.Center) {
                    Text(s.icon, fontSize = 16.sp)
                }
                Spacer(Modifier.width(10.dp))
                Text(s.titleFa, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MahavaTextPrimary)
            }
            Spacer(Modifier.height(6.dp))
            Text(s.bodyFa, style = MaterialTheme.typography.bodyLarge)
        }
    }
    MahavaCard(Modifier.testTag("partner_do_card")) {
        CardTitle("امروز چه کار کنی؟")
        advice.doFa.forEach { Text("• $it", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(vertical = 2.dp)) }
    }
    EvidenceCard(advice.evidence)
    SourcesCard(advice.sourceIds)
    QuietInfo(PartnerAdvice.CAUTION_FA)
}

/** Clear 'remove partner' action with a confirm dialog. Works from both sides. */
@Composable
fun RemovePartnerButton(vm: AppViewModel, partnerName: String, iAmFemale: Boolean, onResult: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var ask by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    TextButton(onClick = { ask = true }, modifier = Modifier.fillMaxWidth().testTag("partner_remove")) {
        Text("حذف همراه و قطع اتصال", color = MahavaDanger, fontWeight = FontWeight.Bold)
    }
    if (ask) {
        AlertDialog(
            onDismissRequest = { if (!busy) ask = false },
            title = { Text("قطع اتصال با $partnerName؟") },
            text = {
                Text(
                    (if (iAmFemale) "$partnerName دیگر وضعیتت را نمی‌بیند و اطلاعات مشترکت از سرور پاک می‌شود."
                    else "دیگر وضعیت $partnerName را نمی‌بینی و اطلاعات مشترک او از سرور پاک می‌شود.") +
                        "\nبه او هم خبر می‌دهیم. هر کس اشتراک خودش را نگه می‌دارد."
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
                }) { Text(if (busy) "صبر کن…" else "قطع اتصال", color = MahavaDanger) }
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
