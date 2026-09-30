package io.suirenx.core.data.sync

import io.suirenx.core.model.Asset
import io.suirenx.core.model.AssetStatus
import io.suirenx.core.model.ExpiryItem
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncCodecTest {
    private val codec = SyncCodec(Clock.fixed(Instant.parse("2026-09-24T00:00:00Z"), ZoneOffset.UTC))
    private val json = Json { encodeDefaults = true }

    @Test
    fun assetDtoUsesSnakeCaseExplicitZeroesAndEmptyArray() {
        val payload = codec.toPayload(
            Asset(
                id = "asset-1",
                name = "Camera",
                priceCents = 12_345,
                purchaseDate = LocalDate.parse("2025-01-02"),
                status = AssetStatus.Active,
                imageUrl = "",
                heldDays = 631,
                dailyCostCents = 20,
                purchaseChannel = null,
                warrantyEndDate = null,
                tags = emptyList(),
            ),
            deleted = false,
            baseVersion = 0,
        )

        assertEquals("ACTIVE", payload.status)
        assertEquals("", payload.warrantyEndDate)
        val encoded = json.encodeToString(payload)
        assertTrue(encoded.contains("\"price_cents\":12345"))
        assertTrue(encoded.contains("\"purchase_date\":\"2025-01-02\""))
        assertTrue(encoded.contains("\"base_version\":0"))
        assertTrue(encoded.contains("\"tags\":[]"))
    }

    @Test
    fun expiryDtoPreservesOptionalEmptyFields() {
        val payload = codec.toExpiryPayload(
            ExpiryItem(
                id = "expiry-1",
                name = "Coffee",
                category = "Food",
                packageExpiryDate = LocalDate.parse("2026-12-01"),
                location = "",
                status = io.suirenx.core.model.ExpiryItemStatus.InUse,
            ),
            deleted = false,
            baseVersion = 0,
        )

        assertEquals("", payload.location)
        assertEquals("", payload.openedDate)
        assertEquals(0, payload.openedValidityDays)
        val encoded = json.encodeToString(payload)
        assertTrue(encoded.contains("\"location\":\"\""))
        assertTrue(encoded.contains("\"opened_date\":\"\""))
        assertTrue(encoded.contains("\"opened_validity_days\":0"))
    }
}
