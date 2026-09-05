package io.suirenx.core.domain

import io.suirenx.core.model.BackendSettings
import kotlinx.coroutines.flow.StateFlow

interface BackendRepository {
    // null means local settings have not been loaded yet.
    val settings: StateFlow<BackendSettings?>
    suspend fun initialize(): Result<Unit>
    suspend fun saveAndSelect(address: String, name: String): Result<Unit>
    suspend fun select(url: String): Result<Unit>
}
