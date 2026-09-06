package io.suirenx.core.data.auth

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

@Singleton
class AuthTokenStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val preferences = context.getSharedPreferences("auth", Context.MODE_PRIVATE)

    fun tokenFor(requestUrl: String): String? {
        val configured = preferences.getString("server_url", null)?.toHttpUrlOrNull() ?: return null
        val request = requestUrl.toHttpUrlOrNull() ?: return null
        if (configured.scheme != request.scheme || configured.host != request.host || configured.port != request.port ||
            !request.encodedPath.startsWith(configured.encodedPath)
        ) {
            return null
        }
        return preferences.getString("access_token", null)
    }

    fun username(): String = preferences.getString("username", "").orEmpty()

    fun save(serverUrl: String, username: String, token: String) {
        check(preferences.edit().putString("server_url", serverUrl).putString("username", username).putString("access_token", token).commit()) {
            "无法保存登录状态，请重试"
        }
    }

    fun clear() {
        check(preferences.edit().remove("server_url").remove("username").remove("access_token").commit()) {
            "无法清除登录状态，请重试"
        }
    }
}
