package com.mahava.app.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.graphics.Typeface
import android.util.TypedValue
import androidx.core.content.res.ResourcesCompat
import com.mahava.app.R
import com.mahava.app.cycle.CyclePhase
import com.mahava.app.util.PersianDigits
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Renders the in-app PeriodCycleWidget / CycleRing look into a Bitmap for RemoteViews.
 * Luxurious deep-moon palette with the same arc geometry, phase segments, progress, and center copy.
 */
object CycleRingBitmapRenderer {

    private const val COLOR_MENSTRUATION = 0xFFDC7E8F.toInt()
    private const val COLOR_FERTILITY = 0xFF8BAA9A.toInt()
    private const val COLOR_PRIMARY = 0xFF7962CA.toInt()
    private const val COLOR_LUTEAL = 0xFF9B7ED9.toInt()
    private const val COLOR_TRACK = 0x55E6DFED
    private const val COLOR_TEXT_PRIMARY = 0xFFF8F4FF.toInt()
    private const val COLOR_TEXT_SECONDARY = 0xFFB8A9D0.toInt()

    data class RingModel(
        val cycleDay: Int?,
        val cycleLength: Int?,
        val phase: CyclePhase?,
        val phaseTitleFa: String,
        val daysUntilPeriod: Int?,
        val isLate: Boolean,
        val periodOngoing: Boolean,
        /** When set (husband's widget), this one word is the main text inside the ring. */
        val summaryWord: String? = null
    )

