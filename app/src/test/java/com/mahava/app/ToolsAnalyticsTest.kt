package com.mahava.app

import com.mahava.app.data.db.*
import com.mahava.app.health.ToolsAnalytics
import org.junit.Assert.*
import org.junit.Test

class ToolsAnalyticsTest {
    private val today=21000L
    private fun periods()=(0..3).map {PeriodEventEntity(startEpochDay=today-90+it*28,endEpochDay=today-86+it*28,createdAt=1,updatedAt=1)}
    @Test fun newUserDoesNotHaveInventedPatterns() {assertTrue(ToolsAnalytics.analyze(emptyList(),emptyList(),today,1).isEmpty())}
    @Test fun missingEnergyAndSleepAreUnknown() {val log=DailyLogEntity(epochDay=today,createdAt=1,updatedAt=1);assertNull(ToolsAnalytics.energy(log));assertNull(ToolsAnalytics.sleep(log))}
    @Test fun explicitZeroSleepIsRetained() {assertEquals(0,ToolsAnalytics.sleep(DailyLogEntity(epochDay=today,sleepMinutes=0,createdAt=1,updatedAt=1)))}
    @Test fun legacyQualitativeEnergyDoesNotBecomeInventedNumericScore() {assertNull(ToolsAnalytics.energy(DailyLogEntity(epochDay=today,energy="low",createdAt=1,updatedAt=1)))}
    @Test fun futureAndDuplicateStartsDoNotCreateCycles() {val rows=periods();assertEquals(3,ToolsAnalytics.windows(rows+rows+PeriodEventEntity(startEpochDay=today+100,createdAt=1,updatedAt=1),today).size)}
    @Test fun oneWeekOfLoggingDoesNotBecomePmsDiagnosis() {val p=periods();val logs=(1..7).map {DailyLogEntity(epochDay=today-10+it,physicalSymptoms="headache",createdAt=1,updatedAt=1)};assertFalse(ToolsAnalytics.analyze(p,logs,today,1).any {it.relatedMetrics.startsWith("pms_")})}
    @Test fun repeatedPmsUsesActualSubsequentPeriodAndCountsCycles() {
        val p=periods();val logs=p.drop(1).map {DailyLogEntity(epochDay=it.startEpochDay-2,physicalSymptoms="headache",createdAt=1,updatedAt=1)}
        val insight=ToolsAnalytics.analyze(p,logs,today,1).single {it.relatedMetrics=="pms_headache"}
        assertEquals(3,insight.sampleCount);assertTrue(insight.description.contains("تشخیص"));assertTrue(insight.dataWindow.isNotBlank())
    }
    @Test fun separateStagesNeedObservedSamplesAndThreeCycles() {
        val p=periods();val logs=p.dropLast(1).flatMap {w -> listOf(w.startEpochDay+3,w.startEpochDay+26).map {DailyLogEntity(epochDay=it,energyScore=if(it-w.startEpochDay<7)4 else 2,sleepMinutes=420,createdAt=1,updatedAt=1)} }
        assertTrue(ToolsAnalytics.analyze(p,logs,today,1).any {it.relatedMetrics=="energy" && it.sampleCount==6})
        assertFalse(ToolsAnalytics.analyze(p.take(3),logs,today,1).any {it.relatedMetrics=="energy"})
    }
    @Test fun inputFingerprintChangesWithEditsAndIsStableForOrder() {
        val p=periods();val a=DailyLogEntity(epochDay=today-5,note="a",createdAt=1,updatedAt=1)
        assertEquals(ToolsAnalytics.fingerprint(p,listOf(a)),ToolsAnalytics.fingerprint(p.reversed(),listOf(a)))
        assertNotEquals(ToolsAnalytics.fingerprint(p,listOf(a)),ToolsAnalytics.fingerprint(p,listOf(a.copy(note="b"))))
    }
}
