package com.mahava.app.health

import com.mahava.app.data.db.DailyLogEntity
import com.mahava.app.data.db.PeriodEventEntity
import com.mahava.app.data.db.UserProfileEntity
import com.mahava.app.health.models.AlertSeverity
import com.mahava.app.health.models.AlertStatus
import com.mahava.app.health.models.DateOnlyFormat
import com.mahava.app.health.models.HealthAlert
import com.mahava.app.util.PersianDigits
import java.time.LocalDate
import java.util.UUID

data class RuleConfig(
    val ruleId: String,
    val ruleVersion: Int,
    val titleFa: String,
    val evidenceIds: List<String>,
    val severity: AlertSeverity
)

/**
 * Deterministic, explainable health pattern rules. Not a diagnosis engine.
 * Cycle rules are disabled while pregnancy mode / active pregnancy is on.
 */
object HealthRuleEngine {

    val RULE_SHORT_CYCLE = RuleConfig(
        "cycle_short", 2, "چرخهٔ کوتاه‌تر از حد معمول این بازه",
        listOf("acog_aub", "acog_hmb"), AlertSeverity.ATTENTION
    )
    val RULE_LONG_CYCLE = RuleConfig(
        "cycle_long", 2, "چرخهٔ بلندتر از حد معمول این بازه",
        listOf("acog_aub"), AlertSeverity.ATTENTION
    )
    val RULE_BLEED_OVER_7 = RuleConfig(
        "bleed_over_7", 2, "خون‌ریزی طولانی‌تر از ۷ روز",
        listOf("acog_hmb", "acog_aub"), AlertSeverity.ATTENTION
    )
    val RULE_BLEED_OVER_8 = RuleConfig(
        "bleed_over_8", 2, "خون‌ریزی طولانی‌تر از ۸ روز",
        listOf("acog_hmb"), AlertSeverity.ATTENTION
    )
    val RULE_PAIN_SEVERE_TODAY = RuleConfig(
        "pain_severe_today", 2, "درد لگن شدید امروز",
        listOf("acog_cramps"), AlertSeverity.URGENT_GUIDANCE
    )
    val RULE_PAIN_SEVERE_STREAK = RuleConfig(
        "pain_severe_streak", 2, "درد لگن شدید در روزهای پیاپی",
        listOf("acog_cramps"), AlertSeverity.URGENT_GUIDANCE
    )

    var shortCycleMaxExclusive: Int = 21
    var longCycleMinExclusive: Int = 35
    var bleedScientificDays: Int = 7
    var bleedProductDays: Int = 8
    var severePainThreshold: Int = 7

    fun evaluate(
        profile: UserProfileEntity?,
        periods: List<PeriodEventEntity>,
        logs: List<DailyLogEntity>,
        today: LocalDate,
        pregnancyActive: Boolean,
        nowMillis: Long
    ): List<HealthAlert> {
        if (pregnancyActive || profile?.pregnancyMode == true) return emptyList()
        if (profile?.postpartumOrBreastfeeding == true) {
            // Soften: still allow pain rules; skip cycle-length population norms.
            return painAlerts(logs, today, nowMillis)
        }
        val out = mutableListOf<HealthAlert>()
        // Population 21–35 day band is not applied under hormonal contraception or teen band.
        val applyCycleNorms = profile?.hormonalContraception != true && profile?.ageBand != "teen"
        if (applyCycleNorms) out += cycleLengthAlerts(periods, nowMillis)
        out += bleedAlerts(periods, nowMillis)
        out += painAlerts(logs, today, nowMillis)
        return out.distinctBy { it.deduplicationKey }
    }

    private fun cycleLengthAlerts(periods: List<PeriodEventEntity>, now: Long): List<HealthAlert> {
        val lengths = CycleAnalyticsService.completedCycleLengths(periods)
        val last = lengths.lastOrNull() ?: return emptyList()
        val lastStart = periods.sortedBy { it.startEpochDay }.zipWithNext().lastOrNull()?.first?.startEpochDay
            ?: return emptyList()
        val date = DateOnlyFormat.fromEpochDay(lastStart)
        return buildList {
            if (last < shortCycleMaxExclusive) {
                add(
                    alert(
                        RULE_SHORT_CYCLE,
                        "آخرین چرخهٔ کامل‌شده‌ات ${PersianDigits.toPersian(last)} روز بود (کمتر از ${PersianDigits.toPersian(shortCycleMaxExclusive)}).",
                        "چرخه‌های کوتاه گاهی طبیعی‌اند، ولی اگر تکرار شوند یا با خون‌ریزی/درد شدید همراه باشند، بهتر است با پزشک حرف بزنی. این بازه برای همهٔ سنین و شرایط یکسان نیست.",
                        "ثبت چرخه‌های بعدی را ادامه بده و اگر نگران هستی، با پزشک مشورت کن. این پیام تشخیص پزشکی نیست.",
                        listOf(date),
                        "cycle_short:$lastStart:$last",
                        now
                    )
                )
            }
            if (last > longCycleMinExclusive) {
                add(
                    alert(
                        RULE_LONG_CYCLE,
                        "آخرین چرخهٔ کامل‌شده‌ات ${PersianDigits.toPersian(last)} روز بود (بیشتر از ${PersianDigits.toPersian(longCycleMinExclusive)}).",
                        "چرخهٔ بلند می‌تواند به استرس، تغییر وزن، بیماری یا عوامل دیگر مربوط باشد. یک بار طولانی بودن به‌تنهایی تشخیص نیست.",
                        "اگر تأخیر تکرار شد یا تست بارداری مثبت/مشکوک بود، با پزشک صحبت کن.",
                        listOf(date),
                        "cycle_long:$lastStart:$last",
                        now
                    )
                )
            }
        }
    }

