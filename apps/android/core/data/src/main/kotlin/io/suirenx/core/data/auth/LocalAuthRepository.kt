package io.suirenx.core.data.auth

import io.suirenx.core.model.UserFacingErrors

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

internal fun authFailureMessage(error: HttpException, json: Json): String {
    val response = runCatching {
        json.parseToJsonElement(error.response()?.errorBody()?.string().orEmpty()).jsonObject
    }.getOrNull()
    val code = response?.get("code")?.jsonPrimitive?.contentOrNull
    val body = response?.get("error")?.jsonPrimitive?.contentOrNull
    return when {
        code == "registration_closed" -> "error_auth_registration_closed"
        body == "username is already registered" -> "error_auth_username_registered"
        error.code() == 401 -> "error_auth_invalid_credentials"
        error.code() == 429 -> "error_rate_limited"
        error.code() >= 500 -> "error_server_unavailable"
        else -> "error_auth_request_failed"
    }
}

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
        val saved = tokens.savedAccounts()
        tokens.retainOnly(url?.takeIf(saved::containsKey))
        val account = tokens.savedAccounts().entries.firstOrNull()
        accounts.value = account?.let { mapOf(it.key to AuthState(it.value, true)) }.orEmpty()
        mutableState.value = account?.let { AuthState(it.value, true) } ?: AuthState("", false)
        Result.success(Unit)
    }

    override suspend fun register(username: String, password: String) = registerAt(activeUrl(), username, password)
    override suspend fun registerAt(url: String, username: String, password: String): Result<Unit> {
        if (username.trim().toByteArray(Charsets.UTF_8).size !in 1..100 || password.toByteArray(Charsets.UTF_8).size !in 8..72) {
            return Result.failure(IllegalArgumentException("error_auth_registration_invalid"))
        }
        return authenticate(url, username, password, AuthApi::register, false)
    }
    override suspend fun login(username: String, password: String): Result<Unit> = authenticate(activeUrl(), username, password, AuthApi::login, true)
    override suspend fun loginAt(url: String, username: String, password: String): Result<Unit> = authenticate(url, username, password, AuthApi::login, false)

    override suspend fun logout(): Result<Unit> {
        val url = tokens.savedAccounts().keys.firstOrNull() ?: return Result.success(Unit)
        return logoutAt(url)
    }
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
        require(normalized.isNotEmpty() && password.isNotEmpty()) { "error_auth_credentials_required" }
        check(tokens.savedAccounts().isEmpty()) { "error_auth_logout_first" }
        val knownAtStart = backends.settings.value?.servers?.any { it.url == url } == true
        val response = api(url).call(AuthRequest(normalized, password))
        require(response.tokenType.equals("Bearer", ignoreCase = true) && response.accessToken.isNotBlank()) { "error_auth_invalid_token" }
        check(!requireCurrent || backends.settings.value?.activeUrl == url) { "error_auth_server_changed" }
        check(!knownAtStart || backends.settings.value?.servers?.any { it.url == url } == true) { "error_auth_address_changed" }
        tokens.save(url, normalized, response.accessToken)
        initialize()
        }
    }

    private fun activeUrl(): String = checkNotNull(backends.settings.value?.activeUrl) { "error_backend_required" }

    private fun api(url: String): AuthApi {
        return Retrofit.Builder().baseUrl(url).client(client).addConverterFactory(json.asConverterFactory("application/json".toMediaType())).build().create(AuthApi::class.java)
    }

    private suspend fun <T> attempt(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        val message = when (error) {
            is HttpException -> authFailureMessage(error, json)
            is IOException -> "error_auth_network"
            else -> UserFacingErrors.codeOrFallback(error.message, "error_auth_operation_failed")
        }
        Result.failure(IllegalStateException(message, error))
    }
}
