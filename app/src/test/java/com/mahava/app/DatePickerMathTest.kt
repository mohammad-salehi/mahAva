package com.mahava.app

import com.mahava.app.ui.components.DatePickerMath
import com.mahava.app.util.JalaliDate
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class DatePickerMathTest {
    private val d = LocalDate.of(2026, 10, 6) // ۱۴ مهر ۱۴۰۵

    @Test fun jalaliMonthStartTitleLength() {
        assertEquals(LocalDate.of(2026, 9, 23), DatePickerMath.monthStart(d, true)) // ۱ مهر ۱۴۰۵
        assertEquals("مهر ۱۴۰۵", DatePickerMath.monthTitle(d, true))
        assertEquals(30, DatePickerMath.monthLength(d, true))
        assertEquals(14, DatePickerMath.dayNumber(d, true))
        assertEquals(30, DatePickerMath.monthLength(LocalDate.of(2025, 3, 1), true)) // اسفند ۱۴۰۳ (کبیسه)
    }

    @Test fun jalaliShiftAcrossYear() {
        assertEquals(LocalDate.of(2026, 10, 23), DatePickerMath.shiftMonth(d, 1, true)) // ۱ آبان
        assertEquals(JalaliDate(1404, 12, 1), JalaliDate.from(DatePickerMath.shiftMonth(LocalDate.of(2026, 3, 25), -1, true)))
        assertEquals(JalaliDate(1405, 1, 1), JalaliDate.from(DatePickerMath.shiftMonth(LocalDate.of(2026, 3, 1), 1, true)))
    }

    @Test fun gridIsSaturdayFirstAndComplete() {
        val g = DatePickerMath.monthGrid(d, true)
        assertEquals(0, g.size % 7)
        assertEquals(4, g.indexOfFirst { it != null }) // ۱ مهر ۱۴۰۵ = Wednesday → 5th column (ش ی د س چ)
        assertEquals(30, g.count { it != null })
        assertEquals(LocalDate.of(2026, 9, 23), g[4])
        val greg = DatePickerMath.monthGrid(d, false)
        assertEquals(LocalDate.of(2026, 10, 1), greg.first { it != null })
        assertEquals(5, greg.indexOfFirst { it != null }) // 1 Oct 2026 = Thursday
        assertEquals("اکتبر ۲۰۲۶", DatePickerMath.monthTitle(d, false))
    }

    @Test fun roundTripEveryDayOfYear() {
        var x = LocalDate.of(2025, 3, 1)
        repeat(800) {
            val j = JalaliDate.from(x)
            assertEquals(x, j.toLocalDate())
            assertEquals(x, DatePickerMath.monthStart(x, true).plusDays((j.day - 1).toLong()))
            x = x.plusDays(1)
        }
    }
}
