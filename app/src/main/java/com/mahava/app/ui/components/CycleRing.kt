package com.mahava.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mahava.app.ui.theme.MahavaBorder
import com.mahava.app.ui.theme.MahavaPrimary
import com.mahava.app.ui.theme.MahavaTextPrimary
import com.mahava.app.ui.theme.MahavaTextSecondary
import com.mahava.app.util.PersianDigits

@Composable
fun CycleRing(
    cycleDay: Int?,
    cycleLengthHint: Int?,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    val progress = if (cycleDay != null && cycleLengthHint != null && cycleLengthHint > 0) {
        (cycleDay.toFloat() / cycleLengthHint.toFloat()).coerceIn(0f, 1f)
    } else 0f
    Box(modifier.size(200.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 14.dp.toPx()
            drawArc(
                color = MahavaBorder,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
                size = Size(size.width - stroke, size.height - stroke),
                topLeft = Offset(stroke / 2, stroke / 2)
            )
            drawArc(
                color = MahavaPrimary,
                startAngle = -90f,
                sweepAngle = 360f * progress,
                useCenter = false,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
                size = Size(size.width - stroke, size.height - stroke),
                topLeft = Offset(stroke / 2, stroke / 2)
            )
        }
        androidx.compose.foundation.layout.Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 28.dp)
        ) {
            Text(
                if (cycleDay != null) "روز ${PersianDigits.toPersian(cycleDay)} چرخه" else "روز چرخه معلوم نیست",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = MahavaTextPrimary,
                textAlign = TextAlign.Center
            )
            Text(subtitle, fontSize = 13.sp, color = MahavaTextSecondary, textAlign = TextAlign.Center)
        }
    }
}
