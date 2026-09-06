package io.suirenx.core.domain

import kotlinx.coroutines.flow.StateFlow

data class AuthState(val username: String, val authenticated: Boolean)

interface AuthRepository {
    val state: StateFlow<AuthState>
    suspend fun initialize(): Result<Unit>
    suspend fun register(username: String, password: String): Result<Unit>
    suspend fun login(username: String, password: String): Result<Unit>
    suspend fun logout(): Result<Unit>
}
