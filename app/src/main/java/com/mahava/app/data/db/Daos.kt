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

@Dao
interface PregnancyDao {
    @Query("SELECT * FROM pregnancies WHERE status = 'active' ORDER BY updatedAt DESC LIMIT 1")
    fun observeActive(): Flow<PregnancyEntity?>

    @Query("SELECT * FROM pregnancies WHERE status = 'active' ORDER BY updatedAt DESC LIMIT 1")
    suspend fun getActive(): PregnancyEntity?

    @Query("SELECT * FROM pregnancies ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<PregnancyEntity>>

    @Query("SELECT * FROM pregnancies ORDER BY updatedAt DESC")
    suspend fun getAll(): List<PregnancyEntity>

    @Query("SELECT * FROM pregnancies WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): PregnancyEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PregnancyEntity): Long

    @Query("DELETE FROM pregnancies WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM pregnancies")
    suspend fun clear()
}

@Dao
interface HealthAlertDao {
    @Query("SELECT * FROM health_alerts ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<HealthAlertEntity>>

    @Query("SELECT * FROM health_alerts WHERE status != 'dismissed' ORDER BY createdAt DESC")
    fun observeActive(): Flow<List<HealthAlertEntity>>

    @Query("SELECT * FROM health_alerts ORDER BY createdAt DESC")
    suspend fun getAll(): List<HealthAlertEntity>

    @Query("SELECT * FROM health_alerts WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): HealthAlertEntity?

    @Query("SELECT * FROM health_alerts WHERE deduplicationKey = :key LIMIT 1")
    suspend fun getByDedupe(key: String): HealthAlertEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: HealthAlertEntity)

    @Query("DELETE FROM health_alerts WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM health_alerts")
    suspend fun clear()
}
