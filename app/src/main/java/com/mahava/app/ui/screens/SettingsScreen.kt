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
fun SettingsScreen(vm: AppViewModel, activity: FragmentActivity, onBack: () -> Unit, onAccount: () -> Unit = {}, onPartner: () -> Unit = {}, onAfterDeleteAll: () -> Unit = {}) {
    val state by vm.state.collectAsState()
    val profile = state.profile
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmLogout by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }

    val notifPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) status = "اجازهٔ اعلان داده نشد؛ یادآوری‌ها نمایش داده نمی‌شن."
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).testTag("settings_screen")) {
        ScreenHeader("تنظیمات و حریم خصوصی", onBack = onBack)
        Illustration(R.drawable.ill_privacy_shield, Modifier.height(100.dp))
        Text("اطلاعاتت فقط روی سرور نگه داشته می‌شه", style = MaterialTheme.typography.headlineLarge)
        QuietInfo("همهٔ ثبت‌ها و تنظیمات چرخه‌ات روی سرور ماه ذخیره می‌شن؛ این گوشی فقط کشه. با ورود دوباره یا گوشی جدید برمی‌گردن. اگه به همسرت وصل شی، اون چیزایی که ثبت می‌کنی رو می‌بینه. با حذف حساب، همه از سرور پاک می‌شه.")

        val accountPhone by vm.accountPhone.collectAsState()
        val loggedIn by vm.isLoggedIn.collectAsState()
        val accountRole by vm.accountRole.collectAsState()
        val isMale = accountRole == "male"
        SettingsGroup("حساب من") {
            QuietInfo(
                when {
                    !loggedIn ->
                        "برای اشتراک سرور وارد شو. ثبت‌نام جدید یک ماه رایگان می‌گیره. خرید سالانه ۵۸۵ هزار تومان — به‌زودی."
                    !accountPhone.isNullOrBlank() ->
                        "وارد شده‌ای (${PersianDigits.toPersian(accountPhone!!)}). وضعیت اشتراک از سرور خونده می‌شه."
                    else ->
                        "وارد شده‌ای. وضعیت اشتراک از سرور خونده می‌شه."
                }
            )
            SecondaryButton("حساب کاربری و اشتراک", onClick = onAccount)
            if (loggedIn) {
                Spacer(Modifier.height(4.dp))
                PrimaryButton("خروج از حساب", modifier = Modifier.testTag("logout_btn")) {
                    scope.launch {
                        if (vm.logoutAccount(force = false) == "pending") confirmLogout = true
                        else status = "خارج شدی. اطلاعاتت روی سرور می‌مونه و با ورود دوباره برمی‌گرده."
                    }
                }
            }
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
                SwitchLine("حالت بارداری", profile?.pregnancyMode == true, "set_pregnancy") { v -> vm.updateProfile { it.copy(pregnancyMode = v) } }
            }

            SettingsGroup("تقویم") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChoiceChipPill("شمسی", profile?.calendarType != "gregorian") { vm.updateProfile { it.copy(calendarType = "jalali") } }
                    ChoiceChipPill("میلادی", profile?.calendarType == "gregorian") { vm.updateProfile { it.copy(calendarType = "gregorian") } }
                }
            }
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
                Text("چه زمانی دوباره قفل بشه؟", style = MaterialTheme.typography.bodyMedium)
                ChipsFlow(listOf("0" to "بلافاصله", "60" to "بعد از ۱ دقیقه", "300" to "بعد از ۵ دقیقه"), profile.lockTimeoutSeconds.toString(), "lock_to") { k ->
                    vm.updateProfile { it.copy(lockTimeoutSeconds = k.toInt()) }
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                MahavaIcon(R.drawable.ic_bell); Spacer(Modifier.width(8.dp))
                Text("متن اعلان‌ها خصوصی باشه", Modifier.weight(1f))
                Switch(profile?.privateNotifications != false, { v -> vm.updateProfile { it.copy(privateNotifications = v) } }, modifier = Modifier.testTag("set_private_notif"))
            }
            if (!isMale) {
                QuietInfo("هر صبح یه توصیهٔ کوتاه بر اساس مرحلهٔ چرخه‌ات برات می‌فرستیم.")
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
        if (confirmLogout) {
            AlertDialog(
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
        if (confirmDelete) {
            AlertDialog(
                onDismissRequest = { confirmDelete = false },
                title = { Text("همهٔ اطلاعات پاک بشه؟") },
                text = { Text("همهٔ پریودها، ثبت‌های روزانه، یادآوری‌ها و تنظیمات از این گوشی و از سرور ماه پاک می‌شن و برنمی‌گردن.") },
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
