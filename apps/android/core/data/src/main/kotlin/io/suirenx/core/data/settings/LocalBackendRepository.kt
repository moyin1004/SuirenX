package io.suirenx.core.data.settings

import io.suirenx.core.model.UserFacingErrors

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.suirenx.core.data.BuildConfig
import io.suirenx.core.data.network.HttpTimeouts
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
import java.util.concurrent.ConcurrentHashMap

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
    private val successfullyTested = ConcurrentHashMap.newKeySet<String>()

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
        if (index < 0) require(successfullyTested.contains(url)) { "error_backend_probe_required" }
        if (index < 0) servers.add(server) else servers[index] = server
        persist(BackendSettings(servers, url))
        if (index < 0) successfullyTested.remove(url)
    }

    override suspend fun saveServer(originalUrl: String?, address: String, name: String): Result<String> {
        var savedUrl = ""
        return perform {
            val url = normalizeBackendAddress(address, BuildConfig.DEBUG)
            loadIfNeeded()
            val current = requireNotNull(settings.value)
            if (originalUrl != null) require(current.servers.any { it.url == originalUrl }) { "error_backend_deleted" }
            require(current.servers.none { it.url == url && it.url != originalUrl }) { "error_backend_duplicate" }
            if (originalUrl == null || originalUrl != url) {
                require(successfullyTested.contains(url)) { "error_backend_probe_required" }
            }
            val server = BackendServer(url, name.trim().ifEmpty { url })
            val servers = if (originalUrl == null) current.servers + server else current.servers.map { if (it.url == originalUrl) server else it }
            persist(BackendSettings(servers, if (originalUrl != null && current.activeUrl == originalUrl) url else current.activeUrl))
            if (originalUrl == null || originalUrl != url) successfullyTested.remove(url)
            savedUrl = url
        }.map { savedUrl }
    }

    override suspend fun testConnection(address: String): Result<String> = try {
        withContext(Dispatchers.IO) {
            val url = normalizeBackendAddress(address, BuildConfig.DEBUG)
            successfullyTested.remove(url)
            // A probe never sends credentials or follows a redirect to another service.
            val client = okhttp3.OkHttpClient.Builder()
                .connectTimeout(HttpTimeouts.PROBE_CONNECT_MILLIS, java.util.concurrent.TimeUnit.MILLISECONDS)
                .callTimeout(HttpTimeouts.PROBE_CALL_MILLIS, java.util.concurrent.TimeUnit.MILLISECONDS)
                .followRedirects(false)
                .build()
            client.newCall(okhttp3.Request.Builder().url(url + "healthz").get().build()).execute().use { response ->
                check(response.isSuccessful) { "error_backend_http" }
                val body = response.body?.string().orEmpty()
                val healthy = runCatching { kotlinx.serialization.json.Json.parseToJsonElement(body)
                    .let { it as? kotlinx.serialization.json.JsonObject }?.get("status")?.toString() == "\"ok\"" }.getOrDefault(false)
                check(healthy) { "error_backend_health" }
                successfullyTested.add(url)
                Result.success("message_backend_reachable")
            }
        }
    } catch (error: CancellationException) { throw error }
    catch (error: Exception) {
        runCatching { normalizeBackendAddress(address, BuildConfig.DEBUG) }.getOrNull()?.let(successfullyTested::remove)
        val message = when (error) {
            is javax.net.ssl.SSLException -> "error_backend_tls"
            is java.net.SocketTimeoutException -> "error_backend_timeout"
            is java.io.IOException -> "error_backend_network"
            else -> UserFacingErrors.codeOrFallback(error.message, "error_backend_probe_failed")
        }
        Result.failure(IllegalStateException(message, error))
    }

    override suspend fun select(url: String): Result<Unit> = perform {
        loadIfNeeded()
        val current = requireNotNull(settings.value)
        require(current.servers.any { it.url == url }) { "error_backend_missing_readd" }
        persist(current.copy(activeUrl = url))
    }

    override suspend fun remove(url: String): Result<Unit> = perform {
        loadIfNeeded()
        val current = requireNotNull(settings.value)
        require(current.servers.any { it.url == url }) { "error_backend_missing" }
        // Address configuration is independent from account login; removing a
        // saved address must not silently log the user out.
        persist(current.copy(
            servers = current.servers.filterNot { it.url == url },
            activeUrl = current.activeUrl.takeUnless { it == url },
        ))
    }

    private fun loadIfNeeded() {
        if (settings.value != null) return
        val encoded = preferences.getString("settings", null)
        val loaded = encoded?.let { json.decodeFromString<StoredSettings>(it) }
        val servers = loaded?.servers?.map { BackendServer(it.url, it.name) }.orEmpty()
        val active = loaded?.activeUrl?.takeIf { url -> servers.any { it.url == url } }
        mutableSettings.value = BackendSettings(servers, active)
    }

    private fun persist(settings: BackendSettings) {
        val stored = StoredSettings(settings.servers.map { StoredServer(it.url, it.name) }, settings.activeUrl)
        check(preferences.edit().putString("settings", json.encodeToString(stored)).commit()) {
            "error_backend_save_failed"
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
