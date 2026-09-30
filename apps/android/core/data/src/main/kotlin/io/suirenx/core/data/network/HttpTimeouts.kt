package io.suirenx.core.data.network

/** Shared HTTP timeout policy; probes keep their shorter, credential-free budget. */
object HttpTimeouts {
    const val CONNECT_MILLIS = 10_000L
    const val CALL_MILLIS = 30_000L
    const val PROBE_CONNECT_MILLIS = 5_000L
    const val PROBE_CALL_MILLIS = 8_000L
}
