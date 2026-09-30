package io.suirenx.core.data.settings

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.suirenx.core.domain.ExpirySettingsRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

@Singleton
class LocalExpirySettingsRepository @Inject constructor(
    @ApplicationContext context: Context,
) : ExpirySettingsRepository {
    private val preferences = context.getSharedPreferences("expiry", Context.MODE_PRIVATE)
    private val mutableSoonDays = MutableStateFlow(DEFAULT_SOON_DAYS)
    override val soonDays: StateFlow<Int> = mutableSoonDays

    override suspend fun initialize(): Result<Unit> {
        mutableSoonDays.value = preferences.getInt(KEY_SOON_DAYS, DEFAULT_SOON_DAYS).coerceIn(MIN_SOON_DAYS, MAX_SOON_DAYS)
        return Result.success(Unit)
    }

    override suspend fun selectSoonDays(days: Int): Result<Unit> {
        if (days !in MIN_SOON_DAYS..MAX_SOON_DAYS) return Result.failure(IllegalArgumentException("临期天数需在 $MIN_SOON_DAYS-$MAX_SOON_DAYS 天之间"))
        if (!preferences.edit().putInt(KEY_SOON_DAYS, days).commit()) return Result.failure(IllegalStateException("无法保存临期设置"))
        mutableSoonDays.value = days
        return Result.success(Unit)
    }

    private companion object {
        const val DEFAULT_SOON_DAYS = 7
        const val MIN_SOON_DAYS = 1
        const val MAX_SOON_DAYS = 30
        const val KEY_SOON_DAYS = "soon_days"
    }
}
