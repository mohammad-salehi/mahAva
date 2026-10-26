package com.mahava.app.ui.auth

import android.graphics.BlurMaskFilter
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.LinearGradientShader
import androidx.compose.ui.graphics.RadialGradientShader
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** CSS `linear-gradient(<angle>deg, ...)` with the exact CSS gradient-line geometry. */
internal fun cssLinear(angleDeg: Float, vararg stops: Pair<Float, Color>): Brush = object : ShaderBrush() {
    override fun createShader(size: Size): Shader {
        val rad = Math.toRadians(angleDeg.toDouble())
        val dx = sin(rad).toFloat()
        val dy = -cos(rad).toFloat()
        val len = abs(size.width * dx) + abs(size.height * dy)
        val c = Offset(size.width / 2f, size.height / 2f)
        val half = Offset(dx * len / 2f, dy * len / 2f)
        return LinearGradientShader(
            from = c - half, to = c + half,
            colors = stops.map { it.second }, colorStops = stops.map { it.first }
        )
    }
}

/** CSS `radial-gradient(circle, a, b <end>)` (farthest-corner) for a square element. */
internal fun cssRadialCircle(inner: Color, outer: Color, end: Float = 0.7f): Brush = object : ShaderBrush() {
    override fun createShader(size: Size): Shader {
        val r = sqrt(size.width * size.width + size.height * size.height) / 2f
        return RadialGradientShader(
            center = Offset(size.width / 2f, size.height / 2f), radius = r,
            colors = listOf(inner, outer), colorStops = listOf(0f, end)
        )
    }
}

/** CSS `box-shadow: <x> <y> <blur> <spread> color` behind a rounded box. */
internal fun Modifier.cssShadow(
    color: Color, blur: Dp, y: Dp = 0.dp, x: Dp = 0.dp, radius: Dp = 0.dp, spread: Dp = 0.dp
): Modifier = if (android.os.Build.VERSION.SDK_INT < 28) this else drawBehind {
    // BlurMaskFilter is ignored by hardware canvases before API 28: skip the shadow there rather than draw a hard block.
    val sigma = blur.toPx() / 2f
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color.toArgb()
    }
    // Android's BlurMaskFilter radius r gives sigma = 0.57735 r + 0.5; invert it for CSS sigma = blur / 2.
    if (sigma > 0f) paint.maskFilter = BlurMaskFilter(((sigma - 0.5f) / 0.57735f).coerceAtLeast(0.5f), BlurMaskFilter.Blur.NORMAL)
    val s = spread.toPx()
    val r = radius.toPx() + s
    drawIntoCanvas { canvas ->
        canvas.nativeCanvas.drawRoundRect(
            x.toPx() - s, y.toPx() - s, size.width + x.toPx() + s, size.height + y.toPx() + s, r, r, paint
        )
    }
}

/** Solid outline drawn outside the box, like `box-shadow: 0 0 0 <spread> color`. */
internal fun Modifier.cssRing(color: Color, spread: Dp, radius: Dp): Modifier = drawBehind {
    val s = spread.toPx()
    val r = radius.toPx() + s
    drawRoundRect(color, topLeft = Offset(-s, -s), size = Size(size.width + 2 * s, size.height + 2 * s), cornerRadius = CornerRadius(r, r))
}

private val iconCache = HashMap<String, ImageVector>()

internal fun featherVector(name: String, strokeWidth: Float): ImageVector {
    val key = "$name@$strokeWidth"
    return iconCache.getOrPut(key) {
        val b = ImageVector.Builder(name = key, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
        FeatherPaths.all.getValue(name).forEach { d ->
            b.addPath(
                pathData = PathParser().parsePathString(d).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = strokeWidth,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            )
        }
        b.build()
    }
}

/** Feather line icon exactly as drawn in the mockups (24 viewBox, round caps/joins). */
@Composable
internal fun Feather(name: String, size: Dp, color: Color, strokeWidth: Float = 2f, modifier: Modifier = Modifier) {
    val v = remember(name, strokeWidth) { featherVector(name, strokeWidth) }
    Icon(v, contentDescription = null, tint = color, modifier = modifier.size(size))
}

private const val FA_DIGITS = "۰۱۲۳۴۵۶۷۸۹"

internal fun faDigits(s: String): String = buildString(s.length) {
    s.forEach { ch -> append(if (ch in '0'..'9') FA_DIGITS[ch - '0'] else if (ch == ',') '٬' else ch) }
}

/** Latin digits for anything typed with a Persian/Arabic keyboard. */
internal fun latinDigits(s: String): String = buildString(s.length) {
    s.forEach { ch ->
        append(
            when (ch) {
                in '۰'..'۹' -> '0' + (ch - '۰')
                in '٠'..'٩' -> '0' + (ch - '٠')
                else -> ch
            }
        )
    }
}

/** `09123456789` shown as `۰۹۱۲ ۳۴۵ ۶۷۸۹` (LTR), like the mockups. */
internal object PhoneGroupingTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        val out = StringBuilder()
        raw.forEachIndexed { i, ch ->
            if (i == 4 || i == 7) out.append(' ')
            out.append(if (ch in '0'..'9') FA_DIGITS[ch - '0'] else ch)
        }
        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int =
                offset + (if (offset > 4) 1 else 0) + (if (offset > 7) 1 else 0)
            override fun transformedToOriginal(offset: Int): Int {
                var o = offset
                if (offset > 8) o -= 2 else if (offset > 4) o -= 1
                return o.coerceIn(0, raw.length)
            }
        }
        return TransformedText(AnnotatedString(out.toString()), mapping)
    }
}

/** `09123456789` -> `۰۹۱۲ ••• ۶۷۸۹` wrapped as an LTR isolate for RTL sentences. */
internal fun maskedPhone(phone: String): String {
    val p = latinDigits(phone).filter { it.isDigit() }
    if (p.length < 8) return "\u2066" + faDigits(p) + "\u2069"
    return "\u2066" + faDigits(p.take(4)) + " ••• " + faDigits(p.takeLast(4)) + "\u2069"
}

/** Persian/Arabic digits to Latin, digits only (for phone and code inputs). */
internal fun digitsOnly(s: String, max: Int): String = latinDigits(s).filter { it in '0'..'9' }.take(max)

/** 0..4 strength bars, same rule in both apps. */
internal fun passwordScore(p: String): Int {
    if (p.isEmpty()) return 0
    var s = 0
    if (p.length >= 6) s++
    if (p.length >= 8) s++
    if (p.any { it.isLetter() } && p.any { it.isDigit() }) s++
    if (p.length >= 10 || p.any { !it.isLetterOrDigit() }) s++
    return s
}
