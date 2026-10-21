package io.suirenx.core.ui.localization

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import java.math.BigDecimal
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Currency
import java.util.Locale

@Composable
fun uiLocale(): Locale {
    val locales = LocalConfiguration.current.locales
    val language = LanguageSettings.resolveLanguage((0 until locales.size()).map(locales::get))
    return Locale.forLanguageTag(language.tag)
}

/** Display locale never changes the monetary unit or integer-cent business value. */
fun cnyFormat(locale: Locale, minimumFractionDigits: Int = 2): NumberFormat =
    NumberFormat.getCurrencyInstance(locale).apply {
        currency = Currency.getInstance("CNY")
        this.minimumFractionDigits = minimumFractionDigits
        maximumFractionDigits = 2
    }

@Composable
fun moneyFormat(minimumFractionDigits: Int = 2): NumberFormat {
    val locale = uiLocale()
    return remember(locale, minimumFractionDigits) { cnyFormat(locale, minimumFractionDigits) }
}

@Composable
fun moneyText(cents: Long): String = moneyFormat().format(BigDecimal.valueOf(cents, 2))

/** Date-only values stay Gregorian calendar dates; no instant/time-zone conversion. */
fun formatLocalDay(value: LocalDate, locale: Locale): String =
    value.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))

@Composable
fun dateText(value: LocalDate): String = formatLocalDay(value, uiLocale())
