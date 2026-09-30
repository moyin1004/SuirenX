package io.suirenx.core.data.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.POST

interface AuthApi {
    @POST("api/v1/auth/register")
    suspend fun register(@Body request: AuthRequest): TokenResponse

    @POST("api/v1/auth/login")
    suspend fun login(@Body request: AuthRequest): TokenResponse

    @POST("api/v1/auth/logout")
    suspend fun logout(): LogoutResponse
}

@Serializable
data class AuthRequest(val username: String, val password: String)

@Serializable
data class TokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("token_type") val tokenType: String,
    @SerialName("expires_at") val expiresAt: String,
)

@Serializable
data class LogoutResponse(val status: String)
