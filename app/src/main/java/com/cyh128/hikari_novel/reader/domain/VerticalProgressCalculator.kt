package com.cyh128.hikari_novel.reader.domain

import com.cyh128.hikari_novel.data.model.ChapterRef
import com.cyh128.hikari_novel.data.model.ReaderAnchor

/** Geometry-independent sample produced by a vertical text/layout adapter. */
data class VerticalProgressSample(
    val globalOffset: Int,
    val displayPercent: Int,
    val scrollY: Int = 0,
    val imageIndex: Int? = null
)

/** Stable chapter position derived from a rendered-text sample. */
data class MappedVerticalProgress(
    val chapter: ChapterRef,
    val content: String,
    val anchor: ReaderAnchor,
    val displayPercent: Int,
    val globalOffset: Int,
    val scrollY: Int
)

/**
 * Maps layout-independent rendered offsets through [ChapterBlockMapper].
 *
 * Android code is responsible only for finding the visible UTF-16 offset and
 * a display percentage. This class owns the chapter boundary and anchor rules,
 * which makes appended-session behavior straightforward to test on the JVM.
 */
class VerticalProgressCalculator(
    private val mapper: ChapterBlockMapper
) {
    fun map(sample: VerticalProgressSample): MappedVerticalProgress? {
        val mapped = mapper.chapterAt(sample.globalOffset) ?: return null
        val content = mapper.content(mapped.ref.cid) ?: return null
        val anchor = mapper.anchorAt(sample.globalOffset)
            ?.copy(imageIndex = sample.imageIndex)
            ?: return null
        return MappedVerticalProgress(
            chapter = mapped.ref,
            content = content,
            anchor = anchor,
            displayPercent = sample.displayPercent.coerceIn(0, 100),
            globalOffset = sample.globalOffset,
            scrollY = sample.scrollY
        )
    }
}
