package com.mahava.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    val premium by vm.isPremium.collectAsState()
    val useJalali = state.profile?.calendarType != "gregorian"
    val today = vm.today()
    // Month anchor = first day of the shown Jalali (or Gregorian) month.
    var monthAnchor by remember { mutableStateOf(monthStart(today, useJalali)) }
    var selected by remember { mutableStateOf(today) }
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
    if (pred is PredictionKind.Estimate) {
        var d = pred.nextPeriodStartEarliest
        while (!d.isAfter(pred.nextPeriodStartLatest)) {
            if (d.isAfter(today)) predictedPeriod += d.toEpochDay()
            d = d.plusDays(1)
        }
        if (premium && state.cycle?.showFertility == true) {
            var f = pred.fertilityEarliest
            while (!f.isAfter(pred.fertilityLatest)) {
                if (!f.isBefore(today)) predictedFertile += f.toEpochDay()
                f = f.plusDays(1)
            }
        }
    }

    val days = monthDays(monthAnchor, useJalali)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag("calendar_screen")) {
        ScreenHeader("تقویم", onSettings = onSettings)
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            androidx.compose.material3.IconButton(onClick = { monthAnchor = shiftMonth(monthAnchor, -1, useJalali) }, modifier = Modifier.testTag("cal_prev")) {
                MahavaIcon(com.mahava.app.R.drawable.ic_chevron_right)
            }
            Text(monthTitle(monthAnchor, useJalali), style = MaterialTheme.typography.titleLarge, modifier = Modifier.testTag("cal_month_title"))
            androidx.compose.material3.IconButton(onClick = { monthAnchor = shiftMonth(monthAnchor, 1, useJalali) }, modifier = Modifier.testTag("cal_next")) {
                MahavaIcon(com.mahava.app.R.drawable.ic_chevron_left)
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("ش","ی","د","س","چ","پ","ج").forEach {
                Text(it, modifier = Modifier.width(40.dp), color = MahavaTextSecondary, fontSize = 13.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
        days.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                week.forEach { day ->
                    if (day == null) Box(Modifier.size(40.dp))
                    else {
                        val epoch = day.toEpochDay()
                        val actual = epoch in periodDays
                        val predP = epoch in predictedPeriod && !actual
                        val fert = epoch in predictedFertile && !actual
                        DayCell(day, useJalali, actual, predP, fert, day == today, day == selected) { selected = day }
                    }
                }
            }
        }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Legend()
            val sel = selected
            val selLog = vm.logFor(sel)
            val cycleDayOfSel = state.cycle?.lastPeriodStart?.let { ls ->
                val d = (sel.toEpochDay() - ls.toEpochDay() + 1).toInt()
                if (d >= 1 && !sel.isAfter(today)) d else null
            }
            MahavaCard(Modifier.testTag("cal_selected_card")) {
                Text(
                    "${JalaliDate.weekdayNameFa(sel)}، ${JalaliDate.from(sel).formatFa()}",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.testTag("cal_selected_date")
                )
                cycleDayOfSel?.let { QuietInfo("روز ${PersianDigits.toPersian(it)} چرخه") }
                when {
                    sel.toEpochDay() in periodDays -> Text("پریود ثبت‌شده", color = MahavaMenstruation)
                    sel.toEpochDay() in predictedPeriod -> Text("پریود احتمالی (تخمینی)", color = MahavaMenstruation)
                    sel.toEpochDay() in predictedFertile -> Text("روز احتمالی باروری (تخمینی)", color = MahavaFertility)
                }
                if (premium) {
                    Text(todayLogLine(selLog).replace("امروز", "این روز").replace("ثبت امروزِ تو", "ثبت این روز"), style = MaterialTheme.typography.bodyMedium)
                    selLog?.note?.takeIf { it.isNotBlank() }?.let { Text("یادداشت: $it", style = MaterialTheme.typography.bodyMedium) }
                    Spacer(Modifier.height(8.dp))
                    if (sel.isAfter(today)) QuietInfo("برای روزهای آینده نمی‌توان چیزی ثبت کرد؛ فقط تخمین‌ها نشان داده می‌شوند.")
                    else SecondaryButton(if (selLog == null) "ثبت برای این روز" else "ویرایش ثبت این روز", modifier = Modifier.testTag("cal_edit_day")) { onOpenDay(sel) }
                } else {
                    Spacer(Modifier.height(8.dp))
                    QuietInfo("ثبت حال روزانه با اشتراک ماه باز می‌شه. دیدن تقویم و پیش‌بینی پریود رایگان است.")
                    SecondaryButton("حساب و اشتراک", modifier = Modifier.testTag("cal_account"), onClick = onAccount)
                }
            }
            SecondaryButton("برگشت به امروز", modifier = Modifier.testTag("cal_today")) { monthAnchor = monthStart(today, useJalali); selected = today }
            QuietInfo("روزهای تخمینی هیچ‌وقت به‌عنوان پریود واقعی ذخیره نمی‌شوند.")
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
    day: LocalDate, jalali: Boolean, actual: Boolean, pred: Boolean, fert: Boolean, isToday: Boolean, isSelected: Boolean, onClick: () -> Unit
) {
    val label = if (jalali) PersianDigits.toPersian(JalaliDate.from(day).day) else PersianDigits.toPersian(day.dayOfMonth)
    Box(
        Modifier.size(40.dp)
            .then(if (isSelected) Modifier.border(2.dp, MahavaPrimary, CircleShape) else Modifier)
            .clickable(onClick = onClick)
            .testTag("day_${day}"),
        contentAlignment = Alignment.Center
    ) {
        when {
            actual -> Box(Modifier.size(34.dp).background(MahavaMenstruation, CircleShape), contentAlignment = Alignment.Center) {
                Text(label, color = Color.White, fontSize = 13.sp)
            }
            pred -> DashedCircle(MahavaMenstruation) { Text(label, fontSize = 13.sp, color = MahavaTextPrimary) }
            fert -> DashedCircle(MahavaFertility) { Text(label, fontSize = 13.sp, color = MahavaTextPrimary) }
            isToday -> Box(Modifier.size(34.dp).background(MahavaFertility, CircleShape), contentAlignment = Alignment.Center) {
                Text(label, color = Color.White, fontSize = 13.sp)
            }
            else -> Text(label, fontSize = 13.sp, color = MahavaTextPrimary)
        }
    }
}

@Composable private fun DashedCircle(color: Color, content: @Composable () -> Unit) {
    Box(Modifier.size(34.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(
                color = color,
                style = Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f)))
            )
        }
        content()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun Legend() {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("● پریود ثبت‌شده", color = MahavaMenstruation, fontSize = 12.sp)
        Text("◌ پریود تخمینی", color = MahavaMenstruation, fontSize = 12.sp)
        Text("◌ باروری تخمینی", color = MahavaFertility, fontSize = 12.sp)
    }
}

private fun monthDays(anchor: LocalDate, jalali: Boolean): List<LocalDate?> {
    // Saturday-first grid (Iranian week).
    val blank = when (anchor.dayOfWeek.value) {
        6 -> 0; 7 -> 1; 1 -> 2; 2 -> 3; 3 -> 4; 4 -> 5; 5 -> 6; else -> 0
    }
    val list = MutableList<LocalDate?>(blank) { null }
    for (i in 0 until monthLength(anchor, jalali)) list += anchor.plusDays(i.toLong())
    while (list.size % 7 != 0) list += null
    return list
}
