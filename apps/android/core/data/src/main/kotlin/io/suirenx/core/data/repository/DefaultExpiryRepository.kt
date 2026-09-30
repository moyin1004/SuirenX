package io.suirenx.core.data.repository

import io.suirenx.core.domain.ExpiryRepository
import io.suirenx.core.domain.RemoteExpiryRepository
import io.suirenx.core.domain.StorageModeRepository
import io.suirenx.core.model.ExpiryItem
import io.suirenx.core.model.ExpiryItemStatus
import io.suirenx.core.model.NewExpiryItem
import javax.inject.Inject

class DefaultExpiryRepository @Inject constructor(
    private val scheduler: io.suirenx.core.domain.SyncScheduleRepository,
    private val local: LocalExpiryRepository,
) : ExpiryRepository {
    override suspend fun delete(id: String): Result<Unit> =
        local.delete(id).also { if (it.isSuccess) scheduler.onLocalChange() }

    override suspend fun list(includeArchived: Boolean): Result<List<ExpiryItem>> = local.list(includeArchived)
    override suspend fun get(id: String): Result<ExpiryItem> = local.get(id)
    override suspend fun create(item: NewExpiryItem): Result<ExpiryItem> = local.create(item).also { if (it.isSuccess) scheduler.onLocalChange() }
    override suspend fun update(id: String, item: NewExpiryItem): Result<ExpiryItem> = local.update(id, item).also { if (it.isSuccess) scheduler.onLocalChange() }
    override suspend fun updateStatus(id: String, status: ExpiryItemStatus): Result<ExpiryItem> = local.updateStatus(id, status).also { if (it.isSuccess) scheduler.onLocalChange() }
    override suspend fun updateArchive(id: String, archive: Boolean): Result<ExpiryItem> = local.updateArchive(id, archive).also { if (it.isSuccess) scheduler.onLocalChange() }
}
