package io.suirenx.core.data.repository

import android.content.Context
import androidx.room.withTransaction
import dagger.hilt.android.qualifiers.ApplicationContext
import io.suirenx.core.data.local.LocalDatabase
import io.suirenx.core.domain.BackupSummary
import io.suirenx.core.domain.LocalDataSnapshot
import io.suirenx.core.domain.LocalBackupRepository
import io.suirenx.core.model.Asset
import io.suirenx.core.model.AssetStatus
import io.suirenx.core.model.ExpiryItemStatus
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val LOCAL_BACKUP_FORMAT = "suirenx-local-backup"
private const val LOCAL_BACKUP_VERSION = 1

@Serializable
private data class LocalBackup(
    val format: String = LOCAL_BACKUP_FORMAT,
    val version: Int = LOCAL_BACKUP_VERSION,
    val exportedAt: String = "",
    val assets: List<BackupAsset> = emptyList(),
    val expiryItems: List<BackupExpiryItem> = emptyList(),
)

@Serializable
private data class BackupExpiryItem(
    val id: String,
    val name: String,
    val category: String,
    val packageExpiryDate: String,
    val openedDate: String? = null,
    val openedValidityDays: Int? = null,
    val location: String = "",
    val notes: String = "",
    val status: String = "InUse",
    val archivedAt: String? = null,
    val createdAt: String = "",
    val updatedAt: String = "",
)

@Serializable
private data class BackupAsset(
    val id: String,
    val name: String,
    val priceCents: Long,
    val purchaseDate: String,
    val status: String = "ACTIVE",
    val retiredDate: String? = null,
    val archivedAt: String? = null,
    val iconKey: String = "devices",
    val purchaseChannel: String? = null,
    val warrantyEndDate: String? = null,
    val notes: String = "",
    val tags: List<String> = emptyList(),
    val createdAt: String = "",
    val updatedAt: String = "",
)

