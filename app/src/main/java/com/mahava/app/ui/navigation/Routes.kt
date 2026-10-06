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
    const val DOCTOR_PDF = "doctor_pdf"
    const val PHASE_DETAIL = "phase/{id}"

    fun dailyLog(day: Long) = "daily_log?day=$day"
    fun bodyCat(cat: String) = "body_cat/$cat"
    fun bodyItem(id: String) = "body_item/$id"
    fun phaseDetail(id: String) = "phase/$id"
}
