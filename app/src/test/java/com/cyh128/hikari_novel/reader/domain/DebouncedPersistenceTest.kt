package com.cyh128.hikari_novel.reader.domain

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import org.junit.Assert.assertEquals
import org.junit.Test

class DebouncedPersistenceTest {
    @Test
    fun flushCoalescesValuesAndIsIdempotent() {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val persisted = mutableListOf<Int>()
        val persistence = DebouncedPersistence<Int>(scope, 10_000L) { persisted += it }

        persistence.submit(1)
        persistence.submit(2)
        persistence.flush()
        persistence.flush()

        assertEquals(listOf(2), persisted)
        scope.cancel()
    }

    @Test
    fun cancelDropsPendingValue() {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val persisted = mutableListOf<Int>()
        val persistence = DebouncedPersistence<Int>(scope, 10_000L) { persisted += it }

        persistence.submit(1)
        persistence.cancel()
        persistence.flush()

        assertEquals(emptyList<Int>(), persisted)
        scope.cancel()
    }
}
