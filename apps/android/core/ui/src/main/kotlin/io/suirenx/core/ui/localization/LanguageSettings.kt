package io.suirenx.core.ui.localization

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import java.util.Locale

enum class AppLanguage(val tag: String) {
    System(""),
    SimplifiedChinese("zh-Hans"),
    English("en"),
}

/** Device UI preference only: independent from account, business data, backups and sync. */
object LanguageSettings {
    private const val PREFERENCES = "language"
    private const val LANGUAGE_KEY = "language_tag"

    fun selection(context: Context): AppLanguage {
        val tags = if (Build.VERSION.SDK_INT >= 33) {
            val locales = context.getSystemService(LocaleManager::class.java).applicationLocales
            if (locales.isEmpty) return AppLanguage.System
            locales.toLanguageTags()
        } else {
            context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).getString(LANGUAGE_KEY, "").orEmpty()
        }
        if (tags.isEmpty()) return AppLanguage.System
        return resolveLanguage(tags.split(',').map(Locale::forLanguageTag))
    }

    fun resolvedLanguage(context: Context): AppLanguage {
        val selected = selection(context)
        return if (selected == AppLanguage.System) resolveLanguage(systemLocales(context)) else selected
    }

    /** Returns the first supported language, preserving Chinese script distinctions. */
    fun resolveLanguage(locales: List<Locale>): AppLanguage {
        for (locale in locales) {
            when (locale.language) {
                "en" -> return AppLanguage.English
                "zh" -> if (locale.script == "Hans" || locale.script.isEmpty() && locale.country in setOf("", "CN", "SG")) {
                    return AppLanguage.SimplifiedChinese
                }
            }
        }
        return AppLanguage.SimplifiedChinese
    }

    fun wrapContext(base: Context): Context {
        migrateLegacyPreference(base)
        val configuration = Configuration(base.resources.configuration)
        configuration.setLocales(LocaleList.forLanguageTags(resolvedLanguage(base).tag))
        return base.createConfigurationContext(configuration)
    }

    fun select(activity: Activity, language: AppLanguage) {
        if (selection(activity) == language) return
        if (Build.VERSION.SDK_INT >= 33) {
            // Android persists the preference and recreates the activity normally.
            activity.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags(language.tag)
        } else {
            val saved = activity.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .edit().putString(LANGUAGE_KEY, language.tag).commit()
            if (saved) activity.recreate()
        }
    }

    private fun systemLocales(context: Context): List<Locale> {
        val locales = if (Build.VERSION.SDK_INT >= 33) {
            context.getSystemService(LocaleManager::class.java).systemLocales
        } else {
            Resources.getSystem().configuration.locales
        }
        return (0 until locales.size()).map(locales::get)
    }

    private fun migrateLegacyPreference(context: Context) {
        if (Build.VERSION.SDK_INT < 33) return
        val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        if (!preferences.contains(LANGUAGE_KEY)) return
        val manager = context.getSystemService(LocaleManager::class.java)
        val legacy = preferences.getString(LANGUAGE_KEY, "").orEmpty()
        if (manager.applicationLocales.isEmpty && legacy.isNotEmpty()) {
            manager.applicationLocales = LocaleList.forLanguageTags(legacy)
        }
        // After an OS upgrade the framework is the only source of truth.
        preferences.edit().remove(LANGUAGE_KEY).apply()
    }
}
