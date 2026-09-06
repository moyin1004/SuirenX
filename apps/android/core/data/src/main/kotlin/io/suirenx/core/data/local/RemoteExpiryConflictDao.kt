package io.suirenx.core.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface RemoteExpiryConflictDao {
    @Query("SELECT * FROM remote_expiry_conflicts WHERE serverUrl = :serverUrl ORDER BY createdAt ASC")
    suspend fun getAll(serverUrl: String): List<RemoteExpiryConflictEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(conflict: RemoteExpiryConflictEntity)

    @Query("DELETE FROM remote_expiry_conflicts WHERE serverUrl = :serverUrl AND expiryId = :expiryId")
    suspend fun delete(serverUrl: String, expiryId: String)
}
