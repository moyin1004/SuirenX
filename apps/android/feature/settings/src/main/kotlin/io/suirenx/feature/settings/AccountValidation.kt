package io.suirenx.feature.settings

internal const val RegistrationPasswordHint = "error_auth_password_rules"

internal fun accountValidation(username: String, password: String, confirmation: String? = null): String? = when {
    username.isBlank() -> "error_auth_username_required"
    username.trim().toByteArray(Charsets.UTF_8).size > 100 -> "error_auth_username_too_long"
    password.isEmpty() -> "error_auth_password_required"
    confirmation != null && (password.length !in 8..72 || password.any { it !in ' '..'~' }) -> RegistrationPasswordHint
    confirmation != null && confirmation.isEmpty() -> "error_auth_confirmation_required"
    confirmation != null && confirmation != password -> "error_auth_password_mismatch"
    else -> null
}
