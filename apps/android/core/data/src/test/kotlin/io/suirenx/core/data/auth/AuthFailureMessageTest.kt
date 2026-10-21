package io.suirenx.core.data.auth

import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class AuthFailureMessageTest {
    private val json = Json

    @Test fun closedRegistrationExplainsThatTheServerDisabledSignups() {
        val response = Response.error<Unit>(
            403,
            """{"code":"registration_closed","error":"account registration is disabled"}""".toResponseBody("application/json".toMediaType()),
        )

        assertEquals(
            "error_auth_registration_closed",
            authFailureMessage(HttpException(response), json),
        )
    }

    @Test fun alreadyRegisteredAccountKeepsItsExistingGuidance() {
        val response = Response.error<Unit>(
            409,
            """{"error":"username is already registered"}""".toResponseBody("application/json".toMediaType()),
        )

        assertEquals(
            "error_auth_username_registered",
            authFailureMessage(HttpException(response), json),
        )
    }
}
