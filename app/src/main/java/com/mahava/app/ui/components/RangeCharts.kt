package com.mahava.app.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mahava.app.health.TimeRange
import com.mahava.app.ui.AppViewModel
import com.mahava.app.util.JalaliDate
import java.time.LocalDate

/** Her first entry (period, check-in or tool record), or null when she has none yet. */
@Composable
fun rememberFirstEntryDay(vm: AppViewModel): Long? {
    val state by vm.state.collectAsState()
    val records by vm.toolRecords.collectAsState()
    val today = vm.today().toEpochDay()
    return remember(state.periods, state.dailyLogs, records, today) {
        TimeRange.firstEntryDay(state.periods, state.dailyLogs, records, today)
    }
}

/** The chosen report range: default «از اولین ثبت»; months and custom are extra choices. */
class ReportRangeState(val choice: Int, val from: Long, val to: Long, val first: Long?) {
    val isAll get() = choice == TimeRange.ALL
}

@Composable
fun rememberReportRange(vm: AppViewModel, key: String, months: List<Int> = listOf(1, 3, 6, 12), allowCustom: Boolean = true,
                        tagPrefix: String = "range"): ReportRangeState {
    val first = rememberFirstEntryDay(vm)
    val today = vm.today()
    var choice by rememberSaveable(key) { mutableIntStateOf(TimeRange.ALL) }
    var customFrom by rememberSaveable(key) { mutableLongStateOf(today.minusMonths(3).toEpochDay()) }
    var customTo by rememberSaveable(key) { mutableLongStateOf(today.toEpochDay()) }
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ChoiceChipPill("از اولین ثبت", choice == TimeRange.ALL, tag = "${tagPrefix}_all") { choice = TimeRange.ALL }
        months.forEach { m ->
            ChoiceChipPill(if (m == 12) "۱ سال" else "${com.mahava.app.util.PersianDigits.toPersian(m)} ماه", choice == m, tag = "${tagPrefix}_$m") { choice = m }
        }
        if (allowCustom) ChoiceChipPill("دلخواه", choice == TimeRange.CUSTOM, tag = "${tagPrefix}_custom") {
            if (choice != TimeRange.CUSTOM) { customFrom = first ?: customFrom; customTo = today.toEpochDay() }
            choice = TimeRange.CUSTOM
        }
    }
    val from = TimeRange.startFor(choice, today, first, customFrom)
    val to = if (choice == TimeRange.CUSTOM) customTo.coerceAtLeast(from) else today.toEpochDay()
    if (choice == TimeRange.CUSTOM) {
        DateStepper(LocalDate.ofEpochDay(from), max = LocalDate.ofEpochDay(to), min = today.minusYears(10), onChange = { customFrom = it.toEpochDay() }, testTagPrefix = "${tagPrefix}_from")
        DateStepper(LocalDate.ofEpochDay(to), max = today, min = LocalDate.ofEpochDay(from), onChange = { customTo = it.toEpochDay() }, testTagPrefix = "${tagPrefix}_to")
    }
    val fromFa = JalaliDate.from(LocalDate.ofEpochDay(from)).formatFa()
    val toFa = JalaliDate.from(LocalDate.ofEpochDay(to)).formatFa()
    QuietInfo(when {
        choice == TimeRange.ALL && first == null -> "هنوز ثبتی نداری؛ نمودارها از اولین ثبتت شروع می‌شوند."
        choice == TimeRange.ALL -> "از اولین ثبتت ($fromFa) تا امروز"
        else -> "از $fromFa تا $toFa"
    })
    return ReportRangeState(choice, from, to, first)
}

/**
 * Hosts a chart. When there are more points than fit (each needs [minSlot]), the chart gets
 * wider and scrolls sideways, opening on the newest data. Always left to right (oldest left).
 */
@Composable
fun ChartFrame(points: Int, modifier: Modifier = Modifier, minSlot: Dp = 26.dp, content: @Composable () -> Unit) {
    androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val needed = minSlot * points
        if (needed <= maxWidth) {
            Column(Modifier.fillMaxWidth()) { content() }
        } else {
            val scroll = rememberScrollState()
            LaunchedEffect(points, scroll.maxValue) { scroll.scrollTo(scroll.maxValue) }
            Row(Modifier.fillMaxWidth().horizontalScroll(scroll).testTag("chart_scroll")) {
                Column(Modifier.width(needed)) { content() }
            }
        }
    }
    }
}

/**
 * Sparse date labels under a time chart (oldest on the left). [centered] = bar slots;
 * otherwise line-chart points from edge to edge.
 */
@Composable
fun TimeAxis(labels: List<String>, centered: Boolean = true) {
    if (labels.isEmpty()) return
    androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr) {
    BoxWithConstraints(Modifier.fillMaxWidth().heightIn(min = 18.dp)) {
        val n = labels.size
        val fit = (maxWidth / (64.dp * androidx.compose.ui.platform.LocalDensity.current.fontScale)).toInt().coerceIn(1, n)
        // Evenly spaced labels that always include the oldest and the newest point.
        val idx = if (fit <= 1) listOf(n - 1) else (0 until fit).map { Math.round(it * (n - 1f) / (fit - 1f)) }.distinct()
        idx.forEach { i ->
            val f = if (centered) (i + 0.5f) / n else if (n == 1) 0.5f else i.toFloat() / (n - 1)
            Text(labels[i], Modifier.align(BiasAlignment(-1f + 2f * f, 0f)), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
    }
}

/** Short Persian note shown when a chart has nothing to draw yet. */
@Composable
fun EmptyChartNote(text: String = "هنوز چیزی برای این نمودار ثبت نشده.") = QuietInfo(text)
