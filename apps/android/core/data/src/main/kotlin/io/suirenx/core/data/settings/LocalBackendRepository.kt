package io.suirenx.core.data.settings

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.suirenx.core.data.BuildConfig
import io.suirenx.core.domain.BackendRepository
import io.suirenx.core.model.BackendServer
import io.suirenx.core.model.BackendSettings
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
private data class StoredServer(val url: String, val name: String)

@Serializable
private data class StoredSettings(val servers: List<StoredServer>, val activeUrl: String?)

@Singleton
class LocalBackendRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val json: Json,
) : BackendRepository {
    private val mutableSettings = MutableStateFlow<BackendSettings?>(null)
    override val settings: StateFlow<BackendSettings?> = mutableSettings
    private val mutex = Mutex()

    private val preferences by lazy { context.getSharedPreferences("backends", Context.MODE_PRIVATE) }

    override suspend fun initialize(): Result<Unit> = perform {
        loadIfNeeded()
    }

    override suspend fun saveAndSelect(address: String, name: String): Result<Unit> = perform {
        val url = normalizeBackendAddress(address, BuildConfig.DEBUG)
        loadIfNeeded()
        val current = requireNotNull(settings.value)
        val server = BackendServer(url, name.trim().ifEmpty { url })
        val servers = current.servers.toMutableList()
        val index = servers.indexOfFirst { it.url == url }
        if (index < 0) servers.add(server) else servers[index] = server
        persist(BackendSettings(servers, url))
    }

    override suspend fun select(url: String): Result<Unit> = perform {
        loadIfNeeded()
        val current = requireNotNull(settings.value)
        require(current.servers.any { it.url == url }) { "该地址已不存在，请重新添加" }
        persist(current.copy(activeUrl = url))
    }

    private fun loadIfNeeded() {
        if (settings.value != null) return
        val encoded = preferences.getString("settings", null)
        val loaded = encoded?.let { json.decodeFromString<StoredSettings>(it) }
        val servers = loaded?.servers?.map { BackendServer(it.url, it.name) }.orEmpty()
        val active = loaded?.activeUrl?.takeIf { url -> servers.any { it.url == url } } ?: servers.firstOrNull()?.url
        mutableSettings.value = BackendSettings(servers, active)
    }

    private fun persist(settings: BackendSettings) {
        val stored = StoredSettings(settings.servers.map { StoredServer(it.url, it.name) }, settings.activeUrl)
        check(preferences.edit().putString("settings", json.encodeToString(stored)).commit()) {
            "无法保存地址，请重试"
        }
        mutableSettings.value = settings
    }

    private suspend fun perform(action: () -> Unit): Result<Unit> = try {
        withContext(Dispatchers.IO) { mutex.withLock { action() } }
        Result.success(Unit)
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        Result.failure(error)
    }
}
