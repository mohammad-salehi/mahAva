package com.mahava.app.content

import com.mahava.app.data.db.DailyLogEntity

/**
 * The «الان توی بدنت چه خبره؟» box on Today: 2–3 short, friendly lines.
 * A calendar estimate is not a hormone test, so the lines only say what *may* happen;
 * what she logged today always wins over the generic guess.
 */
object BodyNowContent {
    data class BodyNow(val points: List<String>, val basisFa: String)

    /** Three moods on Today; stored in the daily log's overallFeeling field (synced). */
    val MOODS = listOf("good" to "خوبم", "ordinary" to "معمولی‌ام", "bad" to "حال ندارم")

    /** Which of the three Today moods a saved log shows (null = nothing logged). */
    fun moodOf(log: DailyLogEntity?): String? {
        if (log == null) return null
        return when (log.overallFeeling) {
            "great", "good" -> "good"
            "ordinary" -> "ordinary"
            "bad", "very_bad" -> "bad"
            else -> null
        }
    }

    fun moodLabel(key: String?): String? = MOODS.find { it.first == key }?.second

    private val phaseLines = mapOf(
        "menstrual" to listOf(
            "این روزها هورمون‌ها پایین‌ترن و بدنت داره پریود رو پشت سر می‌ذاره.",
            "ممکنه دل‌درد یا خستگی داشته باشی.",
            "اگه خسته‌ای، امروز یه کم بیشتر به خودت استراحت بده."
        ),
        "follicular" to listOf(
            "بعد از پریود، استروژن (یکی از هورمون‌های اصلی زنانه) کم‌کم بالا می‌ره.",
            "خیلی‌ها این روزها انرژی بیشتری حس می‌کنن.",
            "اگه حالت خوبه، یه پیاده‌روی کوتاه امتحانش کن."
        ),
        "fertile" to listOf(
            "احتمالاً نزدیک روزهای تخمک‌گذاری هستی؛ این فقط یه تخمینه.",
            "بعضی‌ها این روزها ترشح شفاف‌تر و کش‌دارتری می‌بینن.",
            "برای پیشگیری از بارداری به این تخمین تکیه نکن."
        ),
        "early_luteal" to listOf(
            "بعد از تخمک‌گذاری، پروژسترون (هورمونی که رحم رو آماده نگه می‌داره) بالا می‌ره.",
            "ممکنه اشتهات کمی بیشتر بشه.",
            "اگه شب‌ها زودتر خسته می‌شی، امشب زودتر بخواب."
        ),
        "late_luteal" to listOf(
            "تغییر هورمون‌ها ممکنه این روزها روی خوابت اثر بذاره.",
            "ممکنه کمی نفخ یا حساسیت سینه داشته باشی.",
            "اگه دلت شیرینی خواست، با یه میوه شروع کن."
        ),
        "general" to listOf(
            "با اطلاعات فعلی نمی‌شه دقیق گفت الان کجای چرخه‌ت هستی.",
            "هرچی بیشتر ثبت کنی، الگوی بدن خودت رو بهتر می‌بینی."
        )
    )

    private val symptomLines = mapOf(
        "pain" to "امروز دل‌درد ثبت کردی؛ یه کیسهٔ آب گرم ممکنه کمکت کنه.",
        "bloating" to "امروز نفخ ثبت کردی؛ آب کافی و یه کم راه رفتن معمولاً کمک می‌کنه.",
        "headache" to "امروز سردرد ثبت کردی؛ آب بخور و اگه شد کمی استراحت کن.",
        "breast_tenderness" to "امروز حساسیت سینه ثبت کردی؛ لباس زیر راحت‌تر ممکنه بهتر باشه.",
        "fatigue" to "امروز خستگی ثبت کردی؛ اگه می‌تونی برای استراحتت وقت بذار.",
        "nausea" to "امروز حالت تهوع ثبت کردی؛ وعده‌های کوچیک‌تر معمولاً راحت‌ترن.",
        "acne" to "امروز جوش ثبت کردی؛ صورتت رو با یه شویندهٔ ملایم بشور."
    )

    fun forCycle(phaseGroup: String, today: DailyLogEntity?): BodyNow {
        val base = phaseLines[phaseGroup] ?: phaseLines.getValue("general")
        val symptoms = today?.physicalSymptoms.orEmpty().split(',').map { it.trim() }.filter { it in symptomLines }
        val mood = moodOf(today)
        val out = mutableListOf(base[0])
        if (symptoms.isNotEmpty()) out += symptomLines.getValue(symptoms.first())
        else if (base.size > 2) out += base[1]
        when {
            mood == "bad" -> out += "اگه امروز حال نداری، کارهات رو سبک‌تر بگیر."
            base.size > 2 && symptoms.isEmpty() -> out += base[2]
            base.size == 2 -> out += base[1]
        }
        val logged = today != null && (symptoms.isNotEmpty() || mood != null)
        return BodyNow(out.distinct().take(3), if (logged) "بر اساس ثبت امروزت و زمان تقریبی چرخه‌ت" else "بر اساس زمان تقریبی چرخه‌ت")
    }

    fun forPregnancy(week: Int?): BodyNow {
        val points = when {
            week == null -> listOf("برای این‌که بدونیم کجای بارداری هستی، تاریخ مبنا رو ثبت کن.")
            week < 13 -> listOf(
                "این هفته‌ها ممکنه حالت تهوع یا خستگی بیشتری داشته باشی.",
                "وعده‌های کوچیک و آب کافی معمولاً کمک می‌کنه."
            )
            week < 28 -> listOf(
                "خیلی‌ها این هفته‌ها انرژی بیشتری حس می‌کنن.",
                "ممکنه کم‌کم حرکت‌های کوچولوت رو حس کنی."
            )
            else -> listOf(
                "ممکنه زودتر خسته بشی یا خوابیدن سخت‌تر بشه.",
                "اگه حرکت‌های جنین کمتر شد، زود به پزشک یا ماما خبر بده."
            )
        }
        return BodyNow(points, "بر اساس هفتهٔ بارداریت")
    }

    /** Last [days] days, oldest first; null = nothing logged that day (never counted as «معمولی»). */
    fun moodSeries(logs: List<DailyLogEntity>, todayEpoch: Long, days: Int = 14): List<Pair<Long, String?>> {
        val byDay = logs.associateBy { it.epochDay }
        return ((todayEpoch - days + 1)..todayEpoch).map { d -> d to moodOf(byDay[d]) }
    }
}