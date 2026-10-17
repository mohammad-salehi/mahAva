package com.mahava.app.content

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.mahava.app.network.PartnerToolDto
import com.mahava.app.util.JalaliDate
import com.mahava.app.util.PersianDigits
import java.time.LocalDate

/**
 * Her tool entries in simple Persian for the husband: doctor questions, appointments, today's tip,
 * patterns found in her records and the pregnancy checklist. Unknown types are skipped (never raw keys).
 */
object PartnerToolFormat {
    data class Section(val title: String, val lines: List<String>)

    private fun obj(t: PartnerToolDto): JsonObject? = try { JsonParser.parseString(t.payloadJson).asJsonObject } catch (_: Throwable) { null }
    private fun JsonObject.str(k: String): String? = try { get(k)?.takeIf { !it.isJsonNull }?.asString?.trim()?.takeIf { it.isNotEmpty() } } catch (_: Throwable) { null }
    private fun JsonObject.bool(k: String): Boolean = try { get(k)?.asBoolean == true } catch (_: Throwable) { false }
    private fun day(d: Long) = JalaliDate.from(LocalDate.ofEpochDay(d)).formatShortFa()

    private val CHECKLIST = listOf("care" to "برنامهٔ مراقبت این هفته را مرور کرد", "questions" to "سؤال‌هایش برای پزشک یا ماما را نوشت")

    fun sections(tools: List<PartnerToolDto>, today: Long): List<Section> {
        val out = mutableListOf<Section>()
        tools.filter { it.type == "recommendation" }.maxByOrNull { it.epochDay ?: 0L }?.let { t ->
            val o = obj(t) ?: return@let
            val body = o.str("body") ?: return@let
            val title = o.str("title") ?: "توصیهٔ امروز"
            val whenFa = if (t.epochDay == today) "امروز" else t.epochDay?.let { day(it) } ?: ""
            out += Section("توصیه‌ای که $whenFa دید", listOf("$title: $body"))
        }
        val medical = tools.filter { it.type == "doctor" || it.type == "appointments" }
            .sortedByDescending { it.epochDay ?: 0L }.take(10).mapNotNull { t ->
                val o = obj(t) ?: return@mapNotNull null
                val text = o.str("text") ?: return@mapNotNull null
                val kind = if (t.type == "doctor") "سؤال از پزشک" else "مراجعه"
                val d = t.epochDay?.let { " · ${day(it)}" }.orEmpty()
                val answer = o.str("answer")?.let { " — پاسخ: $it" }.orEmpty()
                "$kind$d: $text${if (o.bool("done")) " (انجام شد)" else ""}$answer"
            }
        if (medical.isNotEmpty()) out += Section("سؤال‌ها و مراجعه‌های پزشکی‌اش", medical)
        val patterns = tools.filter { it.type == "insight" }.mapNotNull { t ->
            val o = obj(t) ?: return@mapNotNull null
            val title = o.str("title") ?: return@mapNotNull null
            val desc = o.str("description")?.let { PersianDigits.toPersian(it) }
            if (desc != null) "$title: $desc" else title
        }.take(6)
        if (patterns.isNotEmpty()) out += Section("الگوهایی که از ثبت‌هایش پیدا شده", patterns)
        tools.filter { it.type == "pregnancy_checklist" }.maxByOrNull { it.epochDay ?: 0L }?.let { t ->
            val o = obj(t) ?: return@let
            val week = t.key.substringAfterLast('_').toIntOrNull()
            val lines = CHECKLIST.map { (k, label) -> (if (o.bool(k)) "✓ " else "○ ") + label }
            out += Section(if (week != null) "کارهای هفتهٔ ${PersianDigits.toPersian(week)} بارداری" else "کارهای این هفتهٔ بارداری", lines)
        }
        return out
    }

    /** Due date from the server's pregnancy key ("main_<epochDay>"), if present. */
    fun dueDay(pregnancyKey: String?): Long? = pregnancyKey?.substringAfterLast('_')?.toLongOrNull()
}
