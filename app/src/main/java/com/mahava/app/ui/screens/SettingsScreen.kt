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
import com.mahava.app.data.backup.RestoreResult
import com.mahava.app.data.db.ReminderPrefEntity
import com.mahava.app.ui.AppViewModel
import com.mahava.app.ui.components.*
import com.mahava.app.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(vm: AppViewModel, activity: FragmentActivity, onBack: () -> Unit, onAccount: () -> Unit = {}, onAfterDeleteAll: () -> Unit = {}) {
    val state by vm.state.collectAsState()
    val profile = state.profile
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    val app = ctx.applicationContext as com.mahava.app.MahavaApplication
    var password by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }

    fun doBackup(uri: android.net.Uri) = scope.launch {
        status = try {
            val bytes = vm.exportBackup(password.toCharArray())
            if (vm.writeBytesToUri(uri, bytes)) "فایل پشتیبان ذخیره شد." else "نوشتن فایل ناموفق بود."
        } catch (t: Throwable) { "ساخت فایل پشتیبان ناموفق بود." }
    }
    fun doRestore(uri: android.net.Uri) = scope.launch {
        val bytes = vm.readBytesFromUri(uri)
        if (bytes == null) { status = "فایل خوانده نشد."; return@launch }
        status = when (val r = vm.restoreBackup(password.toCharArray(), bytes)) {
            RestoreResult.Success -> "اطلاعات از فایل پشتیبان برگردانده شد."
            is RestoreResult.Failed -> if (r.reason.startsWith("decrypt")) "گذرواژه درست نیست یا فایل خراب است. اطلاعات فعلی‌ات دست نخورد."
                else "این فایل قابل بازگرداندن نیست. اطلاعات فعلی‌ات دست نخورد."
        }
    }
    fun doJson(uri: android.net.Uri) = scope.launch {
        status = if (vm.writeBytesToUri(uri, vm.exportJson().toByteArray(Charsets.UTF_8))) "فایل JSON ذخیره شد. این فایل رمز ندارد و اطلاعات خصوصی‌ات در آن است." else "ذخیرهٔ فایل ناموفق بود."
    }
    fun doCsv(uri: android.net.Uri) = scope.launch {
        status = if (vm.writeBytesToUri(uri, vm.exportCsv().toByteArray(Charsets.UTF_8))) "فایل CSV ذخیره شد. این فایل رمز ندارد و اطلاعات خصوصی‌ات در آن است." else "ذخیرهٔ فایل ناموفق بود."
    }

    val createBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri -> uri?.let { doBackup(it) } }
    val openBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { doRestore(it) } }
    val exportJson = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> uri?.let { doJson(it) } }
    val exportCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri -> uri?.let { doCsv(it) } }
    val notifPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) status = "اجازهٔ اعلان داده نشد؛ یادآوری‌ها نمایش داده نمی‌شوند."
    }
    /** Uses the test hook when present, otherwise the system file picker. */
    fun pick(mode: String, name: String, launch: () -> Unit, then: (android.net.Uri) -> Unit) {
        val hook = app.documentPickerOverride
        if (hook != null) hook(mode, name)?.let(then) else launch()
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).testTag("settings_screen")) {
        ScreenHeader("تنظیمات و حریم خصوصی", onBack = onBack)
        Illustration(R.drawable.ill_privacy_shield, Modifier.height(100.dp))
        Text("اطلاعاتت روی همین گوشی می‌ماند", style = MaterialTheme.typography.headlineLarge)
        QuietInfo("اطلاعات چرخه روی همین گوشی می‌ماند. حساب کاربری فقط برای اشتراک است و داده‌های چرخه‌ات را به سرور نمی‌فرستد.")

        val premium by vm.isPremium.collectAsState()
        val accountPhone by vm.accountPhone.collectAsState()
        SettingsGroup("حساب و اشتراک") {
            QuietInfo(
                if (accountPhone.isNullOrBlank())
                    "برای اشتراک سرور وارد شو. ثبت‌نام جدید یک ماه رایگان می‌گیرد. خرید سالانه ۵۸۵ هزار تومان — به‌زودی."
                else
                    "وارد شده‌ای. وضعیت اشتراک از سرور خوانده می‌شود."
            )
            SecondaryButton("حساب کاربری و اشتراک", onClick = onAccount)
            Spacer(Modifier.height(8.dp))
            QuietInfo("فعال‌سازی آزمایشی فقط برای تست روی همین گوشی است.")
            SwitchLine(
                if (premium) "اشتراک آزمایشی روشن است" else "فعال‌سازی آزمایشی اشتراک",
                premium,
                "premium_toggle"
            ) { vm.setPremium(it) }
        }


        SettingsGroup("هدف من") {
            ChipsFlow(listOf("track_period" to "پیگیری پریود", "body_awareness" to "شناخت بدن", "ttc" to "اقدام برای بارداری"), profile?.goal) { k ->
                vm.updateProfile { it.copy(goal = k, fertilityTrackingEnabled = k == "ttc" || it.fertilityTrackingEnabled) }
            }
            SwitchLine("نشان دادن روزهای احتمالی باروری", profile?.fertilityTrackingEnabled == true || profile?.goal == "ttc", "set_fertility") { v ->
                vm.updateProfile { it.copy(fertilityTrackingEnabled = v, goal = if (!v && it.goal == "ttc") "track_period" else it.goal) }
            }
            QuietInfo("این روزها فقط تخمینی‌اند و روش جلوگیری نیستند.")
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
            QuietInfo("وقتی یکی از این‌ها روشن باشد، مرحلهٔ چرخه و تاریخ‌ها را حدس نمی‌زنیم و توضیح کلی را نشان می‌دهیم.")
        }

        SettingsGroup("تقویم") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoiceChipPill("شمسی", profile?.calendarType != "gregorian") { vm.updateProfile { it.copy(calendarType = "jalali") } }
                ChoiceChipPill("میلادی", profile?.calendarType == "gregorian") { vm.updateProfile { it.copy(calendarType = "gregorian") } }
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
                        } else status = "روی این گوشی قفل صفحه یا اثر انگشت فعال نیست؛ اول آن را در تنظیمات گوشی روشن کن."
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
                Column(Modifier.weight(1f)) {
                    Text("متن اعلان‌ها خصوصی باشد")
                    QuietInfo("در اعلان، کلمه‌ای دربارهٔ پریود نوشته نمی‌شود.")
                }
                Switch(profile?.privateNotifications != false, { v -> vm.updateProfile { it.copy(privateNotifications = v) } }, modifier = Modifier.testTag("set_private_notif"))
            }
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
            QuietInfo("یادآوری‌ها حدوداً سر وقت می‌آیند، نه دقیقاً در یک ساعت مشخص.")
        }

        SettingsGroup("پشتیبان با رمز") {
            OutlinedTextField(
                password, { password = it },
                label = { Text("گذرواژهٔ فایل پشتیبان (دست‌کم ۶ حرف)") },
                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth().testTag("backup_password")
            )
            QuietInfo("فایل پشتیبان با این گذرواژه رمزگذاری می‌شود. گذرواژه جایی ذخیره نمی‌شود؛ اگر فراموشش کنی، فایل باز نمی‌شود.")
            SecondaryButton("ذخیرهٔ فایل پشتیبان", modifier = Modifier.testTag("backup_save")) {
                if (password.length >= 6) pick("create", "mahava-backup.mahava", { createBackup.launch("mahava-backup.mahava") }, { doBackup(it) })
                else status = "گذرواژه باید دست‌کم ۶ حرف باشد."
            }
            SecondaryButton("بازگرداندن از فایل پشتیبان", modifier = Modifier.testTag("backup_restore")) {
                if (password.length >= 6) pick("open", "mahava-backup.mahava", { openBackup.launch(arrayOf("*/*")) }, { doRestore(it) })
                else status = "اول گذرواژهٔ همان فایل را وارد کن."
            }
        }

        SettingsGroup("خروجی بدون رمز") {
            QuietInfo("برای نگه‌داری شخصی یا استفاده در برنامه‌های دیگر. این فایل‌ها رمز ندارند.")
            SecondaryButton("خروجی JSON", modifier = Modifier.testTag("export_json")) { pick("create", "mahava-export.json", { exportJson.launch("mahava-export.json") }, { doJson(it) }) }
            SecondaryButton("خروجی CSV (برای اکسل)", modifier = Modifier.testTag("export_csv")) { pick("create", "mahava-export.csv", { exportCsv.launch("mahava-export.csv") }, { doCsv(it) }) }
        }

        if (status.isNotBlank()) Text(status, color = MahavaPrimary, modifier = Modifier.padding(vertical = 8.dp).testTag("settings_status"))

        SettingsGroup("دربارهٔ برنامه") {
            QuietInfo("نسخهٔ برنامه: ${com.mahava.app.util.PersianDigits.toPersian(com.mahava.app.BuildConfig.VERSION_NAME)}")
            QuietInfo("نسخهٔ روش محاسبه: ${state.cycle?.algorithmVersion ?: "-"}")
            QuietInfo("نسخهٔ مطالب آموزشی: ${com.mahava.app.util.PersianDigits.toPersian(vm.contentVersion())}")
            QuietInfo(vm.contentStatus())
            QuietInfo("فونت: وزیرمتن (با مجوز آزاد OFL)، داخل برنامه.")
        }

        SettingsGroup("پاک کردن") {
            SecondaryButton("پاک کردن همهٔ اطلاعات", modifier = Modifier.testTag("delete_all")) { confirmDelete = true }
        }
        if (confirmDelete) {
            AlertDialog(
                onDismissRequest = { confirmDelete = false },
                title = { Text("همهٔ اطلاعات پاک شود؟") },
                text = { Text("همهٔ پریودها، ثبت‌های روزانه، یادآوری‌ها و تنظیمات از این گوشی پاک می‌شوند و برنمی‌گردند. اگر می‌خواهی بعداً برشان گردانی، اول فایل پشتیبان بساز.") },
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
