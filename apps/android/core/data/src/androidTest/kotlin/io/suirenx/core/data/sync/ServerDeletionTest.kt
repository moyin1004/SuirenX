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

@RunWith(AndroidJUnit4::class)
class ServerDeletionTest {
    private fun isolatedContext(): Context {
        val prefix = "server-delete-${java.util.UUID.randomUUID()}-"
        return object : ContextWrapper(InstrumentationRegistry.getInstrumentation().targetContext) {
            override fun getSharedPreferences(name: String, mode: Int) = super.getSharedPreferences(prefix + name, mode)
        }
    }

    @Test fun editingPreservesIdentityAndOnlyAddressChangeClearsItsCredential() = runBlocking {
        val context = isolatedContext(); val tokens = AuthTokenStore(context); val repo = LocalBackendRepository(context, Json, tokens)
        repo.saveAndSelect("https://one.test/", "One").getOrThrow()
        repo.saveServer(null, "https://two.test/", "Two").getOrThrow()
        tokens.save("https://one.test/", "one", "one-token")
        tokens.save("https://two.test/", "two", "two-token")
        repo.saveServer("https://one.test/", "https://one.test/", "Renamed").getOrThrow()
        assertEquals("one-token", tokens.tokenFor("https://one.test/"))
        assertTrue(repo.saveServer("https://one.test/", "https://two.test/", "Duplicate").isFailure)
        repo.saveServer("https://one.test/", "https://new.test/", "New").getOrThrow()
        assertEquals(2, repo.settings.value!!.servers.size)
        assertEquals("https://new.test/", repo.settings.value!!.activeUrl)
        assertNull(tokens.tokenFor("https://one.test/"))
        assertNull(tokens.tokenFor("https://new.test/"))
        assertEquals("two-token", tokens.tokenFor("https://two.test/"))
    }

    @Test fun legacyCredentialMigratesAndLogoutIsServerScoped() {
        val context = isolatedContext()
        context.getSharedPreferences("auth", Context.MODE_PRIVATE).edit()
            .putString("server_url", "https://one.test/").putString("username", "legacy").putString("access_token", "legacy-token").commit()
        val tokens = AuthTokenStore(context)
        assertEquals("legacy-token", tokens.tokenFor("https://one.test/"))
        tokens.save("https://two.test/", "two", "two-token")
        tokens.clearFor("https://two.test/")
        assertEquals("legacy-token", AuthTokenStore(context).tokenFor("https://one.test/"))
        assertNull(tokens.tokenFor("https://two.test/"))
        assertFalse(context.getSharedPreferences("auth", Context.MODE_PRIVATE).contains("access_token"))
    }

    @Test fun deletingCurrentClearsCredentialsAndDoesNotSelectAnotherOnRestart() = runBlocking {
        val context = isolatedContext()
        val tokens = AuthTokenStore(context)
        val repo = LocalBackendRepository(context, Json, tokens)
        repo.saveAndSelect("https://first.test/", "First").getOrThrow()
        repo.saveAndSelect("https://second.test/", "Second").getOrThrow()
        tokens.save("https://second.test/", "user", "fake-test-token")
        repo.remove("https://second.test/").getOrThrow()
        assertNull(repo.settings.value!!.activeUrl)
        assertNull(tokens.tokenFor("https://second.test/"))
        val reopened = LocalBackendRepository(context, Json, tokens)
        reopened.initialize().getOrThrow()
        assertNull(reopened.settings.value!!.activeUrl)
        assertEquals(listOf("https://first.test/"), reopened.settings.value!!.servers.map { it.url })
        reopened.saveAndSelect("https://second.test/", "Second").getOrThrow()
        assertNull(tokens.tokenFor("https://second.test/"))
    }

    @Test fun deletingInactivePreservesCurrentConnectionAndCredential() = runBlocking {
        val context = isolatedContext()
        val tokens = AuthTokenStore(context)
        val repo = LocalBackendRepository(context, Json, tokens)
        repo.saveAndSelect("https://first.test/", "First").getOrThrow()
        repo.saveAndSelect("https://second.test/", "Second").getOrThrow()
        tokens.save("https://second.test/", "user", "fake-test-token")
        repo.remove("https://first.test/").getOrThrow()
        assertEquals("https://second.test/", repo.settings.value!!.activeUrl)
        assertEquals("fake-test-token", tokens.tokenFor("https://second.test/"))
        repo.remove("https://second.test/").getOrThrow()
        assertTrue(repo.settings.value!!.servers.isEmpty())
        assertNull(repo.settings.value!!.activeUrl)
    }
}
