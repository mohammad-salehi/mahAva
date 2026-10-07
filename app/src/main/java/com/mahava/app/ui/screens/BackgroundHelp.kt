package com.mahava.app.ui.screens

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.mahava.app.ui.components.MahavaCard
import com.mahava.app.ui.components.PrimaryButton
import com.mahava.app.ui.components.QuietInfo
import com.mahava.app.ui.components.SecondaryButton
import com.mahava.app.ui.theme.MahavaPrimary

/**
 * Keeps partner updates arriving with the app closed:
 * - Android 13+: asks for the notification permission (once automatically, then via a button)
 * - Xiaomi/Redmi/POCO (MIUI/HyperOS): explains Autostart + battery "No restrictions" and opens those settings
 */
object BackgroundHelp {
    private const val SP = "mahava_bg_help"

    fun isXiaomi(): Boolean {
        val m = (Build.MANUFACTURER + " " + Build.BRAND).lowercase()
        return listOf("xiaomi", "redmi", "poco").any { it in m }
    }

    fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun appDetails(context: Context) =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))

    /** Try each intent in order; the app-details screen is the last resort. Returns true if one opened. */
    private fun openFirst(context: Context, intents: List<Intent>): Boolean {
        for (i in intents + appDetails(context)) {
            try {
                context.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return true
            } catch (_: ActivityNotFoundException) {
            } catch (_: SecurityException) {
            } catch (_: Throwable) {
            }
        }
        return false
    }

    fun openAutostart(context: Context) = openFirst(
        context,
        listOf(
            Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")),
            Intent("miui.intent.action.OP_AUTO_START").addCategory(Intent.CATEGORY_DEFAULT)
        )
    )

    fun openBattery(context: Context): Boolean {
        val label = try { context.applicationInfo.loadLabel(context.packageManager).toString() } catch (_: Throwable) { "ماه" }
        return openFirst(
            context,
            listOf(
                Intent().setComponent(ComponentName("com.miui.powerkeeper", "com.miui.powerkeeper.ui.HiddenAppsConfigActivity"))
                    .putExtra("package_name", context.packageName)
                    .putExtra("package_label", label),
                Intent("miui.intent.action.POWER_HIDE_MODE_APP_LIST").addCategory(Intent.CATEGORY_DEFAULT),
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            )
        )
    }

    fun dismissed(context: Context): Boolean =
        context.getSharedPreferences(SP, Context.MODE_PRIVATE).getBoolean("miui_done", false)

    fun setDismissed(context: Context) {
        context.getSharedPreferences(SP, Context.MODE_PRIVATE).edit().putBoolean("miui_done", true).apply()
    }

    fun askedNotif(context: Context): Boolean =
        context.getSharedPreferences(SP, Context.MODE_PRIVATE).getBoolean("asked_notif", false)

    fun setAskedNotif(context: Context) {
        context.getSharedPreferences(SP, Context.MODE_PRIVATE).edit().putBoolean("asked_notif", true).apply()
    }
}

/** Shown on the partner screens while a pair exists. */
@Composable
fun BackgroundReliabilityCards(active: Boolean) {
    if (!active) return
    val context = LocalContext.current
    var canNotify by remember { mutableStateOf(BackgroundHelp.canNotify(context)) }
    var miuiHidden by remember { mutableStateOf(BackgroundHelp.dismissed(context)) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        canNotify = granted || BackgroundHelp.canNotify(context)
    }
    LaunchedEffect(Unit) {
        if (!canNotify && Build.VERSION.SDK_INT >= 33 && !BackgroundHelp.askedNotif(context)) {
            BackgroundHelp.setAskedNotif(context)
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    if (!canNotify) {
        MahavaCard(Modifier.testTag("notif_permission_card")) {
            Text("اعلان‌ها خاموش است", style = MaterialTheme.typography.titleMedium, color = MahavaPrimary)
            QuietInfo("برای این‌که تغییرهای همراهت و وضعیت روزانه را حتی وقتی برنامه بسته است ببینی، اجازهٔ اعلان بده.")
            Spacer(Modifier.height(8.dp))
            PrimaryButton("اجازهٔ اعلان", modifier = Modifier.testTag("notif_permission_btn")) {
                if (Build.VERSION.SDK_INT >= 33) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                else BackgroundHelp.openBattery(context)
            }
        }
    }
    if (BackgroundHelp.isXiaomi() && !miuiHidden) {
        MahavaCard(Modifier.testTag("miui_hint_card")) {
            Text("گوشی شیائومی داری؟", style = MaterialTheme.typography.titleMedium, color = MahavaPrimary)
            QuietInfo("شیائومی برنامه‌های بسته را خاموش می‌کند و اعلان‌ها دیر می‌رسد. این دو کار را یک بار انجام بده:")
            Text("۱. «اجرای خودکار» (Autostart) را برای ماه روشن کن.", style = MaterialTheme.typography.bodyMedium)
            Text("۲. در «صرفه‌جویی باتری»، ماه را روی «بدون محدودیت» (No restrictions) بگذار.", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryButton("اجرای خودکار", modifier = Modifier.weight(1f).testTag("miui_autostart_btn")) {
                    BackgroundHelp.openAutostart(context)
                }
                SecondaryButton("باتری", modifier = Modifier.weight(1f).testTag("miui_battery_btn")) {
                    BackgroundHelp.openBattery(context)
                }
            }
            TextButton(onClick = { BackgroundHelp.setDismissed(context); miuiHidden = true }) { Text("انجام دادم") }
        }
    }
}
