package io.suirenx.core.model

import java.time.LocalDate

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
)

