package com.mahava.app

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import com.mahava.app.data.db.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=Application::class)
class ToolsMigrationTest {
    @Test fun versionTwoHistorySurvivesUpgrade()=runBlocking {migratePreserves(2)}
    @Test fun versionThreeHistorySurvivesUpgrade()=runBlocking {migratePreserves(3)}
    @Test fun versionFourHistorySurvivesUpgrade()=runBlocking {migratePreserves(4)}
    private suspend fun migratePreserves(version:Int) {
        val context=RuntimeEnvironment.getApplication();val name="tools-migration-$version-test.db"
        var db=Room.databaseBuilder(context,MahavaDatabase::class.java,name).allowMainThreadQueries().build()
        db.dailyLogDao().upsert(DailyLogEntity(epochDay=20000,note="private history",createdAt=1,updatedAt=1))
        db.profileDao().upsert(UserProfileEntity(onboardingDone=true))
        db.close()
        val raw=SQLiteDatabase.openDatabase(context.getDatabasePath(name).path,null,SQLiteDatabase.OPEN_READWRITE)
        try {
            val newFields=setOf("overallFeeling","energyScore","appetite","painType","painDurationMinutes","painAction","painResult") + (if(version<=3) setOf("waterIntakeMl","sleepMinutes","physicalSymptomSeverities") else emptySet()) + (if(version==2)setOf("foodCravings") else emptySet())
            val cols=mutableListOf<String>();val names=mutableListOf<String>()
            raw.rawQuery("PRAGMA table_info(daily_logs)",null).use { cursor ->
                while(cursor.moveToNext()) {
                    val col=cursor.getString(1);if(col in newFields)continue
                    names+="`$col`";cols+="`$col` ${cursor.getString(2)}"+(if(cursor.getInt(5)>0) " PRIMARY KEY AUTOINCREMENT" else "")+(if(cursor.getInt(3)>0)" NOT NULL" else "")
                }
            }
            raw.execSQL("DROP INDEX IF EXISTS index_daily_logs_epochDay")
            raw.execSQL("ALTER TABLE daily_logs RENAME TO previous_logs")
            raw.execSQL("CREATE TABLE daily_logs (${cols.joinToString()})")
            raw.execSQL("INSERT INTO daily_logs (${names.joinToString()}) SELECT ${names.joinToString()} FROM previous_logs")
            raw.execSQL("DROP TABLE previous_logs")
            raw.execSQL("CREATE UNIQUE INDEX index_daily_logs_epochDay ON daily_logs(epochDay)")
            raw.execSQL("ALTER TABLE reminder_prefs RENAME TO previous_reminders")
            raw.execSQL("CREATE TABLE reminder_prefs (id TEXT NOT NULL PRIMARY KEY, enabled INTEGER NOT NULL, hour INTEGER NOT NULL, minute INTEGER NOT NULL, medicationLabel TEXT)")
            raw.execSQL("INSERT INTO reminder_prefs SELECT id,enabled,hour,minute,medicationLabel FROM previous_reminders")
            raw.execSQL("DROP TABLE previous_reminders")
            raw.execSQL("DROP TABLE tool_records")
            if(version<=3) {raw.execSQL("DROP TABLE pregnancies");raw.execSQL("DROP TABLE health_alerts")}
            raw.version=version
        } finally {raw.close()}
        db=Room.databaseBuilder(context,MahavaDatabase::class.java,name).allowMainThreadQueries().addMigrations(MahavaDatabase.MIGRATION_2_3,MahavaDatabase.MIGRATION_3_4,MahavaDatabase.MIGRATION_4_5).build()
        try {
            val row=db.dailyLogDao().getAll().single();assertEquals("private history",row.note);assertNull(row.energyScore)
            assertTrue(db.profileDao().get()!!.onboardingDone)
            db.toolRecordDao().upsert(ToolRecord("doctor_x","doctor",payloadJson="{}",createdAt=1,updatedAt=1))
            assertEquals(1,db.toolRecordDao().getAll().size)
        } finally {db.close();context.deleteDatabase(name)}
    }
}