    private fun bleedAlerts(periods: List<PeriodEventEntity>, now: Long): List<HealthAlert> {
        return periods.mapNotNull { p ->
            val end = p.endEpochDay ?: return@mapNotNull null
            val len = (end - p.startEpochDay + 1).toInt()
            val date = DateOnlyFormat.fromEpochDay(p.startEpochDay)
            when {
                len > bleedProductDays -> alert(
                    RULE_BLEED_OVER_8,
                    "یک دوره خون‌ریزی ${PersianDigits.toPersian(len)} روزه ثبت کرده‌ای (بیشتر از ${PersianDigits.toPersian(bleedProductDays)} روز).",
                    "خون‌ریزی خیلی طولانی ممکن است نیاز به بررسی داشته باشد. این فقط بر اساس ثبت خودت است.",
                    "اگر خون‌ریزی ادامه دارد، لخته‌های بزرگ داری یا ضعف شدید حس می‌کنی، زودتر با پزشک تماس بگیر.",
                    listOf(date),
                    "bleed_over_8:${p.startEpochDay}:$len",
                    now
                )
                len > bleedScientificDays -> alert(
                    RULE_BLEED_OVER_7,
                    "یک دوره خون‌ریزی ${PersianDigits.toPersian(len)} روزه ثبت کرده‌ای (بیشتر از ${PersianDigits.toPersian(bleedScientificDays)} روز).",
                    "بسیاری از راهنماها خون‌ریزی بیش از حدود یک هفته را شایستهٔ توجه می‌دانند؛ برای هر نفر فرق دارد.",
                    "الگو را چند چرخه دنبال کن و در صورت تکرار یا نگرانی با پزشک مشورت کن.",
                    listOf(date),
                    "bleed_over_7:${p.startEpochDay}:$len",
                    now
                )
                else -> null
            }
        }
    }

    private fun painAlerts(logs: List<DailyLogEntity>, today: LocalDate, now: Long): List<HealthAlert> {
        val byDay = logs.associateBy { it.epochDay }
        val todayLog = byDay[today.toEpochDay()]
        val out = mutableListOf<HealthAlert>()
        val todayPain = todayLog?.painScore
        if (todayPain != null && todayPain >= severePainThreshold) {
            out += alert(
                RULE_PAIN_SEVERE_TODAY,
                "امروز درد لگن را ${PersianDigits.toPersian(todayPain)} از ۱۰ ثبت کرده‌ای.",
                "درد شدید می‌تواند علت‌های مختلفی داشته باشد. این آستانه یک معیار عملی اپ است، نه استاندارد تشخیصی پزشکی.",
                "اگر درد ناگهانی، یک‌طرفه، با تب، استفراغ یا ضعف شدید است، همین الان کمک پزشکی بگیر. در غیر این صورت استراحت، گرما و مشورت با پزشک را در نظر بگیر.",
                listOf(DateOnlyFormat.fromLocalDate(today)),
                "pain_today:${today.toEpochDay()}:$todayPain",
                now
            )
        }
        // Consecutive calendar days with logged severe pain (missing day breaks streak).
        var streakEnd: LocalDate? = null
        var streakStart: LocalDate? = null
        var d = today
        while (true) {
            val score = byDay[d.toEpochDay()]?.painScore
            if (score == null || score < severePainThreshold) break
            streakEnd = streakEnd ?: d
            streakStart = d
            d = d.minusDays(1)
        }
        if (streakStart != null && streakEnd != null) {
            val days = streakStart!!.until(streakEnd!!).days + 1
            if (days >= 2) {
                out += alert(
                    RULE_PAIN_SEVERE_STREAK,
                    "حداقل ${PersianDigits.toPersian(days)} روز تقویمی پیاپی درد لگن شدید (≥${PersianDigits.toPersian(severePainThreshold)}) ثبت کرده‌ای.",
                    "روزهایی که ثبت نکرده‌ای به‌عنوان «بدون درد» حساب نشده‌اند؛ فقط روزهای ثبت‌شده با درد شدید در این الگو هستند.",
                    "با پزشک دربارهٔ درد مداوم حرف بزن. اگر علائم هشدار (تب، غش، خون‌ریزی خیلی زیاد) داری، فوریت پزشکی را در نظر بگیر.",
                    (0 until days).map { DateOnlyFormat.fromLocalDate(streakStart!!.plusDays(it.toLong())) },
                    "pain_streak:${streakStart!!.toEpochDay()}:${streakEnd!!.toEpochDay()}",
                    now
                )
            }
        }
        return out
    }

    private fun alert(
        cfg: RuleConfig,
        evidenceFa: String,
        meaningFa: String,
        actionFa: String,
        dates: List<String>,
        dedupe: String,
        now: Long
    ): HealthAlert = HealthAlert(
        id = UUID.nameUUIDFromBytes(dedupe.toByteArray()).toString(),
        ruleId = cfg.ruleId,
        ruleVersion = cfg.ruleVersion,
        severity = cfg.severity,
        titleFa = cfg.titleFa,
        plainLanguageExplanationFa = "$evidenceFa $meaningFa",
        recommendedActionFa = actionFa,
        observedDates = dates,
        evidenceReferences = cfg.evidenceIds,
        deduplicationKey = dedupe,
        status = AlertStatus.ACTIVE,
        createdAt = now
    )
}
