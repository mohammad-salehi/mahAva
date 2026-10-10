package com.mahava.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.mahava.app.ui.AppViewModel
import com.mahava.app.ui.components.*
import com.mahava.app.ui.theme.MahavaPrimary
import com.mahava.app.util.PersianDigits
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(
    vm: AppViewModel,
    onBack: (() -> Unit)? = null,
    onGoRegister: () -> Unit,
    onForgotPassword: () -> Unit = {},
    onSuccess: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    var phone by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).testTag("login_screen")
    ) {
        ScreenHeader("ورود", onBack = onBack)
        Text("با شماره موبایل وارد شو", style = MaterialTheme.typography.headlineLarge)
        QuietInfo("برای استفاده از برنامه وارد حسابت شو. ثبت‌نام جدید یک ماه رایگان می‌گیره. همهٔ اطلاعات چرخه‌ات فقط روی سرور ماه ذخیره می‌شه و با گوشی جدید برمی‌گرده. اگه به همسرت وصل شی، اون چیزایی که ثبت می‌کنی رو می‌بینه.")
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = phone,
            onValueChange = { phone = it },
            label = { Text("شماره موبایل") },
            placeholder = { Text("09xxxxxxxxx") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("login_phone")
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("رمز عبور") },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("login_password")
        )
        error?.let { Spacer(Modifier.height(8.dp)); Text(it, color = MaterialTheme.colorScheme.error) }
        info?.let { Spacer(Modifier.height(8.dp)); Text(it, color = MahavaPrimary) }
        Spacer(Modifier.height(12.dp))
        PrimaryButton(if (busy) "صبر کن…" else "ورود", enabled = !busy, modifier = Modifier.testTag("login_submit")) {
            busy = true; error = null; info = null
            scope.launch {
                val msg = vm.login(phone, password)
                busy = false
                if (msg == null) { onSuccess() } else error = msg
            }
        }
        TextButton(onClick = onForgotPassword, modifier = Modifier.testTag("go_password_recovery")) {
            Text("رمز را فراموش کردی؟")
        }
        TextButton(onClick = onGoRegister, modifier = Modifier.testTag("go_register")) {
            Text("حساب نداری؟ ثبت‌نام")
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun RegisterScreen(vm: AppViewModel, onBack: () -> Unit, onGoLogin: () -> Unit, onSuccess: () -> Unit = onBack) {
    val scope = rememberCoroutineScope()
    var role by remember { mutableStateOf("female") }
    var phone by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).testTag("register_screen")
    ) {
        ScreenHeader("ثبت‌نام", onBack = onBack)
        Text("ساخت حساب ماه", style = MaterialTheme.typography.headlineLarge)
        QuietInfo("با ثبت‌نام، یک ماه اشتراک رایگان فعال می‌شه. همهٔ اطلاعات چرخه‌ات فقط روی سرور ماه ذخیره می‌شه و با گوشی جدید برمی‌گرده. اگه به همسرت وصل شی، اون چیزایی که ثبت می‌کنی رو می‌بینه.")
        Spacer(Modifier.height(12.dp))
        Text("این حساب برای کیست؟", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(6.dp))
        ChipsFlow(
            listOf("female" to "خانم هستم", "male" to "آقا هستم (همسر)"),
            role, tagPrefix = "register_role"
        ) { role = it }
        if (role == "male") QuietInfo("حساب آقا وضعیت همسرش را می‌بیند. بعد از ثبت‌نام، کد اتصال را از همسرت بگیر.")
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = phone,
            onValueChange = { phone = it },
            label = { Text("شماره موبایل") },
            placeholder = { Text("09xxxxxxxxx") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("register_phone")
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("رمز عبور (حداقل ۶ کاراکتر)") },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("register_password")
        )
        error?.let { Spacer(Modifier.height(8.dp)); Text(it, color = MaterialTheme.colorScheme.error) }
        info?.let { Spacer(Modifier.height(8.dp)); Text(it, color = MahavaPrimary) }
        Spacer(Modifier.height(12.dp))
        PrimaryButton(if (busy) "صبر کن…" else "ثبت‌نام", enabled = !busy, modifier = Modifier.testTag("register_submit")) {
            busy = true; error = null; info = null
            scope.launch {
                val msg = vm.register(phone, password, role)
                busy = false
                if (msg == null) onSuccess() else error = msg
            }
        }
        TextButton(onClick = onGoLogin) { Text("قبلاً ثبت‌نام کردی؟ ورود") }
        Spacer(Modifier.height(24.dp))
    }
}

/**
 * Password recovery UI only. Not wired to any backend / SMS OTP yet
 * (planned for after Bazaar publish).
 */
