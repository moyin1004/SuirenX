package io.suirenx.core.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface ExpiryDao {
    @Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ExpiryEntity)

    @Query("SELECT * FROM expiry_items ORDER BY packageExpiryDate ASC, createdAt DESC")
    suspend fun getAll(): List<ExpiryEntity>

    @Query("SELECT * FROM expiry_items WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ExpiryEntity?

    @Query("DELETE FROM expiry_items WHERE id = :id") suspend fun delete(id: String)
    @Insert suspend fun insert(item: ExpiryEntity)
    @Query("DELETE FROM expiry_items") suspend fun deleteAll()
    @Update suspend fun update(item: ExpiryEntity)
}
