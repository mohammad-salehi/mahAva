package com.mahava.app

import com.mahava.app.data.db.DailyLogEntity
import com.mahava.app.data.db.PeriodEventEntity
import com.mahava.app.pattern.PersonalPatternEngine
import org.junit.Assert.*
import org.junit.Test

class PersonalPatternTest {
    private fun period(start: Long, len: Long = 5) =
        PeriodEventEntity(startEpochDay = start, endEpochDay = start + len - 1, createdAt = 0, updatedAt = 0)

    private fun log(day: Long, body: String? = null, mood: String? = null, food: String? = null, pain: Int? = null) =
        DailyLogEntity(epochDay = day, physicalSymptoms = body, moods = mood, foodCravings = food, painScore = pain, createdAt = 0, updatedAt = 0)

    private val periods = listOf(period(1000), period(1028), period(1056), period(1084))

    @Test
    fun headacheTwoDaysBeforePeriodIsFound() {
        val logs = listOf(log(1026, body = "headache"), log(1054, body = "headache"), log(1082, body = "headache"))
        val p = PersonalPatternEngine.forSignal("body", "headache", periods, logs)
        assertNotNull(p)
        p!!
        assertEquals("late_luteal", p.dominantPhase)
        assertEquals(2, p.typicalDaysBefore)
        assertEquals("در ۳ چرخهٔ اخیر، معمولاً ۲ روز قبل از پریود «سردرد» را ثبت کردی.", p.textFa)
        val today = PersonalPatternEngine.forPhase("late_luteal", periods, logs)
        assertEquals(listOf("headache"), today.map { it.key })
        assertTrue(PersonalPatternEngine.forPhase("follicular", periods, logs).isEmpty())
    }

    @Test
    fun needsTwoCompleteCycles() {
        val two = listOf(period(1000), period(1028))
        val logs = listOf(log(1026, body = "headache"))
        assertNull(PersonalPatternEngine.forSignal("body", "headache", two, logs))
        assertNull(PersonalPatternEngine.cycleSummary(two))
        // Present in only one of three cycles -> not a pattern yet.
        assertNull(PersonalPatternEngine.forSignal("body", "headache", periods, logs))
    }

    @Test
    fun painScoreAndMenstrualDayAndScatteredMood() {
        val logs = listOf(
            log(1001, pain = 6), log(1029, pain = 5), log(1057, pain = 7),
            log(1010, mood = "sad"), log(1050, mood = "sad")
        )
        val pain = PersonalPatternEngine.forSignal("body", "pain", periods, logs)!!
        assertEquals("menstrual", pain.dominantPhase)
        assertTrue(pain.textFa.contains("روز ۲ پریود"))
        val sad = PersonalPatternEngine.forSignal("mood", "sad", periods, logs)!!
        assertNull(sad.dominantPhase)
        assertTrue(sad.textFa.contains("هر بار فرق داشت"))
        val summary = PersonalPatternEngine.cycleSummary(periods)!!
        assertEquals(28, summary.medianCycle)
        assertEquals(5, summary.medianBleed)
    }
}
