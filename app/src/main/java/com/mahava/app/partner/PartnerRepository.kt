package com.mahava.app.partner

import com.google.gson.Gson
import com.mahava.app.content.TodaySignalContent
import com.mahava.app.cycle.CycleDayContextResolver
import com.mahava.app.cycle.PredictionKind
import com.mahava.app.data.auth.AuthRepository
import com.mahava.app.data.db.MahavaDatabase
import com.mahava.app.data.prefs.UserPreferences
import com.mahava.app.data.repo.MahavaRepository
import com.mahava.app.network.MahApiClient
import com.mahava.app.network.PartnerCodeDto
import com.mahava.app.network.PartnerEventDto
import com.mahava.app.network.PartnerShareDto
import com.mahava.app.network.PartnerSnapshotDto
import com.mahava.app.network.PartnerStatusDto
import com.mahava.app.network.PartnerTodayDto
import com.mahava.app.util.AppClock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Partner pairing + sharing. Health data leaves the phone ONLY when the woman has given
 * consent and her pair is active; only the whitelisted snapshot below is sent.
 */
class PartnerRepository(
    private val api: MahApiClient,
    private val auth: AuthRepository,
    private val prefs: UserPreferences,
    private val db: MahavaDatabase,
    private val repo: MahavaRepository,
    private val clock: AppClock
) {
    private val gson = Gson()

    val status: Flow<PartnerStatusDto?> = prefs.partnerStatusJson.map { parse(it, PartnerStatusDto::class.java) }
    val share: Flow<PartnerShareDto?> = prefs.partnerSnapshotJson.map { parse(it, PartnerShareDto::class.java) }

    private fun <T> parse(json: String?, cls: Class<T>): T? =
        try { if (json.isNullOrBlank()) null else gson.fromJson(json, cls) } catch (_: Throwable) { null }

    suspend fun cachedStatus(): PartnerStatusDto? = parse(prefs.getPartnerStatusJson(), PartnerStatusDto::class.java)

    suspend fun isActivelyPaired(): Boolean = cachedStatus()?.pair?.status == "active"

    private suspend fun saveStatus(s: PartnerStatusDto) {
        prefs.setPartnerStatusJson(gson.toJson(s))
        if (s.role.isNotBlank()) prefs.setAccountRole(s.role)
        if (s.pair?.status != "active") {
            prefs.setPartnerSnapshotJson(null)
            prefs.setPartnerPushHash(null)
        }
    }

    suspend fun refreshStatus(): PartnerStatusDto {
        val s = auth.withToken { api.partnerStatus(it) }
        saveStatus(s)
        return s
    }

    suspend fun setRole(role: String): PartnerStatusDto {
        val s = auth.withToken { api.partnerSetRole(it, role) }
        saveStatus(s)
        return s
    }

    suspend fun createCode(): PartnerCodeDto = auth.withToken { api.partnerCreateCode(it) }

    suspend fun redeem(code: String): PartnerStatusDto {
        auth.withToken { api.partnerRedeem(it, code.trim()) }
        return refreshStatus()
    }

    suspend fun respond(pairId: String, approve: Boolean): PartnerStatusDto {
        auth.withToken { api.partnerRespond(it, pairId, approve) }
        val s = refreshStatus()
        auth.refreshSubscription()
        if (approve) pushSnapshotIfChanged(force = true)
        return s
    }

    /** Either side. Server deletes her shared data; this phone forgets the partner too. */
    suspend fun unpair() {
        auth.withToken { api.partnerUnpair(it) }
        clearAfterUnpair()
    }

    suspend fun clearAfterUnpair() {
        prefs.clearPartnerLocal()
        try { refreshStatus() } catch (_: Throwable) { prefs.setPartnerStatusJson(null) }
        try { auth.refreshSubscription() } catch (_: Throwable) { }
    }

    /** Only the fields the partner view needs — no notes, intimacy, tests, weight… */
    suspend fun buildSnapshot(): PartnerSnapshotDto {
        val profile = db.profileDao().get()
        val periods = db.periodDao().getAll()
        val result = repo.computeCycle(profile, periods)
        val ctx = CycleDayContextResolver.resolve(result)
        val group = TodaySignalContent.effectivePhaseGroup(
            ctx.subWindow, result.phase,
            hormonalContraception = profile?.hormonalContraception == true,
            regularCycles = profile?.regularCycles
        )
        val est = result.prediction as? PredictionKind.Estimate
        val today = clock.today().toEpochDay()
        val log = db.dailyLogDao().getByDay(today)
        fun csv(s: String?) = s?.split(',')?.map { it.trim() }?.filter { it.isNotBlank() }.orEmpty()
        val general = group == "general"
        return PartnerSnapshotDto(
            phaseGroup = group,
            subWindow = if (general) "" else ctx.subWindow.name,
            cycleDay = result.cycleDay,
            cycleLength = est?.medianCycleLength ?: profile?.typicalCycleLength,
            daysUntilPeriod = result.daysUntilCentralPeriod,
            nextPeriodEpochDay = est?.nextPeriodStartCentral?.toEpochDay(),
            fertileStartEpochDay = if (result.showFertility) est?.fertilityEarliest?.toEpochDay() else null,
            fertileEndEpochDay = if (result.showFertility) est?.fertilityLatest?.toEpochDay() else null,
            periodOngoing = result.periodOngoing,
            isLate = result.isLate,
            daysLate = result.daysLate,
            generalOnly = general,
            today = PartnerTodayDto(
                epochDay = today,
                moods = csv(log?.moods),
                symptoms = csv(log?.physicalSymptoms),
                cravings = csv(log?.foodCravings),
                painScore = log?.painScore
            )
        )
    }

    /** Woman's side. Returns true when something was uploaded. */
    suspend fun pushSnapshotIfChanged(force: Boolean = false): Boolean {
        if (prefs.getAccountRole() == "male") return false
        if (!isActivelyPaired()) return false
        val snap = buildSnapshot()
        val hash = gson.toJson(snap).hashCode().toString()
        if (!force && hash == prefs.getPartnerPushHash()) return false
        auth.withToken { api.partnerPutShare(it, snap) }
        prefs.setPartnerPushHash(hash)
        return true
    }

    /** Man's side: latest shared status. */
    suspend fun fetchShare(): PartnerShareDto? {
        return try {
            val s = auth.withToken { api.partnerGetShare(it) }
            prefs.setPartnerSnapshotJson(gson.toJson(s))
            s
        } catch (e: com.mahava.app.network.MahApiException) {
            if (e.status == 409) prefs.setPartnerSnapshotJson(null)
            null
        }
    }

    data class Poll(val events: List<PartnerEventDto>, val firstPoll: Boolean, val status: PartnerStatusDto)

    /** Changes since the last poll (server time). The first poll only sets the cursor. */
    suspend fun pollChanges(): Poll {
        val since = prefs.getPartnerSince()
        val res = auth.withToken { api.partnerChanges(it, since) }
        val events = res.events.orEmpty()
        val st = PartnerStatusDto(ok = true, role = res.role, pair = res.pair, snapshotUpdatedAt = res.snapshotUpdatedAt)
        saveStatus(st)
        val cursor = events.maxOfOrNull { it.createdAtMs } ?: if (since == 0L) res.now else since
        prefs.setPartnerSince(cursor)
        return Poll(if (since == 0L) emptyList() else events, since == 0L, st)
    }
}
