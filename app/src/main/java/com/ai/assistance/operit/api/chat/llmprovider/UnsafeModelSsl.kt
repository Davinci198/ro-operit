package com.ai.assistance.operit.api.chat.llmprovider

import okhttp3.OkHttpClient

/**
 * Model HTTP clients.
 *
 * Historically this object disabled the TLS certificate chain and hostname checks for every model
 * request, which let any man-in-the-middle read and rewrite traffic carrying API keys and prompts.
 * It now leaves the platform defaults in place: the shared client validates the certificate chain,
 * the hostname, and the revocation status exactly like every other client in the app.
 *
 * The name is kept so existing call sites keep compiling; the behaviour is no longer "unsafe".
 */
internal object UnsafeModelSsl {
    fun apply(builder: OkHttpClient.Builder): OkHttpClient.Builder {
        // Intentionally a pass-through: OkHttp's default sslSocketFactory and hostnameVerifier
        // perform full platform validation. Certificate pinning for known endpoints, if it is ever
        // needed, belongs in a CertificatePinner on the builder rather than a trust-all manager.
        return builder
    }
}
