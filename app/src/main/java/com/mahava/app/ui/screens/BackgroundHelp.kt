package com.mahava.app.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import com.mahava.app.ui.theme.MahavaPrimary

/** Android 13+ notification permission, asked inside the app (never opens system settings). */
object BackgroundHelp {
    private const val SP = "mahava_bg_help"

    fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun askCount(context: Context): Int =
        context.getSharedPreferences(SP, Context.MODE_PRIVATE).getInt("notif_asks", 0)

    fun countAsk(context: Context) {
        val sp = context.getSharedPreferences(SP, Context.MODE_PRIVATE)
        sp.edit().putInt("notif_asks", sp.getInt("notif_asks", 0) + 1).apply()
    }
}

/** Why a woman's account needs notifications (onboarding and the one-time ask on the home screen). */
const val NOTIFICATION_REASON_FA =
    "برای این‌که توصیهٔ روزانهٔ ساعت ۸ صبح و یادآوری‌هات (مثل نزدیک شدن پریود) به دستت برسه، ماه باید اجازهٔ فرستادن اعلان داشته باشه."

/**
 * Asks once automatically for POST_NOTIFICATIONS; the card offers one more in-app request.
 * With [onlyIfNeverAsked] it only does the single automatic ask (for accounts that skipped
 * onboarding) and never shows a card afterwards, so a "no" is respected.
 * [reasonFa] explains why (morning tips for women, partner updates for husbands).
 */
@Composable
fun NotificationPermissionCard(
    reasonFa: String = "برای این‌که وقتی برنامه بسته است هم از ثبت‌های همسرت خبردار شوی، اجازهٔ اعلان بده.",
    onlyIfNeverAsked: Boolean = false
) {
    if (Build.VERSION.SDK_INT < 33) return
    val context = LocalContext.current
    var canNotify by remember { mutableStateOf(BackgroundHelp.canNotify(context)) }
    var asks by remember { mutableStateOf(BackgroundHelp.askCount(context)) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        canNotify = granted || BackgroundHelp.canNotify(context)
    }
    LaunchedEffect(Unit) {
        if (!canNotify && asks == 0) {
            BackgroundHelp.countAsk(context); asks += 1
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    // Android stops showing the request after it was declined twice; then the card goes away.
    if (canNotify || asks >= 2 || (onlyIfNeverAsked && asks >= 1)) return
    MahavaCard(Modifier.testTag("notif_permission_card")) {
        Text("اعلان‌ها خاموشه", style = MaterialTheme.typography.titleMedium, color = MahavaPrimary)
        Text(reasonFa, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(8.dp))
        PrimaryButton("اجازهٔ اعلان", modifier = Modifier.testTag("notif_permission_btn")) {
            BackgroundHelp.countAsk(context); asks += 1
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

/**
 * Onboarding: explains why notifications matter. The system question comes when she taps the
 * button here, or once when she taps «ورود به برنامه» (see [OnboardingFlow]); never repeated.
 */
@Composable
fun OnboardingNotificationCard(asked: Boolean, onAsk: () -> Unit) {
    if (Build.VERSION.SDK_INT < 33) return
    val context = LocalContext.current
    if (BackgroundHelp.canNotify(context)) return
    MahavaCard(Modifier.testTag("onb_notif_card")) {
        Text("اعلان‌ها", style = MaterialTheme.typography.titleMedium, color = MahavaPrimary)
        Text(NOTIFICATION_REASON_FA, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(8.dp))
        if (!asked) {
            PrimaryButton("اجازهٔ اعلان می‌دم", modifier = Modifier.testTag("onb_notif_btn"), onClick = onAsk)
        } else {
            Text("اجازه ندادی؛ مشکلی نیست. اگه بعداً خواستی، از تنظیمات گوشی برای «ماه» اعلان رو روشن کن.", style = MaterialTheme.typography.bodySmall)
        }
    }
}
