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
) {
    val isArchived: Boolean get() = archivedAt != null
}

