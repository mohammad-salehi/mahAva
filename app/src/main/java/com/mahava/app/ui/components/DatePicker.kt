package com.mahava.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mahava.app.R
import com.mahava.app.ui.theme.*
import com.mahava.app.util.JalaliDate
import com.mahava.app.util.PersianDigits
import java.time.LocalDate

/** True = show dates in the Shamsi (Jalali) calendar; false = Gregorian. Provided from the user's setting. */
val LocalUseJalali = staticCompositionLocalOf { true }

/** Pure month/grid math shared by the date picker (unit-tested). */
object DatePickerMath {
    val GREG_MONTHS_FA = listOf("ژانویه", "فوریه", "مارس", "آوریل", "مه", "ژوئن", "ژوئیه", "اوت", "سپتامبر", "اکتبر", "نوامبر", "دسامبر")
    val WEEKDAYS_FA = listOf("ش", "ی", "د", "س", "چ", "پ", "ج") // Saturday first

    fun monthStart(d: LocalDate, jalali: Boolean): LocalDate =
        if (jalali) d.minusDays((JalaliDate.from(d).day - 1).toLong()) else d.withDayOfMonth(1)

    fun shiftMonth(anchor: LocalDate, delta: Int, jalali: Boolean): LocalDate {
        var a = monthStart(anchor, jalali)
        repeat(kotlin.math.abs(delta)) { a = if (delta > 0) monthStart(a.plusDays(32), jalali) else monthStart(a.minusDays(1), jalali) }
        return a
    }

    fun monthLength(anchor: LocalDate, jalali: Boolean): Int =
        if (!jalali) anchor.lengthOfMonth() else JalaliDate.from(anchor).let { JalaliDate.daysInMonth(it.year, it.month) }

    fun monthTitle(anchor: LocalDate, jalali: Boolean): String =
        if (jalali) JalaliDate.from(anchor).let { "${JalaliDate.monthNameFa(it.month)} ${PersianDigits.toPersian(it.year)}" }
        else "${GREG_MONTHS_FA[anchor.monthValue - 1]} ${PersianDigits.toPersian(anchor.year)}"

    /** Saturday-first grid for the month containing [anchor]; null = empty cell. */
    fun monthGrid(anchor: LocalDate, jalali: Boolean): List<LocalDate?> {
        val start = monthStart(anchor, jalali)
        val blank = (start.dayOfWeek.value + 1) % 7 // Sat=0 … Fri=6
        val list = MutableList<LocalDate?>(blank) { null }
        for (i in 0 until monthLength(start, jalali)) list += start.plusDays(i.toLong())
        while (list.size % 7 != 0) list += null
        return list
    }

    fun dayNumber(d: LocalDate, jalali: Boolean): Int = if (jalali) JalaliDate.from(d).day else d.dayOfMonth

    fun formatFa(d: LocalDate, jalali: Boolean): String =
        if (jalali) "${JalaliDate.weekdayNameFa(d)}، ${JalaliDate.from(d).formatFa()}"
        else "${JalaliDate.weekdayNameFa(d)}، ${PersianDigits.toPersian(d.dayOfMonth)} ${GREG_MONTHS_FA[d.monthValue - 1]} ${PersianDigits.toPersian(d.year)}"
}

/** Month-grid date picker. Days outside [min]..[max] (e.g. future days) are greyed out and cannot be chosen. */
@Composable
fun MonthDatePickerDialog(
    initial: LocalDate, min: LocalDate, max: LocalDate,
    onPick: (LocalDate) -> Unit, onDismiss: () -> Unit
) {
    val jalali = LocalUseJalali.current
    var selected by remember { mutableStateOf(initial.coerceIn(min, max)) }
    var anchor by remember { mutableStateOf(DatePickerMath.monthStart(selected, jalali)) }
    val canPrev = DatePickerMath.monthStart(anchor, jalali).isAfter(min)
    val canNext = DatePickerMath.shiftMonth(anchor, 1, jalali) <= max
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("انتخاب تاریخ", style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(Modifier.testTag("date_picker")) {
                Text(DatePickerMath.formatFa(selected, jalali), color = MahavaPrimary, style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("pick_selected"))
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    IconButton(onClick = { anchor = DatePickerMath.shiftMonth(anchor, -1, jalali) }, enabled = canPrev, modifier = Modifier.testTag("pick_prev")) {
                        MahavaIcon(R.drawable.ic_chevron_right, if (canPrev) MahavaTextPrimary else MahavaBorder)
                    }
                    Text(DatePickerMath.monthTitle(anchor, jalali), style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("pick_month"))
                    IconButton(onClick = { anchor = DatePickerMath.shiftMonth(anchor, 1, jalali) }, enabled = canNext, modifier = Modifier.testTag("pick_next")) {
                        MahavaIcon(R.drawable.ic_chevron_left, if (canNext) MahavaTextPrimary else MahavaBorder)
                    }
                }
                Row(Modifier.fillMaxWidth()) {
                    DatePickerMath.WEEKDAYS_FA.forEach { Text(it, Modifier.weight(1f), textAlign = TextAlign.Center, color = MahavaTextSecondary) }
                }
                DatePickerMath.monthGrid(anchor, jalali).chunked(7).forEach { week ->
                    Row(Modifier.fillMaxWidth()) {
                        week.forEach { d ->
                            Box(Modifier.weight(1f).aspectRatio(1f).padding(2.dp), contentAlignment = Alignment.Center) {
                                if (d != null) {
                                    val enabled = !d.isBefore(min) && !d.isAfter(max)
                                    val isSel = d == selected
                                    Box(
                                        Modifier.fillMaxSize().clip(CircleShape)
                                            .background(if (isSel) MahavaPrimary else Color.Transparent)
                                            .then(if (d == max && !isSel) Modifier.border(1.dp, MahavaPrimary, CircleShape) else Modifier)
                                            .then(if (enabled) Modifier.clickable { selected = d } else Modifier)
                                            .testTag("pick_$d"),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            PersianDigits.toPersian(DatePickerMath.dayNumber(d, jalali)),
                                            color = when { isSel -> Color.White; enabled -> MahavaTextPrimary; else -> MahavaBorder }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                if (max >= LocalDate.now().minusDays(1)) QuietInfo("روزهای آینده قابل انتخاب نیستند.")
            }
        },
        confirmButton = { TextButton(onClick = { onPick(selected) }, modifier = Modifier.testTag("pick_ok")) { Text("تأیید") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } }
    )
}
