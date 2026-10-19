package com.mahava.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mahava.app.cycle.PredictionKind
import com.mahava.app.ui.AppViewModel
import com.mahava.app.ui.components.*
import com.mahava.app.ui.theme.*
import com.mahava.app.util.JalaliDate
import com.mahava.app.util.PersianDigits
import java.time.LocalDate

@Composable
fun CalendarScreen(vm: AppViewModel, onSettings: () -> Unit, onAccount: () -> Unit = {}, onOpenDay: (LocalDate) -> Unit) {
    val state by vm.state.collectAsState()
    val toolRecords by vm.toolRecords.collectAsState()
    val premium by vm.isPremium.collectAsState()
    val useJalali = state.profile?.calendarType != "gregorian"
    val today = vm.today()
    // Month anchor = first day of the shown Jalali (or Gregorian) month.
    var monthAnchor by androidx.compose.runtime.saveable.rememberSaveable(useJalali) { mutableStateOf(monthStart(today, useJalali)) }
    var selected by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(today) }
    val pregnancy = state.health?.appMode == com.mahava.app.health.models.AppHealthMode.PREGNANCY
    val periodDays = remember(state.periods, today) {
        buildSet {
            state.periods.forEach { p ->
                val start = p.startEpochDay
                val end = p.endEpochDay ?: if (p.stillOngoing) today.toEpochDay() else start
                for (d in start..end) add(d)
            }
        }
    }
    val predictedPeriod = mutableSetOf<Long>()
    val predictedFertile = mutableSetOf<Long>()
    val pred = state.cycle?.prediction
    if (!pregnancy && pred is PredictionKind.Estimate) {
        val bleed = (state.profile?.typicalBleedLength ?: 5).coerceIn(2, 10)
        val cycleLen = pred.medianCycleLength.coerceIn(15, 90)
        val horizon = today.plusMonths(6)
        // Uncertainty band for the next period start, each expanded to full estimated bleed days.
        var startCursor = pred.nextPeriodStartEarliest
        while (!startCursor.isAfter(pred.nextPeriodStartLatest)) {
            for (b in 0 until bleed) {
                val day = startCursor.plusDays(b.toLong())
                if (!day.isBefore(today) && !day.isAfter(horizon)) predictedPeriod += day.toEpochDay()
            }
            startCursor = startCursor.plusDays(1)
        }
        // Subsequent cycles: full estimated period blocks from the central prediction.
        var cycle = 1
        while (true) {
            val cycleStart = pred.nextPeriodStartCentral.plusDays((cycleLen.toLong() * cycle))
            if (cycleStart.isAfter(horizon)) break
            for (b in 0 until bleed) {
                val day = cycleStart.plusDays(b.toLong())
                if (!day.isBefore(today) && !day.isAfter(horizon)) predictedPeriod += day.toEpochDay()
            }
            // Fertility window for later cycles (anchored ~14 days before next period).
            val fertEnd = cycleStart.minusDays(12)
            val fertStart = cycleStart.minusDays(19)
            var f = fertStart
            while (!f.isAfter(fertEnd)) {
                if (!f.isBefore(today) && !f.isAfter(horizon)) predictedFertile += f.toEpochDay()
                f = f.plusDays(1)
            }
            cycle++
            if (cycle > 8) break
        }
        // Current / nearest fertility estimate from the engine.
        var f = pred.fertilityEarliest
        while (!f.isAfter(pred.fertilityLatest)) {
            if (!f.isBefore(today) && !f.isAfter(horizon)) predictedFertile += f.toEpochDay()
            f = f.plusDays(1)
        }
        // Period markers win over fertility on the same day.
        predictedFertile.removeAll(predictedPeriod)
        predictedFertile.removeAll(periodDays)
    }

    val days = monthDays(monthAnchor, useJalali)
    val k = com.mahava.app.ui.kit.Kit
    Column(Modifier.fillMaxSize().background(MahavaBackground).verticalScroll(com.mahava.app.ui.kit.rememberKitScroll()).testTag("calendar_screen")) {
        // A-main-screens «تقویم»: centred title, month switcher, full weekday names, open grid (no card).
        com.mahava.app.ui.kit.KitHeader("تقویم")
        Row(Modifier.fillMaxWidth().padding(horizontal = 60.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(CircleShape).clickable { monthAnchor = shiftMonth(monthAnchor, -1, useJalali) }.testTag("cal_prev"), contentAlignment = Alignment.Center) {
                MahavaIcon(com.mahava.app.R.drawable.kit_chevron_right, k.ink, 20.dp)
            }
            Text(monthTitle(monthAnchor, useJalali), style = k.cardTitle, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.weight(1f).testTag("cal_month_title"))
            Box(Modifier.size(40.dp).clip(CircleShape).clickable { monthAnchor = shiftMonth(monthAnchor, 1, useJalali) }.testTag("cal_next"), contentAlignment = Alignment.Center) {
                MahavaIcon(com.mahava.app.R.drawable.kit_chevron_left, k.ink, 20.dp)
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
            listOf("شنبه", "یکشنبه", "دوشنبه", "سه‌شنبه", "چهارشنبه", "پنجشنبه", "جمعه").forEach {
                Text(it, modifier = Modifier.weight(1f), style = k.small.copy(fontSize = 11.sp), maxLines = 1, softWrap = false,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
        days.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp)) {
                week.forEach { cell ->
                    val (day, inMonth) = cell
                    val epoch = day.toEpochDay()
                    val actual = !pregnancy && epoch in periodDays
                    val predP = epoch in predictedPeriod && !actual
                    val fert = epoch in predictedFertile && !actual
                    DayCell(day, useJalali, inMonth, actual, predP, fert, day == today, day == selected, Modifier.weight(1f), indicators = vm.logFor(day)?.let { log -> buildList {
                        if(log.painScore!=null) add("درد")
                        if(log.sleepMinutes!=null || log.sleepHours!=null) add("خواب")
                        if(log.moods!=null || log.overallFeeling!=null) add("حال")
                        if(log.energyScore!=null || log.energy!=null) add("انرژی")
                        if(log.discharge!=null) add("ترشحات")
                        if(log.medicationNote!=null) add("دارو")
                        if(log.note!=null) add("یادداشت")
                        if(log.intimacyLogged) add("رابطه")
                    } }.orEmpty()) { selected = day; if (!inMonth) monthAnchor = monthStart(day, useJalali) }
                }
            }
        }
        Legend(pregnancy)
        Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val sel = selected
            val selLog = vm.logFor(sel)
            val cycleDayOfSel = state.periods.filter { it.startEpochDay <= sel.toEpochDay() }.maxByOrNull { it.startEpochDay }?.let { period ->
                val ls = LocalDate.ofEpochDay(period.startEpochDay)
                val d = (sel.toEpochDay() - ls.toEpochDay() + 1).toInt()
                if (d >= 1 && !sel.isAfter(today)) d else null
            }
            Column(
                Modifier.fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(k.cardRadius)).background(k.cardLavender)
                    .padding(16.dp).testTag("cal_selected_card"),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        (if (sel == today) "امروز " else "") + "${JalaliDate.weekdayNameFa(sel)} ${JalaliDate.from(sel).formatShortFa()}",
                        style = k.cardTitle, modifier = Modifier.weight(1f).testTag("cal_selected_date")
                    )
                    MahavaIcon(com.mahava.app.R.drawable.kit_chevron_left, k.muted, 18.dp)
                }
                @Composable fun line(icon: Int, tint: Color, text: String) = Row(verticalAlignment = Alignment.CenterVertically) {
                    MahavaIcon(icon, tint, 20.dp); Spacer(Modifier.width(10.dp)); Text(text, style = k.body.copy(color = k.ink))
                }
                if (pregnancy) {
                    state.health?.pregnancy?.let { p ->
                        val age = com.mahava.app.health.PregnancyService.progressFromEntity(p, sel)
                        if (!sel.isBefore(LocalDate.ofEpochDay(p.estimatedDueEpochDay).minusDays(280))) line(com.mahava.app.R.drawable.kit_pregnancy, k.pink, age.labelFa)
                    }
                } else cycleDayOfSel?.let { line(com.mahava.app.R.drawable.kit_calendar, k.lavender, "روز ${PersianDigits.toPersian(it)} چرخه") }
                when {
                    !pregnancy && sel.toEpochDay() in periodDays -> line(com.mahava.app.R.drawable.kit_water, k.pink, "پریود ثبت‌شده")
                    sel.toEpochDay() in predictedPeriod -> line(com.mahava.app.R.drawable.kit_water, k.pink, "ممکنه پریود باشی؛ این فقط تخمینه")
                    sel.toEpochDay() in predictedFertile -> line(com.mahava.app.R.drawable.kit_leaf, MahavaFertility, "ممکنه نزدیک بازه باروری باشی؛ این فقط تخمینه")
                }
                if (!pregnancy && sel == today) state.health?.currentCycleSummary?.nextPeriodLabelFa?.let { line(com.mahava.app.R.drawable.kit_clock, k.lavender, it) }
                val appointments=toolRecords.filter {it.type=="appointments" && it.deletedAt==null && it.epochDay==sel.toEpochDay()}
                appointments.forEach { appointment -> line(com.mahava.app.R.drawable.kit_bell, k.lavender, "مراجعه: " + com.google.gson.JsonParser.parseString(appointment.payloadJson).asJsonObject.get("text").asString) }
                if (premium) {
                    selLog?.let { log ->
                        val markers=buildList {
                            com.mahava.app.health.ToolsAnalytics.sleep(log)?.let { m -> add("خواب: " + PersianDigits.toPersian(if (m % 60 == 0) "${m / 60} ساعت" else "${m / 60} ساعت و ${m % 60} دقیقه")) }
                            com.mahava.app.health.ToolsAnalytics.energy(log)?.let {add("انرژی: ${PersianDigits.toPersian(it)} از ۵")}
                            log.overallFeeling?.let { f -> com.mahava.app.health.LogLabels.FEELINGS.firstOrNull { it.first == f }?.let { add("حس کلی: ${it.second}") } }
                        }
                        if (markers.isNotEmpty()) line(com.mahava.app.R.drawable.kit_check, k.lavender, markers.joinToString(" · "))
                    }
                    selLog?.note?.takeIf { it.isNotBlank() }?.let { line(com.mahava.app.R.drawable.kit_book, k.lavender, "یادداشت: $it") }
                    if (sel.isAfter(today)) QuietInfo("برای روزهای آینده نمی‌شه چیزی ثبت کرد؛ فقط تخمین‌ها رو می‌بینی.")
                    else com.mahava.app.ui.kit.KitPillButton(
                        if (sel == today && selLog == null) "ثبت علائم امروز" else if (selLog == null) "ثبت برای این روز" else "ویرایش ثبت این روز",
                        modifier = Modifier.testTag("cal_edit_day")
                    ) { onOpenDay(sel) }
                } else {
                    QuietInfo("ثبت حال روزانه با اشتراک ماه باز می‌شه. دیدن تقویم و پیش‌بینی پریود رایگانه.")
                    com.mahava.app.ui.kit.KitPillButton("حساب و اشتراک", modifier = Modifier.testTag("cal_account"), icon = null, onClick = onAccount)
                }
            }
            if (sel != today || monthAnchor != monthStart(today, useJalali)) {
                Text("برگشت به امروز", style = k.label.copy(color = MahavaPrimaryDeep),
                    modifier = Modifier.align(Alignment.CenterHorizontally).clip(CircleShape).clickable { monthAnchor = monthStart(today, useJalali); selected = today }
                        .padding(horizontal = 16.dp, vertical = 12.dp).testTag("cal_today"))
            }
            if (!pregnancy) Text("روزهای تخمینی فقط پیش‌بینی‌ان و هیچ‌وقت پریود واقعی ثبت نمی‌شن؛ برای پیشگیری از بارداری بهشون تکیه نکن.",
                style = k.small, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
    }
}

