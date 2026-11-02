package com.mahava.app

import com.mahava.app.billing.SubscriptionRenewal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubscriptionRenewalTest {
    private val day = 24L * 60 * 60 * 1000
    private val now = java.time.Instant.parse("2026-11-02T10:00:00Z").toEpochMilli()

    @Test fun windowOpensThirtyDaysBeforeExpiry() {
        assertTrue(SubscriptionRenewal.canRenew(now + 30 * day, now))
        assertFalse(SubscriptionRenewal.canRenew(now + 30 * day + 1, now))
        assertFalse(SubscriptionRenewal.canRenew(now + 200 * day, now))
        assertTrue(SubscriptionRenewal.canRenew(now + 5 * day, now))
        assertTrue(SubscriptionRenewal.canRenew(now - day, now))
        assertTrue(SubscriptionRenewal.canRenew(null as Long?, now))
    }

    @Test fun parsesServerIsoDates() {
        assertEquals(now + 10 * day, SubscriptionRenewal.endsAtMillis(java.time.Instant.ofEpochMilli(now + 10 * day).toString()))
        assertFalse(SubscriptionRenewal.canRenew("2027-10-01T00:00:00.000Z", now))
        assertTrue(SubscriptionRenewal.canRenew("2026-11-20T00:00:00.000Z", now))
        assertTrue(SubscriptionRenewal.canRenew("garbage", now))
    }
}
