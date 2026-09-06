package io.suirenx.core.data.repository

import io.suirenx.core.domain.ExpiryRepository
import io.suirenx.core.domain.RemoteExpiryRepository
import io.suirenx.core.domain.StorageModeRepository
import io.suirenx.core.model.ExpiryItem
import io.suirenx.core.model.ExpiryItemStatus
import io.suirenx.core.model.NewExpiryItem
import javax.inject.Inject

class DefaultExpiryRepository @Inject constructor(
    private val local: LocalExpiryRepository,
    private val remote: RemoteExpiryRepository,
    private val modes: StorageModeRepository,
) : ExpiryRepository {
    private fun useLocal() = modes.mode.value == io.suirenx.core.model.StorageMode.Local
    override suspend fun list(includeArchived: Boolean): Result<List<ExpiryItem>> = if (useLocal()) local.list(includeArchived) else remote.listExpiry(includeArchived)
    override suspend fun get(id: String): Result<ExpiryItem> = if (useLocal()) local.get(id) else remote.getExpiry(id)
    override suspend fun create(item: NewExpiryItem): Result<ExpiryItem> = if (useLocal()) local.create(item) else remote.createExpiry(item)
    override suspend fun update(id: String, item: NewExpiryItem): Result<ExpiryItem> = if (useLocal()) local.update(id, item) else remote.updateExpiry(id, item)
    override suspend fun updateStatus(id: String, status: ExpiryItemStatus): Result<ExpiryItem> = if (useLocal()) local.updateStatus(id, status) else remote.updateExpiryStatus(id, status)
    override suspend fun updateArchive(id: String, archive: Boolean): Result<ExpiryItem> = if (useLocal()) local.updateArchive(id, archive) else remote.updateExpiryArchive(id, archive)
}
