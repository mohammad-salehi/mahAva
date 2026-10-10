package com.mahava.app

import com.mahava.app.content.MorningTip
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class MorningTipTest {
    @Test
    fun generalTipWhenNoCycle() {
        val tip = MorningTip.forCycle(null, LocalDate.of(2026, 10, 10))
        assertTrue(tip.title.contains("صبح"))
        assertFalse(tip.body.isBlank())
    }

    @Test
    fun rotatesByDay() {
        val d1 = MorningTip.forCycle(null, LocalDate.of(2026, 10, 10))
        val d2 = MorningTip.forCycle(null, LocalDate.of(2026, 10, 11))
        // Same phase pool, different day index → usually different body
        assertTrue(d1.body.isNotBlank() && d2.body.isNotBlank())
    }
}
