package io.suirenx.core.data.sync

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class SyncFailureMessageTest {
    @Test fun unauthorizedSyncPromptsExplicitLogoutBeforeLogin() {
        val response = Response.error<Unit>(401, "".toResponseBody("application/json".toMediaType()))
        assertEquals("error_sync_login_required", syncFailureMessage(HttpException(response)))
    }

    @Test fun badRequestPreservesBatchGuidanceWithoutExposingServerBody() {
        val response = Response.error<Unit>(400, """{"error":"invalid sync request: private record details"}""".toResponseBody("application/json".toMediaType()))
        assertEquals("error_sync_bad_request", syncFailureMessage(HttpException(response)))
    }

    @Test fun unexpectedInfrastructureDiagnosticsUseGenericFailure() {
        assertEquals("error_sync_failed", syncFailureMessage(IllegalStateException("private database path")))
    }

    @Test fun knownBusinessFailureKeepsItsPresentationCode() {
        assertEquals("error_sync_sale_price_missing", syncFailureMessage(IllegalStateException("error_sync_sale_price_missing")))
    }
}
