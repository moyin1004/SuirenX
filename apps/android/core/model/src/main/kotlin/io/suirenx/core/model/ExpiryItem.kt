package io.suirenx.core.model

import java.time.LocalDate
import java.time.Instant

enum class ExpiryItemStatus { InUse, UsedUp, Discarded }

enum class ExpiryBucket { Expired, DueToday, ExpiringSoon, Normal, Inactive }

data class ExpiryItem(
    val id: String,
    val name: String,
    val category: String,
    val packageExpiryDate: LocalDate,
    val openedDate: LocalDate? = null,
    val openedValidityDays: Int? = null,
    val location: String = "",
    val notes: String = "",
    val status: ExpiryItemStatus = ExpiryItemStatus.InUse,
    val archivedAt: Instant? = null,
    val createdAt: Instant = Instant.EPOCH,
    val updatedAt: Instant = Instant.EPOCH,
    val syncVersion: Long = 0,
) {
    val actualExpiryDate: LocalDate
        get() = listOfNotNull(packageExpiryDate, openedDate?.let { date -> openedValidityDays?.let { date.plusDays(it.toLong() - 1) } }).min()

    fun bucket(today: LocalDate, soonDays: Int): ExpiryBucket = when {
        status != ExpiryItemStatus.InUse || archivedAt != null -> ExpiryBucket.Inactive
        actualExpiryDate.isBefore(today) -> ExpiryBucket.Expired
        actualExpiryDate == today -> ExpiryBucket.DueToday
        !actualExpiryDate.isAfter(today.plusDays(soonDays.toLong())) -> ExpiryBucket.ExpiringSoon
        else -> ExpiryBucket.Normal
    }
}

data class NewExpiryItem(
    val name: String,
    val category: String,
    val packageExpiryDate: LocalDate,
    val openedDate: LocalDate? = null,
    val openedValidityDays: Int? = null,
    val location: String = "",
    val notes: String = "",
)
