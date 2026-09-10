package io.suirenx.core.data.settings

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.suirenx.core.domain.StorageModeRepository
import io.suirenx.core.model.StorageMode
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@Singleton
class LocalStorageModeRepository @Inject constructor(
    @ApplicationContext context: Context,
) : StorageModeRepository {
    private val preferences = context.getSharedPreferences("storage", Context.MODE_PRIVATE)
    private val mutex = Mutex()
    private val mutableMode = MutableStateFlow<StorageMode?>(null)
    override val mode: StateFlow<StorageMode?> = mutableMode

    override suspend fun initialize(): Result<Unit> = perform {
        if (mutableMode.value == null) {
            mutableMode.value = preferences.getString(KEY_MODE, null)?.let(::parse) ?: StorageMode.Local
        }
    }

    override suspend fun select(mode: StorageMode): Result<Unit> = perform {
        check(preferences.edit().putString(KEY_MODE, mode.name).commit()) { "无法保存使用模式，请重试" }
        mutableMode.value = mode
    }

    private fun parse(value: String): StorageMode? = when (value) {
        StorageMode.Local.name -> StorageMode.Local
        StorageMode.Remote.name -> StorageMode.Remote
        else -> null
    }

    private suspend fun perform(action: () -> Unit): Result<Unit> = try {
        withContext(Dispatchers.IO) { mutex.withLock { action() } }
        Result.success(Unit)
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        Result.failure(error)
    }

    private companion object { const val KEY_MODE = "mode" }
}