    fun render(context: Context, model: RingModel, sizePx: Int): Bitmap {
        val size = sizePx.coerceIn(240, 900)
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val density = size / 240f

        val length = (model.cycleLength ?: 28).coerceIn(15, 60)
        val day = model.cycleDay?.coerceIn(1, length + 5)
        val progress = if (day != null) (day.toFloat() / length.toFloat()).coerceIn(0f, 1f) else 0f
        val accent = phaseAccent(model.phase, model.isLate)

        // Soft moon glow behind the ring
        val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = android.graphics.RadialGradient(
                size / 2f,
                size / 2f,
                size * 0.42f,
                intArrayOf(withAlpha(accent, 0x48), Color.TRANSPARENT),
                floatArrayOf(0f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawCircle(size / 2f, size / 2f, size * 0.42f, glowPaint)

        val strokeTrack = 12f * density
        val strokePhase = 18f * density
        val pad = strokePhase / 2f + 4f * density
        val arc = RectF(pad, pad, size - pad, size - pad)

        // Track
        val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = strokeTrack
            strokeCap = Paint.Cap.ROUND
            color = COLOR_TRACK
        }
        canvas.drawArc(arc, -90f, 360f, false, trackPaint)

        // Approximate phase segments (same fractions as PeriodCycleWidget)
        val segments = listOf(
            Triple(0f, 0.18f, COLOR_MENSTRUATION),
            Triple(0.18f, 0.45f, withAlpha(COLOR_PRIMARY, 0x8C)),
            Triple(0.45f, 0.55f, COLOR_FERTILITY),
            Triple(0.55f, 1f, COLOR_PRIMARY)
        )
        val segPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = strokePhase * 0.55f
            strokeCap = Paint.Cap.ROUND
        }
        segments.forEach { (startFrac, endFrac, color) ->
            segPaint.color = withAlpha(color, 0x47)
            canvas.drawArc(arc, -90f + 360f * startFrac, 360f * (endFrac - startFrac), false, segPaint)
        }

        // Progress arc
        if (day != null) {
            val cx = size / 2f
            val cy = size / 2f
            val sweep = SweepGradient(
                cx,
                cy,
                intArrayOf(withAlpha(accent, 0x80), accent, COLOR_PRIMARY, withAlpha(accent, 0x80)),
                floatArrayOf(0f, 0.35f, 0.7f, 1f)
            )
            val matrix = Matrix()
            matrix.preRotate(-90f, cx, cy)
            sweep.setLocalMatrix(matrix)
            val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = strokePhase
                strokeCap = Paint.Cap.ROUND
                shader = sweep
            }
            canvas.drawArc(arc, -90f, 360f * progress, false, progressPaint)

            // Dot at current position
            val angle = Math.toRadians((-90.0 + 360.0 * progress))
            val r = arc.width() / 2f
            val dx = (cx + r * cos(angle)).toFloat()
            val dy = (cy + r * sin(angle)).toFloat()
            val whiteDot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
            val accentDot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent }
            canvas.drawCircle(dx, dy, 9f * density, whiteDot)
            canvas.drawCircle(dx, dy, 6f * density, accentDot)
        }

        drawCenterText(context, canvas, size, density, day, length, model, accent)
        return bmp
    }

    private fun drawCenterText(
        context: Context,
        canvas: Canvas,
        size: Int,
        density: Float,
        day: Int?,
        length: Int,
        model: RingModel,
        accent: Int
    ) {
        val bold = ResourcesCompat.getFont(context, R.font.vazirmatn_bold) ?: Typeface.DEFAULT_BOLD
        val medium = ResourcesCompat.getFont(context, R.font.vazirmatn_medium) ?: Typeface.DEFAULT
        val regular = ResourcesCompat.getFont(context, R.font.vazirmatn_regular) ?: Typeface.DEFAULT
        val cx = size / 2f
        var y = size * 0.38f

        val word = model.summaryWord
        if (!word.isNullOrBlank()) {
            val wordPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = accent
                textAlign = Paint.Align.CENTER
                textSize = 30f * density
                typeface = bold
                isFakeBoldText = true
            }
            val maxWidth = size * 0.6f
            while (wordPaint.measureText(word) > maxWidth && wordPaint.textSize > 14f * density) {
                wordPaint.textSize -= 1f * density
            }
            canvas.drawText(word, cx, size * 0.52f, wordPaint)
            if (day != null) {
                val smallPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = COLOR_TEXT_SECONDARY
                    textAlign = Paint.Align.CENTER
                    textSize = 12f * density
                    typeface = regular
                }
                canvas.drawText("روز ${PersianDigits.toPersian(day)}", cx, size * 0.52f + 22f * density, smallPaint)
            }
            return
        }

        val dayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_TEXT_PRIMARY
            textAlign = Paint.Align.CENTER
            textSize = 28f * density
            typeface = bold
            isFakeBoldText = true
        }
        val dayLine = if (day != null) "روز ${PersianDigits.toPersian(day)}" else "—"
        canvas.drawText(dayLine, cx, y, dayPaint)
        y += 18f * density

        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_TEXT_SECONDARY
            textAlign = Paint.Align.CENTER
            textSize = 13f * density
            typeface = regular
        }
        val subLine = if (day != null) "از ${PersianDigits.toPersian(length)}" else "روز چرخه معلوم نیست"
        canvas.drawText(subLine, cx, y, subPaint)
        y += 22f * density

        val phasePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accent
            textAlign = Paint.Align.CENTER
            textSize = 14f * density
            typeface = medium
        }
        canvas.drawText(model.phaseTitleFa, cx, y, phasePaint)
        y += 18f * density

        val until = when {
            model.periodOngoing -> "در حال پریود"
            model.isLate -> "پریود دیر کرده"
            model.daysUntilPeriod == null -> null
            model.daysUntilPeriod == 0 -> "پریود ممکن است نزدیک باشد"
            model.daysUntilPeriod > 0 -> "حدود ${PersianDigits.toPersian(model.daysUntilPeriod)} روز تا پریود"
            else -> null
        }
        if (until != null) {
            val untilPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = COLOR_TEXT_SECONDARY
                textAlign = Paint.Align.CENTER
                textSize = 11.5f * density
                typeface = regular
            }
            val maxWidth = size * 0.62f
            val lines = wrapText(until, untilPaint, maxWidth)
            lines.forEach { line ->
                canvas.drawText(line, cx, y, untilPaint)
                y += 14f * density
            }
        }
    }

    private fun wrapText(text: String, paint: Paint, maxWidth: Float): List<String> {
        if (paint.measureText(text) <= maxWidth) return listOf(text)
        val words = text.split(' ')
        val lines = mutableListOf<String>()
        var current = StringBuilder()
        for (w in words) {
            val candidate = if (current.isEmpty()) w else "$current $w"
            if (paint.measureText(candidate) <= maxWidth) {
                current = StringBuilder(candidate)
            } else {
                if (current.isNotEmpty()) lines += current.toString()
                current = StringBuilder(w)
            }
        }
        if (current.isNotEmpty()) lines += current.toString()
        return lines.ifEmpty { listOf(text) }
    }

    private fun phaseAccent(phase: CyclePhase?, isLate: Boolean): Int = when {
        isLate -> COLOR_MENSTRUATION
        phase == CyclePhase.MENSTRUATION -> COLOR_MENSTRUATION
        phase == CyclePhase.OVULATION_WINDOW -> COLOR_FERTILITY
        phase == CyclePhase.FOLLICULAR -> COLOR_PRIMARY
        phase == CyclePhase.LUTEAL -> COLOR_LUTEAL
        else -> COLOR_PRIMARY
    }

    private fun withAlpha(color: Int, alpha: Int): Int {
        val a = alpha.coerceIn(0, 255)
        return (color and 0x00FFFFFF) or (a shl 24)
    }


    /** Locked / inactive premium placeholder — no real cycle data. */
    fun renderLocked(context: Context, sizePx: Int): Bitmap {
        val size = sizePx.coerceIn(240, 900)
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val density = size / 240f
        val cx = size / 2f
        val cy = size / 2f

        val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = android.graphics.RadialGradient(
                cx, cy, size * 0.42f,
                intArrayOf(withAlpha(COLOR_PRIMARY, 0x33), Color.TRANSPARENT),
                floatArrayOf(0f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawCircle(cx, cy, size * 0.42f, glowPaint)

        val strokeTrack = 14f * density
        val pad = strokeTrack / 2f + 6f * density
        val arc = RectF(pad, pad, size - pad, size - pad)
        val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = strokeTrack
            strokeCap = Paint.Cap.ROUND
            color = withAlpha(COLOR_PRIMARY, 0x40)
        }
        canvas.drawArc(arc, -90f, 360f, false, trackPaint)
        // Soft dashed-feel accent arc (partial)
        val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = strokeTrack * 0.7f
            strokeCap = Paint.Cap.ROUND
            color = withAlpha(COLOR_PRIMARY, 0x70)
        }
        canvas.drawArc(arc, -90f, 110f, false, accentPaint)

        val bold = ResourcesCompat.getFont(context, R.font.vazirmatn_bold) ?: Typeface.DEFAULT_BOLD
        val medium = ResourcesCompat.getFont(context, R.font.vazirmatn_medium) ?: Typeface.DEFAULT
        val regular = ResourcesCompat.getFont(context, R.font.vazirmatn_regular) ?: Typeface.DEFAULT

        var y = size * 0.36f
        val lockPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_TEXT_PRIMARY
            textAlign = Paint.Align.CENTER
            textSize = 22f * density
            typeface = bold
        }
        canvas.drawText("قفل", cx, y, lockPaint)
        y += 26f * density

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_TEXT_PRIMARY
            textAlign = Paint.Align.CENTER
            textSize = 13.5f * density
            typeface = medium
        }
        val title = "ویجت فقط با اشتراک فعال می‌شود"
        wrapText(title, titlePaint, size * 0.62f).forEach { line ->
            canvas.drawText(line, cx, y, titlePaint)
            y += 17f * density
        }
        y += 6f * density

        val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_TEXT_SECONDARY
            textAlign = Paint.Align.CENTER
            textSize = 11.5f * density
            typeface = regular
        }
        wrapText("برای باز کردن، اینجا بزن", hintPaint, size * 0.62f).forEach { line ->
            canvas.drawText(line, cx, y, hintPaint)
            y += 15f * density
        }
        return bmp
    }

    fun sizePxForWidget(context: Context, minWidthDp: Int, minHeightDp: Int): Int {
        val sideDp = min(minWidthDp, minHeightDp).coerceIn(120, 320)
        val metrics = context.resources.displayMetrics
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            sideDp * 1.35f,
            metrics
        ).toInt().coerceIn(280, 900)
    }
}
