package com.mahava.app.reports

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import com.mahava.app.R
import com.mahava.app.data.db.DailyLogEntity
import com.mahava.app.data.db.PeriodEventEntity
import com.mahava.app.health.CycleStats
import com.mahava.app.health.LogLabels
import com.mahava.app.util.JalaliDate
import com.mahava.app.util.PersianDigits
import java.io.OutputStream
import java.time.LocalDate

/** Everything the doctor report needs, already filtered to the chosen range. */
data class DoctorReportData(
    val from: LocalDate,
    val to: LocalDate,
    val today: Long,
    val allPeriods: List<PeriodEventEntity>,
    val logs: List<DailyLogEntity>,
    val includeNotes: Boolean,
    val includeIntimate: Boolean,
    val includeTests: Boolean,
    val pregnancyLine: String? = null,
    val questions: List<String> = emptyList()
)

/**
 * A clean A4 Persian (RTL) PDF: header, summary tiles, cycle table, symptom and pain tables,
 * mood/energy/sleep by phase and the day-by-day list. Drawn with the app font (Vazirmatn).
 */
object DoctorReportPdf {
    private const val W = 595
    private const val H = 842
    private const val M = 36f
    private val INK = android.graphics.Color.rgb(48, 39, 62)
    private val MUTED = android.graphics.Color.rgb(119, 106, 131)
    private val PRIMARY = android.graphics.Color.rgb(117, 84, 174)
    private val SOFT = android.graphics.Color.rgb(238, 229, 248)
    private val PINK = android.graphics.Color.rgb(228, 107, 134)
    private val LINE = android.graphics.Color.rgb(230, 224, 236)

    private fun fa(n: Number) = PersianDigits.toPersian(if (n is Float || n is Double) String.format(java.util.Locale.US, "%.1f", n.toDouble()).removeSuffix(".0") else n.toString())
    private fun day(d: Long) = JalaliDate.from(LocalDate.ofEpochDay(d)).formatFa()

    fun write(ctx: Context, data: DoctorReportData, out: OutputStream) {
        val doc = build(ctx, data)
        try { doc.writeTo(out) } finally { doc.close() }
    }

