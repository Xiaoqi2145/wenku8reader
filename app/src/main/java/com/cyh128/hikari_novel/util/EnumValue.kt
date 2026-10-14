package com.cyh128.hikari_novel.util

/**
 * Resolves a persisted enum ordinal without throwing.
 *
 * These values come from MMKV as plain ints. A build that removes or reorders
 * an enum leaves an ordinal behind that no longer names anything, and the old
 * `enumValues<T>()[index]` crashed on it. The read happens while the application
 * is starting up, *before* the crash reporter is installed, so the result was a
 * launch-time crash loop with no way to recover from inside the app.
 *
 * An out-of-range index now falls back to [default] instead. [default] is an
 * explicit parameter rather than `entries.first()` because the right fallback
 * is a product decision — the app theme, for instance, defaults to a specific
 * theme rather than to whichever enum member happens to be declared first.
 */
fun <T : Enum<T>> enumValue(entries: List<T>, index: Int, default: T): T =
    entries.getOrNull(index) ?: default