private fun monthStart(d: LocalDate, jalali: Boolean): LocalDate =
    if (jalali) { val j = JalaliDate.from(d); d.minusDays((j.day - 1).toLong()) } else d.withDayOfMonth(1)

private fun shiftMonth(anchor: LocalDate, delta: Int, jalali: Boolean): LocalDate =
    if (!jalali) anchor.plusMonths(delta.toLong()).withDayOfMonth(1)
    else if (delta > 0) monthStart(anchor.plusDays(32), true)
    else monthStart(anchor.minusDays(1), true)

private fun monthLength(anchor: LocalDate, jalali: Boolean): Int =
    if (!jalali) anchor.lengthOfMonth()
    else { val j = JalaliDate.from(anchor); JalaliDate.daysInMonth(j.year, j.month) }

private val GREG_MONTHS_FA = listOf("ژانویه","فوریه","مارس","آوریل","مه","ژوئن","ژوئیه","اوت","سپتامبر","اکتبر","نوامبر","دسامبر")

private fun monthTitle(anchor: LocalDate, jalali: Boolean): String =
    if (jalali) { val j = JalaliDate.from(anchor); "${JalaliDate.monthNameFa(j.month)} ${PersianDigits.toPersian(j.year)}" }
    else "${GREG_MONTHS_FA[anchor.monthValue - 1]} ${PersianDigits.toPersian(anchor.year)}"

