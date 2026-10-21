package io.suirenx.core.ui.localization

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class LanguageSettingsTest {
    private fun resolve(vararg tags: String) = LanguageSettings.resolveLanguage(tags.map(Locale::forLanguageTag))

    @Test fun acceptsEnglishVariants() {
        listOf("en", "en-US", "en-GB", "en-IN").forEach {
            assertEquals(AppLanguage.English, resolve(it))
        }
    }

    @Test fun acceptsSimplifiedChineseScriptAndRegions() {
        listOf("zh", "zh-CN", "zh-SG", "zh-Hans", "zh-Hans-TW").forEach {
            assertEquals(AppLanguage.SimplifiedChinese, resolve(it, "en"))
        }
    }

    @Test fun skipsTraditionalChineseWithoutLosingLaterPreferences() {
        listOf("zh-TW", "zh-HK", "zh-MO", "zh-Hant", "zh-Hant-CN").forEach {
            assertEquals(AppLanguage.English, resolve(it, "en-GB"))
        }
    }

    @Test fun searchesEntireListInPreferenceOrder() {
        assertEquals(AppLanguage.English, resolve("fr-FR", "ja-JP", "en-GB", "zh-Hans"))
        assertEquals(AppLanguage.SimplifiedChinese, resolve("fr", "zh-SG", "en"))
    }

    @Test fun unsupportedOrEmptyPreferencesFallBackToSimplifiedChinese() {
        assertEquals(AppLanguage.SimplifiedChinese, resolve())
        assertEquals(AppLanguage.SimplifiedChinese, resolve("ja", "zh-Hant"))
    }
}
