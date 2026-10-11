package com.mahava.app.health

import com.mahava.app.data.db.DailyLogEntity
import com.mahava.app.data.db.PeriodEventEntity
import com.mahava.app.data.db.UserProfileEntity
import java.time.LocalDate

/**
 * Deterministic fixtures for unit tests only. Never written into a real user account.
 */
object HealthMockFixtures {
    val FIXED_TODAY: LocalDate = LocalDate.of(2026, 6, 15)
    const val NOW_MS = 1_750_000_000_000L

    fun profile(
        pregnancyMode: Boolean = false,
        hormonal: Boolean = false,
        postpartum: Boolean = false,
        ageBand: String? = "adult"
    ) = UserProfileEntity(
        onboardingDone = true,
        typicalCycleLength = 28,
        typicalBleedLength = 5,
        hormonalContraception = hormonal,
        postpartumOrBreastfeeding = postpartum,
        ageBand = ageBand,
        pregnancyMode = pregnancyMode,
        createdAt = NOW_MS,
        updatedAt = NOW_MS
    )

    /** Six completed cycles + current open cycle starting 10 days before FIXED_TODAY. */
    fun periodsWithShortAndLong(): List<PeriodEventEntity> {
        val t = FIXED_TODAY.toEpochDay()
        // starts going back: lengths between starts: 28,20,36,28,28,28 then current
        val starts = listOf(
            t - 10, // current
            t - 10 - 28,
            t - 10 - 28 - 28,
            t - 10 - 28 - 28 - 28,
            t - 10 - 28 - 28 - 28 - 36, // long 36
            t - 10 - 28 - 28 - 28 - 36 - 20, // short 20
            t - 10 - 28 - 28 - 28 - 36 - 20 - 28
        ).sorted()
        return starts.mapIndexed { i, s ->
            val bleed = if (i == 1) 9 else if (i == 2) 8 else 5 // long bleeds on older cycles
            PeriodEventEntity(
                id = i + 1L,
                startEpochDay = s,
                endEpochDay = if (i == starts.lastIndex) null else s + bleed - 1,
                stillOngoing = i == starts.lastIndex,
                createdAt = NOW_MS,
                updatedAt = NOW_MS
            )
        }
    }

    fun logsWithPainStreakAndGaps(): List<DailyLogEntity> {
        val t = FIXED_TODAY.toEpochDay()
        val out = mutableListOf<DailyLogEntity>()
        // 30 days with some missing
        for (back in 0..29) {
            if (back in listOf(3, 7, 11, 18)) continue // gaps
            val day = t - back
            val pain = when (back) {
                0, 1 -> 8 // today + yesterday severe streak
                5 -> 7
                else -> if (back % 5 == 0) 3 else null
            }
            out += DailyLogEntity(
                id = back + 1L,
                epochDay = day,
                painScore = pain,
                moods = if (back % 4 == 0) "anxious" else null,
                physicalSymptoms = if (back % 6 == 0) "bloating" else null,
                waterIntakeMl = if (back % 3 == 0) 1500 else null,
                sleepMinutes = if (back % 2 == 0) 420 else null,
                bleeding = if (back in 10..14) "medium" else null,
                createdAt = NOW_MS,
                updatedAt = NOW_MS
            )
        }
        return out
    }
}
