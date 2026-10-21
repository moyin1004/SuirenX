package io.suirenx.core.data.sync

import io.suirenx.core.model.UserFacingErrors
import retrofit2.HttpException

internal fun syncFailureMessage(error: Exception): String {
    if (error !is HttpException) {
        return UserFacingErrors.codeOrFallback(error.message, "error_sync_failed")
    }
    // Raw error bodies are diagnostics, never localized presentation content.
    return when (error.code()) {
        400 -> "error_sync_bad_request"
        401 -> "error_sync_login_required"
        403 -> "error_sync_forbidden"
        404 -> "error_sync_endpoint_missing"
        429 -> "error_sync_rate_limited"
        else -> "error_sync_server_failed"
    }
}
