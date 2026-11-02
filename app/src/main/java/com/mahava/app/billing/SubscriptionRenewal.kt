package com.mahava.app.billing

/** Early-renewal rule shared with the server: a yearly subscription renews in its last 30 days. */
object SubscriptionRenewal {
    const val WINDOW_DAYS = 30L
    private const val DAY_MS = 24L * 60 * 60 * 1000

    /** Parses the server's ISO-8601 endsAt; null when missing or unreadable. */
    fun endsAtMillis(endsAt: String?): Long? {
        if (endsAt.isNullOrBlank()) return null
        return runCatching { java.time.Instant.parse(endsAt).toEpochMilli() }.getOrNull()
            ?: runCatching { java.time.OffsetDateTime.parse(endsAt).toInstant().toEpochMilli() }.getOrNull()
    }

    /** True when the expiry is unknown/past or at most [WINDOW_DAYS] remain. */
    fun canRenew(endsAtMs: Long?, nowMs: Long): Boolean =
        endsAtMs == null || endsAtMs - nowMs <= WINDOW_DAYS * DAY_MS

    fun canRenew(endsAt: String?, nowMs: Long): Boolean = canRenew(endsAtMillis(endsAt), nowMs)
}
