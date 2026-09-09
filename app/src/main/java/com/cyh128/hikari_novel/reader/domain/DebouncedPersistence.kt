package com.cyh128.hikari_novel.reader.domain

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Coalesces fast-changing values and persists the latest value once.
 *
 * The helper is generic and Android-free, so lifecycle behavior can be tested
 * without TextView or ScrollView. [flush] is idempotent and should be called
 * before a UI owner is destroyed.
 */
class DebouncedPersistence<T>(
    private val scope: CoroutineScope,
    private val delayMillis: Long,
    private val persist: (T) -> Unit
) {
    private var latest: T? = null
    private var lastPersisted: T? = null
    private var job: Job? = null

    fun submit(value: T) {
        latest = value
        job?.cancel()
        job = scope.launch {
            delay(delayMillis)
            persistLatest()
        }
    }

    fun flush() {
        job?.cancel()
        job = null
        persistLatest()
    }

    fun cancel() {
        job?.cancel()
        job = null
        latest = null
    }

    fun latest(): T? = latest

    private fun persistLatest() {
        val value = latest ?: return
        if (value == lastPersisted) return
        lastPersisted = value
        persist(value)
    }
}
