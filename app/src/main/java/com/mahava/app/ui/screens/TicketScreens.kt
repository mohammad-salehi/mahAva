package com.mahava.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.mahava.app.network.MahTicketDto
import com.mahava.app.ui.AppViewModel
import com.mahava.app.ui.components.*
import com.mahava.app.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun TicketsScreen(vm: AppViewModel, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var tickets by remember { mutableStateOf<List<MahTicketDto>>(emptyList()) }
    var selected by remember { mutableStateOf<MahTicketDto?>(null) }
    var subject by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var reply by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }

    fun refresh(keepSelectedId: String? = selected?.id) {
        scope.launch {
            loading = true
            try {
                tickets = vm.loadTickets()
                selected = keepSelectedId?.let { id -> tickets.find { it.id == id } }
            } catch (t: Throwable) {
                status = t.message ?: "بارگذاری تیکت‌ها ناموفق بود"
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(Unit) { refresh(null) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).testTag("tickets_screen")) {
        ScreenHeader("پشتیبانی و تیکت", onBack = onBack)
        QuietInfo("سؤال یا مشکل‌ات را بنویس؛ تیم ماه از پنل ادمین جواب می‌دهد.")
        if (status.isNotBlank()) Text(status, color = MahavaPrimary, modifier = Modifier.padding(vertical = 6.dp))

        if (selected == null) {
            MahavaCard {
                SectionLabel("تیکت جدید")
                OutlinedTextField(subject, { subject = it }, modifier = Modifier.fillMaxWidth(), label = { Text("موضوع") }, singleLine = true)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(body, { body = it }, modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp), label = { Text("متن") })
                Spacer(Modifier.height(10.dp))
                PrimaryButton("ارسال تیکت", enabled = subject.isNotBlank() && body.isNotBlank() && !loading) {
                    scope.launch {
                        status = "داره ارسال می‌شه…"
                        val err = vm.createTicket(subject.trim(), body.trim())
                        if (err == null) {
                            subject = ""; body = ""; status = "تیکت ثبت شد"
                            refresh(null)
                        } else status = err
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            SectionLabel("تیکت‌های قبلی")
            if (loading && tickets.isEmpty()) QuietInfo("در حال بارگذاری…")
            if (!loading && tickets.isEmpty()) QuietInfo("هنوز تیکتی نداری.")
            tickets.forEach { t ->
                MahavaCard(Modifier.clickable { selected = t }) {
                    Text(t.subject, style = MaterialTheme.typography.titleMedium)
                    QuietInfo(
                        when (t.status) {
                            "answered" -> "پاسخ داده شده"
                            "closed" -> "بسته"
                            else -> "باز"
                        }
                    )
                }
                Spacer(Modifier.height(8.dp))
            }
        } else {
            val t = selected!!
            SecondaryButton("بازگشت به فهرست") { selected = null; reply = "" }
            Spacer(Modifier.height(8.dp))
            MahavaCard {
                Text(t.subject, style = MaterialTheme.typography.headlineLarge)
                QuietInfo(
                    when (t.status) {
                        "answered" -> "وضعیت: پاسخ داده شده"
                        "closed" -> "وضعیت: بسته"
                        else -> "وضعیت: باز"
                    }
                )
            }
            Spacer(Modifier.height(8.dp))
            t.messages.orEmpty().forEach { m ->
                MahavaCard(containerColor = if (m.by == "admin") MahavaPrimarySoft else Color(0xFFF4F0F8)) {
                    Text(if (m.by == "admin") "پاسخ پشتیبانی" else "تو", color = MahavaPrimaryDeep)
                    Text(m.body)
                }
                Spacer(Modifier.height(6.dp))
            }
            if (t.status != "closed") {
                MahavaCard {
                    OutlinedTextField(reply, { reply = it }, modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp), label = { Text("پاسخ تو") })
                    Spacer(Modifier.height(8.dp))
                    PrimaryButton("ارسال پاسخ", enabled = reply.isNotBlank()) {
                        scope.launch {
                            val err = vm.replyTicket(t.id, reply.trim())
                            if (err == null) {
                                reply = ""
                                status = "پاسخ ارسال شد"
                                refresh(t.id)
                            } else status = err
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}