@Singleton
class LocalBackupRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: LocalDatabase,
    private val local: LocalAssetRepository,
    private val expiry: LocalExpiryRepository,
    private val json: Json,
) : LocalBackupRepository {
    override suspend fun snapshot(): Result<LocalDataSnapshot> = attempt {
        LocalDataSnapshot(local.allForBackup(), expiry.allForBackup())
    }

    override suspend fun createSafetyBackup(): Result<Unit> = attempt {
        val content = export().getOrThrow()
        withContext(Dispatchers.IO) {
            val directory = context.getDir("backups", Context.MODE_PRIVATE)
            directory.resolve("pre-remote-import-${System.currentTimeMillis()}.json").writeText(content)
        }
    }

    override suspend fun export(): Result<String> = attempt {
        val assets = local.allForBackup().map { asset ->
            BackupAsset(
                id = asset.id, name = asset.name, priceCents = asset.priceCents,
                purchaseDate = asset.purchaseDate.toString(), status = asset.status.name,
                retiredDate = asset.retiredDate?.toString(), archivedAt = asset.archivedAt?.toString(),
                iconKey = asset.iconKey, purchaseChannel = asset.purchaseChannel,
                warrantyEndDate = asset.warrantyEndDate?.toString(), notes = asset.notes, tags = asset.tags,
                createdAt = asset.createdAt.toString(), updatedAt = asset.updatedAt.toString(),
            )
        }
        val expiryItems = expiry.allForBackup().map { item ->
            BackupExpiryItem(item.id, item.name, item.category, item.packageExpiryDate.toString(), item.openedDate?.toString(), item.openedValidityDays, item.location, item.notes, item.status.name, item.archivedAt?.toString(), item.createdAt.toString(), item.updatedAt.toString())
        }
        json.encodeToString(LocalBackup(exportedAt = Instant.now().toString(), assets = assets, expiryItems = expiryItems))
    }

    override suspend fun inspect(content: String): Result<BackupSummary> = attempt {
        parse(content).let { BackupSummary(it.assets.size, it.expiryItems.size) }
    }

    override suspend fun restore(content: String): Result<BackupSummary> = attempt {
        val backup = parse(content)
        val current = export().getOrThrow()
        withContext(Dispatchers.IO) {
            val directory = context.getDir("backups", Context.MODE_PRIVATE)
            directory.resolve("pre-restore-${System.currentTimeMillis()}.json").writeText(current)
        }
        database.withTransaction {
            local.replaceAll(backup.assets.map(::toDomain))
            expiry.replaceAll(backup.expiryItems.map(::toExpiryDomain))
        }
        BackupSummary(backup.assets.size, backup.expiryItems.size)
    }

    private fun parse(content: String): LocalBackup {
        val backup = json.decodeFromString<LocalBackup>(content)
        require(backup.format == LOCAL_BACKUP_FORMAT) { "不是 SuirenX 本地备份文件" }
        require(backup.version == LOCAL_BACKUP_VERSION) { "不支持的备份版本" }
        val ids = HashSet<String>()
        backup.assets.forEach { asset ->
            require(asset.id.isNotBlank() && ids.add(asset.id)) { "备份包含重复或空的资产编号" }
            require(asset.name.isNotBlank() && asset.priceCents >= 0) { "备份包含无效资产资料" }
            val purchase = LocalDate.parse(asset.purchaseDate)
            val status = assetStatus(asset.status)
            val retired = asset.retiredDate?.let(LocalDate::parse)
            require(status == AssetStatus.Active && retired == null || status == AssetStatus.Retired && retired != null && retired >= purchase) {
                "备份包含不一致的退役资料"
            }
            asset.archivedAt?.let(Instant::parse)
            asset.warrantyEndDate?.let(LocalDate::parse)
            require(asset.tags.size <= 20 && asset.tags.all { it.length <= 30 }) { "备份包含过多或过长标签" }
            require(asset.notes.length <= 2000) { "备份备注过长" }
        }
        val expiryIds = HashSet<String>()
        backup.expiryItems.forEach { item ->
            require(item.id.isNotBlank() && expiryIds.add(item.id) && item.name.isNotBlank()) { "备份包含无效用品" }
            LocalDate.parse(item.packageExpiryDate)
            val opened = item.openedDate?.let(LocalDate::parse)
            require((opened == null) == (item.openedValidityDays == null) && (item.openedValidityDays == null || item.openedValidityDays > 0)) { "备份包含不一致的开封期限" }
            item.archivedAt?.let(Instant::parse)
            require(item.notes.length <= 2000) { "备份备注过长" }
            ExpiryItemStatus.valueOf(item.status)
        }
        return backup
    }

    private fun toDomain(asset: BackupAsset): Asset {
        val purchase = LocalDate.parse(asset.purchaseDate)
        val retired = asset.retiredDate?.let(LocalDate::parse)
        val status = assetStatus(asset.status)
        return Asset(
            id = asset.id, name = asset.name, priceCents = asset.priceCents, purchaseDate = purchase,
            status = status, imageUrl = "", heldDays = 1, dailyCostCents = asset.priceCents,
            retiredDate = retired, archivedAt = asset.archivedAt?.let(Instant::parse), iconKey = asset.iconKey,
            purchaseChannel = asset.purchaseChannel, warrantyEndDate = asset.warrantyEndDate?.let(LocalDate::parse),
            notes = asset.notes, tags = asset.tags,
            createdAt = asset.createdAt.takeIf(String::isNotBlank)?.let(Instant::parse) ?: Instant.EPOCH,
            updatedAt = asset.updatedAt.takeIf(String::isNotBlank)?.let(Instant::parse) ?: Instant.EPOCH,
        )
    }

    private fun toExpiryDomain(item: BackupExpiryItem) = io.suirenx.core.model.ExpiryItem(
        id = item.id, name = item.name, category = item.category, packageExpiryDate = LocalDate.parse(item.packageExpiryDate),
        openedDate = item.openedDate?.let(LocalDate::parse), openedValidityDays = item.openedValidityDays,
        location = item.location, notes = item.notes, status = ExpiryItemStatus.valueOf(item.status),
        archivedAt = item.archivedAt?.let(Instant::parse),
        createdAt = item.createdAt.takeIf(String::isNotBlank)?.let(Instant::parse) ?: Instant.EPOCH,
        updatedAt = item.updatedAt.takeIf(String::isNotBlank)?.let(Instant::parse) ?: Instant.EPOCH,
    )

    private fun assetStatus(value: String): AssetStatus = when (value) {
        "ACTIVE", "Active" -> AssetStatus.Active
        "RETIRED", "Retired" -> AssetStatus.Retired
        else -> error("备份包含无效资产状态")
    }

    private suspend fun <T> attempt(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        Result.failure(error)
    }
}
