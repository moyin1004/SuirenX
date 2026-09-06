package io.suirenx.core.model

import java.time.LocalDate
import java.time.Instant

enum class AssetStatus {
    Active,
    Retired,
}

data class Asset(
    val id: String,
    val name: String,
    val priceCents: Long,
    val purchaseDate: LocalDate,
    val status: AssetStatus,
    val imageUrl: String,
    val heldDays: Int,
    val dailyCostCents: Long,
    val retiredDate: LocalDate? = null,
    val archivedAt: Instant? = null,
    val iconKey: String = "devices",
    val purchaseChannel: String? = null,
    val warrantyEndDate: LocalDate? = null,
    val notes: String = "",
    val tags: List<String> = emptyList(),
    val createdAt: Instant = Instant.EPOCH,
    val updatedAt: Instant = Instant.EPOCH,
    val syncVersion: Long = 0,
) {
    val isArchived: Boolean get() = archivedAt != null
}
