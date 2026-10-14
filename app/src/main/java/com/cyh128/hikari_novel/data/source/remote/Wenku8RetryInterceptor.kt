package com.cyh128.hikari_novel.data.source.remote

import android.util.Log
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

/**
 * Retries transient GET failures against the wenku8 web host.
 *
 * The host is reachable but slow and flaky on mobile networks (measured TTFB
 * 9-18 s on device, 2026-10-11, and one run that stalled past 60 s). A single
 * stalled connection therefore failed a whole screen. GET is safe to retry;
 * POST is never retried, so login and bookshelf writes cannot be submitted
 * twice.
 */
class Wenku8RetryInterceptor(
    private val maxRetries: Int = 1
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.method != "GET" || !Wenku8Headers.isWebHost(request.url.host)) {
            return chain.proceed(request)
        }

        var attempt = 0
        while (true) {
            try {
                val response = chain.proceed(request)
                if (response.isSuccessful || attempt >= maxRetries || response.code !in 500..599) {
                    return response
                }
                response.close()
            } catch (e: IOException) {
                if (attempt >= maxRetries) throw e
                Log.w(
                    "Wenku8Retry",
                    "GET ${request.url} failed (${e.javaClass.simpleName}), retrying"
                )
            }
            attempt++
        }
    }
}
