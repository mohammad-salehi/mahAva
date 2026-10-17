package com.mahava.app.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.mahava.app.R
import com.mahava.app.data.db.ReminderPrefEntity
import com.mahava.app.ui.AppViewModel
import com.mahava.app.ui.components.*
import com.mahava.app.ui.theme.*
import com.mahava.app.util.PersianDigits
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(vm: AppViewModel, activity: FragmentActivity, onBack: () -> Unit, onAccount: () -> Unit = {}, onPartner: () -> Unit = {}, onAfterDeleteAll: () -> Unit = {}, onPregnancySetup: () -> Unit = {}, onTickets: () -> Unit = {}) {
    val state by vm.state.collectAsState()
    val profile = state.profile
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    var confirmDelete by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }

    val notifPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) status = "اجازهٔ اعلان داده نشد؛ یادآوری‌ها نمایش داده نمی‌شوند."
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).testTag("settings_screen")) {
        ScreenHeader("تنظیمات و حریم خصوصی", onBack = onBack)
        Illustration(R.drawable.ill_privacy_shield, Modifier.height(100.dp))
        QuietInfo("ثبت‌ها و تنظیماتت روی سرور ماه می‌ماند تا با گوشی جدید هم برگردد.")

        val accountPhone by vm.accountPhone.collectAsState()
        val loggedIn by vm.isLoggedIn.collectAsState()
        val accountRole by vm.accountRole.collectAsState()
        val isMale = accountRole == "male"
        val plan by vm.serverPlan.collectAsState()
        val ends by vm.serverEndsAt.collectAsState()
        val subActive by vm.serverHasActiveSubscription.collectAsState()
        var showRenew by remember { mutableStateOf(false) }
        LaunchedEffect(loggedIn) {
            if (loggedIn) vm.refreshAccountFromServer()
        }
        SettingsGroup("حساب من") {
            if (!loggedIn) {
                QuietInfo("هنوز وارد نشدی. برای ذخیره روی سرور و خرید اشتراک وارد شو.")
                PrimaryButton("ورود / ثبت‌نام", onClick = onAccount)
            } else {
                if (!accountPhone.isNullOrBlank()) {
                    Text("شماره: ${PersianDigits.toPersian(accountPhone!!)}", style = MaterialTheme.typography.titleMedium)
                }
                Text(
                    "نوع اشتراک: ${planLabelFa(plan, subActive)}",
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    "پایان اشتراک: ${endsAtJalaliFa(ends)}",
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(Modifier.height(8.dp))
                PrimaryButton(
                    if (subActive) "تمدید اشتراک" else "خرید اشتراک",
                    modifier = Modifier.testTag("settings_renew")
                ) { showRenew = true }
                Spacer(Modifier.height(8.dp))
                SecondaryButton("خروج از حساب", modifier = Modifier.testTag("logout_btn")) {
                    scope.launch {
                        status = "در حال خروج…"
                        status = vm.logoutAccount() ?: "خارج شدی."
                    }
                }
            }
        }
        if (showRenew) {
            SubscriptionRenewDialog(
                vm = vm,
                onDismiss = { showRenew = false },
                onNeedLogin = { showRenew = false; onAccount() }
            )
        }

        if (!isMale) SettingsGroup("همسر") {
            SecondaryButton("اتصال به همسر", modifier = Modifier.testTag("settings_partner"), onClick = onPartner)
        }

        if (!isMale) {
            SettingsGroup("هدف من") {
                ChipsFlow(listOf("track_period" to "پیگیری پریود", "body_awareness" to "شناخت بدن", "ttc" to "اقدام برای بارداری"), profile?.goal) { k ->
                    vm.updateProfile { it.copy(goal = k, fertilityTrackingEnabled = k == "ttc" || it.fertilityTrackingEnabled) }
                }
                SwitchLine("نشان دادن روزهای احتمالی باروری", profile?.fertilityTrackingEnabled == true || profile?.goal == "ttc", "set_fertility") { v ->
                    vm.updateProfile { it.copy(fertilityTrackingEnabled = v, goal = if (!v && it.goal == "ttc") "track_period" else it.goal) }
                }
            }

            SettingsGroup("چرخه و شرایط من") {
                Text("طول معمول چرخه", style = MaterialTheme.typography.bodyLarge)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { vm.updateProfile { it.copy(typicalCycleLength = ((it.typicalCycleLength ?: 29) - 1).coerceIn(15, 60), cycleLengthUnknown = false) } }) { MahavaIcon(R.drawable.ic_minus) }
                    Text(profile?.typicalCycleLength?.let { "${com.mahava.app.util.PersianDigits.toPersian(it)} روز" } ?: "نمی‌دانم", Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    IconButton(onClick = { vm.updateProfile { it.copy(typicalCycleLength = ((it.typicalCycleLength ?: 27) + 1).coerceIn(15, 60), cycleLengthUnknown = false) } }) { MahavaIcon(R.drawable.ic_plus) }
                }
                if (profile?.typicalCycleLength != null) TextButton(onClick = { vm.updateProfile { it.copy(typicalCycleLength = null, cycleLengthUnknown = true) } }) { Text("نمی‌دانم") }
                ChipsFlow(listOf("regular" to "پریودهایم منظم است", "irregular" to "پریودهایم نامنظم است", "unknown" to "نمی‌دانم"),
                    when (profile?.regularCycles) { true -> "regular"; false -> "irregular"; else -> "unknown" }) { k ->
                    vm.updateProfile { it.copy(regularCycles = when (k) { "regular" -> true; "irregular" -> false; else -> null }) }
                }
                SwitchLine("روش هورمونی جلوگیری", profile?.hormonalContraception == true, "set_hormonal") { v -> vm.updateProfile { it.copy(hormonalContraception = v) } }
                SwitchLine("زایمان اخیر یا شیردهی", profile?.postpartumOrBreastfeeding == true, "set_postpartum") { v -> vm.updateProfile { it.copy(postpartumOrBreastfeeding = v) } }
                SwitchLine("نزدیک یائسگی", profile?.perimenopause == true, "set_peri") { v -> vm.updateProfile { it.copy(perimenopause = v) } }
                SecondaryButton(if (profile?.pregnancyMode == true) "اصلاح تاریخ مبنای بارداری" else "فعال‌سازی بارداری با تاریخ مبنا", onClick = onPregnancySetup)
                if (profile?.pregnancyMode == true) TextButton(onClick = { vm.endPregnancyMode("user_ended") }) { Text("پایان حالت بارداری؛ حفظ سوابق") }
            }

            SettingsGroup("تقویم") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChoiceChipPill("شمسی", profile?.calendarType != "gregorian") { vm.updateProfile { it.copy(calendarType = "jalali") } }
                    ChoiceChipPill("میلادی", profile?.calendarType == "gregorian") { vm.updateProfile { it.copy(calendarType = "gregorian") } }
                }
            }
        }

        SettingsGroup("پشتیبانی") {
            QuietInfo("سؤال یا مشکل داری؟ تیکت بفرست تا از پنل پشتیبانی جواب بگیری.")
            SecondaryButton("تیکت و پشتیبانی", modifier = Modifier.testTag("settings_tickets"), onClick = onTickets)
        }

        SettingsGroup("قفل و اعلان") {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                MahavaIcon(R.drawable.ic_fingerprint); Spacer(Modifier.width(8.dp))
                Text("قفل برنامه با اثر انگشت یا رمز گوشی", Modifier.weight(1f))
                Switch(profile?.lockEnabled == true, { enabled ->
                    if (enabled) {
                        val bm = BiometricManager.from(ctx)
                        val can = bm.canAuthenticate(
                            BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL
                        )
                        if (can == BiometricManager.BIOMETRIC_SUCCESS) {
                            vm.updateProfile { it.copy(lockEnabled = true) }
                        } else status = "روی این گوشی قفل صفحه یا اثر انگشت روشن نیست؛ اول از تنظیمات گوشی روشن‌ش کن."
                    } else vm.updateProfile { it.copy(lockEnabled = false) }
                }, modifier = Modifier.testTag("set_lock"))
            }
            if (profile?.lockEnabled == true) {
                Text("چه زمانی دوباره قفل شود؟", style = MaterialTheme.typography.bodyMedium)
                ChipsFlow(listOf("0" to "بلافاصله", "60" to "بعد از ۱ دقیقه", "300" to "بعد از ۵ دقیقه"), profile.lockTimeoutSeconds.toString(), "lock_to") { k ->
                    vm.updateProfile { it.copy(lockTimeoutSeconds = k.toInt()) }
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                MahavaIcon(R.drawable.ic_bell); Spacer(Modifier.width(8.dp))
                Text("روی صفحهٔ قفل فقط اسم «ماه» دیده شود", Modifier.weight(1f))
                Switch(profile?.privateNotifications == true, { v -> vm.updateProfile { it.copy(privateNotifications = v) } }, modifier = Modifier.testTag("set_private_notif"))
            }
            if (!isMale) {
                QuietInfo("هر صبح خودِ توصیه را برات می‌فرستیم، نه فقط «یادآوری داری».")
                ReminderToggle(vm, "daily_log", "یادآوری روزانه برای ثبت حال", state.reminders, "set_rem_daily") {
                    if (Build.VERSION.SDK_INT >= 33) notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                ReminderToggle(vm, "period", "یادآوری نزدیک شدن پریود", state.reminders, "set_rem_period") {
                    if (Build.VERSION.SDK_INT >= 33) notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                ReminderToggle(vm, "water", "یادآوری نوشیدن آب", state.reminders, "set_rem_water") {
                    if (Build.VERSION.SDK_INT >= 33) notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                ReminderToggle(vm, "sleep", "یادآوری خواب و استراحت", state.reminders, "set_rem_sleep") {
                    if (Build.VERSION.SDK_INT >= 33) notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }

        if (status.isNotBlank()) Text(status, color = MahavaPrimary, modifier = Modifier.padding(vertical = 8.dp).testTag("settings_status"))

        SettingsGroup("پاک کردن") {
            SecondaryButton("پاک کردن همهٔ اطلاعات", modifier = Modifier.testTag("delete_all")) { confirmDelete = true }
        }
        if (confirmDelete) {
            AlertDialog(
                onDismissRequest = { confirmDelete = false },
                title = { Text("همهٔ اطلاعات پاک شود؟") },
                text = { Text("همهٔ پریودها، ثبت‌های روزانه، یادآوری‌ها و تنظیمات از این گوشی و از سرور ماه پاک می‌شوند و برنمی‌گردند.") },
                confirmButton = {
                    TextButton(onClick = {
                        vm.deleteAll()
                        confirmDelete = false
                        onAfterDeleteAll()
                    }, modifier = Modifier.testTag("delete_confirm")) { Text("بله، همه را پاک کن", color = MahavaDanger) }
                },
                dismissButton = {
                    TextButton(onClick = { confirmDelete = false }, modifier = Modifier.testTag("delete_cancel")) { Text("انصراف") }
                }
            )
        }

        Spacer(Modifier.height(32.dp))
    }
}

private fun planLabelFa(plan: String?, active: Boolean): String = when {
    !active -> "ندارد"
    plan == "free_trial" -> "آزمایشی رایگان"
    plan == "yearly" -> "سالانه"
    else -> plan ?: "—"
}

internal fun endsAtJalaliFa(endsAt: String?): String {
    if (endsAt.isNullOrBlank()) return "—"
    return try {
        val instant = java.time.Instant.parse(endsAt)
        val day = java.time.LocalDate.ofInstant(instant, java.time.ZoneId.systemDefault())
        com.mahava.app.util.JalaliDate.from(day).formatFa()
    } catch (_: Throwable) {
        try {
            val day = java.time.LocalDate.parse(endsAt.take(10))
            com.mahava.app.util.JalaliDate.from(day).formatFa()
        } catch (_: Throwable) {
            endsAt
        }
    }
}

@Composable
fun SubscriptionRenewDialog(
    vm: AppViewModel,
    onDismiss: () -> Unit,
    onNeedLogin: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val price by vm.yearlyPriceTomans.collectAsState()
    val active by vm.serverHasActiveSubscription.collectAsState()
    val phone by vm.accountPhone.collectAsState()
    val registryOwner = androidx.activity.compose.LocalActivityResultRegistryOwner.current
    var discountInput by remember { mutableStateOf("") }
    var pendingDisc by remember { mutableStateOf<com.mahava.app.network.MahDiscountPreviewDto?>(null) }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        vm.refreshPricing()
        if (!phone.isNullOrBlank()) pendingDisc = vm.loadPendingDiscount()
    }
    val pay = pendingDisc?.discountedPriceTomans?.takeIf { it >= 0 } ?: price
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(if (active) "تمدید اشتراک" else "خرید اشتراک") },
        text = {
            Column {
                Text("اشتراک سالانه ماه")
                Text(if (pendingDisc == null && pay <= 0) "قیمت در حال دریافت از سرور…" else "${PersianDigits.toPersian(pay.toString())} تومان برای یک سال")
                QuietInfo("پرداخت از کافه‌بازار انجام می‌شود.")
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = discountInput,
                    onValueChange = { discountInput = it.uppercase() },
                    label = { Text("کد تخفیف (اختیاری)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                pendingDisc?.let {
                    QuietInfo("کد ${it.code} · ${PersianDigits.toPersian(it.percentOff)}٪ تخفیف")
                }
                msg?.let { Text(it, color = MahavaPrimary) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy,
                onClick = {
                    if (phone.isNullOrBlank()) {
                        onNeedLogin(); return@TextButton
                    }
                    busy = true
                    scope.launch {
                        if (discountInput.isNotBlank()) {
                            val (err, preview) = vm.applyDiscountCode(discountInput)
                            if (err != null) {
                                busy = false
                                msg = err
                                return@launch
                            }
                            pendingDisc = preview
                            if (preview?.freeGrant == true) {
                                busy = false
                                msg = "اشتراک با کد رایگان فعال شد."
                                onDismiss()
                                return@launch
                            }
                        }
                        val registry = registryOwner?.activityResultRegistry
                        if (registry == null) {
                            busy = false
                            msg = "الان نمی‌شود خرید را باز کرد."
                            return@launch
                        }
                        val err = vm.purchaseYearlyWithBazaar(registry)
                        busy = false
                        if (err == null) {
                            msg = "اشتراک فعال شد."
                            onDismiss()
                        } else msg = err
                    }
                }
            ) { Text(if (busy) "صبر کن…" else if (pendingDisc?.freeGrant == true) "فعال‌سازی رایگان" else "ادامه خرید") }
        },
        dismissButton = {
            TextButton(enabled = !busy, onClick = onDismiss) { Text("بستن") }
        }
    )
}

@Composable
private fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Spacer(Modifier.height(10.dp))
    MahavaCard {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = MahavaPrimary,
            modifier = Modifier.padding(bottom = 10.dp)
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

@Composable
private fun SwitchLine(label: String, checked: Boolean, tag: String, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked, onChange, modifier = Modifier.testTag(tag))
    }
}

@Composable
private fun ReminderToggle(vm: AppViewModel, id: String, label: String, list: List<ReminderPrefEntity>, tag: String, onEnable: () -> Unit) {
    val cur = list.find { it.id == id }
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(cur?.enabled == true, { enabled ->
            if (enabled) onEnable()
            vm.saveReminder(ReminderPrefEntity(id = id, enabled = enabled, hour = 9, minute = 0))
        }, modifier = Modifier.testTag(tag))
    }
}

fun promptUnlock(activity: FragmentActivity, onOk: () -> Unit, onFail: () -> Unit) {
    val executor = ContextCompat.getMainExecutor(activity)
    val prompt = BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { onOk() }
        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) { onFail() }
        override fun onAuthenticationFailed() { onFail() }
    })
    val info = BiometricPrompt.PromptInfo.Builder()
        .setTitle(activity.getString(R.string.lock_title))
        .setSubtitle(activity.getString(R.string.lock_subtitle))
        .setAllowedAuthenticators(
            BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        )
        .build()
    prompt.authenticate(info)
}