@Composable
fun PasswordRecoveryScreen(onBack: () -> Unit) {
    var phone by remember { mutableStateOf("") }
    var submitted by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).testTag("password_recovery_screen")
    ) {
        ScreenHeader("بازیابی رمز", onBack = onBack)
        Text("رمز را فراموش کردی؟", style = MaterialTheme.typography.headlineLarge)
        QuietInfo("به‌زودی با پیامک کد یک‌بارمصرف (OTP) می‌توانی رمز را عوض کنی. فعلاً این بخش به سرور وصل نیست.")
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = phone,
            onValueChange = { phone = it; submitted = false },
            label = { Text("شماره موبایل") },
            placeholder = { Text("09xxxxxxxxx") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("recovery_phone")
        )
        Spacer(Modifier.height(12.dp))
        PrimaryButton("ارسال کد", modifier = Modifier.testTag("recovery_submit")) {
            submitted = true
        }
        if (submitted) {
            Spacer(Modifier.height(12.dp))
            MahavaCard {
                Text("به‌زودی فعال می‌شود", style = MaterialTheme.typography.titleMedium, color = MahavaPrimary)
                QuietInfo("بازیابی رمز با پیامک هنوز راه نیفتاده. فعلاً اگر رمز را یادت نیست، از پشتیبانی ماه کمک بگیر یا بعداً دوباره سر بزن.")
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun AccountScreen(vm: AppViewModel, onBack: () -> Unit, onLogin: () -> Unit, onRegister: () -> Unit) {
    val phone by vm.accountPhone.collectAsState()
    val plan by vm.serverPlan.collectAsState()
    val ends by vm.serverEndsAt.collectAsState()
    val active by vm.serverHasActiveSubscription.collectAsState()
    val price by vm.yearlyPriceTomans.collectAsState()
    val premium by vm.isPremium.collectAsState()
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf("") }
    var confirmLogout by remember { mutableStateOf(false) }
    var askDelete by remember { mutableStateOf(false) }
    var deletePassword by remember { mutableStateOf("") }
    var deleteError by remember { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf(false) }
    val role by vm.accountRole.collectAsState()

    val inbox by vm.inboxItems.collectAsState()
    LaunchedEffect(Unit) {
        vm.refreshAccountFromServer()
        vm.refreshInbox()
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).testTag("account_screen")
    ) {
        ScreenHeader("حساب و اشتراک", onBack = onBack)
        if (inbox.isNotEmpty()) {
            SectionLabel("پیام‌های ماه")
            inbox.forEach { item ->
                MahavaCard(Modifier.padding(vertical = 4.dp)) {
                    Text(item.title, style = MaterialTheme.typography.titleMedium)
                    Text(item.body, style = MaterialTheme.typography.bodyMedium)
                    if (item.dismissible) {
                        Spacer(Modifier.height(6.dp))
                        SecondaryButton("بستن") { vm.dismissInboxItem(item.id) }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
        if (phone.isNullOrBlank()) {
            MahavaCard {
                Text("هنوز وارد نشده‌ای", style = MaterialTheme.typography.titleMedium)
                QuietInfo("برای استفاده از برنامه، ثبت‌نام یا ورود کن.")
                Spacer(Modifier.height(8.dp))
                PrimaryButton("ورود", onClick = onLogin)
                Spacer(Modifier.height(8.dp))
                SecondaryButton("ثبت‌نام", onClick = onRegister)
            }
        } else {
            MahavaCard {
                Text("حساب من", style = MaterialTheme.typography.titleMedium, color = MahavaPrimary)
                Text("شماره: ${PersianDigits.toPersian(phone!!)}")
                Text(
                    if (active) "اشتراک فعال (${planLabel(plan)})"
                    else "اشتراک فعال نیست"
                )
                ends?.let { QuietInfo("پایان تقریبی: $it") }
                QuietInfo(if (premium) "دسترسی ویژه: روشن" else "دسترسی ویژه: خاموش")
                val sharedSub by vm.sharedFromPartner.collectAsState()
                if (sharedSub) QuietInfo("اشتراک از همسرت می‌آید. با یک اشتراک، هر دوی شما از امکانات ویژه استفاده می‌کنید.")
                QuietInfo(if (role == "male") "نوع حساب: آقا (همسر)" else "نوع حساب: خانم")
                Spacer(Modifier.height(8.dp))
                SecondaryButton("به‌روزرسانی وضعیت") {
                    scope.launch {
                        status = if (vm.refreshAccountFromServer()) "به‌روز شد." else "به‌روزرسانی ناموفق بود."
                    }
                }
                Spacer(Modifier.height(8.dp))
                PrimaryButton("خروج از حساب", modifier = Modifier.testTag("logout_btn")) {
                    scope.launch {
                        if (vm.logoutAccount(force = false) == "pending") confirmLogout = true
                        else status = "خارج شدی. اطلاعاتت روی سرور می‌مونه و با ورود دوباره برمی‌گرده."
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            MahavaCard {
                Text("حذف حساب", style = MaterialTheme.typography.titleMedium)
                QuietInfo("با حذف حساب، همهٔ اطلاعاتت از سرور هم پاک می‌شه و اتصال به همسر قطع می‌شه. این کار برگشت نداره.")
                Spacer(Modifier.height(8.dp))
                SecondaryButton("حذف حساب و همهٔ اطلاعات", modifier = Modifier.testTag("delete_account_btn")) { askDelete = true }
            }
        }
        if (confirmLogout) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { confirmLogout = false },
                title = { Text("بعضی تغییرها هنوز به سرور نرسیده") },
                text = { Text("الان اینترنت نیست یا سرور در دسترس نیست. اگه خارج شی، تغییرهای اخیرِ همین گوشی پاک می‌شن. بهتره اول به اینترنت وصل شی.") },
                confirmButton = {
                    TextButton(onClick = {
                        confirmLogout = false
                        scope.launch { vm.logoutAccount(force = true); status = "خارج شدی." }
                    }) { Text("با این حال خارج شو") }
                },
                dismissButton = { TextButton(onClick = { confirmLogout = false }) { Text("صبر می‌کنم") } }
            )
        }
        if (askDelete) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { if (!deleting) askDelete = false },
                title = { Text("حذف حساب") },
                text = {
                    Column {
                        Text("برای تأیید، رمز عبورت را بنویس. همهٔ اطلاعاتت از سرور و این گوشی پاک می‌شود.")
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = deletePassword,
                            onValueChange = { deletePassword = it },
                            label = { Text("رمز عبور") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            modifier = Modifier.fillMaxWidth().testTag("delete_account_password")
                        )
                        deleteError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    }
                },
                confirmButton = {
                    TextButton(enabled = !deleting && deletePassword.isNotBlank(), onClick = {
                        deleting = true
                        scope.launch {
                            val err = vm.deleteAccount(deletePassword)
                            deleting = false
                            if (err == null) { askDelete = false; status = "حساب و همهٔ اطلاعاتت پاک شد." } else deleteError = err
                        }
                    }, modifier = Modifier.testTag("delete_account_confirm")) { Text(if (deleting) "صبر کن…" else "حذف همیشگی") }
                },
                dismissButton = { TextButton(enabled = !deleting, onClick = { askDelete = false }) { Text("انصراف") } }
            )
        }
        Spacer(Modifier.height(12.dp))
        MahavaCard {
            Text("اشتراک سالانه", style = MaterialTheme.typography.titleMedium)
            Text("${PersianDigits.toPersian(price.toString())} تومان / سال")
            QuietInfo("درگاه پرداخت هنوز آماده نیست.")
            Spacer(Modifier.height(8.dp))
            PrimaryButton("خرید سالانه — به‌زودی", enabled = false) { }
        }
        if (status.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            QuietInfo(status)
        }
        Spacer(Modifier.height(24.dp))
    }
}

private fun planLabel(plan: String?): String = when (plan) {
    "free_trial" -> "آزمایشی"
    "yearly" -> "سالانه"
    else -> plan ?: "—"
}


/** "Your data is on the server" status: last sync, pending offline changes, sync button. */
@Composable
fun DataSyncCard(vm: AppViewModel) {
    val st by vm.dataSyncStatus.collectAsState()
    val scope = rememberCoroutineScope()
    var msg by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { vm.refreshSyncPending() }
    MahavaCard(Modifier.testTag("data_sync_card")) {
        Text("ذخیره روی سرور", style = MaterialTheme.typography.titleMedium, color = MahavaPrimary)
        QuietInfo("اطلاعات چرخه و ثبت‌هایت روی سرور ماه امن نگه داشته می‌شود تا با عوض کردن گوشی یا ورود دوباره گم نشود. بدون اینترنت هم کار می‌کنی؛ تغییرها بعداً خودکار فرستاده می‌شوند.")
        val line = when {
            st.running -> "در حال همگام‌سازی…"
            st.pending > 0 -> "${PersianDigits.toPersian(st.pending)} تغییر منتظر اینترنت است."
            st.lastOkAt > 0 -> "آخرین همگام‌سازی: ${syncAgoFa(st.lastOkAt)}"
            else -> "هنوز همگام نشده."
        }
        Text(line, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(8.dp))
        SecondaryButton("همگام‌سازی الان", modifier = Modifier.testTag("data_sync_now")) {
            scope.launch { msg = vm.syncDataNow() }
        }
        msg?.let { QuietInfo(it) }
    }
}

private fun syncAgoFa(ms: Long): String {
    val min = ((System.currentTimeMillis() - ms) / 60000).coerceAtLeast(0)
    return when {
        min < 1 -> "همین الان"
        min < 60 -> "${PersianDigits.toPersian(min.toInt())} دقیقه پیش"
        min < 1440 -> "${PersianDigits.toPersian((min / 60).toInt())} ساعت پیش"
        else -> "${PersianDigits.toPersian((min / 1440).toInt())} روز پیش"
    }
}
