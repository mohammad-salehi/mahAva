package com.mahava.app

import com.mahava.app.util.JalaliConverter
import com.mahava.app.util.JalaliDate
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class JalaliTest {
    @Test fun nowruz1405() {
        val g = LocalDate.of(2026, 3, 21)
        val j = JalaliDate.from(g)
        assertEquals(1405, j.year)
        assertEquals(1, j.month)
        assertEquals(1, j.day)
        assertEquals(g, j.toLocalDate())
    }

    @Test fun roundTripSampleDates() {
        val samples = listOf(
            LocalDate.of(2024, 3, 20), // 1403-01-01
            LocalDate.of(2025, 3, 21),
            LocalDate.of(2020, 2, 29), // leap gregorian
            LocalDate.of(2023, 12, 31),
            LocalDate.of(1990, 6, 15)
        )
        for (g in samples) {
            val j = JalaliDate.from(g)
            assertEquals(g, j.toLocalDate())
        }
    }

    @Test fun leapEsfand30() {
        // 1399 was leap Jalali (Esfand 30 exists)
        val j = JalaliDate(1399, 12, 30)
        val g = j.toLocalDate()
        assertEquals(j, JalaliDate.from(g))
    }

    @Test fun timezoneDoesNotShiftEpochDay() {
        val d = LocalDate.of(2026, 10, 6)
        assertEquals(d.toEpochDay(), JalaliDate.from(d).toLocalDate().toEpochDay())
        // epoch day is timezone-independent by definition
        ZoneId.of("Asia/Tehran")
        ZoneId.of("America/Los_Angeles")
        assertEquals(d, LocalDate.ofEpochDay(d.toEpochDay()))
    }
}
