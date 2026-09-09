package com.cyh128.hikari_novel.reader.domain

import com.cyh128.hikari_novel.data.model.ReaderAnchor

/** Stable offset to which a layout adapter should scroll. */
data class VerticalScrollTarget(
    val globalOffset: Int
)

/** Converts a persisted anchor into a current rendered-text target. */
class VerticalScrollTargetCalculator(
    private val mapper: ChapterBlockMapper
) {
    fun target(anchor: ReaderAnchor): VerticalScrollTarget? =
        mapper.globalOffset(anchor)?.let(::VerticalScrollTarget)
}
