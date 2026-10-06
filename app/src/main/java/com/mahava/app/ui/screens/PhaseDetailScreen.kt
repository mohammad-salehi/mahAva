package com.mahava.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.mahava.app.content.ContentItem
import com.mahava.app.content.EvidenceLabels
import com.mahava.app.content.HormoneTrend
import com.mahava.app.cycle.CycleSubWindow
import com.mahava.app.data.db.DailyLogEntity
import com.mahava.app.ui.AppViewModel
import com.mahava.app.ui.components.*
import com.mahava.app.ui.theme.*
import com.mahava.app.util.PersianDigits

private val ColEstrogen = Color(0xFFDC7E8F)
private val ColProgesterone = Color(0xFF7962CA)
private val ColLh = Color(0xFFE0A33A)
private val ColFsh = Color(0xFF5F9C86)

/**
 * Detail page for any content item, using the fixed template:
 * امروز / در سابقه تو / توضیح علمی / چه کاری می‌توانی انجام بدهی؟ / چه زمانی بررسی لازم است؟ / منبع.
 * Phase items (with a "phase" block) get the richer body-and-hormone sections.
 */
@Composable
fun PhaseDetailScreen(vm: AppViewModel, id: String, onBack: () -> Unit) {
    val state by vm.state.collectAsState()
    val item = vm.contentItem(id)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag("detail_screen")) {
        ScreenHeader(item?.phase?.cardTitleFa ?: item?.titleFa ?: "مطلب", onBack = onBack)
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (item == null) {
                QuietInfo("این مطلب پیدا نشد.")
                SecondaryButton("بازگشت", onClick = onBack)
                return@Column
            }
            val todaySel = vm.phaseTodaySelection()
            val isTodayPhase = todaySel.item.id == item.id
            val care = vm.careToday()
            val isTodayCare = care.item.id == item.id
            val ctx = vm.dayContext()
            val log = vm.logFor(vm.today())

            // ---- امروز ----
            DetailSection("امروز") {
                val ph = item.phase
                Text(ph?.todayFa ?: item.summaryFa, style = MaterialTheme.typography.bodyLarge)
                if (isTodayPhase) {
                    val parts = mutableListOf<String>()
                    ctx.cycleDay?.let { parts += "امروز روز ${PersianDigits.toPersian(it)} چرخهٔ توست" }
                    ctx.daysUntilCentralPeriod?.let { parts += "حدود ${PersianDigits.toPersian(it)} روز تا پریود بعدی (تخمینی)" }
                    if (parts.isNotEmpty()) QuietInfo(parts.joinToString("؛ ") + ".")
                    todaySel.restrictionNoteFa?.let { QuietInfo(it) }
                    if (ctx.uncertaintyNoteFa.isNotBlank()) QuietInfo(ctx.uncertaintyNoteFa)
                } else if (ph != null) {
                    QuietInfo("این توضیح مربوط به «${ph.cardTitleFa}» است، نه لزوماً امروزِ تو.")
                }
                if (isTodayCare && care.reasonFa != null) QuietInfo(care.reasonFa + ".")
                Text(todayLogLine(log), style = MaterialTheme.typography.bodyMedium, color = MahavaTextSecondary)
            }

            // ---- در سابقه تو ----
            DetailSection("در سابقه تو") {
                val ph = item.phase
                if (ph != null) {
                    vm.phaseHistory(ph.subWindow).linesFa.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
                } else if (state.patterns.isNotEmpty()) {
                    state.patterns.take(3).forEach { Text("• ${it.textFa}", style = MaterialTheme.typography.bodyMedium) }
                    QuietInfo("این الگوها فقط از ثبت‌های خودت ساخته شده‌اند و علت چیزی را نشان نمی‌دهند.")
                } else {
                    Text("هنوز از ثبت‌های خودت الگویی نداریم. برای دیدن الگو، دست‌کم ۲ چرخه با ثبت علائم لازم است.", style = MaterialTheme.typography.bodyMedium)
                }
            }

            // ---- توضیح علمی ----
            DetailSection("توضیح علمی") {
                val ph = item.phase
                if (ph != null) {
                    ph.timingFa?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
                    Spacer(Modifier.height(4.dp))
                    Text("هورمون‌ها در این بخش از چرخه", style = MaterialTheme.typography.titleSmall)
                    HormoneChart(CycleSubWindow.fromId(ph.subWindow))
                    ph.hormoneNoteFa?.let { QuietInfo(it) }
                    ph.hormones?.forEach { HormoneRow(it) }
                    BodyPart("تخمدان‌ها", ph.ovaryFa)
                    BodyPart("پوشش رحم", ph.uterusFa)
                    BodyPart("ترشحات واژن", ph.mucusFa)
                    BodyPart("دمای پایهٔ بدن", ph.temperatureFa)
                    if (!ph.experiencesFa.isNullOrEmpty()) {
                        Text("بعضی‌ها ممکن است این‌ها را تجربه کنند", style = MaterialTheme.typography.titleSmall)
                        Bullets(ph.experiencesFa)
                        QuietInfo("تجربهٔ هر کس فرق دارد؛ نداشتن این‌ها هم کاملاً معمول است.")
                    }
                    if (!ph.lifeLinks.isNullOrEmpty()) {
                        Text("خواب، انرژی، پوست و گوارش", style = MaterialTheme.typography.titleSmall)
                        ph.lifeLinks.forEach { link ->
                            Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                EvidenceChip(EvidenceLabels.fa(link.evidence))
                                Text(link.textFa, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                } else if (item.isCare) {
                    Text(item.summaryFa, style = MaterialTheme.typography.bodyLarge)
                } else {
                    Text(item.bodyFa, style = MaterialTheme.typography.bodyLarge)
                }
            }

            // ---- چه کاری می‌توانی انجام بدهی؟ ----
            DetailSection("چه کاری می‌توانی انجام بدهی؟") {
                val lines = item.phase?.careFa ?: if (item.isCare) splitBullets(item.bodyFa) else splitBullets(item.whatYouCanDoFa)
                if (lines.isNotEmpty()) Bullets(lines)
                else Text("علائمت را ثبت کن تا الگوی خودت را ببینی و اگر نگرانی، با پزشک صحبت کن.", style = MaterialTheme.typography.bodyMedium)
            }

            // ---- چه زمانی بررسی لازم است؟ ----
            DetailSection("چه زمانی بررسی لازم است؟") {
                val lines = item.phase?.seekCareFa ?: splitBullets(item.whenToSeekFa)
                if (lines.isNotEmpty()) Bullets(lines)
                else Bullets(listOf(
                    "اگر درد شدید است و مسکن کمکی نکرده، همان روز کمک پزشکی بگیر.",
                    "اگر خون‌ریزی خیلی زیاد است یا بیشتر از ۷ روز طول کشیده، با پزشک صحبت کن.",
                    "درد ناگهانی و شدید شکم همراه با سرگیجه یا غش، اورژانسی است."
                ))
            }

            // ---- منبع ----
            DetailSection("منبع") {
                item.sources.forEach { s ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text("${s.org}: ${s.title}", style = MaterialTheme.typography.bodyMedium)
                        Text(s.url, style = MaterialTheme.typography.bodySmall, color = MahavaPrimary)
                        s.accessed?.let { QuietInfo("تاریخ مطالعهٔ منبع: ${PersianDigits.toPersian(it)}") }
                    }
                }
                QuietInfo(item.medicalReviewStatus)
                QuietInfo("این توضیح آموزشی است و جای معاینه و نظر پزشک را نمی‌گیرد.")
            }
            SecondaryButton("بازگشت", modifier = Modifier.testTag("detail_back"), onClick = onBack)
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun DetailSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    MahavaCard {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MahavaPrimary, modifier = Modifier.padding(bottom = 6.dp))
        content()
    }
}

@Composable
private fun BodyPart(label: String, text: String?) {
    if (text.isNullOrBlank()) return
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.titleSmall)
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun EvidenceChip(label: String) {
    Text(
        label,
        style = MaterialTheme.typography.labelSmall,
        color = MahavaTextPrimary,
        modifier = Modifier.background(MahavaPrimarySoft, RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 2.dp)
    )
}

private fun hormoneNameFa(h: String) = when (h.lowercase()) {
    "estrogen" -> "استروژن"
    "progesterone" -> "پروژسترون"
    "fsh" -> "FSH (هورمون محرک فولیکول)"
    "lh" -> "LH (هورمونی که تخمک‌گذاری را راه می‌اندازد)"
    else -> h
}

private fun trendFa(t: String) = when (t) {
    "low" -> "پایین"
    "rising" -> "رو به بالا ↑"
    "high" -> "بالا"
    "peak" -> "در اوج"
    "falling" -> "رو به پایین ↓"
    "cycle" -> "بسته به روز چرخه فرق می‌کند"
    else -> "معلوم نیست"
}

private fun hormoneColor(h: String) = when (h.lowercase()) {
    "estrogen" -> ColEstrogen; "progesterone" -> ColProgesterone; "lh" -> ColLh; else -> ColFsh
}

@Composable
private fun HormoneRow(h: HormoneTrend) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Box(Modifier.padding(top = 6.dp).size(10.dp).background(hormoneColor(h.hormone), RoundedCornerShape(5.dp)))
        Spacer(Modifier.width(8.dp))
        Column {
            Text("${hormoneNameFa(h.hormone)}: ${trendFa(h.trend)}", style = MaterialTheme.typography.bodyMedium)
            if (h.noteFa.isNotBlank()) QuietInfo(h.noteFa)
        }
    }
}

