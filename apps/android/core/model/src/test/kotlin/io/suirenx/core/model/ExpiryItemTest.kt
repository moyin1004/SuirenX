package io.suirenx.core.model

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class ExpiryItemTest {
    private fun item(packageDate: String, opened: String? = null, days: Int? = null) = ExpiryItem(
        id = "1", name = "牛奶", category = "食品", packageExpiryDate = LocalDate.parse(packageDate),
        openedDate = opened?.let(LocalDate::parse), openedValidityDays = days,
    )

    @Test fun openedExpiryUsesEarlierDateAndIncludesOpenedDay() {
        assertEquals(LocalDate.parse("2026-09-10"), item("2026-09-10", "2026-09-05", 7).actualExpiryDate)
    }

    @Test fun bucketHasExplicitTodayAndYesterdaySemantics() {
        val expired = item("2026-09-05")
        assertEquals(ExpiryBucket.Expired, expired.bucket(LocalDate.parse("2026-09-06"), 7))
        assertEquals(ExpiryBucket.DueToday, expired.bucket(LocalDate.parse("2026-09-05"), 7))
    }

    @Test fun leapDayAndYearBoundaryRemainDateBased() {
        val item = item("2028-02-29")
        assertEquals(ExpiryBucket.ExpiringSoon, item.bucket(LocalDate.parse("2028-02-28"), 7))
        assertEquals(ExpiryBucket.Expired, item.bucket(LocalDate.parse("2028-03-01"), 7))
    }
}
