package com.mahava.app.data.backup

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.mahava.app.data.crypto.CryptoManager
import com.mahava.app.data.db.DailyLogEntity
import com.mahava.app.data.db.MahavaDatabase
import com.mahava.app.data.db.PeriodEventEntity
import com.mahava.app.data.db.ReminderPrefEntity
import com.mahava.app.data.db.UserProfileEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.room.withTransaction

data class BackupPayload(
    @SerializedName("schemaVersion") val schemaVersion: Int = 1,
    @SerializedName("app") val app: String = "mahava",
    @SerializedName("createdAt") val createdAt: Long,
    @SerializedName("profile") val profile: UserProfileEntity?,
    @SerializedName("periods") val periods: List<PeriodEventEntity>,
    @SerializedName("dailyLogs") val dailyLogs: List<DailyLogEntity>,
    @SerializedName("reminders") val reminders: List<ReminderPrefEntity>
)

sealed class RestoreResult {
    data object Success : RestoreResult()
    data class Failed(val reason: String) : RestoreResult()
}

class BackupManager(
    private val db: MahavaDatabase,
    private val crypto: CryptoManager = CryptoManager(),
    private val gson: Gson = Gson()
) {
    suspend fun exportEncrypted(password: CharArray, nowMillis: Long): ByteArray = withContext(Dispatchers.IO) {
        val payload = BackupPayload(
            createdAt = nowMillis,
            profile = db.profileDao().get(),
            periods = db.periodDao().getAll(),
            dailyLogs = db.dailyLogDao().getAll(),
            reminders = db.reminderDao().getAll()
        )
        val json = gson.toJson(payload).toByteArray(Charsets.UTF_8)
        crypto.encryptBackup(password, json)
    }

    /** Validate-then-atomic-restore. Wrong password / corrupt file leaves DB unchanged. */
    suspend fun restoreEncrypted(password: CharArray, blob: ByteArray): RestoreResult = withContext(Dispatchers.IO) {
        val plain = try {
            crypto.decryptBackup(password, blob)
        } catch (t: Throwable) {
            return@withContext RestoreResult.Failed("decrypt:" + (t.message ?: "error"))
        }
        val payload = try {
            gson.fromJson(String(plain, Charsets.UTF_8), BackupPayload::class.java)
        } catch (t: Throwable) {
            return@withContext RestoreResult.Failed("parse")
        }
        if (payload == null || payload.app != "mahava") {
            return@withContext RestoreResult.Failed("invalid_payload")
        }
        if (payload.schemaVersion > 1) {
            return@withContext RestoreResult.Failed("unsupported_schema")
        }
        try {
            db.withTransaction {
                db.profileDao().clear()
                db.periodDao().clear()
                db.dailyLogDao().clear()
                db.reminderDao().clear()
                payload.profile?.let { db.profileDao().upsert(it.copy(id = 1)) }
                payload.periods.forEach { db.periodDao().upsert(it.copy(id = 0)) }
                payload.dailyLogs.forEach { db.dailyLogDao().upsert(it.copy(id = 0)) }
                payload.reminders.forEach { db.reminderDao().upsert(it) }
            }
            RestoreResult.Success
        } catch (t: Throwable) {
            RestoreResult.Failed("apply:" + (t.message ?: "error"))
        }
    }
}
