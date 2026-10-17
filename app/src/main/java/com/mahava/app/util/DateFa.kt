package com.mahava.app.util

import java.time.LocalDate

/** Shows stored ISO dates (yyyy-MM-dd) as Persian Jalali dates; anything else gets Persian digits. */
object DateFa {
    fun iso(value: String): String {
        val text = value.trim()
        if (text.isEmpty()) return "—"
        return runCatching { JalaliDate.from(LocalDate.parse(text.take(10))).formatFa() }
            .getOrElse { PersianDigits.toPersian(text) }
    }
}