    fun build(ctx: Context, d: DoctorReportData): PdfDocument {
        val font = runCatching { androidx.core.content.res.ResourcesCompat.getFont(ctx, R.font.vazirmatn_regular) }.getOrNull() ?: Typeface.DEFAULT
        val bold = Typeface.create(font, Typeface.BOLD)
        val doc = PdfDocument()
        val p = Painter(doc, font, bold)
        val fromE = d.from.toEpochDay(); val toE = d.to.toEpochDay()
        val logs = d.logs.filter { it.epochDay in fromE..toE }.sortedBy { it.epochDay }
        val cycles = CycleStats.cycles(d.allPeriods, d.today).filter { it.start in fromE..toE }

        // Header band
        p.ensure(70f)
        p.canvas.drawRoundRect(RectF(M, p.y, W - M, p.y + 62f), 14f, 14f, Paint().apply { color = SOFT })
        p.text("گزارش ماه برای پزشک", 18f, PRIMARY, bold = true, top = p.y + 10f, width = W - 2 * M - 24f, x = M + 12f)
        p.text("بازه: ${JalaliDate.from(d.from).formatFa()} تا ${JalaliDate.from(d.to).formatFa()}   •   ساخته‌شده از ثبت‌های خود فرد؛ تشخیص پزشکی نیست.", 9.5f, MUTED, top = p.y + 38f, width = W - 2 * M - 24f, x = M + 12f)
        p.y += 76f

        // Summary tiles
        val lens = cycles.mapNotNull { it.length }
        val bleeds = cycles.mapNotNull { it.bleedDays }
        val painDays = logs.filter { (it.painScore ?: 0) > 0 }
        val sleep = logs.mapNotNull { CycleStats.sleepHours(it) }
        val tiles = listOf(
            "میانگین طول چرخه" to (if (lens.isEmpty()) "—" else "${fa(lens.average())} روز"),
            "دامنهٔ چرخه" to (if (lens.isEmpty()) "—" else "${fa(lens.min())} تا ${fa(lens.max())} روز"),
            "میانگین مدت پریود" to (if (bleeds.isEmpty()) "—" else "${fa(bleeds.average())} روز"),
            "روزهای دارای ثبت" to fa(logs.size),
            "روزهای درد" to (if (painDays.isEmpty()) "۰" else "${fa(painDays.size)} (میانگین ${fa(painDays.mapNotNull { it.painScore }.average())} از ۱۰)"),
            "میانگین خواب" to (if (sleep.isEmpty()) "—" else "${fa(sleep.average())} ساعت")
        )
        val tw = (W - 2 * M - 2 * 8f) / 3f
        tiles.chunked(3).forEach { row ->
            p.ensure(54f)
            row.forEachIndexed { i, (t, v) ->
                val right = W - M - i * (tw + 8f)
                p.canvas.drawRoundRect(RectF(right - tw, p.y, right, p.y + 48f), 10f, 10f, Paint().apply { color = android.graphics.Color.rgb(250, 248, 252); style = Paint.Style.FILL })
                p.canvas.drawRoundRect(RectF(right - tw, p.y, right, p.y + 48f), 10f, 10f, Paint().apply { color = LINE; style = Paint.Style.STROKE; strokeWidth = 1f })
                p.text(t, 8.5f, MUTED, top = p.y + 6f, width = tw - 16f, x = right - tw + 8f)
                p.text(v, 11.5f, INK, bold = true, top = p.y + 22f, width = tw - 16f, x = right - tw + 8f)
            }
            p.y += 56f
        }
        d.pregnancyLine?.let { p.section("بارداری"); p.paragraph(it) }

        // Whole range at a glance: one row per Jalali month (several months per row for very long spans).
        if (logs.isNotEmpty() || cycles.isNotEmpty()) {
            val plan = com.mahava.app.health.TimeRange.monthPlan(fromE, toE, maxPoints = 36)
            p.section(if (plan.monthsPerBucket == 1) "خلاصهٔ ماه‌به‌ماه" else "خلاصهٔ ${plan.unitFa}")
            p.table(listOf("ماه", "روز ثبت", "روز درد", "میانگین درد", "حال", "انرژی", "خواب"), listOf(1.3f, 0.8f, 0.8f, 0.9f, 0.7f, 0.7f, 0.8f),
                plan.buckets.map { bk ->
                    val inB = logs.filter { it.epochDay in bk.start..bk.end }
                    fun avg(m: (DailyLogEntity) -> Float?) = inB.mapNotNull(m).takeIf { it.isNotEmpty() }?.average()?.let { fa(it) } ?: "—"
                    val pd = inB.filter { (it.painScore ?: 0) > 0 }
                    listOf(bk.label, fa(inB.size), fa(pd.size), pd.mapNotNull { it.painScore }.takeIf { it.isNotEmpty() }?.average()?.let { fa(it) } ?: "—",
                        avg { CycleStats.mood(it) }, avg { CycleStats.energy(it) }, avg { CycleStats.sleepHours(it) })
                })
        }

        p.section("چرخه‌ها")
        if (cycles.isEmpty()) p.paragraph("در این بازه شروع پریودی ثبت نشده.")
        else p.table(listOf("شروع پریود", "پایان", "مدت پریود", "طول چرخه"), listOf(1.4f, 1.4f, 1f, 1f),
            cycles.map { c ->
                val pe = d.allPeriods.firstOrNull { it.startEpochDay == c.start }
                listOf(day(c.start), pe?.endEpochDay?.let { day(it) } ?: if (pe?.stillOngoing == true) "ادامه دارد" else "ثبت نشده",
                    c.bleedDays?.let { "${fa(it)} روز" } ?: "—", c.length?.let { "${fa(it)} روز" } ?: "چرخهٔ جاری")
            })

        p.section("علائم پرتکرار")
        val sym = CycleStats.symptomCounts(logs).take(10)
        if (sym.isEmpty()) p.paragraph("علامتی ثبت نشده.")
        else p.table(listOf("علامت", "تعداد روز", "درصد روزهای ثبت‌شده"), listOf(1.6f, 1f, 1.2f),
            sym.map { (k, c) -> listOf(LogLabels.symptomFa(k), fa(c), "${fa(Math.round(100f * c / logs.size.coerceAtLeast(1)))}٪") })

        p.section("درد")
        if (painDays.isEmpty()) p.paragraph("در این بازه دردی ثبت نشده.")
        else p.table(listOf("روز", "شدت", "جا", "نوع", "اقدام / نتیجه"), listOf(1.2f, 0.6f, 1.3f, 1f, 1.5f),
            painDays.map { l ->
                listOf(day(l.epochDay), "${fa(l.painScore ?: 0)}/۱۰",
                    l.painLocations.orEmpty().split(',').filter { it.isNotBlank() }.joinToString("، ") { LogLabels.painLocationFa(it) }.ifBlank { "—" },
                    LogLabels.labelOf(LogLabels.PAIN_TYPES, l.painType).ifBlank { "—" },
                    listOfNotNull(l.painAction?.split(',')?.filter { it.isNotBlank() }?.joinToString("، ") { LogLabels.labelOf(LogLabels.PAIN_ACTIONS, it) }?.ifBlank { null },
                        l.painResult?.let { LogLabels.labelOf(LogLabels.PAIN_RESULTS, it) }).joinToString(" ← ").ifBlank { "—" })
            })

        val phases = CycleStats.PHASES
        val mood = CycleStats.byPhase(logs, d.allPeriods, d.today, 1) { CycleStats.mood(it) }.associateBy { it.first }
        val energy = CycleStats.byPhase(logs, d.allPeriods, d.today, 1) { CycleStats.energy(it) }.associateBy { it.first }
        val sl = CycleStats.byPhase(logs, d.allPeriods, d.today, 1) { CycleStats.sleepHours(it) }.associateBy { it.first }
        val pn = CycleStats.byPhase(logs, d.allPeriods, d.today, 1) { it.painScore?.toFloat() }.associateBy { it.first }
        if (listOf(mood, energy, sl, pn).any { it.isNotEmpty() }) {
            p.section("حال، انرژی، خواب و درد در مراحل چرخه (میانگین)")
            p.table(listOf("مرحله", "حال (۱–۵)", "انرژی (۱–۵)", "خواب (ساعت)", "درد (۰–۱۰)"), listOf(1.5f, 1f, 1f, 1f, 1f),
                phases.filter { it in mood || it in energy || it in sl || it in pn }.map { ph ->
                    listOf(CycleStats.phaseTitleFa(ph), mood[ph]?.second?.let { fa(it) } ?: "—", energy[ph]?.second?.let { fa(it) } ?: "—",
                        sl[ph]?.second?.let { fa(it) } ?: "—", pn[ph]?.second?.let { fa(it) } ?: "—")
                })
        }

        if (d.questions.isNotEmpty()) {
            p.section("سؤال‌های من از پزشک")
            d.questions.forEach { p.paragraph("• $it") }
        }

        p.section("ثبت‌های روزانه")
        if (logs.isEmpty()) p.paragraph("در این بازه ثبتی نیست.")
        else p.table(listOf("روز", "خون‌ریزی", "علائم و حال", "سایر"), listOf(1.1f, 0.8f, 2f, 1.6f),
            logs.map { l ->
                val bits = mutableListOf<String>()
                CycleStats.sleepHours(l)?.let { bits += "خواب ${fa(it)} ساعت" }
                l.discharge?.let { bits += "ترشحات ${LogLabels.labelOf(LogLabels.DISCHARGE, it)}" }
                if (d.includeTests && l.pregnancyTest != null) bits += "تست بارداری ${when (l.pregnancyTest) { "positive" -> "مثبت"; "negative" -> "منفی"; else -> "نامعتبر" }}"
                if (d.includeIntimate && l.intimacyLogged) bits += "رابطه ثبت‌شده"
                if (d.includeNotes && !l.note.isNullOrBlank()) bits += "یادداشت: ${l.note}"
                listOf(day(l.epochDay), com.mahava.app.ui.screens.bleedingFa(l.bleeding) ?: "—",
                    (l.physicalSymptoms.orEmpty().split(',').filter { it.isNotBlank() }.map { LogLabels.symptomFa(it) } +
                        l.moods.orEmpty().split(',').filter { it.isNotBlank() }.map { LogLabels.moodFa(it) }).joinToString("، ").ifBlank { "—" },
                    bits.joinToString("؛ ").ifBlank { "—" })
            })
        p.y += 8f
        p.paragraph("روزهای بدون ثبت «نامشخص» هستند و به معنی نبود علامت نیستند. پیش‌بینی‌ها در این گزارش نیامده‌اند.", 8.5f, MUTED)
        p.finish()
        return doc
    }

