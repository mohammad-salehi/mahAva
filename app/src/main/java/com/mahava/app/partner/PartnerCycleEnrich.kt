package com.mahava.app.partner

import com.google.gson.JsonObject
import com.mahava.app.network.PartnerSnapshotDto
import com.mahava.app.network.PartnerTodayDto

/**
 * Fills in cycle day / phase for the husband view when the server snapshot is thin
 * (no daily log, no client push) but her period history is already in the share payload.
 * Same rough rules as the backend deriveCycle helper.
 */
object PartnerCycleEnrich {

    fun enrich(
        snap: PartnerSnapshotDto?,
        periods: List<JsonObject>,
        todayEpoch: Long
    ): PartnerSnapshotDto? {
        val derived = deriveFromPeriods(periods, todayEpoch)
        if (snap == null) return derived
        // Snapshot already has a real cycle day — keep it (and its phase).
        if (snap.cycleDay != null && snap.cycleDay > 0 && !snap.generalOnly) {
            return snap.copy(today = snap.today ?: emptyToday(todayEpoch))
        }
        if (derived == null) {
            return snap.copy(today = snap.today ?: emptyToday(todayEpoch))
        }
        val needCycle = snap.cycleDay == null || snap.cycleDay <= 0
        val needPhase = !snap.generalOnly &&
            (snap.phaseGroup.isBlank() || snap.phaseGroup == "general" || needCycle)
        // Merge: keep today's log from snap; fill cycle day/phase from her periods when missing.
        return snap.copy(
            phaseGroup = when {
                snap.generalOnly -> "general"
                snap.periodOngoing -> "menstrual"
                needPhase -> derived.phaseGroup
                else -> snap.phaseGroup
            },
            cycleDay = snap.cycleDay?.takeIf { it > 0 } ?: derived.cycleDay,
            cycleLength = snap.cycleLength ?: derived.cycleLength,
            daysUntilPeriod = snap.daysUntilPeriod ?: derived.daysUntilPeriod,
            nextPeriodEpochDay = snap.nextPeriodEpochDay ?: derived.nextPeriodEpochDay,
            fertileStartEpochDay = snap.fertileStartEpochDay ?: derived.fertileStartEpochDay,
            fertileEndEpochDay = snap.fertileEndEpochDay ?: derived.fertileEndEpochDay,
            periodOngoing = if (needCycle) derived.periodOngoing else snap.periodOngoing,
            isLate = if (needCycle) derived.isLate else snap.isLate,
            daysLate = if (needCycle) derived.daysLate else snap.daysLate,
            generalOnly = snap.generalOnly,
            today = snap.today ?: derived.today
        )
    }

    fun deriveFromPeriods(periods: List<JsonObject>, todayEpoch: Long): PartnerSnapshotDto? {
        data class P(val start: Long, val end: Long?, val ongoing: Boolean)
        val starts = periods.mapNotNull { p ->
            val start = longOf(p, "startEpochDay") ?: return@mapNotNull null
            if (start > todayEpoch) return@mapNotNull null
            P(
                start = start,
                end = longOf(p, "endEpochDay"),
                ongoing = boolOf(p, "stillOngoing") == true
            )
        }.sortedBy { it.start }
        if (starts.isEmpty()) return null

        val gaps = mutableListOf<Int>()
        for (i in 1 until starts.size) {
            val g = (starts[i].start - starts[i - 1].start).toInt()
            if (g in 15..90) gaps += g
        }
        val cycleLength = median(gaps.takeLast(6)) ?: 28
        val last = starts.last()
        val cycleDay = (todayEpoch - last.start + 1).toInt().coerceAtLeast(1)
        val bleedLength = 5
        val periodOngoing = (last.ongoing && cycleDay <= 15) ||
            (last.end != null && last.end >= todayEpoch) ||
            (last.end == null && !last.ongoing && cycleDay <= bleedLength) ||
            (last.end == null && last.ongoing)
        val next = last.start + cycleLength
        val isLate = !periodOngoing && todayEpoch > next
        val ov = next - 14
        val phaseGroup = when {
            periodOngoing -> "menstrual"
            isLate || todayEpoch >= next - 5 -> "late_luteal"
            todayEpoch in (ov - 5)..(ov + 1) -> "fertile"
            todayEpoch < ov - 5 -> "follicular"
            else -> "early_luteal"
        }
        return PartnerSnapshotDto(
            phaseGroup = phaseGroup,
            cycleDay = cycleDay,
            cycleLength = cycleLength,
            daysUntilPeriod = (next - todayEpoch).toInt(),
            nextPeriodEpochDay = next,
            fertileStartEpochDay = ov - 5,
            fertileEndEpochDay = ov + 1,
            periodOngoing = periodOngoing,
            isLate = isLate,
            daysLate = if (isLate) (todayEpoch - next).toInt() else null,
            generalOnly = false,
            today = emptyToday(todayEpoch)
        )
    }

    private fun emptyToday(todayEpoch: Long) = PartnerTodayDto(epochDay = todayEpoch)

    private fun longOf(o: JsonObject, key: String): Long? = try {
        val el = o.get(key) ?: return null
        if (el.isJsonNull) null else el.asLong
    } catch (_: Throwable) { null }

    private fun boolOf(o: JsonObject, key: String): Boolean? = try {
        val el = o.get(key) ?: return null
        if (el.isJsonNull) null else el.asBoolean
    } catch (_: Throwable) { null }

    private fun median(list: List<Int>): Int? {
        if (list.isEmpty()) return null
        val a = list.sorted()
        val m = a.size / 2
        return if (a.size % 2 == 1) a[m] else ((a[m - 1] + a[m]) / 2.0).toInt()
    }
}