/** Where each sub-window sits on a schematic (textbook) ovulatory cycle, 0..1. */
private fun windowRange(sw: CycleSubWindow?): ClosedFloatingPointRange<Float>? = when (sw) {
    CycleSubWindow.MENSTRUATION_EARLY -> 0f..0.07f
    CycleSubWindow.MENSTRUATION_LATE -> 0.07f..0.18f
    CycleSubWindow.FOLLICULAR_EARLY -> 0.18f..0.3f
    CycleSubWindow.FOLLICULAR_LATE -> 0.3f..0.43f
    CycleSubWindow.PERI_OVULATORY -> 0.43f..0.55f
    CycleSubWindow.LUTEAL_EARLY -> 0.55f..0.63f
    CycleSubWindow.LUTEAL_MID -> 0.63f..0.8f
    CycleSubWindow.LUTEAL_LATE_PREMENSTRUAL -> 0.8f..1f
    CycleSubWindow.LATE_PERIOD -> 0.94f..1f
    else -> null
}

// Schematic educational curves (relative 0..1), shaped after the textbook descriptions in Endotext/StatPearls.
private fun estrogen(x: Float): Float = 0.15f + 0.75f * bump(x, 0.45f, 0.07f) + 0.35f * bump(x, 0.72f, 0.1f)
private fun progesterone(x: Float): Float = 0.06f + 0.8f * bump(x, 0.72f, 0.11f) * if (x < 0.5f) 0.1f else 1f
private fun lh(x: Float): Float = 0.1f + 0.85f * bump(x, 0.48f, 0.02f)
private fun fsh(x: Float): Float = 0.15f + 0.2f * bump(x, 0.08f, 0.08f) + 0.35f * bump(x, 0.48f, 0.02f) + 0.08f * bump(x, 0.98f, 0.04f)
private fun bump(x: Float, c: Float, w: Float): Float = kotlin.math.exp(-((x - c) * (x - c)) / (2 * w * w))

