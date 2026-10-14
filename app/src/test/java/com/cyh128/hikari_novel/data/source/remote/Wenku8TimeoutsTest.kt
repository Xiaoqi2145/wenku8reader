package com.cyh128.hikari_novel.data.source.remote

import org.junit.Assert.assertTrue
import org.junit.Test

class Wenku8TimeoutsTest {

    @Test
    fun readTimeoutCoversTheMeasuredWorstCase() {
        // Device TTFB reached 18.36 s, and one body read was still running at
        // 30 s, on 2026-10-11. OkHttp's 10 s default made those healthy
        // responses look like network failures.
        assertTrue(Wenku8Timeouts.READ_SECONDS >= 60L)
    }
}
