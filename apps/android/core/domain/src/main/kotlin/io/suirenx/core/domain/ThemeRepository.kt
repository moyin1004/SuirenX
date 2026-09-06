package io.suirenx.core.domain

import io.suirenx.core.model.ThemeMode
import kotlinx.coroutines.flow.StateFlow

interface ThemeRepository {
    val theme: StateFlow<ThemeMode>
    suspend fun initialize(): Result<Unit>
    suspend fun select(theme: ThemeMode): Result<Unit>
}
