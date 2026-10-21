package io.suirenx.core.data.settings

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

internal fun normalizeBackendAddress(input: String, allowHttp: Boolean): String {
    val raw = input.trim()
    require(raw.isNotEmpty() && raw.none(Char::isWhitespace) && '\\' !in raw) {
        "error_backend_address_required"
    }
    val authority = raw.substringBefore('/').substringBefore(':')
    val localAddress = authority == "localhost" || authority.matches(Regex("[0-9.]+")) || raw.startsWith("[")
    val candidate = if ("://" in raw) raw else "${if (localAddress) "http" else "https"}://$raw"
    val url = candidate.toHttpUrlOrNull()
        ?: throw IllegalArgumentException("error_backend_address_invalid")
    // OkHttp leniently parses all-numeric dotted labels (e.g. 999.999.999.999) as a
    // hostname, so IPv4 literals must be validated explicitly after parsing.
    if (url.host.matches(Regex("""\d{1,3}(\.\d{1,3}){3}"""))) {
        val validIpv4 = url.host.split('.').all { octet -> octet.toInt() in 0..255 }
        require(validIpv4) { "error_backend_address_invalid" }
    }
    require(url.username.isEmpty() && url.password.isEmpty() && url.query == null && url.fragment == null) {
        "error_backend_address_parts"
    }
    require(allowHttp || url.isHttps) { "error_backend_https_required" }
    return if (url.encodedPath.endsWith('/')) url.toString() else url.newBuilder().addPathSegment("").build().toString()
}
