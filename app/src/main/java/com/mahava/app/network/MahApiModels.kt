package com.mahava.app.network

data class MahUserDto(
    val id: String = "",
    val phone: String = "",
    val role: String = "",
    val isActive: Boolean = true,
    val createdAt: String? = null,
    val lastLoginAt: String? = null
)

data class MahSubscriptionDto(
    val hasActiveSubscription: Boolean = false,
    val endsAt: String? = null,
    val plan: String = "none",
    val priceYearlyTomans: Int = 585000,
    val paymentGatewayReady: Boolean = false,
    /** True when the user's OWN subscription is active. */
    val ownSubscriptionActive: Boolean = false,
    /** True when premium comes from the paired partner's subscription. */
    val sharedFromPartner: Boolean = false
)

data class MahAuthResponse(
    val ok: Boolean = false,
    val message: String? = null,
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val user: MahUserDto? = null,
    val subscription: MahSubscriptionDto? = null
)

data class MahPlansResponse(
    val ok: Boolean = false,
    val priceYearlyTomans: Int = 585000,
    val paymentGatewayReady: Boolean = false,
    val message: String? = null
)

data class MahApiException(val status: Int, override val message: String) : Exception(message)

data class MahForceUpdateDto(
    val mustUpdate: Boolean = false,
    val minSupportedVersionCode: Int = 0,
    val updateDownloadUrl: String = "",
    val messageFa: String = ""
)

data class MahAppConfigResponse(
    val ok: Boolean = false,
    val forceUpdate: MahForceUpdateDto? = null
)

data class MahInboxItemDto(
    val id: String = "",
    val title: String = "",
    val body: String = "",
    val kind: String = "info",
    val actionUrl: String = "",
    val actionLabel: String = "",
    val dismissible: Boolean = true
)

data class MahInboxResponse(
    val ok: Boolean = false,
    val items: List<MahInboxItemDto> = emptyList()
)

// ---- Partner pairing ----

data class PartnerInfoDto(val phoneMasked: String = "")

data class PartnerPairDto(
    val id: String = "",
    val status: String = "", // pending | active
    val myRole: String = "",
    val requestedAt: String? = null,
    val approvedAt: String? = null,
    val partner: PartnerInfoDto? = null
)

data class PartnerStatusDto(
    val ok: Boolean = false,
    val role: String = "",
    val pair: PartnerPairDto? = null,
    val snapshotUpdatedAt: String? = null,
    val codeTtlMinutes: Int = 30
)

data class PartnerCodeDto(val ok: Boolean = false, val code: String = "", val expiresAt: String? = null)

data class PartnerPairResponse(val ok: Boolean = false, val pair: PartnerPairDto? = null)

data class PartnerTodayDto(
    val epochDay: Long? = null,
    val moods: List<String>? = null,
    val symptoms: List<String>? = null,
    val cravings: List<String>? = null,
    val painScore: Int? = null,
    /** Her full daily log for today (every field she recorded), null when nothing logged. */
    val log: com.google.gson.JsonObject? = null
)

/** The woman's status (cycle fields) plus today's full log. */
data class PartnerSnapshotDto(
    val phaseGroup: String = "general",
    val subWindow: String = "",
    val cycleDay: Int? = null,
    val cycleLength: Int? = null,
    val daysUntilPeriod: Int? = null,
    val nextPeriodEpochDay: Long? = null,
    val fertileStartEpochDay: Long? = null,
    val fertileEndEpochDay: Long? = null,
    val periodOngoing: Boolean = false,
    val isLate: Boolean = false,
    val daysLate: Int? = null,
    val generalOnly: Boolean = false,
    val today: PartnerTodayDto? = null
)

data class PartnerShareDto(
    val ok: Boolean = false,
    val snapshot: PartnerSnapshotDto? = null,
    /** Everything she logged recently (full daily logs, newest first). */
    val logs: List<com.google.gson.JsonObject>? = null,
    /** Her periods, newest first. */
    val periods: List<com.google.gson.JsonObject>? = null,
    val updatedAt: String? = null,
    val version: Int = 0
)

data class PartnerEventDto(
    val id: String = "",
    val kind: String = "",
    val changes: List<String>? = null,
    val createdAtMs: Long = 0
)

data class PartnerChangesDto(
    val ok: Boolean = false,
    val now: Long = 0,
    val events: List<PartnerEventDto>? = null,
    val role: String = "",
    val pair: PartnerPairDto? = null,
    val snapshotUpdatedAt: String? = null
)


// ---- Full data sync (/api/mah/data) ----

data class SyncRecordDto(
    val kind: String = "",
    val key: String = "",
    val data: com.google.gson.JsonObject? = null,
    val deleted: Boolean = false,
    val updatedAt: Long = 0L,
    val serverUpdatedAt: Long = 0L
)

data class DataPullDto(
    val now: Long = 0L,
    val cursor: Long = 0L,
    val more: Boolean = false,
    val full: Boolean = false,
    val records: List<SyncRecordDto>? = null
)

data class DataPushDto(
    val now: Long = 0L,
    val applied: Int = 0,
    val conflicts: List<SyncRecordDto>? = null
)
