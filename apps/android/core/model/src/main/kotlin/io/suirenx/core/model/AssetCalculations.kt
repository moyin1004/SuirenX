package io.suirenx.core.model

import java.time.LocalDate

/** Pure Kotlin business calculations shared by local Android storage and the API contract. */
fun calculateHeldDays(purchaseDate: LocalDate, status: AssetStatus, retiredDate: LocalDate?, today: LocalDate): Int {
    val end = if (status == AssetStatus.Retired && retiredDate != null) retiredDate else today
    return maxOf(1, end.toEpochDay().minus(purchaseDate.toEpochDay()).toInt() + 1)
}

fun calculateDailyCostCents(priceCents: Long, heldDays: Int): Long {
    require(heldDays > 0)
    val days = heldDays.toLong()
    val quotient = priceCents / days
    return if (priceCents % days >= (days + 1) / 2) quotient + 1 else quotient
}
