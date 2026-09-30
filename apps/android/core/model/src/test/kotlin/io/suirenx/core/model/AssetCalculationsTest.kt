package io.suirenx.core.model

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class AssetCalculationsTest {
    @Test fun heldDaysIncludesPurchaseAndRetirementDays() {
        assertEquals(5, calculateHeldDays(LocalDate.parse("2026-09-01"), AssetStatus.Retired, LocalDate.parse("2026-09-05"), LocalDate.parse("2026-09-06")))
        assertEquals(6, calculateHeldDays(LocalDate.parse("2026-09-01"), AssetStatus.Active, null, LocalDate.parse("2026-09-06")))
    }

    @Test fun dailyCostUsesCentRounding() {
        assertEquals(6667, calculateDailyCostCents(20_000, 3))
    }
}
