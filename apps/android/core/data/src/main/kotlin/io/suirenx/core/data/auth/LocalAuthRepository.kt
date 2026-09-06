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
import kotlinx.serialization.json.Json
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
    private val mutableState = MutableStateFlow(AuthState("", false))
    override val state: StateFlow<AuthState> = mutableState

    override suspend fun initialize(): Result<Unit> = withContext(Dispatchers.IO) {
        val url = backends.settings.value?.activeUrl
        mutableState.value = AuthState(tokens.username(), url != null && tokens.tokenFor(url) != null)
        Result.success(Unit)
    }

    override suspend fun register(username: String, password: String): Result<Unit> = authenticate(username, password, AuthApi::register)
    override suspend fun login(username: String, password: String): Result<Unit> = authenticate(username, password, AuthApi::login)

    override suspend fun logout(): Result<Unit> = attempt {
        val url = activeUrl()
        if (tokens.tokenFor(url) != null) api(url).logout()
        tokens.clear()
        mutableState.value = AuthState("", false)
    }

    private suspend fun authenticate(username: String, password: String, call: suspend AuthApi.(AuthRequest) -> TokenResponse): Result<Unit> = attempt {
        val normalized = username.trim()
        require(normalized.isNotEmpty() && password.isNotEmpty()) { "请输入账号和密码" }
        val url = activeUrl()
        val response = api(url).call(AuthRequest(normalized, password))
        require(response.tokenType.equals("Bearer", ignoreCase = true) && response.accessToken.isNotBlank()) { "服务器返回了无效登录凭据" }
        tokens.save(url, normalized, response.accessToken)
        mutableState.value = AuthState(normalized, true)
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
        Result.failure(error)
    }
}