@Composable
fun HormoneChart(sw: CycleSubWindow?) {
    val band = windowRange(sw)
    Column(Modifier.fillMaxWidth()) {
        Canvas(
            Modifier.fillMaxWidth().height(130.dp).padding(vertical = 6.dp)
                .semantics { contentDescription = "نمودار آموزشی تغییر هورمون‌ها در یک چرخهٔ معمول؛ مقدار هورمون تو نیست" }
                .testTag("hormone_chart")
        ) {
            val w = size.width; val h = size.height
            // RTL reading: day 1 on the right.
            fun px(x: Float) = w - x * w
            band?.let {
                drawRect(MahavaPrimarySoft, topLeft = Offset(px(it.endInclusive), 0f), size = androidx.compose.ui.geometry.Size((it.endInclusive - it.start) * w, h))
            }
            drawLine(MahavaBorder, Offset(0f, h - 1), Offset(w, h - 1), strokeWidth = 2f)
            listOf<Pair<(Float) -> Float, Color>>(::estrogen to ColEstrogen, ::progesterone to ColProgesterone, ::lh to ColLh, ::fsh to ColFsh).forEach { (f, c) ->
                val path = Path()
                for (i in 0..100) {
                    val x = i / 100f
                    val y = h - f(x).coerceIn(0f, 1f) * (h - 6)
                    if (i == 0) path.moveTo(px(x), y) else path.lineTo(px(x), y)
                }
                drawPath(path, c, style = Stroke(width = 3.dp.toPx()))
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            QuietInfo("شروع پریود")
            QuietInfo("پریود بعدی")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Legend("استروژن", ColEstrogen); Legend("پروژسترون", ColProgesterone); Legend("LH", ColLh); Legend("FSH", ColFsh)
        }
        QuietInfo(if (band != null) "بخش رنگی، جای تقریبی این مرحله در یک چرخهٔ معمول است. نمودار آموزشی است و مقدار هورمون تو را نشان نمی‌دهد."
                  else "نمودار آموزشی است و مقدار هورمون تو را نشان نمی‌دهد.")
    }
}

@Composable
private fun Legend(label: String, color: Color) {
    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).background(color, RoundedCornerShape(5.dp)))
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MahavaTextSecondary)
    }
}

