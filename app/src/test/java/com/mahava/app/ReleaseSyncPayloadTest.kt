package com.mahava.app

import com.mahava.app.content.PartnerToolFormat
import com.mahava.app.data.db.*
import com.mahava.app.network.PartnerToolDto
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** The release sync payload carries every 1.3.0 field and tool type in a shape the server accepts. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = ToolsTestApplication::class)
class ReleaseSyncPayloadTest {
    private lateinit var app: ToolsTestApplication
    @Before fun setup() { app = RuntimeEnvironment.getApplication() as ToolsTestApplication; androidx.work.testing.WorkManagerTestInitHelper.initializeTestWorkManager(app) }
    @After fun close() { app.onTerminate(); androidx.work.WorkManager.getInstance(app).cancelAllWork().result.get(); androidx.work.testing.WorkManagerTestInitHelper.closeWorkDatabase(); app.database.close() }

    // Same rules as the backend (data.service.js KEY_RULES / FIELD_NAME).
    private val keyRules = mapOf(
        "tool" to Regex("^[a-zA-Z0-9_-]{1,64}$"), "profile" to Regex("^main$"), "period" to Regex("^-?\\d{1,7}$"),
        "log" to Regex("^-?\\d{1,7}$"), "reminder" to Regex("^[a-z0-9_]{1,32}$"),
        "pregnancy" to Regex("^(main|active|ended_[0-9]{1,12}|[0-9]{1,12})$"), "alert" to Regex("^[a-zA-Z0-9_]{1,64}$")
    )
    private val fieldName = Regex("^[a-zA-Z][a-zA-Z0-9_]{0,47}$")

    @Test fun everyNewFieldAndToolTypeIsSentAndValid() = runBlocking {
        val day = app.clock.today().toEpochDay()
        app.repository.upsertDailyLog(DailyLogEntity(epochDay = day, sleepMinutes = 450, sleepHours = 7.5f, energyScore = 4, energy = "high",
            overallFeeling = "good", appetite = "high", moods = "happy,focused", painScore = 6, painLocations = "back", painType = "cramping",
            painDurationMinutes = 120, painAction = "heat", painResult = "better", discharge = "eggwhite", dischargeConcerns = "itching",
            waterIntakeMl = 2000, physicalSymptomSeverities = "cramps:2", note = "n", createdAt = 1, updatedAt = 2))
        app.database.pregnancyDao().upsert(PregnancyEntity(estimatedDueEpochDay = day + 200, lmpEpochDay = day - 80, createdAt = 1, updatedAt = 2))
        app.toolsRepository.save("doctor_${java.util.UUID.randomUUID()}", "doctor", mapOf("text" to "q", "done" to false), day)
        app.toolsRepository.save("appointments_${java.util.UUID.randomUUID()}", "appointments", mapOf("text" to "a", "date" to "2026-10-27"), day + 10)
        app.toolsRepository.save("pregnancy_checklist_20270505_12", "pregnancy_checklist", mapOf("care" to true), day)
        app.toolsRepository.ensureRecommendation()
        val all = app.dataSync.collectLocal().values
        val log = all.single { it.kind == "log" }.data!!
        listOf("sleepMinutes", "sleepHours", "energyScore", "overallFeeling", "appetite", "painType", "painDurationMinutes", "painAction",
            "painResult", "dischargeConcerns", "waterIntakeMl", "physicalSymptomSeverities").forEach { assertTrue("log field $it is sent", log.has(it)) }
        assertEquals(450, log.get("sleepMinutes").asInt)
        val toolTypes = all.filter { it.kind == "tool" }.map { it.data!!.get("type").asString }.toSet()
        assertTrue(toolTypes.containsAll(setOf("doctor", "appointments", "pregnancy_checklist", "recommendation")))
        assertTrue(all.any { it.kind == "pregnancy" && it.key == "main" })
        all.forEach { r ->
            assertTrue("key ${r.id}", keyRules[r.kind]?.matches(r.key) == true)
            r.data?.entrySet()?.forEach { (k, v) ->
                assertTrue("field name $k", fieldName.matches(k))
                assertTrue("flat value ${r.id}.$k", v.isJsonNull || v.isJsonPrimitive)
            }
        }
    }
}

class PartnerToolFormatTest {
    private fun t(type: String, key: String, day: Long?, json: String) = PartnerToolDto(key, type, day, json)

    @Test fun everyToolTypeBecomesPersianSections() {
        val today = 20743L
        val s = PartnerToolFormat.sections(listOf(
            t("recommendation", "recommendation_$today", today, """{"title":"توصیه امروز","body":"کمی پیاده‌روی"}"""),
            t("doctor", "doctor_1", today, """{"text":"درد طبیعی است؟","done":true,"answer":"بله"}"""),
            t("appointments", "appointments_1", today + 3, """{"text":"دکتر زنان"}"""),
            t("insight", "insight_cycle_length", null, """{"title":"الگوی چرخه من","description":"میانگین 29 روز"}"""),
            t("pregnancy_checklist", "pregnancy_checklist_20270505_12", today, """{"care":true}"""),
            t("sync_tombstone", "x", null, """{"kind":"log"}"""),
            t("doctor", "doctor_broken", today, "not json")
        ), today)
        val titles = s.map { it.title }
        assertEquals(listOf("توصیه‌ای که امروز دید", "سؤال‌ها و مراجعه‌های پزشکی‌اش", "الگوهایی که از ثبت‌هایش پیدا شده", "کارهای هفتهٔ ۱۲ بارداری"), titles)
        val text = s.flatMap { it.lines }.joinToString("\n")
        assertTrue(text.contains("سؤال از پزشک") && text.contains("(انجام شد)") && text.contains("پاسخ: بله"))
        assertTrue(text.contains("میانگین ۲۹ روز"))
        assertTrue(text.contains("✓ برنامهٔ مراقبت"))
        assertFalse(Regex("[a-zA-Z]").containsMatchIn(text))
    }

    @Test fun dueDateComesFromTheServerKey() {
        assertEquals(20963L, PartnerToolFormat.dueDay("main_20963"))
        assertNull(PartnerToolFormat.dueDay(null))
    }
}
