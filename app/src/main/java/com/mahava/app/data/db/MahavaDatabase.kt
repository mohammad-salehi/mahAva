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
        SecureKvEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class MahavaDatabase : RoomDatabase() {
    abstract fun profileDao(): UserProfileDao
    abstract fun periodDao(): PeriodEventDao
    abstract fun dailyLogDao(): DailyLogDao
    abstract fun reminderDao(): ReminderPrefDao

    companion object {
        const val DB_NAME = "mahava.db"

        fun build(context: Context, inMemory: Boolean = false): MahavaDatabase {
            val builder = if (inMemory) {
                Room.inMemoryDatabaseBuilder(context, MahavaDatabase::class.java)
            } else {
                Room.databaseBuilder(context, MahavaDatabase::class.java, DB_NAME)
            }
            return builder.fallbackToDestructiveMigration().build()
        }
    }
}
