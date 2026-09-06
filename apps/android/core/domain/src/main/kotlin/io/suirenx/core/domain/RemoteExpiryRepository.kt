package io.suirenx.core.domain

import io.suirenx.core.model.ExpiryItem
import io.suirenx.core.model.ExpiryItemStatus
import io.suirenx.core.model.NewExpiryItem
import java.time.LocalDate

interface RemoteExpiryRepository {
    suspend fun listExpiry(includeArchived: Boolean = false): Result<List<ExpiryItem>>
    suspend fun getExpiry(id: String): Result<ExpiryItem>
    suspend fun createExpiry(item: NewExpiryItem): Result<ExpiryItem>
    suspend fun updateExpiry(id: String, item: NewExpiryItem): Result<ExpiryItem>
    suspend fun updateExpiryStatus(id: String, status: ExpiryItemStatus): Result<ExpiryItem>
    suspend fun updateExpiryArchive(id: String, archive: Boolean): Result<ExpiryItem>
    suspend fun importItem(item: ExpiryItem): Result<ExpiryItem>
    suspend fun allExpiryForMigration(): Result<List<ExpiryItem>>
}
