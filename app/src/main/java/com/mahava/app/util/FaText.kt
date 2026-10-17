package com.mahava.app.util

/** Error text for the user: keeps a server message only when it is Persian, never raw English exception text. */
object FaText {
    fun orFallback(message: String?, fallback: String): String =
        message?.trim()?.takeIf { m -> m.isNotEmpty() && m.any { it in '\u0600'..'\u06FF' } } ?: fallback

    /** Phone numbers and codes read left-to-right even inside Persian text («۰۹۳۵***۴۴۱۲», not reversed). */
    fun ltr(text: String): String = "\u2066" + text + "\u2069"
}
