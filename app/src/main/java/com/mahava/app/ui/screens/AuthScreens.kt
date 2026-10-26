package com.mahava.app.ui.screens

import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.mahava.app.BuildConfig
import com.mahava.app.ui.AppViewModel
import com.mahava.app.ui.components.*
import com.mahava.app.ui.theme.MahavaPrimary
import com.mahava.app.util.PersianDigits
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
    var askDelete by remember { mutableStateOf(false) }
    var deletePassword by remember { mutableStateOf("") }
    var deleteError by remember { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf(false) }
    val role by vm.accountRole.collectAsState()

    val inbox by vm.inboxItems.collectAsState()
    LaunchedEffect(Unit) {
        vm.refreshPricing()
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
                ends?.let { QuietInfo("پایان اشتراک: ${endsAtJalaliFa(it)}") }
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
                LogoutButton(vm, primary = true) { status = it }
            }
            Spacer(Modifier.height(12.dp))
            MahavaCard {
                Text("حذف حساب", style = MaterialTheme.typography.titleMedium)
                QuietInfo("با حذف حساب، همهٔ اطلاعاتت از سرور هم پاک می‌شود و اتصال به همسر قطع می‌شود. این کار برگشت ندارد.")
                Spacer(Modifier.height(8.dp))
                SecondaryButton("حذف حساب و همهٔ اطلاعات", modifier = Modifier.testTag("delete_account_btn")) { askDelete = true }
            }
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
            var pendingDisc by remember { mutableStateOf<com.mahava.app.network.MahDiscountPreviewDto?>(null) }
            var discountInput by remember { mutableStateOf("") }
            var applyingDisc by remember { mutableStateOf(false) }
            LaunchedEffect(phone) {
                if (!phone.isNullOrBlank()) pendingDisc = vm.loadPendingDiscount()
            }
            val payPrice = pendingDisc?.discountedPriceTomans?.takeIf { it >= 0 } ?: price
            Text(if (pendingDisc == null && payPrice <= 0) "قیمت در حال دریافت از سرور…" else "${PersianDigits.toPersian(payPrice.toString())} تومان / سال")
            pendingDisc?.let { d ->
                QuietInfo(
                    "کد ${d.code} · ${PersianDigits.toPersian(d.percentOff)}٪ تخفیف" +
                        if (d.freeGrant) " · فعال‌سازی رایگان" else ""
                )
            }
            QuietInfo("پرداخت از طریق کافه‌بازار انجام می‌شود.")
            if (!phone.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = discountInput,
                    onValueChange = { discountInput = it.uppercase() },
                    label = { Text("کد تخفیف") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("discount_code")
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    SecondaryButton(
                        if (applyingDisc) "صبر کن…" else "اعمال کد",
                        modifier = Modifier.weight(1f).testTag("discount_apply")
                    ) {
                        if (discountInput.isBlank() || applyingDisc) return@SecondaryButton
                        applyingDisc = true
                        scope.launch {
                            val (err, preview) = vm.applyDiscountCode(discountInput)
                            applyingDisc = false
                            if (err != null) status = err
                            else {
                                pendingDisc = preview
                                status = if (preview?.freeGrant == true) {
                                    "کد رایگان اعمال شد و اشتراک سالانه فعال شد."
                                } else {
                                    "کد اعمال شد. هنگام خرید ثبت می‌شود."
                                }
                            }
                        }
                    }
                    if (pendingDisc != null) {
                        SecondaryButton("حذف کد", modifier = Modifier.weight(1f)) {
                            scope.launch {
                                val err = vm.clearDiscountCode()
                                if (err == null) {
                                    pendingDisc = null
                                    discountInput = ""
                                    status = "کد تخفیف برداشته شد."
                                } else status = err
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            val registryOwner = LocalActivityResultRegistryOwner.current
            var buying by remember { mutableStateOf(false) }
            val buyEnabled = !phone.isNullOrBlank() && !buying && registryOwner != null && pendingDisc?.freeGrant != true
            PrimaryButton(
                if (buying) "صبر کن…" else if (active) "تمدید اشتراک سالانه" else "خرید سالانه از بازار",
                enabled = buyEnabled,
                modifier = Modifier.testTag("buy_yearly_bazaar")
            ) {
                val registry = registryOwner?.activityResultRegistry ?: return@PrimaryButton
                buying = true
                scope.launch {
                    val err = vm.purchaseYearlyWithBazaar(registry)
                    buying = false
                    if (err == null) pendingDisc = null
                    status = err ?: "اشتراک سالانه فعال شد."
                }
            }
            Spacer(Modifier.height(8.dp))
            SecondaryButton("بازیابی خرید قبلی بازار") {
                scope.launch {
                    status = vm.restoreBazaarPurchases()
                }
            }
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
            st.pending > 0 && st.rejected >= st.pending -> "${PersianDigits.toPersian(st.pending)} تا تغییر رو سرور قبول نکرد؛ روی گوشیت سالم می‌مونه و دفعهٔ بعد که ویرایشش کنی دوباره فرستاده می‌شه."
            st.pending > 0 -> "${PersianDigits.toPersian(st.pending)} تا تغییر هنوز به سرور نرسیده؛ با اینترنت خودش فرستاده می‌شه."
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
