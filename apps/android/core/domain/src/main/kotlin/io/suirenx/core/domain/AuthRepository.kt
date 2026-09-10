package io.suirenx.core.domain

import kotlinx.coroutines.flow.StateFlow

data class AuthState(val username: String, val authenticated: Boolean)

private val emptyAccounts = kotlinx.coroutines.flow.MutableStateFlow<Map<String, AuthState>>(emptyMap())

interface AuthRepository {
    val accounts: StateFlow<Map<String, AuthState>> get() = emptyAccounts
    suspend fun loginAt(url: String, username: String, password: String): Result<Unit> = login(username, password)
    suspend fun registerAt(url: String, username: String, password: String): Result<Unit> = register(username, password)
    suspend fun logoutAt(url: String): Result<Unit> = logout()
    val state: StateFlow<AuthState>
    suspend fun initialize(): Result<Unit>
    suspend fun register(username: String, password: String): Result<Unit>
    suspend fun login(username: String, password: String): Result<Unit>
    suspend fun logout(): Result<Unit>
}
