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
fun LoginScreen(vm: AppViewModel, onBack: () -> Unit, onGoRegister: () -> Unit, onSuccess: () -> Unit = onBack) {
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
        QuietInfo("بعد از ورود، اشتراک سرور چک می‌شود. ثبت‌نام جدید یک ماه رایگان می‌گیرد.")
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
        TextButton(onClick = onGoRegister, modifier = Modifier.testTag("go_register")) {
            Text("حساب نداری؟ ثبت‌نام")
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun RegisterScreen(vm: AppViewModel, onBack: () -> Unit, onGoLogin: () -> Unit, onSuccess: () -> Unit = onBack) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
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
        QuietInfo("با ثبت‌نام، یک ماه اشتراک رایگان خودکار فعال می‌شود.")
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("نام (اختیاری)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("register_name")
        )
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
                val msg = vm.register(phone, password, name.ifBlank { null })
                busy = false
                if (msg == null) onSuccess() else error = msg
            }
        }
        TextButton(onClick = onGoLogin) { Text("قبلاً ثبت‌نام کردی؟ ورود") }
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
                QuietInfo("برای استفاده از اشتراک سرور، ثبت‌نام یا ورود کن.")
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
                Spacer(Modifier.height(8.dp))
                SecondaryButton("به‌روزرسانی وضعیت") {
                    scope.launch {
                        status = if (vm.refreshAccountFromServer()) "به‌روز شد." else "به‌روزرسانی ناموفق بود."
                    }
                }
                Spacer(Modifier.height(8.dp))
                SecondaryButton("خروج از حساب", modifier = Modifier.testTag("logout_btn")) {
                    scope.launch { vm.logoutAccount(); status = "خارج شدی." }
                }
            }
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
