package com.mahava.app.util

import java.time.LocalDate

data class JalaliDate(val year: Int, val month: Int, val day: Int) : Comparable<JalaliDate> {
    init {
        require(month in 1..12) { "month out of range: $month" }
        require(day in 1..daysInMonth(year, month)) { "day out of range: $day for $year/$month" }
    }

    override fun compareTo(other: JalaliDate): Int =
        compareValuesBy(this, other, { it.year }, { it.month }, { it.day })

    fun toLocalDate(): LocalDate = JalaliConverter.toGregorian(this)

    fun plusDays(days: Long): JalaliDate = JalaliConverter.fromGregorian(toLocalDate().plusDays(days))

    fun formatFa(withYear: Boolean = true): String {
        val d = PersianDigits.toPersian(day)
        val mName = monthNameFa(month)
        return if (withYear) "$d $mName ${PersianDigits.toPersian(year)}" else "$d $mName"
    }

    fun formatShortFa(): String = "${PersianDigits.toPersian(day)} ${monthNameFa(month)}"

    companion object {
        fun from(localDate: LocalDate): JalaliDate = JalaliConverter.fromGregorian(localDate)

        fun monthNameFa(month: Int): String = when (month) {
            1 -> "فروردین"; 2 -> "اردیبهشت"; 3 -> "خرداد"
            4 -> "تیر"; 5 -> "مرداد"; 6 -> "شهریور"
            7 -> "مهر"; 8 -> "آبان"; 9 -> "آذر"
            10 -> "دی"; 11 -> "بهمن"; 12 -> "اسفند"
            else -> "?"
        }

        fun weekdayNameFa(localDate: LocalDate): String = when (localDate.dayOfWeek.value) {
            6 -> "شنبه"; 7 -> "یکشنبه"; 1 -> "دوشنبه"; 2 -> "سه‌شنبه"
            3 -> "چهارشنبه"; 4 -> "پنجشنبه"; 5 -> "جمعه"
            else -> ""
        }

        fun isLeap(year: Int): Boolean = JalaliConverter.isLeapJalali(year)

        fun daysInMonth(year: Int, month: Int): Int = when (month) {
            in 1..6 -> 31
            in 7..11 -> 30
            12 -> if (isLeap(year)) 30 else 29
            else -> error("bad month")
        }
    }
}

/**
 * In-house civil Jalali conversion (legacy algorithm used by many open-source ports).
 * Leap years detected by year length (Farvardin 1 → next Farvardin 1 == 366),
 * avoiding circular dependency with Esfand length.
 */
object JalaliConverter {
    fun isLeapJalali(jy: Int): Boolean {
        val a = toGregorianParts(jy, 1, 1)
        val b = toGregorianParts(jy + 1, 1, 1)
        return epochDay(b[0], b[1], b[2]) - epochDay(a[0], a[1], a[2]) == 366L
    }

    fun fromGregorian(g: LocalDate): JalaliDate {
        val p = toJalaliParts(g.year, g.monthValue, g.dayOfMonth)
        return JalaliDate(p[0], p[1], p[2])
    }

    fun toGregorian(j: JalaliDate): LocalDate {
        val p = toGregorianParts(j.year, j.month, j.day)
        return LocalDate.of(p[0], p[1], p[2])
    }

    private fun div(a: Int, b: Int) = a / b
    private fun mod(a: Int, b: Int): Int {
        val r = a % b
        return if (r < 0) r + b else r
    }

    private fun toJalaliParts(gy: Int, gm: Int, gd: Int): IntArray {
        val g_d_m = intArrayOf(0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334)
        var jy = if (gy <= 1600) 0 else 979
        val gy2 = if (gy <= 1600) gy - 621 else gy - 1600
        val gy3 = if (gm > 2) gy2 + 1 else gy2
        var days = 365 * gy2 + div(gy3 + 3, 4) - div(gy3 + 99, 100) + div(gy3 + 399, 400) - 80 + gd + g_d_m[gm - 1]
        jy += 33 * div(days, 12053)
        days = mod(days, 12053)
        jy += 4 * div(days, 1461)
        days = mod(days, 1461)
        if (days > 365) {
            jy += div(days - 1, 365)
            days = mod(days - 1, 365)
        }
        val jm: Int
        val jd: Int
        if (days < 186) {
            jm = 1 + div(days, 31)
            jd = 1 + mod(days, 31)
        } else {
            jm = 7 + div(days - 186, 30)
            jd = 1 + mod(days - 186, 30)
        }
        return intArrayOf(jy, jm, jd)
    }

    private fun toGregorianParts(jy: Int, jm: Int, jd: Int): IntArray {
        var gy = if (jy <= 979) 621 else 1600
        val jy2 = if (jy <= 979) jy else jy - 979
        var days = 365 * jy2 + div(jy2, 33) * 8 + div(mod(jy2, 33) + 3, 4) + 78 + jd +
            if (jm < 7) (jm - 1) * 31 else ((jm - 7) * 30 + 186)
        gy += 400 * div(days, 146097)
        days = mod(days, 146097)
        if (days > 36524) {
            gy += 100 * div(--days, 36524)
            days = mod(days, 36524)
            if (days >= 365) days++
        }
        gy += 4 * div(days, 1461)
        days = mod(days, 1461)
        if (days > 365) {
            gy += div(days - 1, 365)
            days = mod(days - 1, 365)
        }
        var gd = days + 1
        val sal_a = intArrayOf(
            0, 31,
            if ((gy % 4 == 0 && gy % 100 != 0) || (gy % 400 == 0)) 29 else 28,
            31, 30, 31, 30, 31, 31, 30, 31, 30, 31
        )
        var gm = 0
        while (gm < 13 && gd > sal_a[gm]) {
            gd -= sal_a[gm]
            gm++
        }
        return intArrayOf(gy, gm, gd)
    }

    private fun epochDay(y: Int, m: Int, d: Int): Long = LocalDate.of(y, m, d).toEpochDay()
}