@Composable private fun DayCell(
    day: LocalDate, jalali: Boolean, inMonth: Boolean, actual: Boolean, pred: Boolean, fert: Boolean, isToday: Boolean, isSelected: Boolean, modifier: Modifier = Modifier, indicators: List<String> = emptyList(), onClick: () -> Unit
) {
    val k = com.mahava.app.ui.kit.Kit
    val label = if (jalali) PersianDigits.toPersian(JalaliDate.from(day).day) else PersianDigits.toPersian(day.dayOfMonth)
    Box(
        modifier.height(54.dp).clip(CircleShape).clickable(onClick = onClick)
            .then(if (inMonth) Modifier.testTag("day_${day}") else Modifier)
            .then(Modifier.semantics { contentDescription = "${JalaliDate.from(day).formatFa()}، " + when { actual -> "پریود ثبت‌شده"; pred -> "پریود تخمینی"; fert -> "باروری تخمینی"; else -> "بدون نشانگر" } + (if (isToday) "، امروز" else "") + indicators.joinToString(prefix="، ") }),
        contentAlignment = Alignment.Center
    ) {
        val style = k.label.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium, fontSize = 16.sp)
        when {
            isToday || isSelected && inMonth -> Box(
                Modifier.size(44.dp).background(k.pink.copy(alpha = if (isToday) .18f else .0f), CircleShape).padding(4.dp)
                    .background(if (isToday) k.pink else k.softPink, CircleShape)
                    .then(if (!isToday) Modifier.border(1.5.dp, k.pink, CircleShape) else Modifier),
                contentAlignment = Alignment.Center
            ) { Text(label, style = style.copy(color = if (isToday) Color.White else k.pink)) }
            actual -> Box(Modifier.size(40.dp).background(k.pink, CircleShape), contentAlignment = Alignment.Center) {
                Text(label, style = style.copy(color = Color.White))
            }
            pred -> Box(Modifier.size(42.dp).background(Color(0xFFFCE4EE), CircleShape), contentAlignment = Alignment.Center) {
                Text(label, style = style.copy(color = k.pink), modifier = Modifier.offset(y = (-3).dp))
                Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp).size(5.dp).background(k.pink, CircleShape))
            }
            else -> Text(label, style = style.copy(color = if (inMonth) k.ink else Color(0xFFC3C3D0)))
        }
        if (fert && !isToday) Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 2.dp).size(6.dp).background(k.lavender.copy(alpha = .8f), CircleShape))
    }
}

