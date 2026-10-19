package com.mahava.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mahava.app.R
import com.mahava.app.ui.AppViewModel
import com.mahava.app.ui.components.*
import com.mahava.app.ui.theme.*
import com.mahava.app.util.JalaliDate
import java.time.LocalDate
import kotlinx.coroutines.launch

enum class PregnancyPart { HERO, REST }

@Composable
fun PregnancyCompanion(vm: AppViewModel, onPregnancy: () -> Unit, onSetup: () -> Unit, onDaily: () -> Unit, onInsight: (String) -> Unit, part: PregnancyPart = PregnancyPart.HERO) {
    val state by vm.state.collectAsState()
    val tools by vm.toolRecords.collectAsState()
    val scope = rememberCoroutineScope()
    val health = state.health
    val p = health?.pregnancyProgress
    if (part == PregnancyPart.HERO) MahavaCard(containerColor = MahavaLavenderSoft) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            HomeOrbit(p?.completedWeeks, "هفتهٔ بارداری",
                p?.progressFraction)
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(p?.labelFa ?: "همراه بارداری تو", style = MaterialTheme.typography.headlineLarge)
                if (p != null) {
                    Text(when { p.completedWeeks < 13 -> "سه‌ماههٔ اول"; p.completedWeeks < 28 -> "سه‌ماههٔ دوم"; else -> "سه‌ماههٔ سوم" }, color = MahavaPrimaryDeep)
                    Text("زایمان حدودی: ${JalaliDate.from(LocalDate.parse(p.estimatedDueDate)).formatShortFa()}")
                    QuietInfo(if (health.pregnancy?.datingMethod == "clinician_edd") "مبنا: تاریخ ثبت‌شده از پزشک" else "مبنا: اولین روز آخرین پریود")
                } else QuietInfo("برای حساب کردن هفتهٔ بارداریت، تاریخ مبنا رو ثبت کن.")
            }
            Illustration(R.drawable.redesign_pregnancy, Modifier.width(90.dp).height(130.dp))
        }
        if (p != null) LinearProgressIndicator(progress = { p.progressFraction }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp).height(6.dp), color = MahavaMenstruation, trackColor = MahavaSurface)
        TextButton(onClick = onSetup) { Text("تنظیم تاریخ مبنا") }
    }
    if (p == null || part == PregnancyPart.HERO) return
    val week = p.contentWeek.coerceAtMost(42)
    val fetal = vm.fetalWeek(week)
    MahavaCard(Modifier.clickable { onInsight("preg_week_$week") }) {
        SectionLabel("این هفته، کوچولوی تو")
        Text(fetal?.growthFa ?: "برای این هفته هنوز توضیحی نداریم؛ برنامهٔ پزشکت رو دنبال کن.")
        HealthContextNote(HealthNote.FETAL_GROWTH)
        MoreLink("بیشتر بدون")
    }
    Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf(Triple("mother", "برای مادر", R.drawable.design_mother), Triple("father", "برای پدر", R.drawable.design_father)).forEach { (kind, label, image) ->
            MahavaCard(Modifier.weight(1f).fillMaxHeight().clickable { onInsight("preg_${kind}_$week") }, containerColor = if (kind == "mother") MahavaMenstruationSoft else MahavaPrimarySoft) {
                Illustration(image, Modifier.height(65.dp))
                Text(label, style = MaterialTheme.typography.titleMedium)
                QuietInfo(if (kind == "mother") "مراقبت و حال خوب" else "چطور همراهش باشم؟")
            }
        }
    }
    MahavaCard {
        SectionLabel("کارهای مهم این هفته")
        val checklistId="pregnancy_checklist_${p.estimatedDueDate.replace("-", "")}_$week"
        val record=tools.find {it.id==checklistId && it.deletedAt==null}
        val fields=record?.let { com.google.gson.JsonParser.parseString(it.payloadJson).asJsonObject } ?: com.google.gson.JsonObject()
        listOf("care" to "برنامهٔ مراقبت این هفته رو مرور کردم", "questions" to "سؤال‌هام برای پزشک یا ماما رو نوشتم").forEach { (key,label) ->
            Row(verticalAlignment=Alignment.CenterVertically) {
                Checkbox(fields.get(key)?.asBoolean==true,{checked -> scope.launch {
                    fields.addProperty(key,checked)
                    vm.tools.save(checklistId,"pregnancy_checklist",fields,vm.today().toEpochDay())
                }})
                Text(label,modifier=Modifier.weight(1f))
            }
        }
        tools.filter {it.type=="appointments" && it.deletedAt==null && (it.epochDay ?: 0)>=vm.today().toEpochDay()}.take(3).forEach { item ->
            val appointment=com.google.gson.JsonParser.parseString(item.payloadJson).asJsonObject
            QuietInfo("مراجعهٔ ثبت‌شده: ${appointment.get("text")?.asString.orEmpty()} — ${com.mahava.app.util.DateFa.iso(appointment.get("date")?.asString.orEmpty())}")
        }
        CompanionAction("مرور برنامهٔ مراقبت‌ها", "وقت مراجعه‌هات رو با پزشک یا ماما هماهنگ کن.", R.drawable.design_calendar, onPregnancy)
        CompanionAction("ثبت علائم امروز", "علائم مهم رو به‌موقع ثبت و پیگیری کن.", R.drawable.design_notes, onDaily)
        HealthContextNote(HealthNote.GENERAL)
    }
}
