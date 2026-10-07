package com.mahava.app.ui.navigation

object Routes {
    const val ONBOARDING = "onboarding"
    const val TODAY = "today"
    const val CALENDAR = "calendar"
    const val LOG_HUB = "log_hub"
    const val DAILY_LOG = "daily_log?day={day}"
    const val PERIOD_LOG = "period_log"
    const val REPORTS = "reports"
    const val BODY = "body"
    const val BODY_CAT = "body_cat/{category}"
    const val BODY_ITEM = "body_item/{id}"
    const val LATE = "late"
    const val CARE = "care"
    const val SETTINGS = "settings"
    const val LOGIN = "login"
    const val REGISTER = "register"
    const val PASSWORD_RECOVERY = "password_recovery"
    const val ACCOUNT = "account"
    const val DOCTOR_PDF = "doctor_pdf"
    const val PHASE_DETAIL = "phase/{id}"
    const val DAILY_INSIGHT = "daily_insight"
    const val CRAVING_DETAIL = "craving_detail"
    const val PHASE_FORECAST = "phase_forecast"
    const val SYMPTOM_PATTERNS = "symptom_patterns"
    const val CYCLE_TRENDS = "cycle_trends"
    const val SYMPTOM_CHECKER = "symptom_checker"
    const val PHASE_SCIENCE = "phase_science"
    const val QUICK_LOG = "quick_log"
    const val FOOD_TIPS = "food_tips"
    const val FERTILITY_WINDOW = "fertility_window"
    const val SIGNAL_DETAIL = "signal/{kind}/{key}"
    /** Woman: pair with a partner, approve, remove. */
    const val PARTNER_HUB = "partner_hub"
    /** Man: the partner home (start screen for male accounts). */
    const val PARTNER_HOME = "partner_home"

    fun dailyLog(day: Long) = "daily_log?day=$day"
    fun bodyCat(cat: String) = "body_cat/$cat"
    fun bodyItem(id: String) = "body_item/$id"
    fun phaseDetail(id: String) = "phase/$id"
    fun signalDetail(kind: String, key: String) = "signal/$kind/$key"

    /** Screens allowed before the user has logged in (after onboarding). */
    val authOnly = setOf(LOGIN, REGISTER, PASSWORD_RECOVERY)

    /** Cycle-owner screens a male (partner) account should not land on. */
    val womanMain = setOf(TODAY, CALENDAR, LOG_HUB, REPORTS, BODY)
}