fun bleedingFa(v: String?) = when (v) {
    "none" -> "ندارم"; "spotting" -> "لکه‌بینی"; "light" -> "کم"; "medium" -> "متوسط"; "heavy" -> "زیاد"; else -> null
}

fun moodFa(v: String?) = when (v) {
    "happy" -> "خوشحال"; "calm" -> "آرام"; "sad" -> "غمگین"; "anxious" -> "نگران"; "irritable" -> "زودرنج"; else -> v
}

fun todayLogLine(log: DailyLogEntity?): String {
    if (log == null) return "امروز هنوز چیزی ثبت نکرده‌ای."
    if (log.noSymptoms && log.bleeding == null) return "امروز «هیچ علامتی ندارم» را ثبت کرده‌ای."
    val parts = mutableListOf<String>()
    bleedingFa(log.bleeding)?.let { parts += "خون‌ریزی: $it" }
    log.painScore?.let { parts += "درد: ${PersianDigits.toPersian(it)} از ۱۰" }
    log.moods?.split(',')?.filter { it.isNotBlank() }?.takeIf { it.isNotEmpty() }?.let { parts += "حال: " + it.joinToString("، ") { m -> moodFa(m) ?: m } }
    log.physicalSymptoms?.split(',')?.filter { it.isNotBlank() }?.takeIf { it.isNotEmpty() }?.let {
        parts += "علائم: " + it.joinToString("، ") { k -> com.mahava.app.content.PhaseHistory.labelFa(k) }
    }
    if (log.noSymptoms) parts += "هیچ علامتی ندارم"
    return if (parts.isEmpty()) "امروز ثبتی داری، اما علامتی انتخاب نشده." else "ثبت امروزِ تو — " + parts.joinToString("؛ ")
}
