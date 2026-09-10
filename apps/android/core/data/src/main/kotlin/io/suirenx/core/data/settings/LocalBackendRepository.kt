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
    private val tokens: io.suirenx.core.data.auth.AuthTokenStore,
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

    override suspend fun saveServer(originalUrl: String?, address: String, name: String): Result<String> {
        var savedUrl = ""
        return perform {
            val url = normalizeBackendAddress(address, BuildConfig.DEBUG)
            loadIfNeeded()
            val current = requireNotNull(settings.value)
            if (originalUrl != null) require(current.servers.any { it.url == originalUrl }) { "该服务器已删除" }
            require(current.servers.none { it.url == url && it.url != originalUrl }) { "该地址已存在，请编辑已有服务器" }
            val server = BackendServer(url, name.trim().ifEmpty { url })
            val servers = if (originalUrl == null) current.servers + server else current.servers.map { if (it.url == originalUrl) server else it }
            if (originalUrl != null && originalUrl != url) tokens.clearFor(originalUrl)
            persist(BackendSettings(servers, if (originalUrl != null && current.activeUrl == originalUrl) url else current.activeUrl))
            savedUrl = url
        }.map { savedUrl }
    }

    override suspend fun testConnection(address: String): Result<String> = try {
        withContext(Dispatchers.IO) {
            val url = normalizeBackendAddress(address, BuildConfig.DEBUG)
            // A probe never sends credentials or follows a redirect to another service.
            val client = okhttp3.OkHttpClient.Builder().connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                .callTimeout(8, java.util.concurrent.TimeUnit.SECONDS).followRedirects(false).build()
            client.newCall(okhttp3.Request.Builder().url(url + "healthz").get().build()).execute().use { response ->
                check(response.isSuccessful) { "连接测试失败：HTTP ${response.code}" }
                val body = response.body?.string().orEmpty()
                val healthy = runCatching { kotlinx.serialization.json.Json.parseToJsonElement(body)
                    .let { it as? kotlinx.serialization.json.JsonObject }?.get("status")?.toString() == "\"ok\"" }.getOrDefault(false)
                check(healthy) { "服务有响应，但不是预期的健康接口" }
                Result.success("服务可达；账号登录和数据同步仍需单独验证")
            }
        }
    } catch (error: CancellationException) { throw error }
    catch (error: Exception) {
        val message = when (error) {
            is javax.net.ssl.SSLException -> "证书验证失败，请检查 HTTPS 配置"
            is java.net.SocketTimeoutException -> "连接超时，请检查地址和网络"
            is java.io.IOException -> "无法连接，请检查服务器地址、端口和网络"
            else -> error.message ?: "连接测试失败"
        }
        Result.failure(IllegalStateException(message, error))
    }

    override suspend fun select(url: String): Result<Unit> = perform {
        loadIfNeeded()
        val current = requireNotNull(settings.value)
        require(current.servers.any { it.url == url }) { "该地址已不存在，请重新添加" }
        persist(current.copy(activeUrl = url))
    }

    override suspend fun remove(url: String): Result<Unit> = perform {
        loadIfNeeded()
        val current = requireNotNull(settings.value)
        require(current.servers.any { it.url == url }) { "该地址已不存在" }
        // Clear only this connection's credentials; local data and sync binding are untouched.
        tokens.clearFor(url)
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
