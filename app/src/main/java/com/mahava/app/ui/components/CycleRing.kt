package com.mahava.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mahava.app.cycle.CyclePhase
import com.mahava.app.ui.theme.MahavaBorder
import com.mahava.app.ui.theme.MahavaFertility
import com.mahava.app.ui.theme.MahavaMenstruation
import com.mahava.app.ui.theme.MahavaPrimary
import com.mahava.app.ui.theme.MahavaTextPrimary
import com.mahava.app.ui.theme.MahavaTextSecondary
import com.mahava.app.util.PersianDigits
import kotlin.math.cos
import kotlin.math.sin

/**
 * Elegant period/cycle widget for Today: multi-phase arc + moon glow.
 */
@Composable
fun PeriodCycleWidget(
    cycleDay: Int?,
    cycleLengthHint: Int?,
    phase: CyclePhase?,
    phaseTitleFa: String,
    daysUntilPeriod: Int?,
    isLate: Boolean,
    modifier: Modifier = Modifier
) {
    val length = (cycleLengthHint ?: 28).coerceIn(15, 60)
    val day = cycleDay?.coerceIn(1, length + 5)
    val progress = if (day != null) (day.toFloat() / length.toFloat()).coerceIn(0f, 1f) else 0f
    val accent = phaseAccent(phase, isLate)

    Box(
        modifier
            .size(240.dp)
            .testTag("period_cycle_widget"),
        contentAlignment = Alignment.Center
    ) {
        // Soft moon glow behind the ring
        Box(
            Modifier
                .size(200.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(accent.copy(alpha = 0.22f), Color.Transparent)
                    ),
                    CircleShape
                )
        )
        Canvas(Modifier.fillMaxSize()) {
            val strokeTrack = 12.dp.toPx()
            val strokePhase = 18.dp.toPx()
            val pad = strokePhase / 2 + 4.dp.toPx()
            val arcSize = Size(size.width - pad * 2, size.height - pad * 2)
            val topLeft = Offset(pad, pad)

            // Track
            drawArc(
                color = MahavaBorder.copy(alpha = 0.85f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = strokeTrack, cap = StrokeCap.Round),
                size = arcSize,
                topLeft = topLeft
            )

            // Approximate phase segments around the ring (educative, not exact)
            val segments = listOf(
                Triple(0f, 0.18f, MahavaMenstruation),
                Triple(0.18f, 0.45f, MahavaPrimary.copy(alpha = 0.55f)),
                Triple(0.45f, 0.55f, MahavaFertility),
                Triple(0.55f, 1f, MahavaPrimary)
            )
            segments.forEach { (startFrac, endFrac, color) ->
                drawArc(
                    color = color.copy(alpha = 0.28f),
                    startAngle = -90f + 360f * startFrac,
                    sweepAngle = 360f * (endFrac - startFrac),
                    useCenter = false,
                    style = Stroke(width = strokePhase * 0.55f, cap = StrokeCap.Round),
                    size = arcSize,
                    topLeft = topLeft
                )
            }

            // Progress arc (where she is)
            if (day != null) {
                drawArc(
                    brush = Brush.sweepGradient(
                        colors = listOf(accent.copy(alpha = 0.5f), accent, MahavaPrimary)
                    ),
                    startAngle = -90f,
                    sweepAngle = 360f * progress,
                    useCenter = false,
                    style = Stroke(width = strokePhase, cap = StrokeCap.Round),
                    size = arcSize,
                    topLeft = topLeft
                )
                // Dot at current position
                val angle = Math.toRadians((-90.0 + 360.0 * progress))
                val cx = size.width / 2f
                val cy = size.height / 2f
                val r = (arcSize.width / 2f)
                val dx = (cx + r * cos(angle)).toFloat()
                val dy = (cy + r * sin(angle)).toFloat()
                drawCircle(color = Color.White, radius = 9.dp.toPx(), center = Offset(dx, dy))
                drawCircle(color = accent, radius = 6.dp.toPx(), center = Offset(dx, dy))
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 36.dp)
        ) {
            Text(
                if (day != null) "روز ${PersianDigits.toPersian(day)}" else "—",
                fontWeight = FontWeight.Bold,
                fontSize = 28.sp,
                color = MahavaTextPrimary,
                textAlign = TextAlign.Center
            )
            Text(
                if (day != null) "از ${PersianDigits.toPersian(length)}" else "روز چرخه معلوم نیست",
                fontSize = 13.sp,
                color = MahavaTextSecondary,
                textAlign = TextAlign.Center
            )
            Text(
                phaseTitleFa,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = accent,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp)
            )
            val until = when {
                isLate -> "پریود دیر کرده"
                daysUntilPeriod == null -> null
                daysUntilPeriod == 0 -> "پریود ممکن است نزدیک باشد"
                daysUntilPeriod > 0 -> "حدود ${PersianDigits.toPersian(daysUntilPeriod)} روز تا پریود"
                else -> null
            }
            if (until != null) {
                Text(
                    until,
                    fontSize = 12.sp,
                    color = MahavaTextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}

private fun phaseAccent(phase: CyclePhase?, isLate: Boolean): Color = when {
    isLate -> MahavaMenstruation
    phase == CyclePhase.MENSTRUATION -> MahavaMenstruation
    phase == CyclePhase.OVULATION_WINDOW -> MahavaFertility
    phase == CyclePhase.FOLLICULAR -> MahavaPrimary
    phase == CyclePhase.LUTEAL -> Color(0xFF9B7ED9)
    else -> MahavaPrimary
}

/** Back-compat thin wrapper used by older call sites. */
@Composable
fun CycleRing(
    cycleDay: Int?,
    cycleLengthHint: Int?,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    PeriodCycleWidget(
        cycleDay = cycleDay,
        cycleLengthHint = cycleLengthHint,
        phase = null,
        phaseTitleFa = subtitle,
        daysUntilPeriod = null,
        isLate = false,
        modifier = modifier
    )
}
