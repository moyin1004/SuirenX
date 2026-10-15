package io.suirenx.feature.settings

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class AccountValidationTest {
    @Test
    fun registrationAcceptsPrintableAsciiAtBoundariesWithoutCompositionRequirements() {
        listOf("a".repeat(8), "7".repeat(72), " !~ abcd", " ".repeat(8)).forEach {
            assertNull(accountValidation("user", it, it))
        }
    }

    @Test
    fun registrationRejectsLengthNonAsciiAndControls() {
        listOf("a".repeat(7), "a".repeat(73), "密码密码密码密码", "abcd\nefgh", "abcd\tefgh", "abcd\u007fefgh").forEach {
            assertNotNull(accountValidation("user", it, it))
        }
        assertNotNull(accountValidation("user", "password", "different"))
    }

    @Test
    fun loginPreservesLegacyPasswords() {
        listOf("短密码", "short", "abcd\tefgh").forEach {
            assertNull(accountValidation("user", it))
        }
        assertNotNull(accountValidation("user", ""))
    }
}