@Composable private fun Legend(pregnancy: Boolean) {
    if (pregnancy) return
    val k = com.mahava.app.ui.kit.Kit
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        listOf(k.pink to "روزهای پریود", Color(0xFFFCD3E2) to "پریود تخمینی", k.lavender.copy(alpha = .8f) to "روزهای باروری").forEach { (c, t) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(14.dp).background(c, CircleShape)); Spacer(Modifier.width(6.dp)); Text(t, style = k.small)
            }
        }
    }
}

private fun monthDays(anchor: LocalDate, jalali: Boolean): List<Pair<LocalDate, Boolean>> {
    // Saturday-first grid (Iranian week); the neighbouring months' days fill the first/last week (faded).
    val blank = when (anchor.dayOfWeek.value) {
        6 -> 0; 7 -> 1; 1 -> 2; 2 -> 3; 3 -> 4; 4 -> 5; 5 -> 6; else -> 0
    }
    val list = mutableListOf<Pair<LocalDate, Boolean>>()
    for (i in blank downTo 1) list += anchor.minusDays(i.toLong()) to false
    val n = monthLength(anchor, jalali)
    for (i in 0 until n) list += anchor.plusDays(i.toLong()) to true
    var extra = 0L
    while (list.size % 7 != 0) list += anchor.plusDays(n + extra++) to false
    return list
}
