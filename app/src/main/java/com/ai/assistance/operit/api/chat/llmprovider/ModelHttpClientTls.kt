package com.ai.assistance.operit.api.chat.llmprovider

import okhttp3.OkHttpClient

/**
 * TLS configuration for model HTTP clients.
 *
 * Model requests carry API keys and full prompts, so they must never skip certificate validation.
 * This was previously an "unsafe" object that disabled the certificate chain and hostname checks;
 * it is now a pass-through that leaves the platform defaults in place. The shared client validates
 * the certificate chain, the hostname, and the revocation status exactly like every other client in
 * the app.
 */
internal object ModelHttpClientTls {
    fun apply(builder: OkHttpClient.Builder): OkHttpClient.Builder {
        // Intentionally a pass-through: OkHttp's default sslSocketFactory and hostnameVerifier
        // perform full platform validation. Certificate pinning for known endpoints, if it is ever
        // needed, belongs in a CertificatePinner on the builder rather than a trust-all manager.
        return builder
    }
}
