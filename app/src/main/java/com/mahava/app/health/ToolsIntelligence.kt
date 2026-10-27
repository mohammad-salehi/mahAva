package com.mahava.app.health

import com.mahava.app.data.db.*
import java.time.LocalDate
import java.security.MessageDigest

data class StoredInsight(
    val title: String, val description: String, val dataWindow: String, val sampleCount: Int,
    /** Descriptive evidence strength, not a validated medical probability. */
    val confidence: String = "low", val relatedMetrics: String, val generatedAt: Long,
    /** For pre-period patterns: in how many cycles it was logged. */
    val cycleCount: Int = 0
)

/** Historical context uses actual adjacent period starts, never a future prediction. */
object ToolsAnalytics {
    data class Window(val start: Long, val next: Long) {
        val length get() = next - start
        fun contains(day: Long) = day >= start && day < next
    }
    fun windows(periods: List<PeriodEventEntity>, today: Long): List<Window> =
        periods.map { it.startEpochDay }.filter { it <= today }.distinct().sorted().zipWithNext()
            .filter { (a,b) -> b-a in 15..90 }.map { (a,b) -> Window(a,b) }
    fun stage(day: Long, windows: List<Window>): String? {
        val w = windows.find { it.contains(day) } ?: return null
        return if (w.next-day <= 7) "هفت روز پیش از پریود ثبت‌شده" else "سایر روزهای چرخهٔ کامل"
    }
    fun energy(log: DailyLogEntity): Int? = log.energyScore?.takeIf { it in 1..5 }
    fun sleep(log: DailyLogEntity): Int? = log.sleepMinutes ?: log.sleepHours?.let { (it*60).toInt() }
    fun analyze(periods: List<PeriodEventEntity>, logs: List<DailyLogEntity>, today: Long, now: Long): List<StoredInsight> {
        val ws=windows(periods,today).takeLast(6)
        val valid=logs.filter { it.epochDay <= today && ws.any { w -> w.contains(it.epochDay) } }
        val range=ws.firstOrNull()?.let { "${LocalDate.ofEpochDay(it.start)} تا ${LocalDate.ofEpochDay(ws.last().next)}" } ?: ""
        val result=mutableListOf<StoredInsight>()
        if(ws.size >= 2) {
            val lens=ws.map { it.length }
            result += StoredInsight("الگوی چرخه من", "میانگین ${"%.1f".format(java.util.Locale.US,lens.average())} روز؛ کوتاه‌ترین ${lens.min()}، بلندترین ${lens.max()} و دامنهٔ تغییر ${lens.max()-lens.min()} روز. این آمار تشخیص نیست.",range,ws.size,relatedMetrics="cycle_length",generatedAt=now)
        }
        listOf("headache" to "سردرد", "bloating" to "نفخ", "pain" to "درد", "irritable" to "تحریک‌پذیری", "sad" to "غمگینی").forEach { (key,label) ->
            val matching=valid.filter { key in it.physicalSymptoms.orEmpty().split(',') || key in it.moods.orEmpty().split(',') || (key=="pain" && (it.painScore ?: -1)>0) }
            val pre=matching.filter { stage(it.epochDay,ws)=="هفت روز پیش از پریود ثبت‌شده" }
            val cycles=ws.count { w -> pre.any { w.contains(it.epochDay) } }
            if(cycles>=3) result += StoredInsight("الگوی پیش از پریود: $label", "در $cycles چرخه، $label در هفت روز پیش از پریود ثبت شده؛ ${pre.size} روز دارای این ثبت است. روزهای بدون ثبت نامشخص‌اند. این مشاهده تشخیص سندرم پیش از قاعدگی (PMS) یا نوع شدیدش (PMDD) نیست و علت را نشان نمی‌دهد.",range,pre.size,relatedMetrics="pms_$key",generatedAt=now,cycleCount=cycles)
        }
        listOf("sleep" to "خواب", "energy" to "انرژی", "mood" to "حال").forEach { (key,label) ->
            val observed=valid.filter { when(key) { "sleep" -> sleep(it)!=null; "energy" -> energy(it)!=null; else -> !it.moods.isNullOrBlank() } }
            val groups=observed.groupBy { stage(it.epochDay,ws) }
            if(ws.size>=3 && groups.size==2 && groups.values.all { it.size>=3 }) {
                val desc=groups.entries.joinToString("؛ ") { (context,items) ->
                    val value=when(key) {
                        "sleep" -> "میانگین ${"%.1f".format(java.util.Locale.US,items.mapNotNull { sleep(it) }.average()/60.0)} ساعت"
                        "energy" -> "میانگین ${"%.1f".format(java.util.Locale.US,items.mapNotNull { energy(it) }.average())} از ۵"
                        else -> "${items.count { it.moods.orEmpty().split(',').any { m -> m in setOf("sad","anxious","irritable") } }} روز با ثبت غمگینی، اضطراب یا تحریک‌پذیری"
                    }
                    "$context: $value (${items.size} روز دارای ثبت)"
                }
                result += StoredInsight("$label و چرخه", "$desc. هم‌زمانی به معنی علت یا تشخیص نیست.",range,observed.size,relatedMetrics=key,generatedAt=now)
            }
        }
        return result.map { it.copy(title = com.mahava.app.util.PersianDigits.toPersian(it.title), description = com.mahava.app.util.PersianDigits.toPersian(it.description)) }
    }
    fun fingerprint(periods: List<PeriodEventEntity>, logs: List<DailyLogEntity>): String {
        val raw=periods.sortedBy { it.startEpochDay }.toString()+logs.sortedBy { it.epochDay }.toString()
        return MessageDigest.getInstance("SHA-256").digest(raw.toByteArray()).take(12).joinToString("") { "%02x".format(it) }
    }
}

data class DailyRecommendation(
    val title: String="توصیه امروز", val body: String, val category: String,
    val reasonType: String, val reasonText: String, val evidenceKeys: String="",
    val medicalSourceIds: String="", val generatedAt: Long,
    val shown: Boolean=false, val opened: Boolean=false,
    /** Care item the Today card opens for this tip (null for pregnancy/general). */
    val contentId: String?=null
)
