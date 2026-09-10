package io.suirenx.core.data.repository

import androidx.room.withTransaction
import io.suirenx.core.data.local.AssetDao
import io.suirenx.core.data.local.AssetEntity
import io.suirenx.core.data.local.LocalDatabase
import io.suirenx.core.domain.AssetRepository
import io.suirenx.core.model.Asset
import io.suirenx.core.model.AssetStatus
import io.suirenx.core.model.NewAsset
import io.suirenx.core.model.calculateDailyCostCents
import io.suirenx.core.model.calculateHeldDays
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class LocalAssetRepository @Inject constructor(
    private val database: LocalDatabase,
    private val json: Json,
    private val clock: Clock,
    private val journal: io.suirenx.core.data.sync.LocalSyncJournal,
) : AssetRepository {
    private val dao: AssetDao = database.assetDao()

    override suspend fun deleteAsset(id: String): Result<Unit> = attempt {
        dao.getById(id)?.let { journal.asset(toDomain(it), deleted = true) }
        dao.delete(id)
    }

    override suspend fun updateAssetArchive(id: String, archive: Boolean): Result<Asset> = attempt {
        val current = requireAsset(id)
        val updated = current.copy(
            archivedAt = if (archive) current.archivedAt ?: Instant.now(clock) else null,
            updatedAt = Instant.now(clock),
        )
        dao.update(updated.toEntity())
        journal.asset(updated)
        updated
    }

    override suspend fun updateAssetStatus(id: String, status: AssetStatus, retiredDate: LocalDate?): Result<Asset> = attempt {
        val current = requireAsset(id)
        check(!current.isArchived) { "归档资产请先恢复" }
        val updated = current.copy(status = status, retiredDate = retiredDate, updatedAt = Instant.now(clock))
        dao.update(updated.toEntity())
        journal.asset(updated)
        updated
    }

    override suspend fun createAsset(asset: NewAsset): Result<Asset> = attempt {
        val now = Instant.now(clock)
        val saved = Asset(
            id = asset.id ?: UUID.randomUUID().toString(), name = asset.name.trim(), priceCents = asset.priceCents,
            purchaseDate = asset.purchaseDate, status = AssetStatus.Active, imageUrl = "",
            heldDays = 0, dailyCostCents = 0, iconKey = asset.iconKey,
            purchaseChannel = asset.purchaseChannel, warrantyEndDate = asset.warrantyEndDate,
            notes = asset.notes, tags = asset.tags, archivedAt = null,
        )
        val persisted = saved.copy(createdAt = now, updatedAt = now)
        dao.insert(persisted.toEntity())
        journal.asset(persisted)
        persisted
    }

    override suspend fun updateAsset(id: String, asset: NewAsset): Result<Asset> = attempt {
        val current = requireAsset(id)
        check(!current.isArchived) { "归档资产请先恢复" }
        check(current.status != AssetStatus.Retired || current.retiredDate == null || asset.purchaseDate <= current.retiredDate) {
            "购买日期不能晚于退役日期"
        }
        val updated = current.copy(
            name = asset.name.trim(), priceCents = asset.priceCents, purchaseDate = asset.purchaseDate,
            iconKey = asset.iconKey, purchaseChannel = asset.purchaseChannel,
            warrantyEndDate = asset.warrantyEndDate, notes = asset.notes, tags = asset.tags,
            updatedAt = Instant.now(clock),
        )
        dao.update(updated.toEntity())
        journal.asset(updated)
        updated
    }

    override suspend fun getAssets(status: AssetStatus?, includeArchived: Boolean): Result<List<Asset>> = attempt {
        dao.getAll().asSequence().map(::toDomain).filter { asset ->
            (includeArchived || !asset.isArchived) && (status == null || asset.status == status)
        }.toList()
    }

    override suspend fun getAsset(id: String): Result<Asset> = attempt { requireAsset(id) }

    suspend fun replaceAll(assets: List<Asset>) {
        database.withTransaction {
            dao.getAll().forEach { journal.asset(toDomain(it), deleted = true) }
            dao.deleteAll()
            dao.insertAll(assets.map { it.toEntity() })
            assets.forEach { journal.asset(it) }
        }
    }

    suspend fun allForBackup(): List<Asset> = dao.getAll().map(::toDomain)

    private suspend fun requireAsset(id: String): Asset = dao.getById(id)?.let(::toDomain)
        ?: throw NoSuchElementException("未找到资产")

    private fun toDomain(entity: AssetEntity): Asset {
        val status = if (entity.status == AssetStatus.Retired.name) AssetStatus.Retired else AssetStatus.Active
        val retiredDate = entity.retiredDate?.let(LocalDate::parse)
        val heldDays = calculateHeldDays(entity.purchaseDate.toDate(), status, retiredDate, today())
        return Asset(
            id = entity.id, name = entity.name, priceCents = entity.priceCents,
            purchaseDate = entity.purchaseDate.toDate(), status = status, imageUrl = "",
            heldDays = heldDays, dailyCostCents = calculateDailyCostCents(entity.priceCents, heldDays),
            retiredDate = retiredDate, archivedAt = entity.archivedAt?.let(Instant::parse), iconKey = entity.iconKey,
            purchaseChannel = entity.purchaseChannel, warrantyEndDate = entity.warrantyEndDate?.let(LocalDate::parse),
            notes = entity.notes, tags = json.decodeFromString(entity.tagsJson),
            createdAt = Instant.parse(entity.createdAt), updatedAt = Instant.parse(entity.updatedAt),
        )
    }

    private fun today(): LocalDate = LocalDate.now(clock.withZone(ZoneId.systemDefault()))

    internal fun Asset.toEntity(): AssetEntity = AssetEntity(
        id = id, name = name, priceCents = priceCents, purchaseDate = purchaseDate.toString(),
        status = status.name, retiredDate = retiredDate?.toString(), archivedAt = archivedAt?.toString(),
        iconKey = iconKey, purchaseChannel = purchaseChannel, warrantyEndDate = warrantyEndDate?.toString(),
        notes = notes, tagsJson = json.encodeToString(tags),
        createdAt = createdAt.toString(), updatedAt = updatedAt.toString(),
    )

    private fun String.toDate() = LocalDate.parse(this)
    private suspend fun <T> attempt(block: suspend () -> T): Result<T> = try {
        Result.success(database.withTransaction { block() })
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        Result.failure(error)
    }
}
