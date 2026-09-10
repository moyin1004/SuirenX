package io.suirenx.core.data.sync

import android.content.Context
import android.content.ContextWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.suirenx.core.data.auth.AuthTokenStore
import io.suirenx.core.data.auth.LocalAuthRepository
import io.suirenx.core.domain.BackendRepository
import io.suirenx.core.model.BackendSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class AccountFlowTest {
    private val prefix = "account-test-${UUID.randomUUID()}-"
    private val context = object : ContextWrapper(InstrumentationRegistry.getInstrumentation().targetContext) {
        override fun getSharedPreferences(name: String, mode: Int) = super.getSharedPreferences(prefix + name, mode)
    }
    private val tokens = AuthTokenStore(context)
    private val backends = object : BackendRepository {
        override val settings = MutableStateFlow<BackendSettings?>(BackendSettings(activeUrl = "https://one.test/"))
        override suspend fun initialize() = Result.success(Unit)
        override suspend fun saveAndSelect(address: String, name: String): Result<Unit> {
            settings.value = BackendSettings(activeUrl = address); return Result.success(Unit)
        }
        override suspend fun select(url: String) = saveAndSelect(url, "")
        override suspend fun remove(url: String) = Result.success(Unit)
    }
    private fun repo(interceptor: Interceptor) = LocalAuthRepository(backends, OkHttpClient.Builder().addInterceptor(interceptor).build(), Json { ignoreUnknownKeys = true }, tokens)
    private fun reply(chain: Interceptor.Chain, code: Int, body: String) = Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(code).message("test").body(body.toResponseBody("application/json".toMediaType())).build()

    @Test fun registrationLogsInAndChangedTargetRejectsLateResponse() = runBlocking {
        val auth = repo { chain -> reply(chain, 200, """{"access_token":"test-only","token_type":"Bearer","expires_at":"2030-01-01T00:00:00Z"}""") }
        auth.register(" tester ", "password").getOrThrow()
        assertTrue(auth.state.value.authenticated)
        assertEquals("tester", auth.state.value.username)
        backends.select("https://two.test/")
        auth.initialize()
        assertFalse(auth.state.value.authenticated)
        assertEquals("", auth.state.value.username)
        val raced = repo { chain ->
            backends.settings.value = BackendSettings(activeUrl = "https://three.test/")
            reply(chain, 200, """{"access_token":"late","token_type":"Bearer","expires_at":"2030-01-01T00:00:00Z"}""")
        }
        assertTrue(raced.login("tester", "password").isFailure)
        assertNull(tokens.tokenFor("https://two.test/"))
    }

    @Test fun nonCurrentServerLoginAndLogoutPreserveCurrentAccount() = runBlocking {
        tokens.save("https://one.test/", "current", "current-token")
        val auth = repo { chain -> reply(chain, 200, """{"access_token":"other-token","token_type":"Bearer","expires_at":"2030-01-01T00:00:00Z","status":"ok"}""") }
        auth.loginAt("https://two.test/", "other", "password").getOrThrow()
        assertEquals("current", auth.state.value.username)
        assertEquals("other", auth.accounts.value["https://two.test/"]?.username)
        assertEquals("https://one.test/", backends.settings.value?.activeUrl)
        auth.logoutAt("https://two.test/").getOrThrow()
        assertEquals("current-token", tokens.tokenFor("https://one.test/"))
        assertNull(tokens.tokenFor("https://two.test/"))
        assertTrue(auth.state.value.authenticated)
    }

    @Test fun offlineLogoutClearsLocalCredentials() = runBlocking {
        tokens.save("https://one.test/", "tester", "test-only")
        val auth = repo { throw IOException("offline") }
        auth.initialize(); assertTrue(auth.state.value.authenticated)
        auth.logout().getOrThrow()
        assertFalse(auth.state.value.authenticated)
        assertNull(tokens.tokenFor("https://one.test/"))
    }

    @Test fun weakPasswordNeverRequestsServerAndDuplicateAccountIsReadable() = runBlocking {
        var requests = 0
        val auth = repo { chain -> requests++; reply(chain, 400, """{"error":"username is already registered"}""") }
        assertTrue(auth.register("tester", "short").isFailure)
        assertTrue(auth.register("tester", "a".repeat(73)).isFailure)
        assertEquals(0, requests)
        assertEquals("该账号已注册，请切换到登录", auth.register("tester", "password").exceptionOrNull()?.message)
    }
}
