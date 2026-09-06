package io.suirenx.core.domain

import io.suirenx.core.model.ExpiryItem
import io.suirenx.core.model.ExpiryItemStatus
import io.suirenx.core.model.NewExpiryItem

interface ExpiryRepository {
    suspend fun list(includeArchived: Boolean = false): Result<List<ExpiryItem>>
    suspend fun get(id: String): Result<ExpiryItem>
    suspend fun create(item: NewExpiryItem): Result<ExpiryItem>
    suspend fun update(id: String, item: NewExpiryItem): Result<ExpiryItem>
    suspend fun updateStatus(id: String, status: ExpiryItemStatus): Result<ExpiryItem>
    suspend fun updateArchive(id: String, archive: Boolean): Result<ExpiryItem>
}
