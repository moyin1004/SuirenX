package io.suirenx.core.model

import java.time.LocalDate

data class NewAsset(
    val name: String,
    val priceCents: Long,
    val purchaseDate: LocalDate,
    val iconKey: String = "devices",
)
