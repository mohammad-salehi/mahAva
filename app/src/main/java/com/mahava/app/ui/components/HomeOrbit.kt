package com.mahava.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mahava.app.ui.theme.*
import com.mahava.app.util.PersianDigits

/** The central number is shown only when it comes from a recorded cycle or pregnancy date. */
@Composable
fun HomeOrbit(number: Int?, caption: String, progress: Float?, modifier: Modifier = Modifier) {
    Box(modifier.size(246.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(246.dp)) {
            val diameter = size.minDimension
            drawCircle(MahavaPrimarySoft, radius = diameter * .43f, center = Offset(size.width / 2, size.height / 2))
            drawCircle(MahavaLavenderSoft, radius = diameter * .34f, center = Offset(size.width / 2, size.height / 2))
            val inset = 13.dp.toPx()
            val ringSize = Size(size.width - inset * 2, size.height - inset * 2)
            drawArc(MahavaBorder, -90f, 360f, false, Offset(inset, inset), ringSize,
                style = Stroke(9.dp.toPx(), cap = StrokeCap.Round))
            progress?.let {
                drawArc(MahavaPrimary, -90f, 360f * it.coerceIn(0f, 1f), false,
                    Offset(inset, inset), ringSize, style = Stroke(9.dp.toPx(), cap = StrokeCap.Round))
            }
            drawCircle(MahavaLavender, radius = 7.dp.toPx(), center = Offset(size.width * .10f, size.height * .34f))
            drawCircle(MahavaPrimary, radius = 5.dp.toPx(), center = Offset(size.width * .87f, size.height * .76f))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(number?.let { PersianDigits.toPersian(it) } ?: "—",
                style = MaterialTheme.typography.headlineLarge.copy(fontSize = 54.sp), color = MahavaPrimary)
            Text(caption, style = MaterialTheme.typography.titleMedium, color = MahavaTextPrimary,
                textAlign = TextAlign.Center)
        }
    }
}
