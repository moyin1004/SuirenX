package io.suirenx.core.model

data class BackendServer(val url: String, val name: String)

data class BackendSettings(
    val servers: List<BackendServer> = emptyList(),
    val activeUrl: String? = null,
)
