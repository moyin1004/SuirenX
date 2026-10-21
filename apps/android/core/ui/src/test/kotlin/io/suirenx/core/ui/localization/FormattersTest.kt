package io.suirenx.core.ui.localization

import java.math.BigDecimal
import java.time.LocalDate
import java.util.Locale
import org.junit.Assert.*
import org.junit.Test

class FormattersTest {
    @Test fun languageNeverChangesCurrencyOrCentPrecision() {
        for (locale in listOf(Locale.ENGLISH, Locale.SIMPLIFIED_CHINESE)) {
            val formatter = cnyFormat(locale)
            assertEquals("CNY", formatter.currency.currencyCode)
            assertEquals(BigDecimal("123.45"), BigDecimal(formatter.parse(formatter.format(BigDecimal("123.45"))).toString()))
            assertTrue(formatter.format(BigDecimal.ZERO).endsWith("0.00"))
        }
    }

    @Test fun dateOnlyRenderingDoesNotShiftCalendarDay() {
        val day = LocalDate.of(2026, 10, 20)
        assertEquals("Oct 20, 2026", formatLocalDay(day, Locale.ENGLISH))
        assertEquals("2026年10月20日", formatLocalDay(day, Locale.SIMPLIFIED_CHINESE))
        assertEquals("2026-10-20", day.toString())
    }
}
