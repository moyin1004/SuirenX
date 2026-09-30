package io.suirenx.core.domain

import io.suirenx.core.model.StorageMode
import kotlinx.coroutines.flow.StateFlow

interface StorageModeRepository {
    /** Null means the persisted mode has not been initialized yet. */
    val mode: StateFlow<StorageMode?>
    suspend fun initialize(): Result<Unit>
    suspend fun select(mode: StorageMode): Result<Unit>
}
