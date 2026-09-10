package io.suirenx.core.data.sync

import io.suirenx.core.data.network.*
import io.suirenx.core.model.*
import java.time.*
import javax.inject.Inject

class SyncCodec @Inject constructor(private val clock: Clock) {
    fun toPayload(asset: Asset, deleted: Boolean, baseVersion: Long) = SyncAssetPayload(
        id = asset.id, baseVersion = baseVersion, deleted = deleted,
        name = asset.name, priceCents = asset.priceCents, purchaseDate = asset.purchaseDate.toString(),
        status = asset.status.toApiValue(), imageUrl = asset.imageUrl,
        retiredDate = asset.retiredDate?.toString().orEmpty(), archivedAt = asset.archivedAt?.toString().orEmpty(),
        iconKey = asset.iconKey, purchaseChannel = asset.purchaseChannel.orEmpty(),
        warrantyEndDate = asset.warrantyEndDate?.toString().orEmpty(), notes = asset.notes, tags = asset.tags,
    )

    fun toExpiryPayload(item: ExpiryItem, deleted: Boolean, baseVersion: Long) = SyncExpiryPayload(
        id = item.id, baseVersion = baseVersion, deleted = deleted, name = item.name, category = item.category,
        packageExpiryDate = item.packageExpiryDate.toString(), openedDate = item.openedDate?.toString().orEmpty(),
        openedValidityDays = item.openedValidityDays ?: 0, location = item.location, notes = item.notes,
        status = item.status.toApiValue(), archivedAt = item.archivedAt?.toString().orEmpty(),
    )

    fun SyncAssetPayload.toAsset(version: Long, deletedAt: String): Asset {
        val statusValue = if (status == "RETIRED") AssetStatus.Retired else AssetStatus.Active
        val retired = retiredDate.takeIf(String::isNotBlank)?.let(LocalDate::parse)
        val purchase = LocalDate.parse(purchaseDate)
        val heldDays = calculateHeldDays(purchase, statusValue, retired, today())
        val timestamp = deletedAt.takeIf(String::isNotBlank)?.let(Instant::parse) ?: Instant.now(clock)
        return Asset(
            id = id, name = name, priceCents = priceCents, purchaseDate = purchase, status = statusValue,
            imageUrl = imageUrl, heldDays = heldDays, dailyCostCents = calculateDailyCostCents(priceCents, heldDays),
            retiredDate = retired, archivedAt = archivedAt.takeIf(String::isNotBlank)?.let(Instant::parse),
            iconKey = iconKey.ifBlank { "devices" }, purchaseChannel = purchaseChannel.takeIf(String::isNotBlank),
            warrantyEndDate = warrantyEndDate.takeIf(String::isNotBlank)?.let(LocalDate::parse), notes = notes,
            tags = tags, createdAt = timestamp, updatedAt = timestamp, syncVersion = version,
        )
    }

    fun SyncExpiryPayload.toExpiryItem(version: Long): ExpiryItem {
        val timestamp = Instant.now(clock)
        return ExpiryItem(
            id = id, name = name, category = category, packageExpiryDate = LocalDate.parse(packageExpiryDate),
            openedDate = openedDate.takeIf(String::isNotBlank)?.let(LocalDate::parse),
            openedValidityDays = openedValidityDays.takeIf { it > 0 }, location = location, notes = notes,
            status = when (status) {
                "USED_UP" -> ExpiryItemStatus.UsedUp
                "DISCARDED" -> ExpiryItemStatus.Discarded
                else -> ExpiryItemStatus.InUse
            }, archivedAt = archivedAt.takeIf(String::isNotBlank)?.let(Instant::parse),
            createdAt = timestamp, updatedAt = timestamp, syncVersion = version,
        )
    }

    fun today(): LocalDate = LocalDate.now(clock.withZone(ZoneId.systemDefault()))
    fun String.toDate() = LocalDate.parse(this)
    fun AssetStatus.toApiValue() = if (this == AssetStatus.Retired) "RETIRED" else "ACTIVE"
    fun ExpiryItemStatus.toApiValue() = when (this) {
        ExpiryItemStatus.InUse -> "IN_USE"
        ExpiryItemStatus.UsedUp -> "USED_UP"
        ExpiryItemStatus.Discarded -> "DISCARDED"
    }

}
