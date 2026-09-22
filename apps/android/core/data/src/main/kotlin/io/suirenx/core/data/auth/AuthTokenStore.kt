package io.suirenx.core.data.auth

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONObject

/** One app-wide login credential, pinned to the server that issued it. */
@Singleton
class AuthTokenStore @Inject constructor(@ApplicationContext context: Context) {
    private val preferences = context.getSharedPreferences("auth", Context.MODE_PRIVATE)

    @Synchronized private fun accounts(): JSONObject {
        preferences.getString("accounts", null)?.let { return JSONObject(it) }
        val result = JSONObject()
        val url = preferences.getString("server_url", null)
        val token = preferences.getString("access_token", null)
        if (url != null && token != null) result.put(url, JSONObject().put("username", preferences.getString("username", "")).put("token", token))
        persist(result)
        return result
    }

    private fun persist(value: JSONObject) {
        check(preferences.edit().putString("accounts", value.toString())
            .remove("server_url").remove("username").remove("access_token").commit()) { "无法保存登录状态，请重试" }
    }

    @Synchronized fun tokenFor(requestUrl: String): String? {
        val request = requestUrl.toHttpUrlOrNull() ?: return null
        val stored = accounts()
        val key = stored.keys().asSequence().filter { key ->
            val configured = key.toHttpUrlOrNull() ?: return@filter false
            configured.scheme == request.scheme && configured.host == request.host && configured.port == request.port &&
                request.encodedPath.startsWith(configured.encodedPath)
        }.maxByOrNull { it.length } ?: return null
        return stored.getJSONObject(key).optString("token").takeIf { it.isNotBlank() }
    }

    @Synchronized fun username(serverUrl: String? = null): String {
        val stored = accounts()
        val url = serverUrl ?: stored.keys().asSequence().firstOrNull() ?: return ""
        return stored.optJSONObject(url)?.optString("username").orEmpty()
    }

    @Synchronized fun savedAccounts(): Map<String, String> {
        val stored = accounts()
        return stored.keys().asSequence().associateWith { stored.getJSONObject(it).optString("username") }
    }

    @Synchronized fun save(serverUrl: String, username: String, token: String) {
        val stored = accounts()
        check(stored.length() == 0 || stored.has(serverUrl)) { "当前已有登录账号，请先退出后再登录" }
        persist(JSONObject().put(serverUrl, JSONObject().put("username", username).put("token", token)))
    }

    @Synchronized fun retainOnly(serverUrl: String?) {
        val stored = accounts()
        if (stored.length() <= 1) return
        val selected = serverUrl?.takeIf(stored::has) ?: stored.keys().asSequence().firstOrNull()
        val keep = selected?.let { stored.optJSONObject(it)?.let { account -> JSONObject().put(it, account) } } ?: JSONObject()
        persist(keep)
    }

    @Synchronized fun clearFor(serverUrl: String) { val stored = accounts(); stored.remove(serverUrl); persist(stored) }
    @Synchronized fun clear() { persist(JSONObject()) }
}
