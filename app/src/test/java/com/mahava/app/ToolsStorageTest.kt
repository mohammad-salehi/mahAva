package com.mahava.app

import androidx.room.Room
import com.google.gson.Gson
import com.mahava.app.data.db.*
import com.mahava.app.health.*
import com.mahava.app.util.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.LocalDate

class ToolsTestApplication:MahavaApplication() {
    override fun createClock():AppClock=FakeAppClock(LocalDate.of(2026,10,17))
    override fun createDatabase()=Room.inMemoryDatabaseBuilder(this,MahavaDatabase::class.java).allowMainThreadQueries().build()
    override fun onCreate() {super.onCreate();super.onTerminate()}
}
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=ToolsTestApplication::class)
class ToolsStorageTest {
    private lateinit var app:ToolsTestApplication
    @Before fun setup() {app=RuntimeEnvironment.getApplication() as ToolsTestApplication;androidx.work.testing.WorkManagerTestInitHelper.initializeTestWorkManager(app)}
    @After fun close() {app.onTerminate();androidx.work.WorkManager.getInstance(app).cancelAllWork().result.get();androidx.work.testing.WorkManagerTestInitHelper.closeWorkDatabase();app.database.close()}
    @Test fun dailyRecommendationIsExactlyOnePersistedRecordAcrossRepositoryRecreation()=runBlocking {
        val a=app.toolsRepository.ensureRecommendation();val b=ToolsRepository(app).ensureRecommendation()
        assertEquals(a.id,b.id);assertEquals(a.payloadJson,b.payloadJson)
        assertEquals(1,app.database.toolRecordDao().getAll().count {it.type=="recommendation"})
    }
    @Test fun thirtyDaysOfGeneralTipsDoNotRepeatExactText()=runBlocking {
        val bodies=mutableSetOf<String>();val clock=app.clock as FakeAppClock
        repeat(30) {
            val record=ToolsRepository(app).ensureRecommendation();val rec=Gson().fromJson(record.payloadJson,DailyRecommendation::class.java)
            assertTrue(bodies.add(rec.body));assertEquals("general",rec.reasonType)
            clock.setToday(clock.today().plusDays(1));clock.setNowMillis(clock.nowMillis()+86400000)
        }
    }
    @Test fun painEditorPreservesPrivateNoteAndOtherDailyFields()=runBlocking {
        val day=app.clock.today().toEpochDay();app.repository.upsertDailyLog(DailyLogEntity(epochDay=day,note="private",sleepMinutes=480,createdAt=1,updatedAt=1))
        app.toolsRepository.updateDaily(day) {it.copy(painScore=0,painType="cramp",painDurationMinutes=30)}
        val row=app.database.dailyLogDao().getByDay(day)!!;assertEquals("private",row.note);assertEquals(480,row.sleepMinutes);assertEquals(0,row.painScore);assertNull(row.discharge)
    }
    @Test fun questionDeletionKeepsTimestampedTombstoneAndDoesNotDeleteHealthHistory()=runBlocking {
        app.toolsRepository.save("doctor_test","doctor",mapOf("text" to "private question"))
        app.toolsRepository.delete("doctor_test")
        assertNotNull(app.database.toolRecordDao().get("doctor_test")!!.deletedAt)
    }
    @Test fun offlineDayDeletionHasDurableOriginalTimestampAndExplicitRecreationWins()=runBlocking {
        val day=app.clock.today().toEpochDay()
        app.toolsRepository.updateDaily(day) {it.copy(note="delete me")}
        val original=app.database.dailyLogDao().getByDay(day)!!
        app.toolsRepository.deleteDaily(day)
        val tombstone=app.database.toolRecordDao().get("tombstone_log_$day")!!
        assertTrue(tombstone.updatedAt>original.updatedAt)
        assertTrue(app.dataSync.collectLocal()["log/$day"]!!.deleted)
        val recreated=ToolsRepository(app)
        recreated.updateDaily(day) {it.copy(note="restored explicitly")}
        val row=app.database.dailyLogDao().getByDay(day)!!
        assertTrue(row.updatedAt>tombstone.updatedAt)
        assertFalse(app.dataSync.collectLocal()["log/$day"]!!.deleted)
    }
    @Test fun pregnancyNeverUsesMenstrualCycleRecommendation()=runBlocking {
        val day=app.clock.today().toEpochDay();app.repository.saveProfile(UserProfileEntity(pregnancyMode=true))
        app.repository.upsertPregnancy(PregnancyEntity(estimatedDueEpochDay=day+130,lmpEpochDay=day-150))
        val item=app.toolsRepository.ensureRecommendation();val rec=Gson().fromJson(item.payloadJson,DailyRecommendation::class.java)
        assertEquals("pregnancy",rec.reasonType);assertTrue(rec.medicalSourceIds.isNotBlank())
    }
    @Test fun dailyRecommendationAlwaysAtEightWithPrivateTextAndDoesNotRegenerate()=runBlocking {
        app.repository.saveProfile(UserProfileEntity(onboardingDone=true,privateNotifications=true))
        // An old saved setting must be ignored: the time is fixed at 08:00.
        app.toolsRepository.save("recommendation_settings","settings",mapOf("enabled" to false,"hour" to 10,"minute" to 45))
        val clock=app.clock as FakeAppClock
        fun setTime(h:Int,minute:Int) {clock.setNowMillis(clock.today().atTime(h,minute).atZone(clock.zoneId()).toInstant().toEpochMilli())}
        suspend fun tick() {androidx.work.testing.TestListenableWorkerBuilder<com.mahava.app.reminders.ReminderWorker>(app).build().doWork()}
        setTime(7,59);tick()
        assertTrue(app.database.toolRecordDao().getAll().none {it.type=="recommendation"})
        setTime(8,0);tick()
        val record=app.database.toolRecordDao().getAll().single {it.type=="recommendation"}
        assertTrue(Gson().fromJson(record.payloadJson,DailyRecommendation::class.java).shown)
        val manager=app.getSystemService(android.app.NotificationManager::class.java)
        val notes=org.robolectric.Shadows.shadowOf(manager).allNotifications
        assertEquals(1,notes.size)
        assertEquals("توصیهٔ امروز در ماه آماده است.",notes.single().extras.getCharSequence(android.app.Notification.EXTRA_TEXT).toString())
        tick();assertEquals(1,app.database.toolRecordDao().getAll().count {it.type=="recommendation"})
    }
}
