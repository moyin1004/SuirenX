package io.suirenx.core.data.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.suirenx.core.data.repository.DefaultAssetRepository
import io.suirenx.core.domain.AssetRepository
import io.suirenx.core.domain.AuthRepository
import io.suirenx.core.domain.BackendRepository
import io.suirenx.core.domain.StorageModeRepository
import io.suirenx.core.domain.LocalBackupRepository
import io.suirenx.core.domain.ExpiryRepository
import io.suirenx.core.domain.ThemeRepository
import io.suirenx.core.domain.ExpirySettingsRepository
import io.suirenx.core.domain.RemoteSyncRepository
import io.suirenx.core.domain.RemoteImportRepository
import io.suirenx.core.domain.RemoteExpiryRepository
import io.suirenx.core.data.settings.LocalBackendRepository
import io.suirenx.core.data.settings.LocalStorageModeRepository
import io.suirenx.core.data.repository.LocalBackupRepositoryImpl
import io.suirenx.core.data.repository.LocalExpiryRepository
import io.suirenx.core.data.repository.DefaultExpiryRepository
import io.suirenx.core.data.settings.LocalThemeRepository
import io.suirenx.core.data.settings.LocalExpirySettingsRepository
import io.suirenx.core.data.auth.LocalAuthRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {
    @Binds
    @Singleton
    abstract fun bindSyncSchedule(implementation: io.suirenx.core.data.sync.SyncScheduler): io.suirenx.core.domain.SyncScheduleRepository

    @Binds
    @Singleton
    abstract fun bindAuthRepository(implementation: LocalAuthRepository): AuthRepository
    @Binds
    @Singleton
    abstract fun bindBackendRepository(implementation: LocalBackendRepository): BackendRepository

    @Binds
    @Singleton
    abstract fun bindStorageModeRepository(implementation: LocalStorageModeRepository): StorageModeRepository

    @Binds
    @Singleton
    abstract fun bindLocalBackupRepository(implementation: LocalBackupRepositoryImpl): LocalBackupRepository

    @Binds
    @Singleton
    abstract fun bindExpiryRepository(implementation: DefaultExpiryRepository): ExpiryRepository

    @Binds
    @Singleton
    abstract fun bindThemeRepository(implementation: LocalThemeRepository): ThemeRepository

    @Binds
    @Singleton
    abstract fun bindExpirySettingsRepository(implementation: LocalExpirySettingsRepository): ExpirySettingsRepository

    @Binds
    @Singleton
    abstract fun bindAssetRepository(implementation: DefaultAssetRepository): AssetRepository

    @Binds
    @Singleton
    abstract fun bindRemoteSyncRepository(implementation: io.suirenx.core.data.sync.LocalFirstSyncRepository): RemoteSyncRepository

    @Binds
    @Singleton
    abstract fun bindRemoteImportRepository(implementation: io.suirenx.core.data.repository.RemoteImportRepositoryImpl): RemoteImportRepository

    @Binds
    @Singleton
    abstract fun bindRemoteExpiryRepository(implementation: io.suirenx.core.data.repository.RemoteAssetSyncStore): RemoteExpiryRepository
}
