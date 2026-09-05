package io.suirenx.core.data.network

import io.suirenx.core.domain.BackendRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

@Singleton
class AssetApiProvider @Inject constructor(
    private val backends: BackendRepository,
    private val client: OkHttpClient,
    private val json: Json,
) {
    // Each call captures an immutable Retrofit base URL. In-flight requests are never redirected.
    fun current(): AssetApi {
        val url = checkNotNull(backends.settings.value?.activeUrl) { "请先配置后端地址" }
        return Retrofit.Builder()
            .baseUrl(url)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(AssetApi::class.java)
    }
}
