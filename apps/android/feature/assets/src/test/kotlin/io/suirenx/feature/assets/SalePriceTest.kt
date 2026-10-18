package io.suirenx.feature.assets

import org.junit.Assert.*
import org.junit.Test

class SalePriceTest {
    @Test fun optionalExactCents() {
        assertNull(parseSalePrice("  "))
        assertEquals(0L, parseSalePrice("0"))
        assertEquals(80050L, parseSalePrice("800.50"))
        assertEquals(Long.MAX_VALUE, parseSalePrice("92233720368547758.07"))
        for (bad in listOf("-1", "1.001", "1e2", "NaN", "92233720368547758.08")) {
            try { parseSalePrice(bad); fail(bad) } catch (_: IllegalArgumentException) {}
        }
    }
}
