package com.mahava.app.content

/**
 * Bundled content models. New fields are nullable on purpose: Gson does not apply Kotlin
 * defaults, so anything that may be missing from older JSON must tolerate null.
 */
data class ContentSource(
    val title: String,
    val org: String,
    val url: String,
    val id: String? = null,
    val accessed: String? = null,
    val note: String? = null
)

data class DisplayRules(
    val showWhenPhase: List<String> = listOf("any"),
    val showWhenSubWindow: List<String> = listOf("any"),
    val hideWhenPregnancyMode: Boolean = false,
    val requiresFertilityEnabled: Boolean = false,
    val minCycleDay: Int? = null,
    val maxCycleDay: Int? = null
)

/** Educational hormone trend (never the user's measured level). */
data class HormoneTrend(val hormone: String, val trend: String, val noteFa: String)

/** Link between a sub-window and sleep/energy/skin/digestion etc., with an evidence label. */
data class LifeLink(val topic: String, val textFa: String, val evidence: String, val sourceIds: List<String>?)

data class Claim(val textFa: String, val sourceIds: List<String>?)

data class PhaseDetail(
    val subWindow: String,
    val cardTitleFa: String,
    val todayFa: String,
    val timingFa: String?,
    val hormoneNoteFa: String?,
    val hormones: List<HormoneTrend>?,
    val ovaryFa: String?,
    val uterusFa: String?,
    val mucusFa: String?,
    val temperatureFa: String?,
    val experiencesFa: List<String>?,
    val lifeLinks: List<LifeLink>?,
    val careFa: List<String>?,
    val seekCareFa: List<String>?,
    val claims: List<Claim>?
)

data class ContentItem(
    val id: String,
    val category: String,
    val titleFa: String,
    val summaryFa: String,
    val bodyFa: String,
    val applicableContexts: List<String>,
    val excludedContexts: List<String>,
    val evidenceType: String,
    val sources: List<ContentSource>,
    val sourceCheckedDate: String,
    val contentVersion: String,
    val medicalReviewStatus: String,
    val displayRules: DisplayRules,
    val whatYouCanDoFa: String? = null,
    val whenToSeekFa: String? = null,
    val sourceIds: List<String>? = null,
    val careTags: List<String>? = null,
    val phase: PhaseDetail? = null
) {
    val isCare: Boolean get() = id.startsWith("care_")
    val isContext: Boolean get() = id.startsWith("ctx_")
    val isPhase: Boolean get() = phase != null
}

data class ContentBank(
    val contentVersion: String,
    val sourceCheckedDate: String,
    val medicalReviewStatus: String,
    val notesFa: String?,
    val items: List<ContentItem>
)

data class PhaseContentSelection(
    val item: ContentItem,
    val subWindowId: String,
    val restrictionNoteFa: String? = null,
    val usedGeneralFallback: Boolean = false,
    val uncertaintyNoteFa: String = ""
)

data class CareSelection(
    val item: ContentItem,
    /** Plain-language reason, e.g. "چون امروز درد ثبت کرده‌ای". Null when chosen by cycle timing only. */
    val reasonFa: String?
)

object EvidenceLabels {
    fun fa(evidence: String): String = when (evidence) {
        "good" -> "شواهد خوب"
        "moderate" -> "شواهد متوسط"
        "limited" -> "شواهد محدود"
        "mixed" -> "نتایج پژوهش‌ها یکسان نیست"
        else -> "سطح شواهد نامشخص"
    }
}

object BodyCategories {
    const val CYCLE_HORMONES = "cycle_hormones"
    const val PERIOD_PAIN = "period_pain"
    const val FERTILITY_DISCHARGE = "fertility_discharge"
    const val SLEEP_MOOD = "sleep_mood"
    const val DIGESTION_SKIN = "digestion_skin"
    const val PATTERN_CARE = "pattern_care"

    val all = listOf(
        CYCLE_HORMONES to "چرخه و هورمون‌ها",
        PERIOD_PAIN to "پریود و درد",
        FERTILITY_DISCHARGE to "باروری و ترشحات",
        SLEEP_MOOD to "خواب و حال روحی",
        DIGESTION_SKIN to "گوارش و پوست",
        PATTERN_CARE to "الگو و مراقبت"
    )
}
