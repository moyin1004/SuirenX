package io.suirenx.core.model

import java.time.LocalDate

data class NewAsset(
    val name: String,
    val priceCents: Long,
    val purchaseDate: LocalDate,
    val iconKey: String = "item",
    val purchaseChannel: String? = null,
    val warrantyEndDate: LocalDate? = null,
    val notes: String = "",
    val tags: List<String> = emptyList(),
    val id: String? = null,
)
