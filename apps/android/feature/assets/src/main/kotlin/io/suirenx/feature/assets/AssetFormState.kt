package io.suirenx.feature.assets

import io.suirenx.core.model.NewAsset
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeParseException

data class AssetFormState(
    val name: String = "",
    val price: String = "",
    val purchaseDate: String = LocalDate.now().toString(),
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val iconKey: String = "item",
    val purchaseChannel: String = "",
    val warrantyEndDate: String = "",
    val notes: String = "",
    val tags: String = "",
)

internal fun AssetFormState.toNewAsset(): NewAsset {
    require(name.isNotBlank()) { "error_asset_name_required" }
    val amount = price.trim()
    require(amount.matches(Regex("[0-9]+(\\.[0-9]{1,2})?"))) {
        "error_amount_invalid"
    }
    val cents = try {
        BigDecimal(amount).movePointRight(2).longValueExact()
    } catch (_: ArithmeticException) {
        throw IllegalArgumentException("error_amount_too_large")
    }
    val date = try {
        require(purchaseDate.matches(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}")))
        LocalDate.parse(purchaseDate)
    } catch (_: IllegalArgumentException) {
        throw IllegalArgumentException("error_date_invalid")
    } catch (_: DateTimeParseException) {
        throw IllegalArgumentException("error_date_invalid")
    }
    require(!date.isAfter(LocalDate.now())) { "error_purchase_future" }
    require(notes.length <= 2000) { "error_notes_too_long" }
    val normalizedTags = tags.split(',').map(String::trim).filter(String::isNotEmpty).distinct()
    require(normalizedTags.size <= 20 && normalizedTags.all { it.length <= 30 }) { "error_tags_invalid" }
    val warranty = warrantyEndDate.trim().takeIf(String::isNotEmpty)?.let { value ->
        try { LocalDate.parse(value) } catch (_: Exception) { throw IllegalArgumentException("error_warranty_invalid") }
    }
    require(warranty == null || !warranty.isBefore(date)) { "error_warranty_before_purchase" }
    return NewAsset(name.trim(), cents, date, iconKey, purchaseChannel.trim().ifEmpty { null }, warranty, notes.trim(), normalizedTags)
}
