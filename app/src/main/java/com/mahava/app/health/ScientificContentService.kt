package com.mahava.app.health

import android.content.Context
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.mahava.app.content.ContentRepository
import com.mahava.app.content.ScienceSources
import com.mahava.app.health.models.FetalDevelopmentEntry
import com.mahava.app.health.models.HealthInsight
import com.mahava.app.health.models.InsightReviewStatus
import com.mahava.app.health.models.InsightSection
import com.mahava.app.health.models.PregnancyMilestone
import com.mahava.app.health.models.ScientificSource
import com.mahava.app.cycle.CycleEngineResult
import com.mahava.app.cycle.CyclePhase

class ScientificContentService(
    private val context: Context,
    private val contentRepository: ContentRepository,
    private val gson: Gson = Gson()
) {
    private var weeksBank: PregnancyWeeksBank? = null

    fun scientificSources(): List<ScientificSource> {
        val curated = listOf(
            ScientificSource(
                "acog_aub", "ACOG", "خون‌ریزی غیرطبیعی رحم",
                "https://www.acog.org/womens-health/faqs/abnormal-uterine-bleeding",
                null, "2026-10-10", "faq", "en"
            ),
            ScientificSource(
                "acog_hmb", "ACOG", "خون‌ریزی شدید قاعدگی",
                "https://www.acog.org/womens-health/faqs/heavy-menstrual-bleeding",
                null, "2026-10-10", "faq", "en"
            ),
            ScientificSource(
                "acog_edd", "ACOG", "روش‌های تخمین تاریخ زایمان",
                "https://www.acog.org/clinical/clinical-guidance/committee-opinion/articles/2017/05/methods-for-estimating-the-due-date",
                "2017-05-01", "2026-10-10", "committee_opinion", "en"
            ),
            ScientificSource(
                "acog_fetus", "ACOG", "رشد جنین در بارداری",
                "https://www.acog.org/womens-health/faqs/how-your-fetus-grows-during-pregnancy",
                null, "2026-10-10", "faq", "en"
            ),
            ScientificSource(
                "acog_cramps", "ACOG", "درد پریود",
                "https://www.acog.org/womens-health/faqs/dysmenorrhea-painful-periods",
                null, "2026-10-10", "faq", "en"
            )
        )
        val fromApp = ScienceSources.allSources().map {
            ScientificSource(it.id, it.host, it.labelFa, it.url, null, "2026-10-10", "curated", "en")
        }
        val verified = context.assets.open("content/design_sources.json").bufferedReader().use {
            gson.fromJson(it, Array<ScientificSource>::class.java).toList()
        }
        return (verified + curated + fromApp).distinctBy { it.id }
    }

    fun sourceById(id: String): ScientificSource? = scientificSources().find { it.id == id }

    private fun loadWeeks(): PregnancyWeeksBank {
        weeksBank?.let { return it }
        val json = context.assets.open("content/pregnancy_weeks.json").bufferedReader().use { it.readText() }
        val bank = gson.fromJson(json, PregnancyWeeksBank::class.java)
        weeksBank = bank
        return bank
    }

    fun fetalWeek(week: Int): FetalDevelopmentEntry? {
        if (week < 1) return null
        val entry = loadWeeks().weeks.find { it.week == week } ?: return null
        return FetalDevelopmentEntry(
            week = entry.week,
            growthFa = entry.growthFa,
            bodyChangesFa = entry.bodyChangesFa,
            milestonesFa = entry.milestonesFa.orEmpty(),
            sizeHintFa = entry.sizeHintFa,
            sourceIds = entry.sourceIds.orEmpty(),
            motherAdviceFa = entry.motherAdviceFa,
            fatherAdviceFa = entry.fatherAdviceFa,
            careFa = entry.careFa
        )
    }

    fun milestones(): List<PregnancyMilestone> = loadWeeks().milestones.orEmpty().map {
        PregnancyMilestone(it.week, it.titleFa, it.bodyFa)
    }

    fun insightsForHome(cycle: CycleEngineResult?, pregnancyWeek: Int?): List<HealthInsight> {
        val list = mutableListOf<HealthInsight>()
        if (pregnancyWeek != null) {
            fetalWeek(pregnancyWeek.coerceAtMost(42))?.let { w ->
                list += HealthInsight(
                    id = "preg_week_${w.week}",
                    titleFa = "رشد تقریبی این هفته",
                    summaryFa = w.growthFa.take(160),
                    sections = listOf(
                        InsightSection("رشد عمومی", w.growthFa),
                        InsightSection("تغییرات رایج بدن", w.bodyChangesFa),
                        InsightSection("برای مادر", w.motherAdviceFa),
                        InsightSection("برای پدر", w.fatherAdviceFa),
                        InsightSection("مراقبت‌های این مرحله", w.careFa),
                        InsightSection(
                            "اندازهٔ تقریبی",
                            (w.sizeHintFa ?: "برای این هفته اندازهٔ مقایسه‌ای ثبت نشده.") +
                                " این مقایسه آموزشی است و اندازهٔ واقعی جنین شما نیست."
                        ),
                        InsightSection(
                            "عدم قطعیت",
                            "هر بارداری فرق دارد. این متن جایگزین مراقبت پزشکی نیست."
                        )
                    ),
                    topic = "pregnancy",
                    tags = listOf("pregnancy", "week_${w.week}"),
                    audienceGroup = "pregnant",
                    applicabilityNoteFa = "فقط وقتی حالت بارداری را خودت روشن کرده باشی.",
                    sourceIds = w.sourceIds.ifEmpty { listOf("acog_fetus", "acog_edd") },
                    publishedAt = "2026-10-10",
                    reviewedAt = null,
                    reviewStatus = InsightReviewStatus.SOURCE_VERIFIED,
                    contentVersion = "1.0.0"
                )
            }
            // Home only shows personal/time-varying cards — not static encyclopedia copy.
            return list
        }
        // Static educational articles live in the health feed, not on Today.
        return emptyList()
    }

    fun insightById(id: String): HealthInsight? {
        if (id.startsWith("preg_mother_") || id.startsWith("preg_father_")) {
            val week = id.substringAfterLast('_').toIntOrNull() ?: return null
            val w = fetalWeek(week) ?: return null
            val mother = id.startsWith("preg_mother_")
            return HealthInsight(id, if (mother) "برای مادر؛ این هفته" else "برای پدر؛ این هفته",
                if (mother) w.bodyChangesFa else w.fatherAdviceFa,
                listOf(InsightSection("رشد معمول این مرحله", w.growthFa),
                    InsightSection("کارهای کاربردی", if (mother) w.motherAdviceFa else w.fatherAdviceFa),
                    InsightSection("مراقبت و محدودیت", w.careFa),
                    InsightSection("زمان بررسی", "خون‌ریزی، درد شدید، سردرد شدید یا تغییر دید و کاهش حرکت جنین نیازمند تماس به‌موقع با تیم مراقبت است.")),
                "pregnancy", listOf("week_$week", if (mother) "mother" else "father"), "pregnant",
                "توصیهٔ آموزشی برای هفتهٔ انتخاب‌شده؛ وضعیت واقعی فرد از علائم و ارزیابی پزشکی مشخص می‌شود.",
                w.sourceIds, "2026-10-11", "2026-10-11", InsightReviewStatus.SOURCE_VERIFIED, "1.1.0")
        }
        pregnancyArticles().find { it.id == id }?.let { return it }
        if (id.startsWith("preg_week_")) {
            val w = id.removePrefix("preg_week_").toIntOrNull() ?: return null
            return insightsForHome(null, w).find { it.id == id }
        }
        allStaticInsights().find { it.id == id }?.let { return it }
        val item = contentRepository.item(id) ?: return null
        return HealthInsight(
            id = item.id,
            titleFa = item.titleFa,
            summaryFa = item.summaryFa,
            sections = listOf(
                InsightSection("توضیح ساده", item.bodyFa),
                InsightSection("چه کارهایی معمولاً کم‌خطرند؟", item.whatYouCanDoFa ?: "استراحت، آب کافی و ثبت علائم."),
                InsightSection("کی به پزشک سر بزنی؟", item.whenToSeekFa ?: "اگر علائم شدید، ناگهانی یا نگران‌کننده‌اند.")
            ),
            topic = item.category,
            tags = item.careTags.orEmpty(),
            audienceGroup = "general",
            applicabilityNoteFa = "محتوای آموزشی؛ وضعیت فردی را پزشک بهتر می‌داند.",
            sourceIds = item.sourceIds.orEmpty().ifEmpty { item.sources.mapNotNull { s -> s.id } },
            publishedAt = item.sourceCheckedDate.ifBlank { "2026-01-01" },
            reviewedAt = null,
            reviewStatus = InsightReviewStatus.SOURCE_VERIFIED,
            contentVersion = item.contentVersion
        )
    }

    fun feed(): List<HealthInsight> {
        val fromBank = contentRepository.load().items
            .filter { !it.isCare && !it.isContext }
            .take(24)
            .mapNotNull { insightById(it.id) }
        return (pregnancyArticles() + allStaticInsights() + fromBank).distinctBy { it.id }
    }

    private fun pregnancyArticles(): List<HealthInsight> {
        fun article(id: String, title: String, summary: String, action: String, source: List<String>) = HealthInsight(
            id, title, summary,
            listOf(InsightSection("توضیح ساده", summary), InsightSection("کارهای کاربردی", action),
                InsightSection("محدودیت", "اطلاعات عمومی است؛ برنامهٔ پزشک و شرایط تو مقدم است. دارو یا دوز مکمل شخصی تجویز نمی‌شود.")),
            "pregnancy", listOf("pregnancy"), "pregnant", "مطالعهٔ آموزشی؛ وضعیت واقعی بارداری را تغییر نمی‌دهد.",
            source, "2026-10-11", "2026-10-11", InsightReviewStatus.SOURCE_VERIFIED, "1.1.0")
        return listOf(
            article("preg_nutrition", "تغذیه و فعالیت در بارداری", "غذای متنوع و فعالیت متناسب با توان بدن، بخشی از مراقبت بارداری است.", "با مراقب سلامت دربارهٔ فعالیت مناسب و مکمل مورد نیازت صحبت کن. داروی ثابت را خودسرانه قطع نکن.", listOf("nhs_week_4", "nhs_partners")),
            article("preg_care", "مراقبت‌ها و مراجعه‌ها", "زمان و نوع مراقبت در کشورها و بارداری‌های مختلف متفاوت است.", "زمان مراجعه و نتیجهٔ آزمایش‌ها را با پزشک یا ماما مرور کن؛ برنامهٔ شخصی خودت را دنبال کن.", listOf("nhs_week_16", "nhs_week_20")),
            article("preg_mental", "سلامت روان مادر و همراه", "احساس نگرانی در بارداری ممکن است پیش بیاید؛ کمک خواستن بخشی از مراقبت است.", "احساساتت را با فرد مورد اعتماد و مراقب سلامت مطرح کن؛ شریک زندگی می‌تواند شنونده و کمک عملی باشد.", listOf("nhs_week_8", "nhs_partners")),
            article("preg_birth", "آمادگی برای تولد", "آمادگی تدریجی، هماهنگی مسیر مراجعه و شناخت خواسته‌های مادر به همراهی کمک می‌کند.", "اطلاعات تماس، مسیر مرکز زایمان و وسایل ضروری را آماده کنید. دربارهٔ نقش همراه و حمایت بعد از تولد گفت‌وگو کنید.", listOf("nhs_partners", "nhs_week_37")),
            article("preg_warning", "علائم هشدار در بارداری", "خون‌ریزی در بارداری، درد شدید، سردرد شدید یا اختلال دید و کاهش حرکت جنین نیازمند بررسی‌اند.", "با تیم مراقبت تماس بگیر. برای خون‌ریزی زیاد، درد شدید همراه غش یا وضعیت ناگهانی خطرناک از اورژانس محلی کمک بگیر. کاهش حرکت را تا فردا به تعویق نینداز.", listOf("nhs_week_25", "nhs_week_26", "nhs_week_40"))
        )
    }

    private fun allStaticInsights(): List<HealthInsight> = listOf(
        bleedingInsight(),
        ovulationInsight(),
        cycleGeneralInsight(),
        heavyBleedInsight()
    )

    private fun bleedingInsight() = HealthInsight(
        id = "insight_aub",
        titleFa = "خون‌ریزی غیرمعمول چیست؟",
        summaryFa = "اگر طول، حجم یا نظم خون‌ریزی‌ات با الگوی معمول خودت خیلی فرق کند، ارزش دارد با پزشک حرف بزنی.",
        sections = listOf(
            InsightSection(
                "توضیح ساده",
                "خون‌ریزی غیرطبیعی رحم یعنی الگو یا مقدار خون‌ریزی از آنچه برای خودت معمول است فاصله بگیرد. علت‌های زیادی دارد و فقط با ثبت در اپ تشخیص داده نمی‌شود."
            ),
            InsightSection(
                "برای تو چه معنایی می‌تواند داشته باشد؟",
                "یک چرخهٔ کوتاه یا بلند به‌تنهایی نگران‌کننده نیست. اگر تکرار شد، با درد شدید یا خون‌ریزی خیلی زیاد همراه بود، مشورت پزشکی منطقی است. قطعی نیست."
            ),
            InsightSection(
                "کارهای عمومی کم‌خطر",
                "ثبت تاریخ شروع و پایان پریود، شدت خون‌ریزی و علائم. استراحت و مایعات کافی وقتی خون‌ریزی سنگین است."
            ),
            InsightSection(
                "کی به پزشک سر بزنی؟",
                "خون‌ریزی که بیش از حدود یک هفته طول بکشد، لخته‌های خیلی بزرگ، ضعف شدید، یا خون‌ریزی بین پریودها."
            )
        ),
        topic = "bleeding",
        tags = listOf("cycle", "bleeding"),
        audienceGroup = "cycling",
        applicabilityNoteFa = "در بارداری این کارت جایگزین مراقبت بارداری نیست.",
        sourceIds = listOf("acog_aub", "acog_hmb"),
        publishedAt = "2026-10-10",
        reviewedAt = null,
        reviewStatus = InsightReviewStatus.SOURCE_VERIFIED,
        contentVersion = "1.0.0"
    )

    private fun heavyBleedInsight() = HealthInsight(
        id = "insight_hmb",
        titleFa = "خون‌ریزی شدید قاعدگی",
        summaryFa = "اگر هر ساعت چند بار نوار یا کاپ را عوض می‌کنی یا فعالیت روزانه‌ات مختل شده، موضوع را جدی بگیر.",
        sections = listOf(
            InsightSection("توضیح ساده", "خون‌ریزی شدید یعنی حجم خون بیش از حد تحمل روزانهٔ معمول. تعریف دقیق پزشکی با معاینه و شرح حال است."),
            InsightSection("عدم قطعیت", "اپ نمی‌تواند حجم خون را اندازه بگیرد؛ فقط الگوی ثبت‌شدهٔ تو را نشان می‌دهد."),
            InsightSection("کی به پزشک سر بزنی؟", "اگر علائم کم‌خونی (خستگی شدید، تنگی نفس، رنگ‌پریدگی) داری یا خون‌ریزی مهار نمی‌شود.")
        ),
        topic = "bleeding",
        tags = listOf("heavy_bleeding"),
        audienceGroup = "cycling",
        applicabilityNoteFa = "عمومی",
        sourceIds = listOf("acog_hmb"),
        publishedAt = "2026-10-10",
        reviewedAt = null,
        reviewStatus = InsightReviewStatus.SOURCE_VERIFIED,
        contentVersion = "1.0.0"
    )

    private fun ovulationInsight() = HealthInsight(
        id = "insight_ovulation_edu",
        titleFa = "بازهٔ تخمینی تخمک‌گذاری",
        summaryFa = "بازهٔ باروری در اپ تخمینی است و روش جلوگیری از بارداری نیست.",
        sections = listOf(
            InsightSection("توضیح ساده", "تخمک‌گذاری معمولاً حدود دو هفته قبل از پریود بعدی رخ می‌دهد، ولی برای هر نفر و هر چرخه فرق دارد."),
            InsightSection("برای تو", "اگر دادهٔ کافی داری، اپ یک بازه نشان می‌دهد. آن را قطعی ندان."),
            InsightSection("کار کم‌خطر", "اگر نمی‌خواهی باردار شوی از روش مطمئن جلوگیری استفاده کن."),
            InsightSection("کی به پزشک؟", "درد شدید یک‌طرفه با احتمال بارداری یا علائم نگران‌کننده.")
        ),
        topic = "fertility",
        tags = listOf("ovulation"),
        audienceGroup = "cycling",
        applicabilityNoteFa = "آموزشی",
        sourceIds = listOf("nhs_cycle_current"),
        publishedAt = "2026-10-10",
        reviewedAt = null,
        reviewStatus = InsightReviewStatus.SOURCE_VERIFIED,
        contentVersion = "1.0.0"
    )

    private fun cycleGeneralInsight() = HealthInsight(
        id = "insight_cycle_general",
        titleFa = "چرخهٔ قاعدگی به‌زبان ساده",
        summaryFa = "چرخه از روز اول خون‌ریزی تا روز قبل از خون‌ریزی بعدی حساب می‌شود.",
        sections = listOf(
            InsightSection("توضیح ساده", "طول چرخه در افراد مختلف فرق دارد. میانگین‌های جمعیت جایگزین الگوی شخصی تو نیستند."),
            InsightSection("عدم قطعیت", "پیش‌بینی پریود بعدی فقط تخمین است."),
            InsightSection("کار کم‌خطر", "ثبت منظم شروع پریود و علائم مهم."),
            InsightSection("کی به پزشک؟", "تغییر ناگهانی الگوی چندماهه، درد شدید، یا علائم غیرعادی.")
        ),
        topic = "cycle",
        tags = listOf("education"),
        audienceGroup = "cycling",
        applicabilityNoteFa = "عمومی",
        sourceIds = listOf("acog_aub"),
        publishedAt = "2026-10-10",
        reviewedAt = null,
        reviewStatus = InsightReviewStatus.SOURCE_VERIFIED,
        contentVersion = "1.0.0"
    )

    private data class PregnancyWeeksBank(
        val contentVersion: String? = null,
        val reviewStatus: String? = null,
        val weeks: List<WeekDto> = emptyList(),
        val milestones: List<MilestoneDto>? = null
    )

    private data class WeekDto(
        val week: Int,
        val growthFa: String = "",
        val bodyChangesFa: String = "",
        val milestonesFa: List<String>? = null,
        val sizeHintFa: String? = null,
        val sourceIds: List<String>? = null,
        val motherAdviceFa: String = "",
        val fatherAdviceFa: String = "",
        val careFa: String = ""
    )

    private data class MilestoneDto(
        val week: Int,
        val titleFa: String = "",
        val bodyFa: String = ""
    )
}
