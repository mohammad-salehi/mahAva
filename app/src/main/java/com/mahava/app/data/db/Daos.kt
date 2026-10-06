package com.mahava.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface UserProfileDao {
    @Query("SELECT * FROM user_profile WHERE id = 1")
    fun observe(): Flow<UserProfileEntity?>

    @Query("SELECT * FROM user_profile WHERE id = 1")
    suspend fun get(): UserProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: UserProfileEntity)

    @Query("DELETE FROM user_profile")
    suspend fun clear()
}

@Dao
interface PeriodEventDao {
    @Query("SELECT * FROM period_events ORDER BY startEpochDay ASC")
    fun observeAll(): Flow<List<PeriodEventEntity>>

    @Query("SELECT * FROM period_events ORDER BY startEpochDay ASC")
    suspend fun getAll(): List<PeriodEventEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(event: PeriodEventEntity): Long

    @Update
    suspend fun update(event: PeriodEventEntity)

    @Query("DELETE FROM period_events WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM period_events")
    suspend fun clear()
}

@Dao
interface DailyLogDao {
    @Query("SELECT * FROM daily_logs WHERE epochDay = :epochDay LIMIT 1")
    suspend fun getByDay(epochDay: Long): DailyLogEntity?

    @Query("SELECT * FROM daily_logs WHERE epochDay = :epochDay LIMIT 1")
    fun observeByDay(epochDay: Long): Flow<DailyLogEntity?>

    @Query("SELECT * FROM daily_logs ORDER BY epochDay ASC")
    fun observeAll(): Flow<List<DailyLogEntity>>

    @Query("SELECT * FROM daily_logs ORDER BY epochDay ASC")
    suspend fun getAll(): List<DailyLogEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(log: DailyLogEntity): Long

    @Query("DELETE FROM daily_logs WHERE epochDay = :epochDay")
    suspend fun deleteByDay(epochDay: Long)

    @Query("DELETE FROM daily_logs")
    suspend fun clear()
}

@Dao
interface ReminderPrefDao {
    @Query("SELECT * FROM reminder_prefs")
    fun observeAll(): Flow<List<ReminderPrefEntity>>

    @Query("SELECT * FROM reminder_prefs")
    suspend fun getAll(): List<ReminderPrefEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(pref: ReminderPrefEntity)

    @Query("DELETE FROM reminder_prefs")
    suspend fun clear()
}
