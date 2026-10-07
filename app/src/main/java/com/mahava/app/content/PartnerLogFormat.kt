package com.mahava.app.content

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.mahava.app.util.PersianDigits

/**
 * Turns one of her synced daily logs (all fields) into simple Persian lines for the husband.
 * Every field she recorded is shown; unknown future fields are shown as-is.
 */
object PartnerLogFormat {
    data class Line(val label: String, val value: String)

    private val SKIP = setOf("id", "epochDay", "createdAt", "updatedAt", "pregnancyTestEpochDay")

    private val ORDER = listOf(
        "bleeding", "clots", "painScore", "painLocations", "painActivityImpact", "noSymptoms", "physicalSymptoms",
        "moods", "energy", "fatigue", "stress", "focusDifficulty", "sleepQuality", "sleepHours", "foodCravings",
        "discharge", "dischargeConcerns", "intimacyLogged", "intimacyProtected", "desire", "intimacyDiscomfort",
        "bbtCelsius", "ovulationTest", "pregnancyTest", "medicationNote", "weightKg", "note"
    )

    private fun lmh(v: String) = when (v) {
        "low" -> "کم"; "medium" -> "متوسط"; "high" -> "زیاد"; "none" -> "ندارد"; else -> v
    }

    private fun csvLabels(v: String, kind: String): String =
        v.split(',').map { it.trim() }.filter { it.isNotBlank() }.joinToString("، ") { key ->
            when (kind) {
                "food" -> FoodCravingKeys.labelFa(key)
                "body" -> TodaySignalContent.label("body", key)
                "mood" -> TodaySignalContent.label("mood", key)
                else -> painPlace(key)
            }
        }

    private fun painPlace(k: String) = when (k) {
        "lower_abdomen", "abdomen" -> "زیر شکم"; "back", "lower_back" -> "کمر"; "legs", "thighs" -> "پاها"
        "head" -> "سر"; "breasts", "breast" -> "سینه"; "pelvis" -> "لگن"; else -> k
    }

    private fun num(e: JsonElement): String {
        val d = e.asDouble
        val s = if (d == Math.floor(d)) d.toLong().toString() else "%.1f".format(java.util.Locale.US, d)
        return PersianDigits.toPersian(s)
    }

    private fun lineFor(key: String, e: JsonElement): Line? {
        if (e.isJsonNull) return null
        val p = if (e.isJsonPrimitive) e.asJsonPrimitive else return null
        val str = if (p.isString) p.asString.trim() else null
        if (str != null && str.isEmpty()) return null
        val bool = if (p.isBoolean) p.asBoolean else null
        return when (key) {
            "bleeding" -> Line("خون‌ریزی", when (str) {
                "none" -> "ندارد"; "spotting" -> "لکه‌بینی"; "light" -> "کم"; "medium" -> "متوسط"; "heavy" -> "زیاد"; else -> str ?: return null
            })
            "clots" -> Line("لخته", when (str) { "none" -> "ندارد"; "sometimes" -> "گاهی"; "frequent" -> "زیاد"; else -> str ?: return null })
            "painScore" -> Line("شدت درد", "${num(e)} از ۱۰")
            "painLocations" -> Line("جای درد", csvLabels(str ?: return null, "pain"))
            "painActivityImpact" -> Line("اثر درد روی کارهایش", when (str) {
                "none" -> "ندارد"; "partial" -> "کمی"; "medium" -> "متوسط"; "high" -> "زیاد"; else -> str ?: return null
            })
            "noSymptoms" -> if (bool == true) Line("علائم", "هیچ علامتی ندارد") else null
            "physicalSymptoms" -> Line("علائم جسمی", csvLabels(str ?: return null, "body"))
            "moods" -> Line("حال", csvLabels(str ?: return null, "mood"))
            "energy" -> Line("انرژی", lmh(str ?: return null))
            "fatigue" -> Line("خستگی", lmh(str ?: return null))
            "stress" -> Line("استرس", lmh(str ?: return null))
            "focusDifficulty" -> if (bool == true) Line("تمرکز", "سخت بود") else null
            "sleepQuality" -> Line("خواب دیشب", when (str) { "poor" -> "بد"; "ok" -> "معمولی"; "good" -> "خوب"; else -> str ?: return null })
            "sleepHours" -> Line("ساعت خواب", "${num(e)} ساعت")
            "foodCravings" -> Line("هوس", csvLabels(str ?: return null, "food"))
            "discharge" -> Line("ترشحات", when (str) {
                "dry" -> "خشک"; "sticky" -> "چسبناک"; "creamy" -> "کرمی"; "watery" -> "آبکی"; "eggwhite" -> "شفاف و کش‌دار"; else -> str ?: return null
            })
            "dischargeConcerns" -> Line("نکتهٔ ترشحات", str ?: return null)
            "intimacyLogged" -> if (bool == true) Line("رابطه", "ثبت کرده") else null
            "intimacyProtected" -> Line("محافظت در رابطه", when (str) { "yes", "true" -> "با محافظت"; "no", "false" -> "بدون محافظت"; else -> str ?: return null })
            "desire" -> Line("میل جنسی", lmh(str ?: return null))
            "intimacyDiscomfort" -> if (bool == true) Line("ناراحتی در رابطه", "داشته") else null
            "bbtCelsius" -> Line("دمای پایهٔ بدن", "${num(e)} درجه")
            "ovulationTest" -> Line("تست تخمک‌گذاری", testFa(str ?: return null))
            "pregnancyTest" -> Line("تست بارداری", testFa(str ?: return null))
            "medicationNote" -> Line("دارو", str ?: return null)
            "weightKg" -> Line("وزن", "${num(e)} کیلو")
            "note" -> Line("یادداشت", str ?: return null)
            else -> when {
                bool != null -> if (bool) Line(key, "بله") else null
                p.isNumber -> Line(key, num(e))
                else -> Line(key, str ?: return null)
            }
        }
    }

    private fun testFa(v: String) = when (v) {
        "pos", "positive" -> "مثبت"; "neg", "negative" -> "منفی"; "invalid" -> "نامعتبر"; else -> v
    }

    fun lines(log: JsonObject?): List<Line> {
        if (log == null) return emptyList()
        val keys = log.keySet().filter { it !in SKIP }
        val ordered = ORDER.filter { it in keys } + keys.filter { it !in ORDER }.sorted()
        return ordered.mapNotNull { k -> try { lineFor(k, log.get(k)) } catch (_: Throwable) { null } }
    }

    fun epochDay(log: JsonObject?): Long? = try { log?.get("epochDay")?.asLong } catch (_: Throwable) { null }
}
