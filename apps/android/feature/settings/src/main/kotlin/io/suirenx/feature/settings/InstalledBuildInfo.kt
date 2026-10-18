package io.suirenx.feature.settings

import android.content.Context
import android.content.pm.ApplicationInfo
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal data class InstalledBuildInfo(
    val channel: String,
    val commit: String,
    val dirty: Boolean,
    val builtAt: String,
) {
    val isDevelopment: Boolean get() = channel == "development"
    val code: String get() = commit.take(7) + if (dirty) "-dirty" else ""
    val localTime: String get() = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        .withZone(ZoneId.systemDefault()).format(Instant.parse(builtAt))

    companion object {
        fun read(context: Context): InstalledBuildInfo? {
            if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) return null
            val resource = context.resources.getIdentifier("suirenx_build", "raw", context.packageName)
            if (resource == 0) return null // Component-test hosts do not package app metadata.
            val json = context.resources.openRawResource(resource).bufferedReader().use { JSONObject(it.readText()) }
            return InstalledBuildInfo(json.getString("channel"), json.getString("commit"),
                json.getBoolean("dirty"), json.getString("built_at"))
        }
    }
}
