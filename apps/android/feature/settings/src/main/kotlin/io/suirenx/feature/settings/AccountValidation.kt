package io.suirenx.feature.settings

internal const val RegistrationPasswordHint = "8–72 个字符，仅限英文字母、数字、半角符号和空格；不支持中文或换行，无大小写、数字组合要求。"

internal fun accountValidation(username: String, password: String, confirmation: String? = null): String? = when {
    username.isBlank() -> "请输入账号"
    username.trim().toByteArray(Charsets.UTF_8).size > 100 -> "账号过长，请缩短后重试"
    password.isEmpty() -> "请输入密码"
    confirmation != null && (password.length !in 8..72 || password.any { it !in ' '..'~' }) -> RegistrationPasswordHint
    confirmation != null && confirmation.isEmpty() -> "请再次输入密码"
    confirmation != null && confirmation != password -> "两次密码不一致"
    else -> null
}
