package com.mahava.app.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "user_profile")
data class UserProfileEntity(
    @PrimaryKey val id: Int = 1,
    val onboardingDone: Boolean = false,
    val goal: String = "track_period", // track_period | body_awareness | ttc
    val lastPeriodStartEpochDay: Long? = null,
    val typicalCycleLength: Int? = null,
    val typicalBleedLength: Int? = null,
    val cycleLengthUnknown: Boolean = false,
    val bleedLengthUnknown: Boolean = false,
    val regularCycles: Boolean? = null,
    val ageBand: String? = null,
    val hormonalContraception: Boolean = false,
    val contraceptionType: String? = null,
    val postpartumOrBreastfeeding: Boolean = false,
    val perimenopause: Boolean = false,
    val diagnosedConditionsNote: String? = null,
    val fertilityTrackingEnabled: Boolean = false,
    val pregnancyMode: Boolean = false,
    val calendarType: String = "jalali", // jalali | gregorian
    val lockEnabled: Boolean = false,
    val lockTimeoutSeconds: Int = 60,
    val privateNotifications: Boolean = true,
    val hiddenBodyCategories: String = "", // comma-separated
    val contentVersionShown: String = "1.0.0",
    val qaSampleData: Boolean = false,
    val schemaVersion: Int = 1,
    val createdAt: Long = 0,
    val updatedAt: Long = 0
)

@Entity(
    tableName = "period_events",
    indices = [Index(value = ["startEpochDay"], unique = false)]
)
data class PeriodEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startEpochDay: Long,
    val endEpochDay: Long? = null, // null = ongoing
    val stillOngoing: Boolean = false,
    val note: String? = null,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(
    tableName = "daily_logs",
    indices = [Index(value = ["epochDay"], unique = true)]
)
data class DailyLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val epochDay: Long,
    val bleeding: String? = null, // none|spotting|light|medium|heavy
    val noSymptoms: Boolean = false, // distinct from not logged
    val painScore: Int? = null, // 0-10
    val painLocations: String? = null, // csv
    val painActivityImpact: String? = null, // none|partial|medium|high
    val physicalSymptoms: String? = null, // csv keys
    val moods: String? = null, // csv
    val energy: String? = null, // low|medium|high
    val fatigue: String? = null,
    val sleepQuality: String? = null,
    val sleepHours: Float? = null,
    val stress: String? = null,
    val focusDifficulty: Boolean? = null,
    val discharge: String? = null,
    val dischargeConcerns: String? = null,
    val intimacyLogged: Boolean = false,
    val intimacyProtected: String? = null,
    val desire: String? = null,
    val intimacyDiscomfort: Boolean? = null,
    val bbtCelsius: Float? = null,
    val ovulationTest: String? = null, // pos|neg|invalid
    val pregnancyTest: String? = null,
    val pregnancyTestEpochDay: Long? = null,
    val medicationNote: String? = null,
    val weightKg: Float? = null,
    val clots: String? = null, // none|sometimes|frequent
    /** CSV of food craving keys: chocolate,sweet,salty,carbs,spicy,dairy,red_meat,caffeine,other */
    val foodCravings: String? = null,
    val note: String? = null,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(tableName = "reminder_prefs")
data class ReminderPrefEntity(
    @PrimaryKey val id: String, // period|daily_log|period_end|test|medication
    val enabled: Boolean = false,
    val hour: Int = 9,
    val minute: Int = 0,
    val medicationLabel: String? = null
)

@Entity(tableName = "secure_kv")
data class SecureKvEntity(
    @PrimaryKey val key: String,
    val ciphertext: ByteArray,
    val iv: ByteArray,
    val updatedAt: Long
)
