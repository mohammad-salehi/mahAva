package com.mahava.app

import com.google.gson.JsonParser
import com.mahava.app.cycle.*
import com.mahava.app.health.HealthRuleEngine
import com.mahava.app.data.db.DailyLogEntity
import com.mahava.app.data.db.UserProfileEntity
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.time.LocalDate

class DesignHealthRegressionTest {
    private val today=LocalDate.of(2026,10,11)
    @Test fun dayElevenTomorrowIsNotTheEndOfPeriod() {
        val start=today.minusDays(10)
        val engine=CycleEngine()
        val current=engine.compute(CycleEngine.Input(today,listOf(PeriodInterval(start,start.plusDays(4))),28,5,true))
        val next=engine.compute(CycleEngine.Input(today.plusDays(1),listOf(PeriodInterval(start,start.plusDays(4))),28,5,true))
        val tomorrow=CycleDayContextResolver.resolveOn(current,today.plusDays(1))
        assertEquals(CycleDayContextResolver.resolve(next).subWindow,tomorrow.subWindow)
        assertFalse(tomorrow.fromLoggedBleeding)
        assertNotEquals(CycleSubWindow.MENSTRUATION_LATE,tomorrow.subWindow)
    }
    @Test fun ongoingBleedingDoesNotConfirmTomorrow() {
        val current=CycleEngine().compute(CycleEngine.Input(today,listOf(PeriodInterval(today.minusDays(1),null)),28,5,true))
        assertTrue(CycleDayContextResolver.resolve(current).fromLoggedBleeding)
        assertFalse(CycleDayContextResolver.resolveOn(current,today.plusDays(1)).fromLoggedBleeding)
    }
    @Test fun predictionRangeUsesObservedHistoryRatherThanFixedPadding() {
        val first=today.minusDays(66)
        val second=first.plusDays(27)
        val last=second.plusDays(31)
        val result=CycleEngine().compute(CycleEngine.Input(today,listOf(first,second,last).map { PeriodInterval(it,it.plusDays(4)) },null,5,true))
        val p=result.prediction as PredictionKind.Estimate
        assertEquals(last.plusDays(27),p.nextPeriodStartEarliest)
        assertEquals(last.plusDays(31),p.nextPeriodStartLatest)
        val initial=CycleEngine().compute(CycleEngine.Input(today,listOf(PeriodInterval(last,last.plusDays(4))),28,5,true)).prediction as PredictionKind.Estimate
        assertEquals(initial.nextPeriodStartCentral,initial.nextPeriodStartEarliest)
        assertEquals(initial.nextPeriodStartCentral,initial.nextPeriodStartLatest)
    }
    @Test fun pregnancyImportantSymptomsTakePriority() {
        val log=DailyLogEntity(epochDay=today.toEpochDay(),physicalSymptoms="vision_changes",createdAt=1,updatedAt=1)
        val alerts=HealthRuleEngine.evaluate(UserProfileEntity(pregnancyMode=true),emptyList(),listOf(log),today,true,1)
        assertEquals("pregnancy_symptom_guidance",alerts.single().ruleId)
        assertTrue(alerts.single().evidenceReferences.isNotEmpty())
    }
    @Test fun weeklyBankCoversAllWeeksWithSourcesAndDistinctAdvice() {
        val file=listOf(File("src/main/assets/content/pregnancy_weeks.json"),File("app/src/main/assets/content/pregnancy_weeks.json")).first { it.exists() }
        val bank=JsonParser.parseString(file.readText()).asJsonObject.getAsJsonArray("weeks")
        assertEquals((1..42).toList(),bank.map { it.asJsonObject["week"].asInt })
        bank.forEach { entry ->
            val e=entry.asJsonObject
            listOf("growthFa","bodyChangesFa","motherAdviceFa","fatherAdviceFa","careFa").forEach { assertTrue(e[it].asString.isNotBlank()) }
            assertTrue(e.getAsJsonArray("sourceIds").size()>0)
        }
        assertTrue(bank.map { it.asJsonObject["growthFa"].asString }.distinct().size>=40)
        assertTrue(bank[0].asJsonObject["growthFa"].asString.contains("هنوز لقاح رخ نداده"))
    }
}
