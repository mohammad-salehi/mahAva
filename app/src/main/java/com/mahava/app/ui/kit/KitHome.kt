package com.mahava.app.ui.kit

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mahava.app.ui.theme.Vazirmatn
import com.mahava.app.util.JalaliDate
import com.mahava.app.util.PersianDigits
import java.time.LocalDate
import kotlin.math.cos
import kotlin.math.sin

private const val GAUGE_START = 136f
private const val GAUGE_SWEEP = 278f

/**
 * The approved-home ring: a gauge open at the bottom, light-pink→pink progress with a white-ringed
 * handle, a lavender track, and small dots for marked days ([marks] are fractions 0..1 of the gauge).
 * It shows a stage, not a health score.
 */
@Composable
fun KitOrbit(
    number: String,
    caption: String,
    sub: String?,
    progress: Float?,
    marks: List<Float> = emptyList(),
    modifier: Modifier = Modifier
) {
    Box(modifier.fillMaxWidth().height(212.dp).testTag("kit_orbit"), contentAlignment = Alignment.Center) {
        KitBlobs(Modifier.matchParentSize())
        Canvas(Modifier.requiredSize(232.dp)) {
            val stroke = 6.dp.toPx()
            val r = size.minDimension / 2 - 10.dp.toPx()
            val c = Offset(size.width / 2, size.height / 2)
            val tl = Offset(c.x - r, c.y - r); val sz = Size(2 * r, 2 * r)
            drawArc(Kit.track, GAUGE_START, GAUGE_SWEEP, false, tl, sz, style = Stroke(stroke, cap = StrokeCap.Round))
            val p = progress?.coerceIn(0f, 1f)
            if (p != null && p > 0f) {
                val steps = 48
                val total = GAUGE_SWEEP * p
                for (i in 0 until steps) {
                    val a = GAUGE_START + total * i / steps
                    val color = lerp(Color(0xFFFBD3E3), Kit.pink, (i.toFloat() / steps).coerceIn(0f, 1f))
                    drawArc(color, a, total / steps + .6f, false, tl, sz, style = Stroke(stroke, cap = if (i == 0) StrokeCap.Round else StrokeCap.Butt))
                }
            }
            marks.forEach { m ->
                val a = Math.toRadians((GAUGE_START + GAUGE_SWEEP * m.coerceIn(0f, 1f)).toDouble())
                val o = Offset(c.x + r * cos(a).toFloat(), c.y + r * sin(a).toFloat())
                drawCircle(Color.White, 4.6.dp.toPx(), o)
                drawCircle(Kit.pink.copy(alpha = .85f), 3.4.dp.toPx(), o)
            }
            if (p != null) {
                val a = Math.toRadians((GAUGE_START + GAUGE_SWEEP * p).toDouble())
                val o = Offset(c.x + r * cos(a).toFloat(), c.y + r * sin(a).toFloat())
                drawCircle(Kit.pink.copy(alpha = .18f), 11.dp.toPx(), o)
                drawCircle(Color.White, 9.dp.toPx(), o)
                drawCircle(Kit.pink, 6.5.dp.toPx(), o)
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(number, style = Kit.title.copy(fontSize = 74.sp, lineHeight = 74.sp, color = Kit.pink, fontWeight = FontWeight.Bold,
                platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false)),
                modifier = Modifier.testTag("kit_orbit_number"))
            Text(caption, style = Kit.title.copy(fontSize = 20.sp), textAlign = TextAlign.Center)
            if (sub != null) {
                Spacer(Modifier.height(2.dp))
                Text(sub, style = Kit.small.copy(fontSize = 13.sp), textAlign = TextAlign.Center)
            }
        }
    }
}

private val WEEKDAYS = listOf("شنبه", "یکشنبه", "دوشنبه", "سه‌شنبه", "چهارشنبه", "پنجشنبه", "جمعه")

/** Week strip from approved-home: full weekday names, date, a status dot under each day. */
@Composable
fun KitWeekStrip(today: LocalDate, dot: (LocalDate) -> Color, onCalendar: () -> Unit) {
    val saturday = today.minusDays(((today.dayOfWeek.value + 1) % 7).toLong())
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp).testTag("home_week_strip")) {
        repeat(7) { i ->
            val date = saturday.plusDays(i.toLong())
            val sel = date == today
            Column(Modifier.weight(1f).clickable(onClick = onCalendar), horizontalAlignment = Alignment.CenterHorizontally) {
                Column(
                    Modifier.clip(RoundedCornerShape(12.dp)).background(if (sel) Kit.pink else Color.Transparent)
                        .padding(horizontal = 3.dp, vertical = 5.dp).widthIn(min = 44.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(WEEKDAYS[i], style = Kit.small.copy(fontSize = 11.sp, color = if (sel) Color.White else Kit.muted), maxLines = 1, softWrap = false)
                    Spacer(Modifier.height(1.dp))
                    Text(PersianDigits.toPersian(JalaliDate.from(date).day),
                        style = Kit.label.copy(fontFamily = Vazirmatn, fontWeight = FontWeight.Normal, fontSize = 14.sp, color = if (sel) Color.White else Kit.muted))
                }
                Spacer(Modifier.height(8.dp))
                Box(Modifier.size(6.dp).clip(CircleShape).background(dot(date)))
            }
        }
    }
}
