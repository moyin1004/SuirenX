package io.suirenx.core.data.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.suirenx.core.data.BuildConfig
import io.suirenx.core.data.auth.AuthTokenStore
import io.suirenx.core.data.network.HttpTimeouts
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        encodeDefaults = true
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(tokens: AuthTokenStore): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(HttpTimeouts.CONNECT_MILLIS, java.util.concurrent.TimeUnit.MILLISECONDS)
        .callTimeout(HttpTimeouts.CALL_MILLIS, java.util.concurrent.TimeUnit.MILLISECONDS)
        .addInterceptor(
            HttpLoggingInterceptor().apply {
                level = if (BuildConfig.DEBUG) {
                    HttpLoggingInterceptor.Level.BASIC
                } else {
                    HttpLoggingInterceptor.Level.NONE
                }
            },
        )
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .header("X-Request-ID", java.util.UUID.randomUUID().toString())
                .build()
            chain.proceed(request)
        }
        .addInterceptor { chain ->
            val token = tokens.tokenFor(chain.request().url.toString())
            val request = if (token.isNullOrBlank() || chain.request().header("Authorization") != null) chain.request() else chain.request().newBuilder()
                .addHeader("Authorization", "Bearer $token")
                .build()
            chain.proceed(request)
        }
        .build()

}
