package io.suirenx.feature.settings

internal fun accountValidation(username: String, password: String, confirmation: String? = null): String? = when {
    username.isBlank() -> "请输入账号"
    username.trim().toByteArray(Charsets.UTF_8).size > 100 -> "账号不能超过 100 个 UTF-8 字节"
    password.isEmpty() -> "请输入密码"
    confirmation != null && password.toByteArray(Charsets.UTF_8).size !in 8..72 -> "密码需为 8–72 个 UTF-8 字节（中文通常占 3 字节）"
    confirmation != null && confirmation.isEmpty() -> "请再次输入密码"
    confirmation != null && confirmation != password -> "两次密码不一致"
    else -> null
}
