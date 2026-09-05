package io.suirenx.feature.assets

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AssetFormStateTest {
    @Test
    fun convertsMoneyWithoutFloatingPointRounding() {
        for ((input, cents) in mapOf(
            "0" to 0L,
            "0.01" to 1L,
            "19.99" to 1999L,
            " 899.5 " to 89950L,
            "90071992547409.93" to 9007199254740993L,
            "92233720368547758.07" to Long.MAX_VALUE,
        )) {
            val asset = AssetFormState(" Keyboard ", input, "2024-02-29").toNewAsset()
            assertEquals(cents, asset.priceCents)
            assertEquals("Keyboard", asset.name)
            assertEquals("2024-02-29", asset.purchaseDate.toString())
        }
    }

    @Test
    fun rejectsInvalidAndOverflowingMoney() {
        for (input in listOf("", "-1", "1.001", "1e3", "1,000", ".1", "92233720368547758.08")) {
            assertThrows(IllegalArgumentException::class.java) {
                AssetFormState("Keyboard", input, "2026-09-05").toNewAsset()
            }
        }
    }

    @Test
    fun rejectsInvalidDatesAndBlankNames() {
        for (input in listOf("", "2026-02-29", "2026-2-01", "2026-13-01", "2026-01-01T00:00:00Z")) {
            assertThrows(IllegalArgumentException::class.java) {
                AssetFormState("Keyboard", "1", input).toNewAsset()
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            AssetFormState("  ", "1", "2026-09-05").toNewAsset()
        }
    }
}
