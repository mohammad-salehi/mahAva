package com.mahava.app.content

import com.mahava.app.data.db.DailyLogEntity

/**
 * «رابطه» in the daily log and on Today. Stored in the existing log fields:
 * intimacyLogged + intimacyProtected ("yes" / "no"; "none" = she said she didn't).
 * Private: never shown to the husband's account, only to her and in the doctor report.
 */
object IntimacyLog {
    val OPTIONS = listOf("none" to "نداشتم", "protected" to "با محافظت", "unprotected" to "بدون محافظت")

    /** Fields that never go to the partner (app view, change lines, server share). */
    val PRIVATE_FIELDS = setOf("intimacyLogged", "intimacyProtected", "intimacyDiscomfort", "desire")

    /** The chosen option, or null (nothing chosen, or an older "logged" without protection info). */
    fun of(l: DailyLogEntity?): String? = when {
        l == null -> null
        l.intimacyLogged -> when (l.intimacyProtected) { "yes", "true" -> "protected"; "no", "false" -> "unprotected"; else -> null }
        l.intimacyProtected == "none" -> "none"
        else -> null
    }

    /** [choice] null clears it. Other intimacy fields are kept. */
    fun apply(l: DailyLogEntity, choice: String?): DailyLogEntity = when (choice) {
        "protected" -> l.copy(intimacyLogged = true, intimacyProtected = "yes")
        "unprotected" -> l.copy(intimacyLogged = true, intimacyProtected = "no")
        "none" -> l.copy(intimacyLogged = false, intimacyProtected = "none")
        else -> l.copy(intimacyLogged = false, intimacyProtected = null)
    }

    /** Doctor report text, only when she logged it. */
    fun reportFa(l: DailyLogEntity): String? {
        if (!l.intimacyLogged) return null
        return when (l.intimacyProtected) {
            "yes", "true" -> "رابطه با محافظت"
            "no", "false" -> "رابطه بدون محافظت"
            else -> "رابطه"
        }
    }
}
