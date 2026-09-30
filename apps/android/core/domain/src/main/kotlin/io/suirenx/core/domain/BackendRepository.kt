package io.suirenx.core.domain

import io.suirenx.core.model.BackendSettings
import kotlinx.coroutines.flow.StateFlow

interface BackendRepository {
    // null means local settings have not been loaded yet.
    val settings: StateFlow<BackendSettings?>
    suspend fun initialize(): Result<Unit>
    /** Add a new address only after testConnection has verified that exact normalized URL. */
    suspend fun saveAndSelect(address: String, name: String): Result<Unit>
    suspend fun select(url: String): Result<Unit>
    suspend fun remove(url: String): Result<Unit>
    /** New or changed addresses require a successful health probe; name-only edits do not. */
    suspend fun saveServer(originalUrl: String?, address: String, name: String): Result<String> = Result.failure(UnsupportedOperationException())
    suspend fun testConnection(address: String): Result<String> = Result.failure(UnsupportedOperationException())
}
