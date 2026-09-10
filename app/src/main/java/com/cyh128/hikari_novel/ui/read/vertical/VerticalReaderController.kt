package com.cyh128.hikari_novel.ui.read.vertical

import com.cyh128.hikari_novel.data.model.CachedChapter
import com.cyh128.hikari_novel.data.model.ReaderAnchor
import com.cyh128.hikari_novel.reader.domain.ChapterBlock
import com.cyh128.hikari_novel.reader.domain.ChapterBlockMapper
import com.cyh128.hikari_novel.reader.domain.MappedChapterOffset

/** A single chapter append operation as understood by the content renderer. */
data class VerticalChapterRender(
    val chapter: CachedChapter,
    val prefix: String,
    val block: ChapterBlock
) {
    val renderedText: String
        get() = prefix + chapter.content
}

/**
 * Coordinates a vertical reader's rendered chapter session.
 *
 * The controller accepts the same [CachedChapter] produced by the Phase-3
 * loading pipeline, owns which chapters are currently rendered, and exposes
 * only stable mapper operations to UI adapters. Manual navigation calls
 * [start]; continuous reading calls [append].
 */
class VerticalReaderController(
    val blockMapper: ChapterBlockMapper = ChapterBlockMapper(),
    private val maxLoadedChapters: Int = DEFAULT_MAX_LOADED_CHAPTERS
) {
    init {
        require(maxLoadedChapters > 0) { "maxLoadedChapters must be positive" }
    }
    private val mutableChapters = linkedMapOf<String, CachedChapter>()
    private var nextChapterRequested = false

    val blocks: List<ChapterBlock>
        get() = blockMapper.blocks

    val loadedChapters: List<CachedChapter>
        get() = mutableChapters.values.toList()

    val currentChapter: CachedChapter?
        get() = mutableChapters.values.lastOrNull()

    val isEmpty: Boolean
        get() = blocks.isEmpty()

    /** Replace the rendered session with [chapter]. */
    fun start(chapter: CachedChapter): VerticalChapterRender {
        reset()
        return appendInternal(chapter)
    }

    /** Append one chapter to the current continuous-reading session. */
    fun append(chapter: CachedChapter): VerticalChapterRender = appendInternal(chapter)

    /** Apply a loaded result according to the current navigation mode. */
    fun acceptLoaded(chapter: CachedChapter, append: Boolean): VerticalChapterRender =
        if (append) append(chapter) else start(chapter)

    /** Build render instructions for an already accepted chapter without mutating the session. */
    fun render(chapter: CachedChapter): VerticalChapterRender? =
        block(chapter.ref.cid)?.let { block ->
            VerticalChapterRender(chapter, chapterPrefix(chapter.ref.title), block)
        }

    fun reset() {
        blockMapper.clear()
        mutableChapters.clear()
        nextChapterRequested = false
    }

    fun block(cid: String): ChapterBlock? = blocks.firstOrNull { it.ref.cid == cid }

    fun content(cid: String): String? = blockMapper.content(cid)

    fun contains(cid: String): Boolean = block(cid) != null

    fun chapterAt(globalOffset: Int): MappedChapterOffset? = blockMapper.chapterAt(globalOffset)

    fun anchorAt(globalOffset: Int): ReaderAnchor? = blockMapper.anchorAt(globalOffset)

    fun globalOffset(anchor: ReaderAnchor): Int? = blockMapper.globalOffset(anchor)

    /** True until the caller has accepted an automatic next-chapter request. */
    fun canRequestNextChapter(): Boolean =
        !nextChapterRequested && mutableChapters.size < maxLoadedChapters

    /** Stop unbounded TextView growth; a manual chapter jump starts a new session. */
    val sessionLimitReached: Boolean
        get() = mutableChapters.size >= maxLoadedChapters

    fun markNextChapterRequested() {
        nextChapterRequested = true
    }

    /** Clear the guard after the requested chapter has been rendered. */
    fun onChapterAppended() {
        nextChapterRequested = false
    }

    private fun appendInternal(chapter: CachedChapter): VerticalChapterRender {
        val prefix = chapterPrefix(chapter.ref.title)
        val block = blockMapper.append(
            ref = chapter.ref,
            content = chapter.content,
            prefixLength = prefix.length
        )
        mutableChapters[chapter.ref.cid] = chapter
        return VerticalChapterRender(chapter, prefix, block)
    }

    companion object {
        /** Bound the retained TextView session until virtualized rendering lands. */
        const val DEFAULT_MAX_LOADED_CHAPTERS = 24

        /** Keep the prefix in one place so mapper offsets and rendered text agree. */
        fun chapterPrefix(title: String): String = "\n\n$title\n\n"
    }
}
