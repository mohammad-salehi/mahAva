package com.mahava.app.content

import com.mahava.app.network.PartnerSnapshotDto
import com.mahava.app.util.PersianDigits

/**
 * Plain-Persian, science-based guidance for the partner (man) about the woman's day.
 * Built only from facts already used (and sourced) in TodaySignalContent / PhaseScienceBank.
 * Always tentative: a cycle phase never tells you for sure how someone feels.
 */
object PartnerAdvice {

    data class Section(val icon: String, val titleFa: String, val bodyFa: String)

    data class Advice(
        val headlineFa: String,
        val statusFa: String,
        val sections: List<Section>,
        val doFa: List<String>,
        val evidence: ScienceSources.Evidence,
        val sourceIds: List<String>
    )

    const val CRISIS_FA =
        "اگر حالش چند هفته بد است یا از آسیب به خودش حرف می‌زند، همین حالا کمک بگیرید: اورژانس ۱۱۵ یا صدای مشاور بهزیستی ۱۴۸۰."

    const val CAUTION_FA =
        "این‌ها الگوهای رایج‌اند، نه پیش‌بینی قطعی. بهترین راه دانستن حالش، پرسیدن از خودش است."

    fun phaseNameFa(group: String): String = when (group) {
        "menstrual" -> "روزهای پریود"
        "follicular" -> "بعد از پریود"
        "fertile" -> "حوالی تخمک‌گذاری"
        "early_luteal" -> "بعد از تخمک‌گذاری"
        "late_luteal" -> "روزهای پیش از پریود"
        else -> "مرحلهٔ نامعلوم"
    }

    fun name(raw: String?): String = raw?.trim()?.takeIf { it.isNotBlank() } ?: "همراهت"

    private fun fa(n: Int) = PersianDigits.toPersian(n)

    fun statusFa(s: PartnerSnapshotDto): String {
        val parts = mutableListOf<String>()
        when {
            s.periodOngoing && s.cycleDay != null -> parts += "روز ${fa(s.cycleDay)} پریود"
            s.periodOngoing -> parts += "در روزهای پریود"
            s.isLate -> parts += "پریودش حدود ${fa(s.daysLate ?: 0)} روز دیر کرده"
            s.cycleDay != null -> parts += "روز ${fa(s.cycleDay)} چرخه"
        }
        if (!s.periodOngoing && !s.isLate) {
            when (val d = s.daysUntilPeriod) {
                null -> {}
                0 -> parts += "پریود ممکن است همین روزها شروع شود"
                else -> if (d > 0) parts += "حدود ${fa(d)} روز تا پریود بعدی"
            }
        }
        return parts.joinToString(" · ").ifBlank { "هنوز تاریخ کافی ثبت نشده" }
    }

    fun forSnapshot(rawName: String?, s: PartnerSnapshotDto): Advice {
        val n = name(rawName)
        val g = if (s.generalOnly) "general" else s.phaseGroup
        val base = phaseBase(g, n)
        val sections = base.sections.toMutableList()
        val today = s.today
        val moods = today?.moods.orEmpty()
        val symptoms = today?.symptoms.orEmpty()
        val cravings = today?.cravings.orEmpty()
        val pain = today?.painScore

        if (moods.isNotEmpty()) {
            val labels = moods.joinToString("، ") { TodaySignalContent.label("mood", it) }
            val negative = moods.any { it in setOf("sad", "anxious", "irritable") }
            sections.add(0, Section("💬", "امروز ثبت کرده: $labels",
                if (negative) "بیشتر گوش بده و کمتر نصیحت کن. بپرس «چه کمکی از من برمی‌آید؟». اگر زودرنج است، شخصی نگیرش. $CRISIS_FA"
                else "حال خوبش را ببین و همراهی کن."))
        }
        if (symptoms.isNotEmpty() || (pain ?: 0) > 0) {
            val labels = symptoms.map { TodaySignalContent.label("body", it) }
            val tips = symptoms.mapNotNull { symptomTip[it] }.distinct().take(3)
            val painLine = when {
                pain == null || pain == 0 -> ""
                pain >= 7 -> "شدت درد امروزش ${fa(pain)} از ۱۰ است؛ زیاد است. اگر با مسکن بهتر نمی‌شود یا هر ماه بدتر می‌شود، بهتر است پزشک ببیند. "
                else -> "شدت درد امروزش ${fa(pain)} از ۱۰ است. "
            }
            val title = if (labels.isNotEmpty()) "بدنش امروز: ${labels.joinToString("، ")}" else "امروز درد ثبت کرده"
            sections.add(0, Section("🌿", title, painLine + tips.joinToString(" ")))
        }
        if (cravings.isNotEmpty()) {
            val labels = cravings.joinToString("، ") { FoodCravingKeys.labelFa(it) }
            sections.add(Section("🍫", "هوس امروزش: $labels",
                "هوس خوراکی نشانهٔ کمبود ماده‌ای در بدن نیست. اگر دوست دارد، کمی از همان را کنار یک وعدهٔ سیرکننده آماده کن؛ سرزنش نکن."))
        }
        return base.copy(statusFa = statusFa(s), sections = sections)
    }