    private class Painter(val doc: PdfDocument, val font: Typeface, val bold: Typeface) {
        var pageNo = 0
        lateinit var page: PdfDocument.Page
        val canvas: Canvas get() = page.canvas
        var y = 0f
        init { newPage() }
        fun newPage() {
            if (pageNo > 0) doc.finishPage(page)
            pageNo++
            page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, pageNo).create())
            y = M
            val f = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = font; textSize = 8f; color = MUTED }
            canvas.drawText("ماه • صفحهٔ ${PersianDigits.toPersian(pageNo)}", M, H - 18f, f)
        }
        fun finish() = doc.finishPage(page)
        fun ensure(h: Float) { if (y + h > H - 40f) newPage() }
        fun layout(s: String, size: Float, color: Int, bold: Boolean, width: Float, align: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL): StaticLayout {
            val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = if (bold) this@Painter.bold else font; textSize = size; this.color = color }
            val t = s.ifEmpty { " " }
            return StaticLayout.Builder.obtain(t, 0, t.length, tp, width.toInt().coerceAtLeast(10))
                .setAlignment(align).setTextDirection(TextDirectionHeuristics.RTL).setLineSpacing(2f, 1f).setIncludePad(false).build()
        }
        fun text(s: String, size: Float, color: Int, bold: Boolean = false, top: Float, width: Float, x: Float) {
            val l = layout(s, size, color, bold, width)
            canvas.save(); canvas.translate(x, top); l.draw(canvas); canvas.restore()
        }
        fun section(title: String) {
            ensure(44f)
            y += 10f
            canvas.drawRect(W - M - 4f, y + 2f, W - M, y + 18f, Paint().apply { color = PINK })
            text(title, 12.5f, INK, true, y, W - 2 * M - 12f, M)
            y += 26f
        }
        fun paragraph(s: String, size: Float = 10f, color: Int = INK) {
            val l = layout(s, size, color, false, W - 2 * M)
            for (i in 0 until l.lineCount) {
                val h = (l.getLineBottom(i) - l.getLineTop(i)).toFloat()
                ensure(h)
                canvas.save(); canvas.translate(M, y - l.getLineTop(i)); canvas.clipRect(0, l.getLineTop(i), (W - 2 * M).toInt(), l.getLineBottom(i)); l.draw(canvas); canvas.restore()
                y += h
            }
            y += 4f
        }
        /** Columns run right-to-left; header row is repeated on each new page. */
        fun table(header: List<String>, weights: List<Float>, rows: List<List<String>>) {
            val total = W - 2 * M
            val ws = weights.map { it / weights.sum() * total }
            fun drawRow(cells: List<String>, head: Boolean, zebra: Boolean) {
                val layouts = cells.mapIndexed { i, c -> layout(c, if (head) 9f else 9.5f, if (head) PRIMARY else INK, head, ws[i] - 10f) }
                val h = (layouts.maxOf { it.height } + 10f)
                if (y + h > H - 40f) { newPage(); if (!head) drawRow(header, true, false) }
                if (head) canvas.drawRoundRect(RectF(M, y, W - M, y + h), 6f, 6f, Paint().apply { color = SOFT })
                else if (zebra) canvas.drawRect(M, y, W - M, y + h, Paint().apply { color = android.graphics.Color.rgb(251, 250, 253) })
                var right = W - M
                layouts.forEachIndexed { i, l ->
                    canvas.save(); canvas.translate(right - ws[i] + 5f, y + 5f); l.draw(canvas); canvas.restore()
                    right -= ws[i]
                }
                y += h
                if (!head) canvas.drawLine(M, y, W - M, y, Paint().apply { color = LINE; strokeWidth = 0.6f })
            }
            ensure(40f)
            drawRow(header, true, false)
            rows.forEachIndexed { i, r -> drawRow(r, false, i % 2 == 1) }
            y += 6f
        }
    }
}
