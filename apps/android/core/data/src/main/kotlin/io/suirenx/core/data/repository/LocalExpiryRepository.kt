package io.suirenx.core.data.repository

import androidx.room.withTransaction
import io.suirenx.core.data.local.ExpiryDao
import io.suirenx.core.data.local.ExpiryEntity
import io.suirenx.core.data.local.LocalDatabase
import io.suirenx.core.domain.ExpiryRepository
import io.suirenx.core.model.ExpiryItem
import io.suirenx.core.model.ExpiryItemStatus
import io.suirenx.core.model.NewExpiryItem
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException

class LocalExpiryRepository @Inject constructor(
    private val database: LocalDatabase,
    private val clock: Clock,
    private val journal: io.suirenx.core.data.sync.LocalSyncJournal,
) : ExpiryRepository {
    private val dao: ExpiryDao = database.expiryDao()
    override suspend fun delete(id: String): Result<Unit> = attempt {
        dao.getById(id)?.let { journal.expiry(toDomain(it), deleted = true) }
        dao.delete(id)
    }
    override suspend fun list(includeArchived: Boolean) = attempt {
        dao.getAll().map(::toDomain).filter { includeArchived || it.archivedAt == null }
    }
    override suspend fun get(id: String) = attempt { toDomain(dao.getById(id) ?: error("未找到用品")) }
    override suspend fun create(item: NewExpiryItem) = attempt {
        val now = Instant.now(clock)
        val saved = ExpiryItem(UUID.randomUUID().toString(), item.name.trim(), item.category.trim(), item.packageExpiryDate, item.openedDate, item.openedValidityDays, item.location.trim(), item.notes.trim(), createdAt = now, updatedAt = now)
        dao.insert(saved.toEntity())
        journal.expiry(saved)
        saved
    }
    override suspend fun update(id: String, item: NewExpiryItem) = attempt {
        val current = dao.getById(id)?.let(::toDomain) ?: error("未找到用品")
        check(current.archivedAt == null) { "归档用品请先恢复" }
        val updated = current.copy(name = item.name.trim(), category = item.category.trim(), packageExpiryDate = item.packageExpiryDate, openedDate = item.openedDate, openedValidityDays = item.openedValidityDays, location = item.location.trim(), notes = item.notes.trim(), updatedAt = Instant.now(clock))
        dao.update(updated.toEntity())
        journal.expiry(updated)
        updated
    }
    override suspend fun updateStatus(id: String, status: ExpiryItemStatus) = attempt {
        val current = dao.getById(id)?.let(::toDomain) ?: error("未找到用品")
        check(current.archivedAt == null) { "归档用品请先恢复" }
        val updated = current.copy(status = status, updatedAt = Instant.now(clock))
        dao.update(updated.toEntity())
        journal.expiry(updated)
        updated
    }
    override suspend fun updateArchive(id: String, archive: Boolean) = attempt {
        val current = dao.getById(id)?.let(::toDomain) ?: error("未找到用品")
        val updated = current.copy(archivedAt = if (archive) current.archivedAt ?: Instant.now(clock) else null, updatedAt = Instant.now(clock))
        dao.update(updated.toEntity())
        journal.expiry(updated)
        updated
    }

    private fun toDomain(entity: ExpiryEntity) = ExpiryItem(entity.id, entity.name, entity.category, LocalDate.parse(entity.packageExpiryDate), entity.openedDate?.let(LocalDate::parse), entity.openedValidityDays, entity.location, entity.notes, ExpiryItemStatus.valueOf(entity.status), entity.archivedAt?.let(Instant::parse), Instant.parse(entity.createdAt), Instant.parse(entity.updatedAt))
    internal fun ExpiryItem.toEntity() = ExpiryEntity(id, name, category, packageExpiryDate.toString(), openedDate?.toString(), openedValidityDays, location, notes, status.name, archivedAt?.toString(), createdAt.toString(), updatedAt.toString())
    suspend fun allForBackup(): List<ExpiryItem> = dao.getAll().map(::toDomain)
    suspend fun replaceAll(items: List<ExpiryItem>) {
        dao.getAll().forEach { journal.expiry(toDomain(it), deleted = true) }
        dao.deleteAll()
        items.forEach { dao.insert(it.toEntity()); journal.expiry(it) }
    }
    private suspend fun <T> attempt(block: suspend () -> T): Result<T> = try { Result.success(database.withTransaction { block() }) } catch (error: CancellationException) { throw error } catch (error: Exception) { Result.failure(error) }
}