    private val symptomTip = mapOf(
        "pain" to "کیسهٔ آب گرم و استراحت کمک می‌کند. مسکن ضدالتهاب مثل ایبوپروفن هم اگر منع پزشکی ندارد.",
        "bloating" to "غذای کم‌نمک و نوشیدنی بدون کافئین کمک می‌کند.",
        "headache" to "محیط آرام، نور کم و آب کافی کمک می‌کند.",
        "breast_tenderness" to "سینه‌اش ممکن است حساس و دردناک باشد؛ موقع بغل کردن مراقب باش.",
        "fatigue" to "کارهای خانه را سبک‌تر کن و بگذار زودتر استراحت کند.",
        "nausea" to "غذای سبک و کم‌حجم بهتر است.",
        "acne" to "درباره‌اش حرفی نزن؛ این تغییر رایج و موقتی است."
    )

    private fun phaseBase(g: String, n: String): Advice = when (g) {
        "menstrual" -> Advice(
            headlineFa = "$n در روزهای پریود است",
            statusFa = "",
            sections = listOf(
                Section("🌙", "حالش", "خستگی و درد روی حال اثر می‌گذارد. علائم پیش از پریود معمولاً تا ۴ روز بعد از شروع پریود کم می‌شوند؛ پس حال خیلی‌ها کم‌کم بهتر می‌شود."),
                Section("🩸", "بدنش", "رحم منقبض می‌شود تا پوشش داخلی‌اش را بیرون بفرستد. درد معمولاً روزهای اول بیشتر است. حدود نصف زن‌ها این روزها خستگی دارند."),
                Section("🍫", "هوس‌ها", "هوس شیرینی و شوری ممکن است از روزهای قبل ادامه داشته باشد. نشانهٔ کمبود نیست.")
            ),
            doFa = listOf(
                "کیسهٔ آب گرم یا یک نوشیدنی گرم آماده کن.",
                "کارهای سنگین را این چند روز خودت انجام بده.",
                "اگر درد خیلی شدید است یا خون‌ریزی خیلی زیاد است، پیشنهاد کن پزشک ببیند."
            ),
            evidence = ScienceSources.Evidence.HIGH,
            sourceIds = listOf("acog_cramps", "acog_pms", "gi_study", "cc_cycle")
        )
        "follicular" -> Advice(
            headlineFa = "$n در روزهای بعد از پریود است",
            statusFa = "",
            sections = listOf(
                Section("🌱", "حالش", "علائم پیش از پریود معمولاً تمام شده‌اند و خیلی‌ها حس سبکی دارند. ولی حال روحی به خواب و اتفاق‌های روز هم بستگی دارد."),
                Section("🌿", "بدنش", "استروژن کم‌کم بالا می‌رود و پوشش رحم دوباره ساخته می‌شود."),
                Section("🍽️", "هوس‌ها", "پژوهش‌ها نشان می‌دهند هوس و اشتها این روزها معمولاً کمتر از روزهای قبل از پریود است.")
            ),
            doFa = listOf(
                "زمان خوبی برای برنامهٔ مشترک، پیاده‌روی یا ورزش با هم است.",
                "از حال و برنامه‌هایش بپرس؛ همه مثل هم نیستند."
            ),
            evidence = ScienceSources.Evidence.MEDIUM,
            sourceIds = listOf("cc_cycle", "acog_pms", "energy_review", "biocycle")
        )
        "fertile" -> Advice(
            headlineFa = "$n حوالی تخمک‌گذاری است (تخمینی)",
            statusFa = "",
            sections = listOf(
                Section("🌼", "حالش", "برای بیشتر آدم‌ها حال روحی این روزها تغییر خاصی ندارد. از روی تاریخ نمی‌شود حال کسی را قطعی گفت."),
                Section("🌿", "بدنش", "احتمالاً تخمک آزاد می‌شود. بعضی‌ها درد خفیف یک‌طرفه در پایین شکم دارند که معمولاً بی‌خطر است."),
                Section("🍽️", "هوس‌ها", "اشتها حوالی تخمک‌گذاری معمولاً در کمترین حد چرخه است.")
            ),
            doFa = listOf(
                "این روزها فقط تخمینی‌اند و روش جلوگیری از بارداری نیستند.",
                "اگر برای بارداری برنامه دارید، این بازه احتمال بیشتری دارد."
            ),
            evidence = ScienceSources.Evidence.MEDIUM,
            sourceIds = listOf("owh_cycle", "cc_ovulation", "nhs_ovulation_pain", "energy_review")
        )
        "early_luteal" -> Advice(
            headlineFa = "$n در روزهای بعد از تخمک‌گذاری است",
            statusFa = "",
            sections = listOf(
                Section("🌤️", "حالش", "تغییر حالِ مربوط به چرخه بیشتر چند روز آخر قبل از پریود دیده می‌شود، نه این روزها."),
                Section("🌿", "بدنش", "پروژسترون بالا می‌رود و دمای بدنش کمی گرم‌تر می‌شود. نفخ و حساسیت سینه ممکن است کم‌کم شروع شود."),
                Section("🍽️", "هوس‌ها", "در نیمهٔ دوم چرخه، زن‌ها به‌طور میانگین روزی حدود ۱۷۰ کالری بیشتر می‌خورند. گرسنگی بیشتر طبیعی است.")
            ),
            doFa = listOf(
                "وعده‌های منظم و میان‌وعدهٔ سالم در خانه داشته باشید.",
                "چند روز دیگر روزهای پیش از پریود می‌رسد؛ برنامه‌های پرفشار را سبک‌تر بچین."
            ),
            evidence = ScienceSources.Evidence.MEDIUM,
            sourceIds = listOf("sleep_review", "energy_meta", "hartlage", "nhs_breast")
        )
        "late_luteal" -> Advice(
            headlineFa = "$n در روزهای پیش از پریود است",
            statusFa = "",
            sections = listOf(
                Section("🌧️", "حالش", "زودرنجی، نگرانی یا غم در این روزها برای خیلی‌ها پیش می‌آید؛ بیشتر از حدود ۴ روز قبل تا ۳ روز اول پریود. همه این‌طور نیستند و معمولاً با شروع پریود کم می‌شود."),
                Section("🌿", "بدنش", "استروژن و پروژسترون پایین می‌آیند. نفخ، حساسیت سینه، سردرد و خستگی رایج‌اند."),
                Section("🍫", "هوس‌ها", "هوس شکلات، شیرینی و شوری این روزها بیشتر دیده شده. نشانهٔ کمبود نیست.")
            ),
            doFa = listOf(
                "اگر زودرنج است، شخصی نگیرش؛ با آرامش جواب بده.",
                "یک پیاده‌روی با هم یا یک شب آرام برنامه بریز.",
                "اگر هر ماه علائمش آن‌قدر شدید است که کار یا رابطه را به هم می‌زند، تشویقش کن با پزشک حرف بزند؛ درمان دارد."
            ),
            evidence = ScienceSources.Evidence.HIGH,
            sourceIds = listOf("owh_pms", "acog_pms", "hartlage", "cc_pmdd", "biocycle")
        )
        else -> Advice(
            headlineFa = "مرحلهٔ چرخهٔ $n معلوم نیست",
            statusFa = "",
            sections = listOf(
                Section("ℹ️", "چرا؟", "یا هنوز تاریخ کافی ثبت نشده، یا روش هورمونی جلوگیری یا چرخهٔ نامنظم ثبت شده. قرص ترکیبی جلوی تخمک‌گذاری را می‌گیرد؛ پس فازهای معمول چرخه صدق نمی‌کند."),
                Section("💬", "بهترین راه", "از خودش بپرس امروز چه حسی دارد و چه کمکی از تو برمی‌آید.")
            ),
            doFa = listOf("حالش را از خودش بپرس.", "خواب و ورزش منظم برای هر دوتان مفید است."),
            evidence = ScienceSources.Evidence.LOW,
            sourceIds = listOf("cc_cycle", "cc_ovulation")
        )
    }

