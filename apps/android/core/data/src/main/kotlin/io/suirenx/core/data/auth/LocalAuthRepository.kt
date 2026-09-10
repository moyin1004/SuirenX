package io.suirenx.core.data.auth

import io.suirenx.core.data.network.AuthApi
import io.suirenx.core.data.network.AuthRequest
import io.suirenx.core.data.network.TokenResponse
import io.suirenx.core.domain.AuthRepository
import io.suirenx.core.domain.AuthState
import io.suirenx.core.domain.BackendRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import retrofit2.HttpException
import java.io.IOException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

@Singleton
class LocalAuthRepository @Inject constructor(
    private val backends: BackendRepository,
    private val client: OkHttpClient,
    private val json: Json,
    private val tokens: AuthTokenStore,
) : AuthRepository {
    private val mutex = Mutex()
    override val accounts = MutableStateFlow<Map<String, AuthState>>(emptyMap())
    private val mutableState = MutableStateFlow(AuthState("", false))
    override val state: StateFlow<AuthState> = mutableState

    override suspend fun initialize(): Result<Unit> = withContext(Dispatchers.IO) {
        val url = backends.settings.value?.activeUrl
        val loggedIn = url != null && tokens.tokenFor(url) != null
        accounts.value = tokens.savedAccounts().mapValues { AuthState(it.value, true) }
        mutableState.value = AuthState(if (loggedIn) tokens.username(url) else "", loggedIn)
        Result.success(Unit)
    }

    override suspend fun register(username: String, password: String) = registerAt(activeUrl(), username, password)
    override suspend fun registerAt(url: String, username: String, password: String): Result<Unit> {
        if (username.trim().toByteArray(Charsets.UTF_8).size !in 1..100 || password.toByteArray(Charsets.UTF_8).size !in 8..72) {
            return Result.failure(IllegalArgumentException("账号需为 1–100 字节，密码需为 8–72 字节（中文通常占 3 字节）"))
        }
        return authenticate(url, username, password, AuthApi::register, false)
    }
    override suspend fun login(username: String, password: String): Result<Unit> = authenticate(activeUrl(), username, password, AuthApi::login, true)
    override suspend fun loginAt(url: String, username: String, password: String): Result<Unit> = authenticate(url, username, password, AuthApi::login, false)

    override suspend fun logout(): Result<Unit> = logoutAt(activeUrl())
    override suspend fun logoutAt(url: String): Result<Unit> = attempt {
        mutex.withLock {
            try {
                if (tokens.tokenFor(url) != null) {
                    withTimeoutOrNull(3_000) {
                        try { api(url).logout() }
                        catch (error: CancellationException) { throw error }
                        catch (_: Exception) { /* Local logout must work offline. */ }
                    }
                }
            } finally {
                tokens.clearFor(url)
                initialize()
            }
        }
    }

    private suspend fun authenticate(url: String, username: String, password: String, call: suspend AuthApi.(AuthRequest) -> TokenResponse, requireCurrent: Boolean): Result<Unit> = attempt {
        mutex.withLock {
        val normalized = username.trim()
        require(normalized.isNotEmpty() && password.isNotEmpty()) { "请输入账号和密码" }
        val knownAtStart = backends.settings.value?.servers?.any { it.url == url } == true
        val response = api(url).call(AuthRequest(normalized, password))
        require(response.tokenType.equals("Bearer", ignoreCase = true) && response.accessToken.isNotBlank()) { "服务器返回了无效登录凭据" }
        check(!requireCurrent || backends.settings.value?.activeUrl == url) { "服务器已切换，请在当前服务器重新登录" }
        check(!knownAtStart || backends.settings.value?.servers?.any { it.url == url } == true) { "服务器地址已修改或删除，请重新登录" }
        tokens.save(url, normalized, response.accessToken)
        initialize()
        }
    }

    private fun activeUrl(): String = checkNotNull(backends.settings.value?.activeUrl) { "请先配置后端地址" }

    private fun api(url: String): AuthApi {
        return Retrofit.Builder().baseUrl(url).client(client).addConverterFactory(json.asConverterFactory("application/json".toMediaType())).build().create(AuthApi::class.java)
    }

    private suspend fun <T> attempt(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        val message = when (error) {
            is HttpException -> {
                val body = runCatching { json.parseToJsonElement(error.response()?.errorBody()?.string().orEmpty()).jsonObject["error"]?.jsonPrimitive?.content }.getOrNull()
                when {
                    body == "username is already registered" -> "该账号已注册，请切换到登录"
                    error.code() == 401 -> "账号或密码不正确，请重试"
                    error.code() == 429 -> "操作过于频繁，请稍后重试"
                    error.code() >= 500 -> "服务器暂时不可用，请稍后重试"
                    else -> "账号请求失败，请检查输入和服务器地址"
                }
            }
            is IOException -> "无法连接服务器，请检查地址和网络后重试"
            else -> error.message ?: "账号操作失败，请重试"
        }
        Result.failure(IllegalStateException(message, error))
    }
}
