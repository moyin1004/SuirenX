package io.suirenx.core.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [RemoteAssetEntity::class, RemoteOutboxEntity::class, RemoteSyncStateEntity::class, RemoteConflictEntity::class, RemoteExpiryEntity::class, RemoteExpiryOutboxEntity::class, RemoteExpiryConflictEntity::class],
    version = 5,
    exportSchema = false,
)
abstract class RemoteDatabase : RoomDatabase() {
    abstract fun assets(): RemoteAssetDao
    abstract fun outbox(): RemoteOutboxDao
    abstract fun syncState(): RemoteSyncStateDao

    abstract fun conflicts(): RemoteConflictDao
    abstract fun expiry(): RemoteExpiryDao
    abstract fun expiryOutbox(): RemoteExpiryOutboxDao
    abstract fun expiryConflicts(): RemoteExpiryConflictDao
}
