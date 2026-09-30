package io.suirenx.core.data.sync

import android.content.Context
import android.content.ContextWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.suirenx.core.data.auth.AuthTokenStore
import io.suirenx.core.data.settings.LocalBackendRepository
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.net.ServerSocket
import java.nio.charset.StandardCharsets

@RunWith(AndroidJUnit4::class)
class ServerDeletionTest {
    private class HealthyServer : AutoCloseable {
        private val socket = ServerSocket(0, 8, java.net.InetAddress.getByName("127.0.0.1"))
        val address = "http://127.0.0.1:${socket.localPort}/"
        @Volatile private var running = true
        private val worker = Thread {
            while (running) try {
                socket.accept().use { client ->
                    val reader = client.getInputStream().bufferedReader(StandardCharsets.US_ASCII)
                    while (reader.readLine()?.isNotEmpty() == true) { }
                    val body = "{\"status\":\"ok\"}"
                    val response = "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${body.toByteArray().size}\r\nConnection: close\r\n\r\n$body"
                    client.getOutputStream().apply { write(response.toByteArray(StandardCharsets.UTF_8)); flush() }
                }
            } catch (_: java.io.IOException) { if (running) throw RuntimeException("health fixture failed") }
        }.apply { isDaemon = true; start() }
        override fun close() { running = false; socket.close(); worker.join(500) }
    }

    private fun isolatedContext(): Context {
        val prefix = "server-delete-${java.util.UUID.randomUUID()}-"
        return object : ContextWrapper(InstrumentationRegistry.getInstrumentation().targetContext) {
            override fun getSharedPreferences(name: String, mode: Int) = super.getSharedPreferences(prefix + name, mode)
        }
    }

    @Test fun newAddressesRequireSuccessfulProbeAndChangingAddressPreservesAccount() = runBlocking {
        HealthyServer().use { first -> HealthyServer().use { second -> HealthyServer().use { third ->
            val context = isolatedContext(); val tokens = AuthTokenStore(context); val repo = LocalBackendRepository(context, Json)
            assertTrue(repo.saveAndSelect(second.address, "Two").isFailure)
            repo.testConnection(first.address).getOrThrow()
            repo.saveAndSelect(first.address, "One").getOrThrow()
            repo.testConnection(second.address).getOrThrow()
            repo.saveServer(null, second.address, "Two").getOrThrow()
            tokens.save(first.address, "one", "one-token")
            repo.saveServer(first.address, first.address, "Renamed").getOrThrow()
            assertEquals("one-token", tokens.tokenFor(first.address))
            assertTrue(repo.saveServer(first.address, second.address, "Duplicate").isFailure)
            repo.testConnection(third.address).getOrThrow()
            repo.saveServer(first.address, third.address, "New").getOrThrow()
            assertEquals(2, repo.settings.value!!.servers.size)
            assertEquals(third.address, repo.settings.value!!.activeUrl)
            assertEquals("one-token", tokens.tokenFor(first.address))
            assertNull(tokens.tokenFor(third.address))
        } } }
    }

    @Test fun legacyCredentialMigratesAndLogoutClearsTheSingleSession() {
        val context = isolatedContext()
        context.getSharedPreferences("auth", Context.MODE_PRIVATE).edit()
            .putString("server_url", "https://one.test/").putString("username", "legacy").putString("access_token", "legacy-token").commit()
        val tokens = AuthTokenStore(context)
        assertEquals("legacy-token", tokens.tokenFor("https://one.test/"))
        tokens.clear()
        tokens.save("https://two.test/", "two", "two-token")
        tokens.clearFor("https://two.test/")
        assertNull(AuthTokenStore(context).tokenFor("https://one.test/"))
        assertNull(tokens.tokenFor("https://two.test/"))
        assertFalse(context.getSharedPreferences("auth", Context.MODE_PRIVATE).contains("access_token"))
    }

    @Test fun deletingCurrentAddressPreservesAccountAndDoesNotSelectAnotherOnRestart() = runBlocking {
        HealthyServer().use { first -> HealthyServer().use { second ->
            val context = isolatedContext()
            val tokens = AuthTokenStore(context)
            val repo = LocalBackendRepository(context, Json)
            repo.testConnection(first.address).getOrThrow()
            repo.saveAndSelect(first.address, "First").getOrThrow()
            repo.testConnection(second.address).getOrThrow()
            repo.saveAndSelect(second.address, "Second").getOrThrow()
            tokens.save(second.address, "user", "fake-test-token")
            repo.remove(second.address).getOrThrow()
            assertNull(repo.settings.value!!.activeUrl)
            assertEquals("fake-test-token", tokens.tokenFor(second.address))
            val reopened = LocalBackendRepository(context, Json)
            reopened.initialize().getOrThrow()
            assertNull(reopened.settings.value!!.activeUrl)
            assertEquals(listOf(first.address), reopened.settings.value!!.servers.map { it.url })
            reopened.testConnection(second.address).getOrThrow()
            reopened.saveAndSelect(second.address, "Second").getOrThrow()
            assertEquals("fake-test-token", tokens.tokenFor(second.address))
        } }
    }

    @Test fun deletingInactivePreservesCurrentConnectionAndCredential() = runBlocking {
        HealthyServer().use { first -> HealthyServer().use { second ->
            val context = isolatedContext()
            val tokens = AuthTokenStore(context)
            val repo = LocalBackendRepository(context, Json)
            repo.testConnection(first.address).getOrThrow(); repo.saveAndSelect(first.address, "First").getOrThrow()
            repo.testConnection(second.address).getOrThrow(); repo.saveAndSelect(second.address, "Second").getOrThrow()
            tokens.save(second.address, "user", "fake-test-token")
            repo.remove(first.address).getOrThrow()
            assertEquals(second.address, repo.settings.value!!.activeUrl)
            assertEquals("fake-test-token", tokens.tokenFor(second.address))
            repo.remove(second.address).getOrThrow()
            assertTrue(repo.settings.value!!.servers.isEmpty())
            assertNull(repo.settings.value!!.activeUrl)
            assertEquals("fake-test-token", tokens.tokenFor(second.address))
        } }
    }
}
