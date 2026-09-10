package io.suirenx.core.data.local

import androidx.room.*

@Entity(tableName = "local_sync_records", primaryKeys = ["kind", "id"])
@kotlinx.serialization.Serializable
data class LocalSyncRecord(
    val kind: String,
    val id: String,
    val revision: String,
    val version: Long = 0,
    val payload: String,
    val dirty: Boolean = true,
    val remotePayload: String? = null,
    val remoteVersion: Long? = null,
)

@Entity(tableName = "local_sync_session")
@kotlinx.serialization.Serializable
data class LocalSyncSession(
    @PrimaryKey val singleton: Int = 1,
    val target: String,
    val cursor: Long = 0,
    val expiryCursor: Long = 0,
    val batch: String? = null,
    val lastSyncedAt: String? = null,
)

@Dao
interface LocalSyncDao {
    @Query("SELECT * FROM local_sync_records WHERE kind = :kind AND id = :id")
    suspend fun get(kind: String, id: String): LocalSyncRecord?
    @Query("SELECT * FROM local_sync_records")
    suspend fun all(): List<LocalSyncRecord>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(record: LocalSyncRecord)
    @Query("SELECT * FROM local_sync_session WHERE singleton = 1")
    suspend fun session(): LocalSyncSession?
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSession(session: LocalSyncSession)
}
