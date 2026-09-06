package io.suirenx.core.domain

import kotlinx.coroutines.flow.StateFlow

interface ExpirySettingsRepository {
    val soonDays: StateFlow<Int>
    suspend fun initialize(): Result<Unit>
    suspend fun selectSoonDays(days: Int): Result<Unit>
}
