package com.mahava.app

import com.google.gson.Gson
import com.mahava.app.data.db.*
import com.mahava.app.health.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Today's tip is personal-first and is the one record shared by the Today card, its detail and the notification. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=ToolsTestApplication::class)
class DailyTipPriorityTest {
    private lateinit var app:ToolsTestApplication
    @Before fun setup() {app=RuntimeEnvironment.getApplication() as ToolsTestApplication;androidx.work.testing.WorkManagerTestInitHelper.initializeTestWorkManager(app)}
    @After fun close() {app.onTerminate();androidx.work.WorkManager.getInstance(app).cancelAllWork().result.get();androidx.work.testing.WorkManagerTestInitHelper.closeWorkDatabase();app.database.close()}

    private val today get()=app.clock.today().toEpochDay()
    private fun rec(r:ToolRecord)=Gson().fromJson(r.payloadJson,DailyRecommendation::class.java)
    /** Regular 28-day cycles; the last one started [daysAgo] days ago. */
    private suspend fun cycles(daysAgo:Int) {
        app.repository.saveProfile(UserProfileEntity(onboardingDone=true,typicalCycleLength=28,typicalBleedLength=5))
        (0..3).forEach { i -> val s=today-daysAgo-28L*i
            app.database.periodDao().upsert(PeriodEventEntity(startEpochDay=s,endEpochDay=s+4,createdAt=1,updatedAt=1)) }
    }

    @Test fun nearPeriodGivesCycleTipAddressedToHerNotAGenericOne()=runBlocking {
        cycles(25)
        val r=rec(app.toolsRepository.ensureRecommendation())
        assertEquals(r.body,"cycle",r.reasonType)
        assertTrue(r.body,r.body.startsWith("چون "))
        assertFalse(r.body,r.body.contains("برای همه"))
        assertTrue(r.contentId,r.contentId!!.startsWith("care_sw_"))
    }
    @Test fun symptomLoggedTodayWinsAndOpensItsCareItem()=runBlocking {
        cycles(10)
        app.repository.upsertDailyLog(DailyLogEntity(epochDay=today,physicalSymptoms="pain",painScore=6,createdAt=1,updatedAt=1))
        val r=rec(app.toolsRepository.ensureRecommendation())
        assertEquals("personal",r.reasonType);assertEquals("care_log_pain",r.contentId)
        assertTrue(r.body,r.body.startsWith("چون امروز درد ثبت کرده‌ای"))
    }
    @Test fun savedGenericTipIsUpgradedOnceSomethingPersonalExistsAndKeepsShownFlag()=runBlocking {
        app.toolsRepository.save("recommendation_$today","recommendation",
            DailyRecommendation(body="generic",category="general_0",reasonType="general",reasonText="x",generatedAt=1,shown=true),today)
        cycles(25)
        val r=rec(app.toolsRepository.ensureRecommendation())
        assertEquals("cycle",r.reasonType);assertTrue(r.shown)
        assertEquals(1,app.database.toolRecordDao().getAll().count {it.type=="recommendation"})
        // Once personal, the tip of the day no longer changes.
        app.repository.upsertDailyLog(DailyLogEntity(epochDay=today,physicalSymptoms="pain",createdAt=1,updatedAt=1))
        assertEquals(r.body,rec(ToolsRepository(app).ensureRecommendation()).body)
    }
    @Test fun pickerRanksPersonalThenCycleThenGeneralAndAvoidsGenericWhenPersonalWasSeen() {
        fun d(body:String,type:String,cat:String=type)=DailyRecommendation(body=body,category=cat,reasonType=type,reasonText="",generatedAt=0)
        val g=d("g","general","general_0");val c1=d("c1","cycle");val c2=d("c2","cycle");val p=d("p","personal","sleep")
        assertEquals(p,RecPicker.choose(listOf(g,c1,p),emptyList()))
        assertEquals(c1,RecPicker.choose(listOf(g,c1,c2),emptyList()))
        assertEquals(c2,RecPicker.choose(listOf(g,c1,c2),listOf(c1)))
        // Both cycle tips already shown: repeat the older one instead of falling back to a generic tip.
        assertEquals(c2,RecPicker.choose(listOf(g,c1,c2),listOf(c1,c2)))
        assertEquals(g,RecPicker.choose(listOf(g),emptyList()))
    }
}
