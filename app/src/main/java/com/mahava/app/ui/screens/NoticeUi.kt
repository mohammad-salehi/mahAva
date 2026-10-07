package com.mahava.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mahava.app.R
import com.mahava.app.ui.AppViewModel
import com.mahava.app.ui.components.MahavaCard
import com.mahava.app.ui.components.MahavaIcon
import com.mahava.app.ui.components.QuietInfo
import com.mahava.app.ui.components.ScreenHeader
import com.mahava.app.ui.components.SecondaryButton
import com.mahava.app.ui.theme.MahavaDanger
import com.mahava.app.ui.theme.MahavaPrimary
import com.mahava.app.ui.theme.MahavaTextPrimary
import com.mahava.app.util.PersianDigits
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Bell with an unread dot; opens the in-app notification list. */
@Composable
fun NotificationBell(vm: AppViewModel, onClick: () -> Unit) {
    val notices by vm.notices.collectAsState()
    val inbox by vm.inboxItems.collectAsState()
    val unread = notices.count { !it.read } + inbox.size
    IconButton(onClick = onClick, modifier = Modifier.size(48.dp).testTag("open_notifications")) {
        Box {
            MahavaIcon(R.drawable.ic_bell, MahavaTextPrimary)
            if (unread > 0) {
                Box(Modifier.size(9.dp).clip(CircleShape).background(MahavaDanger).align(Alignment.TopEnd))
            }
        }
    }
}

private fun timeFa(ms: Long): String {
    val zone = ZoneId.systemDefault()
    val t = Instant.ofEpochMilli(ms).atZone(zone)
    val days = java.time.LocalDate.now(zone).toEpochDay() - t.toLocalDate().toEpochDay()
    val hm = PersianDigits.toPersian(t.format(DateTimeFormatter.ofPattern("HH:mm")))
    return when (days) {
        0L -> "امروز $hm"
        1L -> "دیروز $hm"
        else -> "${PersianDigits.toPersian(days.toInt())} روز پیش"
    }
}

/** In-app notifications: pairing news, her updates (husband) and messages from the team. */
@Composable
fun NotificationsScreen(vm: AppViewModel, onBack: () -> Unit, onOpenPartner: () -> Unit) {
    val notices by vm.notices.collectAsState()
    val inbox by vm.inboxItems.collectAsState()
    LaunchedEffect(Unit) { vm.refreshInbox() }
    // Opening the list marks everything as read (after it was shown once).
    LaunchedEffect(notices.size) { delay(1500); vm.markNoticesRead() }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag("notifications_screen")) {
        ScreenHeader("اعلان‌ها", onBack = onBack)
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (notices.isEmpty() && inbox.isEmpty()) {
                MahavaCard { Text("اعلانی نداری.", style = MaterialTheme.typography.bodyLarge) }
            }
            inbox.forEach { item ->
                MahavaCard(Modifier.testTag("inbox_item")) {
                    Text(item.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MahavaPrimary)
                    Text(item.body, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(6.dp))
                    SecondaryButton("بستن") { vm.dismissInboxItem(item.id) }
                }
            }
            notices.forEach { n ->
                MahavaCard(Modifier.testTag("notice_${n.kind}")) {
                    Text(n.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                        color = if (n.read) MahavaTextPrimary else MahavaPrimary)
                    Text(n.body, style = MaterialTheme.typography.bodyMedium)
                    QuietInfo(timeFa(n.time))
                    if (n.kind.startsWith("pair") || n.kind == "update" || n.kind == "daily") {
                        TextButton(onClick = onOpenPartner) { Text("باز کردن", color = MahavaPrimary) }
                    }
                }
            }
            if (notices.isNotEmpty()) {
                TextButton(onClick = { vm.clearNotices() }) { Text("پاک کردن همه", color = MahavaDanger) }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/**
 * App-wide, in-app approvals and alerts: her partner-request approval dialog and one-time
 * popups for pairing/unpairing news. Also polls the partner feed while the app is open.
 */
@Composable
fun AppOverlays(vm: AppViewModel, active: Boolean, onOpenPartner: () -> Unit) {
    if (!active) return
    val status by vm.partnerStatus.collectAsState()
    val notices by vm.notices.collectAsState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        while (true) {
            val wait = vm.partnerForegroundPoll()
            delay(wait)
        }
    }

    val pair = status?.pair
    var hiddenRequest by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    if (pair != null && pair.status == "pending" && pair.myRole == "female" && hiddenRequest != pair.id) {
        val phone = PersianDigits.toPersian(pair.partner?.phoneMasked.orEmpty())
        AlertDialog(
            onDismissRequest = { if (!busy) hiddenRequest = pair.id },
            title = { Text("درخواست اتصال همسر") },
            text = {
                Column {
                    Text("همسرت${if (phone.isNotBlank()) " ($phone)" else ""} می‌خواهد به تو وصل شود.")
                    Spacer(Modifier.height(6.dp))
                    Text(PARTNER_APPROVE_FA, style = MaterialTheme.typography.bodyMedium)
                    error?.let { Spacer(Modifier.height(6.dp)); Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(enabled = !busy, modifier = Modifier.testTag("dialog_partner_approve"), onClick = {
                    busy = true; error = null
                    scope.launch {
                        error = vm.partnerRespond(pair.id, true)
                        busy = false
                        if (error == null) vm.noteInApp("pair_approved_self", "اتصال برقرار شد", "به همسرت وصل شدی. او حالا ثبت‌هایت را می‌بیند.")
                    }
                }) { Text(if (busy) "صبر کن…" else "تأیید", color = MahavaPrimary, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(enabled = !busy, modifier = Modifier.testTag("dialog_partner_reject"), onClick = {
                    busy = true; error = null
                    scope.launch { error = vm.partnerRespond(pair.id, false); busy = false }
                }) { Text("رد", color = MahavaDanger) }
            }
        )
        return
    }

    val popup = notices.firstOrNull { it.popup }
    if (popup != null) {
        AlertDialog(
            onDismissRequest = { vm.dismissNoticePopup(popup.id) },
            title = { Text(popup.title) },
            text = { Text(popup.body) },
            confirmButton = {
                TextButton(modifier = Modifier.testTag("dialog_notice_ok"), onClick = {
                    vm.dismissNoticePopup(popup.id)
                    if (popup.kind == "pair_approved") onOpenPartner()
                }) { Text("باشه", color = MahavaPrimary, fontWeight = FontWeight.Bold) }
            }
        )
    }
}
