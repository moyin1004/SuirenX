package io.suirenx.core.model

data class BackendServer(val url: String, val name: String)

enum class StorageMode {
    Local,
    Remote,
}

enum class ThemeMode { System, Light, Dark }

data class BackendSettings(
    val servers: List<BackendServer> = emptyList(),
    val activeUrl: String? = null,
)
