package io.suirenx.core.domain

data class BackupSummary(
    val assetCount: Int,
    val expiryItemCount: Int = 0,
)

interface LocalBackupRepository {
    suspend fun snapshot(): Result<LocalDataSnapshot>

    suspend fun createSafetyBackup(): Result<Unit>

    suspend fun export(): Result<String>
    suspend fun inspect(content: String): Result<BackupSummary>
    suspend fun restore(content: String): Result<BackupSummary>
}
