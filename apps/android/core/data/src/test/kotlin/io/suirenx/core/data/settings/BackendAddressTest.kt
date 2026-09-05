package io.suirenx.core.data.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BackendAddressTest {
    @Test fun normalizesDomainsIPsPortsAndPrefixes() {
        for ((input, expected) in mapOf(
            " api.example.com " to "https://api.example.com/",
            "192.168.1.2:8888" to "http://192.168.1.2:8888/",
            "localhost:8888" to "http://localhost:8888/",
            "[::1]:8888" to "http://[::1]:8888/",
            "https://EXAMPLE.com:443/backend" to "https://example.com/backend/",
            "http://example.com:8888/" to "http://example.com:8888/",
        )) assertEquals(expected, normalizeBackendAddress(input, allowHttp = true))
    }

    @Test fun rejectsMalformedOrAmbiguousAddresses() {
        for (input in listOf("", "not a host", "ftp://example.com", "https://user:pass@example.com", "https://example.com/?token=a", "https://example.com/#x", "http://host:99999", "http://999.999.999.999", "http://256.0.0.1", "http:\\example.com")) {
            assertThrows(input, IllegalArgumentException::class.java) { normalizeBackendAddress(input, true) }
        }
    }

    @Test fun releaseRequiresHttps() {
        assertEquals("https://example.com/", normalizeBackendAddress("example.com", false))
        assertThrows(IllegalArgumentException::class.java) { normalizeBackendAddress("192.168.1.2:8888", false) }
    }
}
