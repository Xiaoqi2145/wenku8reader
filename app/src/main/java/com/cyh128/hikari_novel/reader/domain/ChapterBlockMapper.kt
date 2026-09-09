package com.cyh128.hikari_novel.reader.domain

import com.cyh128.hikari_novel.data.model.ChapterRef
import com.cyh128.hikari_novel.data.model.ReaderAnchor

/**
 * A chapter's content range inside the rendered, concatenated reader text.
 *
 * [startOffset] and [endOffsetExclusive] use the same UTF-16 offsets as
 * Kotlin String.length and Android TextView.Layout. Prefixes such as a title
 * header are deliberately outside the range.
 */
data class ChapterBlock(
    val ref: ChapterRef,
    val startOffset: Int,
    val endOffsetExclusive: Int,
    val contentLength: Int
) {
    init {
        require(startOffset >= 0) { "startOffset must not be negative" }
        require(endOffsetExclusive >= startOffset) {
            "endOffsetExclusive must not be before startOffset"
        }
        require(contentLength == endOffsetExclusive - startOffset) {
            "contentLength must match the block range"
        }
    }

    fun contains(globalOffset: Int): Boolean =
        globalOffset in startOffset until endOffsetExclusive
}

data class MappedChapterOffset(
    val ref: ChapterRef,
    val localOffset: Int,
    val normalizedPercent: Int
)

typealias ChapterOffset = MappedChapterOffset

/**
 * Maps offsets in concatenated reader text to chapter-local anchors.
 *
 * The mapper is intentionally independent of Android UI. Call [clear] when a
 * manual chapter jump replaces the rendered session, then append the selected
 * chapter again. A prefix is rendered text (for example, a chapter heading)
 * and is not part of the chapter's local content range.
 */
class ChapterBlockMapper {
    private val mutableBlocks = mutableListOf<ChapterBlock>()
    private val contentByCid = mutableMapOf<String, String>()

    val blocks: List<ChapterBlock>
        get() = mutableBlocks.toList()

    /** The end of the rendered text represented by the current blocks. */
    val totalLength: Int
        get() = mutableBlocks.lastOrNull()?.endOffsetExclusive ?: 0

    val renderedLength: Int
        get() = totalLength

    fun clear() {
        mutableBlocks.clear()
        contentByCid.clear()
    }

    /**
     * Append one chapter and return its newly created block.
     *
     * Existing block offsets never change. [prefixLength] is the number of
     * rendered characters inserted before the chapter content.
     */
    fun append(ref: ChapterRef, content: String, prefixLength: Int = 0): ChapterBlock {
        require(prefixLength >= 0) { "prefixLength must not be negative" }
        require(mutableBlocks.none { it.ref.cid == ref.cid }) {
            "A chapter cid may only occur once in a rendered session"
        }

        val startOffset = (mutableBlocks.lastOrNull()?.endOffsetExclusive ?: 0) + prefixLength
        val block = ChapterBlock(
            ref = ref,
            startOffset = startOffset,
            endOffsetExclusive = startOffset + content.length,
            contentLength = content.length
        )
        mutableBlocks += block
        contentByCid[ref.cid] = content
        return block
    }

    /**
     * Map a global rendered-text offset to a chapter-local offset.
     *
     * Offsets before the first block clamp to its start. A prefix gap maps to
     * the nearest preceding block at its end; an offset at the next block's
     * start belongs to that next block. Empty mappers return null.
     */
    fun chapterAt(globalOffset: Int): MappedChapterOffset? {
        val block = blockAt(globalOffset) ?: return null
        val localOffset = localOffset(block, globalOffset)
        return MappedChapterOffset(
            ref = block.ref,
            localOffset = localOffset,
            normalizedPercent = percent(localOffset, block.contentLength)
        )
    }

    /** Map a global rendered-text offset to a persistence-ready anchor. */
    fun anchorAt(globalOffset: Int): ReaderAnchor? {
        val mapped = chapterAt(globalOffset) ?: return null
        val content = contentByCid[mapped.ref.cid] ?: return null
        val localOffset = mapped.localOffset.coerceIn(0, content.length)
        return ReaderAnchor(
            cid = mapped.ref.cid,
            paragraphIndex = content.take(localOffset).count { it == '\n' },
            charOffset = localOffset,
            normalizedPercent = percent(localOffset, content.length)
        )
    }

    /** Map a persisted anchor back to the current rendered-text offset. */
    fun globalOffset(anchor: ReaderAnchor): Int? {
        val block = mutableBlocks.firstOrNull { it.ref.cid == anchor.cid } ?: return null
        return block.startOffset + anchor.charOffset.coerceIn(0, block.contentLength)
    }

    /** Return the current anchor after clamping a persisted anchor to content. */
    fun clamp(anchor: ReaderAnchor): ReaderAnchor? {
        val block = mutableBlocks.firstOrNull { it.ref.cid == anchor.cid } ?: return null
        val content = contentByCid[block.ref.cid] ?: return null
        val offset = anchor.charOffset.coerceIn(0, content.length)
        return anchor.copy(
            paragraphIndex = content.take(offset).count { it == '\n' },
            charOffset = offset,
            normalizedPercent = percent(offset, content.length)
        )
    }

    /** Convenience overload for callers that already have a chapter ref. */
    fun globalOffset(ref: ChapterRef, localOffset: Int): Int? =
        globalOffset(ReaderAnchor(ref.cid, 0, localOffset, 0))

    private fun blockAt(globalOffset: Int): ChapterBlock? {
        if (mutableBlocks.isEmpty()) return null

        // A non-empty range owns its start and all offsets before its end.
        mutableBlocks.firstOrNull { it.contains(globalOffset) }?.let { return it }

        val first = mutableBlocks.first()
        if (globalOffset < first.startOffset) return first

        // Gaps are heading/separator text. Attribute them to the preceding
        // chapter, but let an exact next start belong to the next chapter.
        val preceding = mutableBlocks.lastOrNull { it.endOffsetExclusive <= globalOffset }
        if (preceding != null) {
            val next = mutableBlocks.firstOrNull { it.startOffset >= globalOffset }
            if (next == null || globalOffset < next.startOffset) return preceding
        }

        // This mainly covers zero-length blocks and offsets in unusual
        // overlapping/empty ranges; keep the result deterministic.
        return mutableBlocks.lastOrNull { it.startOffset <= globalOffset } ?: first
    }

    private fun localOffset(block: ChapterBlock, globalOffset: Int): Int =
        (globalOffset - block.startOffset).coerceIn(0, block.contentLength)

    private fun percent(offset: Int, length: Int): Int =
        if (length <= 0) 0 else (offset * 100 / length).coerceIn(0, 100)
}
