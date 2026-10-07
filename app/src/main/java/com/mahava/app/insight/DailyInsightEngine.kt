package com.mahava.app.insight

import com.mahava.app.content.CravingContent
import com.mahava.app.content.FoodCravingKeys
import com.mahava.app.cycle.CycleDayContext
import com.mahava.app.cycle.CycleEngineResult
import com.mahava.app.data.db.DailyLogEntity
import com.mahava.app.util.PersianDigits

/**
 * Personalized daily insight from phase + recent logs.
 * Educational, non-diagnostic. Free users get [teaserFa]; premium gets full fields.
 */
data class DailyInsight(
    val titleFa: String,
    val teaserFa: String,
    val bodyFa: String,
    val tipsFa: List<String>,
    val cravingLineFa: String?,
    val dataBasisFa: String
)

object DailyInsightEngine {
    fun build(
        ctx: CycleDayContext,
        cycle: CycleEngineResult?,
        todayLog: DailyLogEntity?,
        recentLogs: List<DailyLogEntity>
    ): DailyInsight {
        val phaseTitle = ctx.subWindow.titleFa
        val title = "بینش امروز · $phaseTitle"

        val bits = mutableListOf<String>()
        bits += "امروز در بازهٔ «$phaseTitle» هستی."
        ctx.uncertaintyNoteFa.takeIf { it.isNotBlank() }?.let { bits += it }

        val moods = todayLog?.moods?.split(',')?.filter { it.isNotBlank() }.orEmpty()
        val energy = todayLog?.energy
        val sleep = todayLog?.sleepQuality
        val pain = todayLog?.painScore
        val symptoms = todayLog?.physicalSymptoms?.split(',')?.filter { it.isNotBlank() }.orEmpty()
        val cravings = todayLog?.foodCravings?.split(',')?.filter { it.isNotBlank() }.orEmpty()

        if (moods.isNotEmpty()) {
            bits += "حال روحی که ثبت کرده‌ای: ${moods.joinToString("، ") { moodFa(it) }}."
        }
        if (energy != null) bits += "سطح انرژی‌ات را «${energyFa(energy)}» نوشته‌ای."
        if (sleep != null) bits += "خواب دیشب: ${sleepFa(sleep)}."
        if (pain != null && pain > 0) bits += "شدت درد: ${PersianDigits.toPersian(pain)} از ۱۰."
        if (symptoms.isNotEmpty()) {
            bits += "علائم جسمی امروز: ${symptoms.joinToString("، ") { symFa(it) }}."
        }

        val phaseGroup = CravingContent.phaseGroupOf(ctx.subWindow, cycle?.phase)
        var cravingLine: String? = null
        if (cravings.isNotEmpty()) {
            val first = cravings.first()
            cravingLine = CravingContent.shortTipFa(first, phaseGroup)
            bits += cravingLine ?: "هوس خوراکی امروز: ${cravings.joinToString("، ") { FoodCravingKeys.labelFa(it) }}."
        }

        val recentPain = recentLogs.mapNotNull { it.painScore }.takeLast(5)
        if (recentPain.size >= 3 && recentPain.average() >= 5.0) {
            bits += "در چند روز اخیر درد متوسط رو به بالا ثبت کرده‌ای؛ اگر با فعالیت روزمره‌ات تداخل دارد، ارزش دارد با پزشک حرف بزنی."
        }
        val recentLowEnergy = recentLogs.takeLast(5).count { it.energy == "low" }
        if (recentLowEnergy >= 3) {
            bits += "چند روز انرژی پایین ثبت شده؛ خواب، غذا و استرس را هم در نظر بگیر."
        }

        val tips = mutableListOf<String>()
        when (phaseGroup) {
            "menstrual" -> {
                tips += "اگر درد داری، گرمای موضعی و استراحت کوتاه گاهی کمک می‌کند."
                tips += "مایعات و آهن غذایی (عدس، گوشت کم‌چرب، سبزی برگ‌دار) را جدی بگیر."
            }
            "follicular" -> {
                tips += "این دوره معمولاً برای حرکت و کار جدید مناسب‌تر است؛ اگر بدنت آماده‌ست، کمی فعالیت سبک خوب است."
                tips += "وعده‌های منظم انرژی را پایدارتر می‌کند."
            }
            "fertile" -> {
                tips += "اگر هدف باروری داری، این بازه تخمینی است نه تضمین."
                tips += "خواب کافی و استرس کمتر به حال عمومی کمک می‌کند."
            }
            "early_luteal", "late_luteal" -> {
                tips += "نوسان حال و اشتها در نیمهٔ دوم چرخه شایع است؛ با خودت مهربان باش."
                tips += "کافئین و نمک زیاد گاهی نفخ و اضطراب را بیشتر می‌کند."
            }
            else -> tips += "ثبت منظم حال روزانه کمک می‌کند الگوهای خودت را ببینی."
        }
        if (sleep == "poor") tips += "خواب ضعیف روی درد و حال روحی اثر می‌گذارد؛ اگر می‌توانی زودتر بخواب."
        if (cravings.contains("sweet") || cravings.contains("chocolate")) {
            tips += "برای هوس شیرینی، میوه یا میان‌وعدهٔ ترکیبی معمولاً پایدارتر از قند خالی است."
        }

        val body = bits.joinToString("\n\n")
        val teaser = buildString {
            append("امروز در «$phaseTitle». ")
            if (moods.isNotEmpty()) append("حال: ${moodFa(moods.first())}. ")
            else if (energy != null) append("انرژی: ${energyFa(energy)}. ")
            else append("با ثبت حال امروز، بینش کامل‌تری می‌گیری. ")
            append("برای متن کامل، اشتراک لازم است.")
        }

        val basis = buildString {
            val n = recentLogs.size + if (todayLog != null && recentLogs.none { it.epochDay == todayLog.epochDay }) 1 else 0
            append("بر اساس مرحلهٔ چرخه")
            if (todayLog != null) append(" و ثبت امروز")
            if (recentLogs.isNotEmpty()) append(" و ${PersianDigits.toPersian(recentLogs.size)} روز اخیر")
            append(". آموزشی است، نه تشخیص پزشکی.")
        }

        return DailyInsight(
            titleFa = title,
            teaserFa = teaser,
            bodyFa = body.ifBlank { "هنوز ثبت کافی برای بینش شخصی نیست. حال امروز را وارد کن." },
            tipsFa = tips.distinct().take(4),
            cravingLineFa = cravingLine,
            dataBasisFa = basis
        )
    }

    private fun moodFa(k: String) = when (k) {
        "happy" -> "خوشحال"; "calm" -> "آرام"; "sad" -> "غمگین"
        "anxious" -> "نگران"; "irritable" -> "زودرنج"; else -> k
    }
    private fun energyFa(k: String) = when (k) {
        "low" -> "کم"; "medium" -> "متوسط"; "high" -> "زیاد"; else -> k
    }
    private fun sleepFa(k: String) = when (k) {
        "poor" -> "بد"; "ok" -> "معمولی"; "good" -> "خوب"; else -> k
    }
    private fun symFa(k: String) = when (k) {
        "pain" -> "درد"; "bloating" -> "نفخ"; "headache" -> "سردرد"
        "breast_tenderness" -> "حساسیت سینه"; "nausea" -> "تهوع"; "acne" -> "جوش"; else -> k
    }
}
