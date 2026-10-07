package com.mahava.app

import com.google.gson.JsonObject
import com.mahava.app.content.PartnerAdvice
import com.mahava.app.content.PartnerLogFormat
import com.mahava.app.content.ScienceSources
import com.mahava.app.network.PartnerSnapshotDto
import com.mahava.app.network.PartnerTodayDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PartnerContentTest {
    private val groups = listOf("menstrual", "follicular", "fertile", "early_luteal", "late_luteal", "general")

    @Test fun widgetWordIsOneSimpleWord() {
        val expected = mapOf(
            "menstrual" to "پریود", "follicular" to "پرانرژی", "fertile" to "تخمک‌گذاری",
            "early_luteal" to "آرام", "late_luteal" to "پیش‌پریود", "general" to "نامعلوم"
        )
        expected.forEach { (g, w) -> assertEquals(w, PartnerAdvice.oneWordFa(PartnerSnapshotDto(phaseGroup = g))) }
        assertEquals("پریود", PartnerAdvice.oneWordFa(PartnerSnapshotDto(phaseGroup = "follicular", periodOngoing = true)))
        assertEquals("تأخیر", PartnerAdvice.oneWordFa(PartnerSnapshotDto(phaseGroup = "late_luteal", isLate = true)))
        groups.forEach { g -> assertFalse(PartnerAdvice.oneWordFa(PartnerSnapshotDto(phaseGroup = g)).contains(' ')) }
    }

    @Test fun noLogToday_showsSourcedPhaseScience_withoutAskHerOrNotEnoughData() {
        for (g in groups) {
            val items = PartnerAdvice.phaseScience(g)
            assertTrue(g, items.isNotEmpty())
            assertTrue(g, items.map { it.kind }.containsAll(listOf("food", "mood", "body")))
            items.forEach { assertTrue("$g ${it.key} has sources", ScienceSources.list(it.explanation.sourceIds).isNotEmpty()) }
            val advice = PartnerAdvice.forSnapshot(PartnerSnapshotDto(phaseGroup = g, generalOnly = g == "general"))
            val text = (listOf(advice.headlineFa, advice.statusFa) + advice.sections.flatMap { listOf(it.titleFa, it.bodyFa) } + advice.doFa).joinToString(" ")
            for (bad in listOf("بپرس", "پرسیدن از خودش", "کافی ثبت نشده", "معلوم نیست", "همراه")) {
                assertFalse("$g contains '$bad'", text.contains(bad))
            }
            assertTrue(ScienceSources.list(advice.sourceIds).isNotEmpty())
        }
    }

    @Test fun changeLinesUseSpouseWordAndCoverAllCategories() {
        val snap = PartnerSnapshotDto(phaseGroup = "menstrual", periodOngoing = true,
            today = PartnerTodayDto(moods = listOf("sad"), symptoms = listOf("pain"), cravings = listOf("chocolate"), painScore = 5))
        val line = PartnerAdvice.changeLineFa(listOf("period", "mood", "symptoms", "pain", "cravings", "flow", "note", "intimacy"), snap)
        assertTrue(line.startsWith("همسرت"))
        listOf("پریودش شروع شد", "غمگین", "درد ۵ از ۱۰", "شکلات", "خون‌ریزی", "یادداشت", "رابطه").forEach { assertTrue(it, line.contains(it)) }
        assertTrue(PartnerAdvice.dailyLineFa(snap).startsWith("همسرت"))
    }

    @Test fun logFormatShowsEveryField_includingUnknownOnes() {
        val o = JsonObject().apply {
            addProperty("epochDay", 20000); addProperty("note", "سلام"); addProperty("intimacyLogged", true)
            addProperty("desire", "high"); addProperty("bleeding", "spotting"); addProperty("sleepHours", 7.5)
            addProperty("someFutureField", "x"); addProperty("createdAt", 1L)
        }
        val labels = PartnerLogFormat.lines(o).associate { it.label to it.value }
        assertEquals("سلام", labels["یادداشت"])
        assertEquals("ثبت کرده", labels["رابطه"])
        assertEquals("زیاد", labels["میل جنسی"])
        assertEquals("لکه‌بینی", labels["خون‌ریزی"])
        assertTrue(labels.getValue("ساعت خواب").startsWith("۷"))
        assertEquals("x", labels["someFutureField"])
        assertFalse(labels.containsKey("createdAt"))
    }
}
