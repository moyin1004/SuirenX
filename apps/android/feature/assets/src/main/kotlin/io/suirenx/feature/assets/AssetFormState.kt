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
    val iconKey: String = "devices",
    val purchaseChannel: String = "",
    val warrantyEndDate: String = "",
    val notes: String = "",
    val tags: String = "",
)

internal fun AssetFormState.toNewAsset(): NewAsset {
    require(name.isNotBlank()) { "请输入资产名称" }
    val amount = price.trim()
    require(amount.matches(Regex("[0-9]+(\\.[0-9]{1,2})?"))) {
        "请输入非负金额，最多两位小数"
    }
    val cents = try {
        BigDecimal(amount).movePointRight(2).longValueExact()
    } catch (_: ArithmeticException) {
        throw IllegalArgumentException("金额过大，请检查输入")
    }
    val date = try {
        require(purchaseDate.matches(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}")))
        LocalDate.parse(purchaseDate)
    } catch (_: IllegalArgumentException) {
        throw IllegalArgumentException("请输入有效日期，格式为 YYYY-MM-DD")
    } catch (_: DateTimeParseException) {
        throw IllegalArgumentException("请输入有效日期，格式为 YYYY-MM-DD")
    }
    require(notes.length <= 2000) { "备注不能超过 2000 个字符" }
    val normalizedTags = tags.split(',').map(String::trim).filter(String::isNotEmpty).distinct()
    require(normalizedTags.size <= 20 && normalizedTags.all { it.length <= 30 }) { "标签最多 20 个，每个不超过 30 个字符" }
    val warranty = warrantyEndDate.trim().takeIf(String::isNotEmpty)?.also {
        try { LocalDate.parse(it) } catch (_: Exception) { throw IllegalArgumentException("请输入有效保修截止日，格式为 YYYY-MM-DD") }
    }
    return NewAsset(name.trim(), cents, date, iconKey, purchaseChannel.trim().ifEmpty { null }, warranty?.let(LocalDate::parse), notes.trim(), normalizedTags)
}
