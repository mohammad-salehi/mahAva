package com.mahava.app

import android.app.Application
import androidx.room.Room
import com.mahava.app.data.db.DailyLogEntity
import com.mahava.app.data.db.MahavaDatabase
import com.mahava.app.data.db.UserProfileEntity
import com.mahava.app.data.repo.MahavaRepository
import com.mahava.app.util.FakeAppClock
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28], application=Application::class)
class DesignPersistenceTest {
    @Test fun editedDaySurvivesDatabaseReopen() = runBlocking {
        val context=RuntimeEnvironment.getApplication()
        val clock=FakeAppClock(LocalDate.of(2026,10,11))
        val name="design-reopen-test.db"
        fun open()=Room.databaseBuilder(context,MahavaDatabase::class.java,name).allowMainThreadQueries().build()
        var db=open()
        try {
            val repo=MahavaRepository(db,clock)
            val row=DailyLogEntity(epochDay=clock.today().toEpochDay(),physicalSymptoms="fatigue",createdAt=1,updatedAt=1)
            repo.upsertDailyLog(row)
            repo.upsertDailyLog(row.copy(physicalSymptoms="fatigue,bloating",note="یادداشت خصوصی"))
            db.close()
            db=open()
            val logs=db.dailyLogDao().getAll()
            assertEquals(1,logs.size)
            assertEquals("fatigue,bloating",logs.single().physicalSymptoms)
            assertEquals("یادداشت خصوصی",logs.single().note)
            assertNull(logs.single().bleeding)
            assertNull(logs.single().painScore)
            assertFalse(logs.single().noSymptoms)
        } finally { db.close(); context.deleteDatabase(name) }
    }

    @Test fun refreshAtMidnightUsesNewCalendarDay() = runBlocking {
        val context=RuntimeEnvironment.getApplication()
        val db=Room.inMemoryDatabaseBuilder(context,MahavaDatabase::class.java).allowMainThreadQueries().build()
        try {
            val clock=FakeAppClock(LocalDate.of(2026,10,11))
            val repo=MahavaRepository(db,clock)
            repo.saveProfile(UserProfileEntity(lastPeriodStartEpochDay=clock.today().minusDays(10).toEpochDay(),typicalCycleLength=28,regularCycles=true))
            val first=repo.observeCycleResult().first()
            clock.setToday(clock.today().plusDays(1))
            repo.refreshCalendarDay()
            val next=repo.observeCycleResult().first()
            assertEquals(first.today.plusDays(1),next.today)
        } finally { db.close() }
    }
}
