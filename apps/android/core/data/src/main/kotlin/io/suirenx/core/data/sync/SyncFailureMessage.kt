package io.suirenx.core.data.sync

import kotlinx.serialization.json.*
import retrofit2.HttpException

internal fun syncFailureMessage(error: Exception): String {
    if (error !is HttpException) return error.message ?: "同步失败，请重试"
    val reason = runCatching { Json.parseToJsonElement(error.response()?.errorBody()?.string().orEmpty()).jsonObject["error"]?.jsonPrimitive?.content }.getOrNull()
    return when (error.code()) {
        400 -> if (reason == null || reason == "invalid sync request")
            "服务器拒绝同步数据（HTTP 400）。请检查记录字段与服务端版本；旧服务端可能不接受用品的空位置。数据和待发送批次已保留。"
            else "服务器拒绝同步数据（HTTP 400）：${reason.take(240)}。数据和待发送批次已保留。"
        401 -> "当前服务器无法验证已登录账号，请先注销账号，再登录后重试"
        403 -> "当前账号没有同步权限，请检查服务器配置"
        404 -> "服务器未提供当前同步接口，请检查地址与服务端版本"
        429 -> "同步请求过于频繁，请稍后重试"
        else -> "服务器暂时无法完成同步（HTTP ${error.code()}），请稍后重试"
    }
}
