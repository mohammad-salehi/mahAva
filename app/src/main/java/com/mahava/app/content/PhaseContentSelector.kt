package com.mahava.app.content

import com.mahava.app.cycle.CycleContentPolicy
import com.mahava.app.cycle.CycleDayContext
import com.mahava.app.cycle.CyclePhase
import com.mahava.app.cycle.CycleSubWindow
import com.mahava.app.cycle.PredictionRestriction
import com.mahava.app.data.db.DailyLogEntity

object PhaseContentSelector {

    fun phaseKey(phase: CyclePhase): String = when (phase) {
        CyclePhase.MENSTRUATION -> "menstruation"
        CyclePhase.FOLLICULAR -> "follicular"
        CyclePhase.OVULATION_WINDOW -> "ovulation"
        CyclePhase.LUTEAL -> "luteal"
        CyclePhase.UNKNOWN -> "unknown"
    }

    /** Plain-language reason why the specific sub-window is not shown. */
    fun restrictionNoteFa(restriction: PredictionRestriction): String? = when (restriction) {
        PredictionRestriction.NONE -> null
        PredictionRestriction.INSUFFICIENT_DATA ->
            "هنوز اطلاعات کافی برای تخمین مرحلهٔ امروز نداریم؛ برای همین توضیح کلی را می‌بینی."
        PredictionRestriction.IRREGULAR ->
            "چون چرخه‌ات را نامنظم ثبت کرده‌ای، مرحلهٔ امروز را حدس نمی‌زنیم و توضیح کلی را می‌بینی."
        PredictionRestriction.HORMONAL_CONTRACEPTION ->
            "چون روش هورمونی جلوگیری ثبت کرده‌ای، مرحله‌های طبیعی چرخه برای تو صدق نمی‌کند؛ توضیح کلی را می‌بینی."
        PredictionRestriction.POSTPARTUM_OR_BREASTFEEDING ->
            "بعد از زایمان یا در دوران شیردهی، زمان تخمک‌گذاری قابل پیش‌بینی نیست؛ توضیح کلی را می‌بینی."
        PredictionRestriction.PERIMENOPAUSE ->
            "نزدیک یائسگی، چرخه‌ها قابل پیش‌بینی نیستند؛ توضیح کلی را می‌بینی."
        PredictionRestriction.PREGNANCY_MODE ->
            "حالت بارداری روشن است؛ توضیح مرحله‌های چرخه متوقف شده است."
        PredictionRestriction.USER_DISABLED_FERTILITY -> null
    }

    fun contextItemId(restriction: PredictionRestriction): String? = when (restriction) {
        PredictionRestriction.INSUFFICIENT_DATA -> "ctx_insufficient_data"
        PredictionRestriction.IRREGULAR -> "ctx_irregular"
        PredictionRestriction.HORMONAL_CONTRACEPTION -> "ctx_hormonal_contraception"
        PredictionRestriction.POSTPARTUM_OR_BREASTFEEDING -> "ctx_postpartum"
        PredictionRestriction.PERIMENOPAUSE -> "ctx_perimenopause"
        PredictionRestriction.PREGNANCY_MODE -> "ctx_pregnancy"
        else -> null
    }

