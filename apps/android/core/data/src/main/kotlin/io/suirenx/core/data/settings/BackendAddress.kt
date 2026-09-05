package io.suirenx.core.data.settings

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

internal fun normalizeBackendAddress(input: String, allowHttp: Boolean): String {
    val raw = input.trim()
    require(raw.isNotEmpty() && raw.none(Char::isWhitespace) && '\\' !in raw) {
        "请输入有效的域名或 IP 地址"
    }
    val authority = raw.substringBefore('/').substringBefore(':')
    val localAddress = authority == "localhost" || authority.matches(Regex("[0-9.]+")) || raw.startsWith("[")
    val candidate = if ("://" in raw) raw else "${if (localAddress) "http" else "https"}://$raw"
    val url = candidate.toHttpUrlOrNull()
        ?: throw IllegalArgumentException("地址无效，请检查协议、域名或 IP 和端口")
    // OkHttp leniently parses all-numeric dotted labels (e.g. 999.999.999.999) as a
    // hostname, so IPv4 literals must be validated explicitly after parsing.
    if (url.host.matches(Regex("""\d{1,3}(\.\d{1,3}){3}"""))) {
        val validIpv4 = url.host.split('.').all { octet -> octet.toInt() in 0..255 }
        require(validIpv4) { "地址无效，请检查协议、域名或 IP 和端口" }
    }
    require(url.username.isEmpty() && url.password.isEmpty() && url.query == null && url.fragment == null) {
        "地址不能包含用户名、密码、查询参数或片段"
    }
    require(allowHttp || url.isHttps) { "此版本请使用 HTTPS 地址" }
    return if (url.encodedPath.endsWith('/')) url.toString() else url.newBuilder().addPathSegment("").build().toString()
}
