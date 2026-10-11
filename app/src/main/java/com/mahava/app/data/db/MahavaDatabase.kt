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
        HealthAlertEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class MahavaDatabase : RoomDatabase() {
    abstract fun profileDao(): UserProfileDao
    abstract fun periodDao(): PeriodEventDao
    abstract fun dailyLogDao(): DailyLogDao
    abstract fun reminderDao(): ReminderPrefDao
    abstract fun pregnancyDao(): PregnancyDao
    abstract fun healthAlertDao(): HealthAlertDao

    companion object {
        const val DB_NAME = "mahava.db"

        fun build(context: Context, inMemory: Boolean = false): MahavaDatabase {
            val builder = if (inMemory) {
                Room.inMemoryDatabaseBuilder(context, MahavaDatabase::class.java)
            } else {
                Room.databaseBuilder(context, MahavaDatabase::class.java, DB_NAME)
            }
            // v3→v4: pregnancy + health_alerts + log water/sleep/severity fields
            return builder.fallbackToDestructiveMigration().build()
        }
    }
}
