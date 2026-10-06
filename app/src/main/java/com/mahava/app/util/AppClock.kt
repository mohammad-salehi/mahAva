package com.mahava.app.util

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Abstraction over "today" so unit tests can freeze or shift the calendar day. */
interface AppClock {
    fun today(): LocalDate
    fun zoneId(): ZoneId
    fun nowMillis(): Long
}

class SystemAppClock(
    private val zoneId: ZoneId = ZoneId.systemDefault(),
    private val clock: Clock = Clock.system(zoneId)
) : AppClock {
    override fun today(): LocalDate = LocalDate.now(clock.withZone(zoneId))
    override fun zoneId(): ZoneId = zoneId
    override fun nowMillis(): Long = Instant.now(clock).toEpochMilli()
}

class FakeAppClock(
    private var today: LocalDate,
    private var zoneId: ZoneId = ZoneId.of("Asia/Tehran"),
    private var nowMillis: Long = today.atStartOfDay(zoneId).toInstant().toEpochMilli()
) : AppClock {
    override fun today(): LocalDate = today
    override fun zoneId(): ZoneId = zoneId
    override fun nowMillis(): Long = nowMillis
    fun setToday(date: LocalDate) { today = date }
    fun setZone(zone: ZoneId) { zoneId = zone }
    fun setNowMillis(value: Long) { nowMillis = value }
}
