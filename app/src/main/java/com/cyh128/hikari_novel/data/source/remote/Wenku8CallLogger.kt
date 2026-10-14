package com.cyh128.hikari_novel.data.source.remote

import android.util.Log
import okhttp3.Call
import okhttp3.EventListener
import java.io.IOException

/**
 * Diagnostic-only listener that prints the real exception behind a failed call.
 *
 * The repository wraps every failure into one generic user-facing message, so
 * the cause (timeout vs. reset vs. TLS) was previously invisible in `logcat`.
 * This only logs; it changes no behaviour.
 */
class Wenku8CallLogger : EventListener() {
    override fun callFailed(call: Call, ioe: IOException) {
        Log.w(
            "Wenku8Net",
            "callFailed ${call.request().url} ${ioe.javaClass.simpleName}: ${ioe.message}"
        )
    }
}
