package io.suirenx.core.data.settings

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.suirenx.core.domain.ThemeRepository
import io.suirenx.core.model.ThemeMode
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

@Singleton
class LocalThemeRepository @Inject constructor(@ApplicationContext context: Context) : ThemeRepository {
    private val preferences = context.getSharedPreferences("appearance", Context.MODE_PRIVATE)
    private val mutableTheme = MutableStateFlow(ThemeMode.System)
    override val theme: StateFlow<ThemeMode> = mutableTheme
    override suspend fun initialize(): Result<Unit> {
        mutableTheme.value = preferences.getString(KEY_THEME, null)?.let { value -> ThemeMode.entries.firstOrNull { it.name == value } } ?: ThemeMode.System
        return Result.success(Unit)
    }
    override suspend fun select(theme: ThemeMode): Result<Unit> {
        if (!preferences.edit().putString(KEY_THEME, theme.name).commit()) return Result.failure(IllegalStateException("无法保存主题设置"))
        mutableTheme.value = theme
        return Result.success(Unit)
    }
    private companion object { const val KEY_THEME = "theme" }
}
