package com.mahava.app.network

data class MahUserDto(
    val id: String = "",
    val phone: String = "",
    val name: String = "",
    val isActive: Boolean = true,
    val createdAt: String? = null,
    val lastLoginAt: String? = null
)

data class MahSubscriptionDto(
    val hasActiveSubscription: Boolean = false,
    val endsAt: String? = null,
    val plan: String = "none",
    val priceYearlyTomans: Int = 585000,
    val paymentGatewayReady: Boolean = false
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
