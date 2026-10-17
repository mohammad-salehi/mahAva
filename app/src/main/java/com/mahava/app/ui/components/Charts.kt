package com.mahava.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mahava.app.util.PersianDigits

/** Soft card background that also works in dark mode. */
@Composable
fun softTint(color: Color): Color = if (isSystemInDarkTheme()) color.copy(alpha = 0.22f) else color.copy(alpha = 0.16f)

/** Vertical bars, oldest → newest; null = no record (drawn as a faint stub). */
@Composable
fun BarChart(
    values: List<Float?>,
    color: Color,
    modifier: Modifier = Modifier,
    height: Dp = 120.dp,
    maxValue: Float? = null,
    labels: List<String> = emptyList(),
    showValues: Boolean = false
) {
    val max = (maxValue ?: values.filterNotNull().maxOrNull() ?: 1f).coerceAtLeast(0.1f)
    val faint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    Column(modifier.fillMaxWidth()) {
        if (showValues && values.size <= 12) {
            Row(Modifier.fillMaxWidth()) {
                values.forEach { v ->
                    Text(v?.let { PersianDigits.toPersian(fmt(it)) } ?: "–", Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            }
        }
        Canvas(Modifier.fillMaxWidth().height(height)) {
            if (values.isEmpty()) return@Canvas
            val slot = size.width / values.size
            val bw = (slot * 0.62f).coerceAtMost(28.dp.toPx())
            values.forEachIndexed { i, v ->
                // RTL: the oldest value sits on the right (start), matching Row order in RTL.
                val x = size.width - (i + 0.5f) * slot - bw / 2
                if (v == null) {
                    drawRoundRect(faint, Offset(x, size.height - 4.dp.toPx()), Size(bw, 4.dp.toPx()), CornerRadius(3f, 3f))
                } else {
                    val h = (v / max).coerceIn(0.03f, 1f) * size.height
                    drawRoundRect(color, Offset(x, size.height - h), Size(bw, h), CornerRadius(bw / 3, bw / 3))
                }
            }
        }
        if (labels.isNotEmpty()) {
            Row(Modifier.fillMaxWidth()) {
                labels.forEach {
                    Text(it, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            }
        }
    }
}

/** Line chart with dots; gaps where a day has no record. */
@Composable
fun LineChart(
    series: List<Pair<List<Float?>, Color>>,
    modifier: Modifier = Modifier,
    height: Dp = 120.dp,
    minValue: Float = 0f,
    maxValue: Float = 5f
) {
    val grid = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f)
    Canvas(modifier.fillMaxWidth().height(height)) {
        for (g in 0..4) {
            val y = size.height * g / 4f
            drawLine(grid, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
        }
        series.forEach { (values, color) ->
            if (values.isEmpty()) return@forEach
            val step = if (values.size > 1) size.width / (values.size - 1) else 0f
            fun pt(i: Int, v: Float) = Offset(size.width - i * step,
                size.height - ((v - minValue) / (maxValue - minValue)).coerceIn(0f, 1f) * size.height)
            var path: Path? = null
            values.forEachIndexed { i, v ->
                if (v == null) { path?.let { drawPath(it, color, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round)) }; path = null; return@forEachIndexed }
                val p = pt(i, v)
                if (path == null) path = Path().apply { moveTo(p.x, p.y) } else path!!.lineTo(p.x, p.y)
                drawCircle(color, 3.dp.toPx(), p)
            }
            path?.let { drawPath(it, color, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round)) }
        }
    }
}

/** Horizontal bars with a label and a count, for symptom / mood frequencies. */
@Composable
fun HorizontalBars(items: List<Pair<String, Float>>, color: Color, suffix: String = "", max: Float? = null) {
    val m = (max ?: items.maxOfOrNull { it.second } ?: 1f).coerceAtLeast(0.1f)
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { (label, v) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, Modifier.width(110.dp), style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                Box(Modifier.weight(1f).height(14.dp).clip(RoundedCornerShape(7.dp)).background(track)) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth((v / m).coerceIn(0.02f, 1f)).clip(RoundedCornerShape(7.dp)).background(color))
                }
                Spacer(Modifier.width(8.dp))
                Text(PersianDigits.toPersian(fmt(v)) + suffix, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.widthIn(min = 36.dp))
            }
        }
    }
}

@Composable
fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Simple table: first row is the header. */
@Composable
fun SimpleTable(rows: List<List<String>>, weights: List<Float> = emptyList()) {
    val line = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    Column {
        rows.forEachIndexed { r, cells ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                cells.forEachIndexed { c, t ->
                    Text(t, Modifier.weight(weights.getOrElse(c) { 1f }),
                        style = if (r == 0) MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold) else MaterialTheme.typography.bodyMedium,
                        color = if (r == 0) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
                }
            }
            if (r < rows.size - 1) Box(Modifier.fillMaxWidth().height(1.dp).background(line))
        }
    }
}

/** Big number with a caption, for summary tiles. */
@Composable
fun StatTile(title: String, value: String, caption: String, color: Color, modifier: Modifier = Modifier) {
    Column(modifier.clip(RoundedCornerShape(18.dp)).background(softTint(color)).padding(12.dp)) {
        Text(title, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.headlineMedium, color = color, fontWeight = FontWeight.Bold)
        Text(caption, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

fun fmt(v: Float): String = if (v % 1f == 0f) v.toInt().toString() else String.format(java.util.Locale.US, "%.1f", v)
