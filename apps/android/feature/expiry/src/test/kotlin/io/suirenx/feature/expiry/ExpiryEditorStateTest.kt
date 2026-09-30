package io.suirenx.feature.expiry

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ExpiryEditorStateTest {
    @Test
    fun keepsOptionalOpenedDatesEmpty() {
        val item = ExpiryEditorState(
            name = "咖啡豆",
            packageExpiryDate = "2026-10-06",
        ).toNewExpiryItem()

        assertEquals(LocalDate.of(2026, 10, 6), item.packageExpiryDate)
        assertEquals(null, item.openedDate)
        assertEquals(null, item.openedValidityDays)
    }

    @Test
    fun validatesManualDatesAndOpenedDateRelationship() {
        assertThrows(IllegalArgumentException::class.java) {
            ExpiryEditorState(name = "用品", packageExpiryDate = "2026-2-01").toNewExpiryItem()
        }
        assertThrows(IllegalArgumentException::class.java) {
            ExpiryEditorState(
                name = "用品",
                packageExpiryDate = "2026-10-06",
                openedDate = "2026-10-07",
                openedValidityDays = "30",
            ).toNewExpiryItem()
        }
        assertThrows(IllegalArgumentException::class.java) {
            ExpiryEditorState(
                name = "用品",
                packageExpiryDate = "2026-10-06",
                openedDate = "2026-09-01",
            ).toNewExpiryItem()
        }
    }
}
