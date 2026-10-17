package com.mahava.app.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/** UUID or deterministic day key. Tombstones survive process death and sync retries. */
@Entity(tableName = "tool_records")
data class ToolRecord(
    @PrimaryKey val id: String,
    val type: String,
    val epochDay: Long? = null,
    val payloadJson: String,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null
)

@Dao
interface ToolRecordDao {
    @Query("SELECT * FROM tool_records ORDER BY createdAt DESC") fun observeAll(): Flow<List<ToolRecord>>
    @Query("SELECT * FROM tool_records ORDER BY createdAt DESC") suspend fun getAll(): List<ToolRecord>
    @Query("SELECT * FROM tool_records WHERE id = :id") suspend fun get(id: String): ToolRecord?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(record: ToolRecord)
    @Query("DELETE FROM tool_records WHERE id = :id") suspend fun removeSyncedTombstone(id:String)
    @Query("DELETE FROM tool_records") suspend fun clear()
}
