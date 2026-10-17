package com.mahava.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        UserProfileEntity::class,
        PeriodEventEntity::class,
        DailyLogEntity::class,
        ReminderPrefEntity::class,
        SecureKvEntity::class,
        PregnancyEntity::class,
        ToolRecord::class,
        HealthAlertEntity::class
    ],
    version = 5,
    exportSchema = false
)
abstract class MahavaDatabase : RoomDatabase() {
    abstract fun toolRecordDao(): ToolRecordDao
    abstract fun profileDao(): UserProfileDao
    abstract fun periodDao(): PeriodEventDao
    abstract fun dailyLogDao(): DailyLogDao
    abstract fun reminderDao(): ReminderPrefDao
    abstract fun pregnancyDao(): PregnancyDao
    abstract fun healthAlertDao(): HealthAlertDao

    companion object {
        const val DB_NAME = "mahava.db"
        val MIGRATION_2_3 = object : androidx.room.migration.Migration(2,3) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE daily_logs ADD COLUMN foodCravings TEXT")
            }
        }
        val MIGRATION_3_4 = object : androidx.room.migration.Migration(3,4) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE daily_logs ADD COLUMN waterIntakeMl INTEGER")
                db.execSQL("ALTER TABLE daily_logs ADD COLUMN sleepMinutes INTEGER")
                db.execSQL("ALTER TABLE daily_logs ADD COLUMN physicalSymptomSeverities TEXT")
                db.execSQL("CREATE TABLE IF NOT EXISTS `pregnancies` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `status` TEXT NOT NULL, `lmpEpochDay` INTEGER, `estimatedDueEpochDay` INTEGER NOT NULL, `datingMethod` TEXT NOT NULL, `clinicianConfirmed` INTEGER NOT NULL, `timezone` TEXT NOT NULL, `endedAt` INTEGER, `endReason` TEXT, `schemaVersion` INTEGER NOT NULL, `revision` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_pregnancies_status` ON `pregnancies` (`status`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `health_alerts` (`id` TEXT NOT NULL, `ruleId` TEXT NOT NULL, `ruleVersion` INTEGER NOT NULL, `severity` TEXT NOT NULL, `titleFa` TEXT NOT NULL, `plainLanguageExplanationFa` TEXT NOT NULL, `recommendedActionFa` TEXT NOT NULL, `observedDates` TEXT NOT NULL, `evidenceReferences` TEXT NOT NULL, `deduplicationKey` TEXT NOT NULL, `status` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `schemaVersion` INTEGER NOT NULL, `revision` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_health_alerts_deduplicationKey` ON `health_alerts` (`deduplicationKey`)")
            }
        }
        val MIGRATION_4_5 = object : androidx.room.migration.Migration(4, 5) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                listOf("overallFeeling TEXT", "energyScore INTEGER", "appetite TEXT", "painType TEXT",
                    "painDurationMinutes INTEGER", "painAction TEXT", "painResult TEXT").forEach {
                    db.execSQL("ALTER TABLE daily_logs ADD COLUMN $it")
                }
                db.execSQL("ALTER TABLE reminder_prefs ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("CREATE TABLE IF NOT EXISTS tool_records (id TEXT NOT NULL PRIMARY KEY, type TEXT NOT NULL, epochDay INTEGER, payloadJson TEXT NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, deletedAt INTEGER)")
            }
        }

        fun build(context: Context, inMemory: Boolean = false): MahavaDatabase {
            val builder = if (inMemory) {
                Room.inMemoryDatabaseBuilder(context, MahavaDatabase::class.java)
            } else {
                Room.databaseBuilder(context, MahavaDatabase::class.java, DB_NAME)
            }
            // v3→v4: pregnancy + health_alerts + log water/sleep/severity fields
            return builder.addMigrations(MIGRATION_2_3,MIGRATION_3_4,MIGRATION_4_5).build()
        }
    }
}
