package io.suirenx.core.data.sync

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class SyncFailureMessageTest {
    @Test fun badRequestExplainsPreservedBatchAndFieldReason() {
        val response = Response.error<Unit>(400, """{"error":"invalid sync request: expiry[0] has invalid fields"}""".toResponseBody("application/json".toMediaType()))
        val message = syncFailureMessage(HttpException(response))
        assertTrue(message.contains("服务器拒绝同步数据"))
        assertTrue(message.contains("expiry[0]"))
        assertTrue(message.contains("批次已保留"))
    }
}
