package io.suirenx.core.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface AssetDao {
    @Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: AssetEntity)

    @Query("SELECT * FROM assets ORDER BY createdAt DESC")
    suspend fun getAll(): List<AssetEntity>

    @Query("SELECT * FROM assets WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): AssetEntity?

    @Insert
    suspend fun insert(asset: AssetEntity)

    @Insert
    suspend fun insertAll(assets: List<AssetEntity>)

    @Update
    suspend fun update(asset: AssetEntity)

    @Query("DELETE FROM assets WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM assets")
    suspend fun deleteAll()
}
