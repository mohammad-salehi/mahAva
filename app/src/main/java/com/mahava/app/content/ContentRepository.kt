package com.mahava.app.content

import android.content.Context
import com.google.gson.Gson
import com.mahava.app.cycle.CycleDayContext
import com.mahava.app.cycle.CyclePhase
import com.mahava.app.cycle.PredictionRestriction

class ContentRepository(private val context: Context, private val gson: Gson = Gson()) {
    @Volatile private var cached: ContentBank? = null

    fun load(): ContentBank {
        cached?.let { return it }
        val json = context.assets.open("content/content_bank.json").bufferedReader().use { it.readText() }
        val bank = gson.fromJson(json, ContentBank::class.java)
        cached = bank
        return bank
    }

    fun itemsForCategory(category: String): List<ContentItem> =
        load().items.filter { it.category == category }

    fun sourceById(id: String): ContentSource? =
        load().items.asSequence().flatMap { it.sources.asSequence() }.firstOrNull { it.id == id }

    fun item(id: String): ContentItem? = load().items.find { it.id == id }

    fun visibleItems(
        category: String? = null,
        phase: CyclePhase,
        pregnancyMode: Boolean,
        fertilityEnabled: Boolean,
        cycleDay: Int?,
        hiddenCategories: Set<String>,
        subWindowId: String? = null
    ): List<ContentItem> {
        return load().items.filter { item ->
            // Phase, restricted-context and care items are reached from Today/body home, not listed in categories.
            if (item.isPhase || item.isCare || item.isContext) return@filter false
            if (category != null && item.category != category) return@filter false
            if (item.category in hiddenCategories) return@filter false
            val rules = item.displayRules
            if (rules.hideWhenPregnancyMode && pregnancyMode) return@filter false
            if (rules.requiresFertilityEnabled && !fertilityEnabled) return@filter false
            val phaseKey = PhaseContentSelector.phaseKey(phase)
            val phaseOk = rules.showWhenPhase.contains("any") || rules.showWhenPhase.contains(phaseKey)
            if (!phaseOk) return@filter false
            if (subWindowId != null && !PhaseContentSelector.matchesSubWindow(item, subWindowId)) {
                // allow educational items tagged any
                if (!rules.showWhenSubWindow.contains("any") && rules.showWhenSubWindow.isNotEmpty()) return@filter false
            }
            if (rules.minCycleDay != null && (cycleDay == null || cycleDay < rules.minCycleDay)) return@filter false
            if (rules.maxCycleDay != null && (cycleDay == null || cycleDay > rules.maxCycleDay)) return@filter false
            true
        }
    }

    fun selectPhaseForToday(ctx: CycleDayContext, restriction: PredictionRestriction): PhaseContentSelection =
        PhaseContentSelector.selectPhase(load(), ctx, restriction)

    fun selectCareForToday(ctx: CycleDayContext, restriction: PredictionRestriction, todayLog: com.mahava.app.data.db.DailyLogEntity?): CareSelection =
        PhaseContentSelector.selectCare(load(), ctx, restriction, todayLog)

    fun validateNoFabricatedReview(): Boolean {
        val bank = load()
        val forbidden = listOf("بازبینی پزشکی انجام شده", "تأیید شده توسط پزشک", "approved by physician")
        if (forbidden.any { bank.medicalReviewStatus.contains(it, ignoreCase = true) }) return false
        return bank.items.all { item ->
            item.medicalReviewStatus.contains("بازبینی پزشکی مستقل انجام نشده") &&
                item.sources.all { it.url.startsWith("https://") } &&
                item.sources.none { it.url.contains("example.com") }
        }
    }
}
