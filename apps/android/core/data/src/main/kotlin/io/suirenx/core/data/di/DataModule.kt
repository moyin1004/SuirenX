package io.suirenx.core.data.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.suirenx.core.data.repository.DefaultAssetRepository
import io.suirenx.core.domain.AssetRepository
import io.suirenx.core.domain.BackendRepository
import io.suirenx.core.data.settings.LocalBackendRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {
    @Binds
    @Singleton
    abstract fun bindBackendRepository(implementation: LocalBackendRepository): BackendRepository

    @Binds
    @Singleton
    abstract fun bindAssetRepository(implementation: DefaultAssetRepository): AssetRepository
}