    /** One short line for notifications and the widget-less daily status. */
    fun dailyLineFa(rawName: String?, s: PartnerSnapshotDto): String {
        val n = name(rawName)
        val g = if (s.generalOnly) "general" else s.phaseGroup
        val tip = phaseBase(g, n).doFa.firstOrNull().orEmpty()
        val head = if (g == "general") "$n: ${statusFa(s)}." else "$n در ${phaseNameFa(g)} است؛ ${statusFa(s)}."
        return "$head $tip".trim()
    }

    fun changeLineFa(rawName: String?, changes: Collection<String>, s: PartnerSnapshotDto?): String {
        val n = name(rawName)
        if (s == null) return "$n وضعیتش را به‌روز کرد."
        val parts = mutableListOf<String>()
        val today = s.today
        if ("period" in changes) parts += when {
            s.periodOngoing -> "پریودش شروع شد"
            s.isLate -> "پریودش دیر کرده"
            else -> "پریودش تمام شد"
        }
        if ("phase" in changes && "period" !in changes && !s.generalOnly) parts += "وارد ${phaseNameFa(s.phaseGroup)} شد"
        if ("mood" in changes && !today?.moods.isNullOrEmpty())
            parts += "حالش: " + today!!.moods!!.joinToString("، ") { TodaySignalContent.label("mood", it) }
        if ("symptoms" in changes && !today?.symptoms.isNullOrEmpty())
            parts += "بدنش: " + today!!.symptoms!!.joinToString("، ") { TodaySignalContent.label("body", it) }
        if ("pain" in changes && (today?.painScore ?: 0) > 0) parts += "درد ${fa(today!!.painScore!!)} از ۱۰"
        if ("cravings" in changes && !today?.cravings.isNullOrEmpty())
            parts += "هوس " + today!!.cravings!!.joinToString("، ") { FoodCravingKeys.labelFa(it) }
        return if (parts.isEmpty()) "$n وضعیتش را به‌روز کرد." else "$n: " + parts.joinToString(" · ")
    }
}