    fun selectPhase(bank: ContentBank, ctx: CycleDayContext, restriction: PredictionRestriction): PhaseContentSelection {
        val restricted = CycleContentPolicy.forceGeneral(restriction)
        // Logged bleeding → period content (with a note) even when dates cannot be estimated.
        if (ctx.fromLoggedBleeding && ctx.subWindow in setOf(CycleSubWindow.MENSTRUATION_EARLY, CycleSubWindow.MENSTRUATION_LATE)) {
            val item = bank.items.find { it.id == "sw_${ctx.subWindow.id}" }
            if (item != null) {
                return PhaseContentSelection(
                    item = item,
                    subWindowId = ctx.subWindow.id,
                    restrictionNoteFa = if (restricted)
                        "این توضیح بر اساس خون‌ریزی‌ای است که ثبت کرده‌ای؛ بقیهٔ مرحله‌های چرخه را برای تو حدس نمی‌زنیم."
                    else null,
                    usedGeneralFallback = false,
                    uncertaintyNoteFa = ctx.uncertaintyNoteFa
                )
            }
        }
        if (restricted) {
            val id = contextItemId(restriction)
            val item = bank.items.find { it.id == id } ?: bank.items.find { it.id == "sw_unknown" } ?: bank.items.first()
            return PhaseContentSelection(
                item = item,
                subWindowId = CycleSubWindow.UNKNOWN.id,
                restrictionNoteFa = restrictionNoteFa(restriction),
                usedGeneralFallback = true,
                uncertaintyNoteFa = ""
            )
        }
        val sw = ctx.subWindow
        val item = bank.items.find { it.id == "sw_${sw.id}" }
            ?: bank.items.find { it.id == "sw_unknown" }
            ?: bank.items.first { it.category == BodyCategories.CYCLE_HORMONES }
        val general = sw == CycleSubWindow.UNKNOWN
        return PhaseContentSelection(
            item = item,
            subWindowId = sw.id,
            restrictionNoteFa = if (general) "مرحلهٔ امروز معلوم نیست؛ توضیح کلی را می‌بینی." else null,
            usedGeneralFallback = general,
            uncertaintyNoteFa = ctx.uncertaintyNoteFa
        )
    }

    private val NEGATIVE_MOODS = setOf("sad", "anxious", "irritable", "anxiety")

    /**
     * Care card. Today's real log wins over cycle timing:
     * heavy bleeding > pain ≥ 4 > hard mood > bloating/nausea > headache/breast tenderness > low energy/poor sleep > sub-window default.
     */
    fun selectCare(
        bank: ContentBank,
        ctx: CycleDayContext,
        restriction: PredictionRestriction,
        todayLog: DailyLogEntity?
    ): CareSelection {
        fun find(id: String) = bank.items.find { it.id == id }
        if (todayLog != null && !todayLog.noSymptoms) {
            val symptoms = todayLog.physicalSymptoms?.split(',')?.map { it.trim() }?.toSet() ?: emptySet()
            val moods = todayLog.moods?.split(',')?.map { it.trim() }?.toSet() ?: emptySet()
            val pick: Pair<String, String>? = when {
                todayLog.bleeding == "heavy" || todayLog.clots == "frequent" ->
                    "care_log_heavy" to "چون امروز خون‌ریزی زیاد ثبت کرده‌ای"
                (todayLog.painScore ?: 0) >= 4 || "pain" in symptoms ->
                    "care_log_pain" to "چون امروز درد ثبت کرده‌ای"
                moods.any { it in NEGATIVE_MOODS } ->
                    "care_log_mood" to "چون امروز حال روحی سخت‌تری ثبت کرده‌ای"
                "bloating" in symptoms || "nausea" in symptoms ->
                    "care_log_bloating" to "چون امروز نفخ یا علائم گوارشی ثبت کرده‌ای"
                "headache" in symptoms || "breast_tenderness" in symptoms ->
                    "care_log_headache" to "چون امروز سردرد یا حساسیت سینه ثبت کرده‌ای"
                todayLog.energy == "low" || todayLog.sleepQuality == "poor" || todayLog.fatigue == "high" ->
                    "care_log_sleep_energy" to "چون امروز انرژی کم یا خواب بد ثبت کرده‌ای"
                else -> null
            }
            if (pick != null) {
                find(pick.first)?.let { return CareSelection(it, pick.second) }
            }
        }
        val sw = when {
            ctx.fromLoggedBleeding -> ctx.subWindow
            CycleContentPolicy.forceGeneral(restriction) -> CycleSubWindow.UNKNOWN
            else -> ctx.subWindow
        }
        val id = when (sw) {
            CycleSubWindow.LUTEAL_LATE_PREMENSTRUAL -> "care_sw_luteal_late"
            CycleSubWindow.LATE_PERIOD -> "care_sw_late"
            else -> "care_sw_${sw.id}"
        }
        val item = find(id) ?: find("care_sw_unknown") ?: bank.items.first { it.category == BodyCategories.PATTERN_CARE }
        return CareSelection(item, null)
    }

    fun matchesSubWindow(item: ContentItem, subWindowId: String): Boolean {
        val rules = item.displayRules.showWhenSubWindow
        return rules.contains("any") || rules.contains(subWindowId)
    }
}
