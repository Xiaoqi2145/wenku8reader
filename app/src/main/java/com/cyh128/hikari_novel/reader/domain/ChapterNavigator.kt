package com.cyh128.hikari_novel.reader.domain

import com.cyh128.hikari_novel.data.model.ChapterRef

data class ChapterPosition(
    val volumeIndex: Int,
    val chapterIndex: Int
)

data class ChapterLocation(
    val volumeIndex: Int,
    val chapterIndex: Int,
    val chapter: ChapterRef
) {
    val position: ChapterPosition
        get() = ChapterPosition(volumeIndex, chapterIndex)
}

/**
 * Pure chapter navigation for a novel's volume/chapter structure.
 * Empty volumes are skipped in both directions.
 */
class ChapterNavigator(volumes: List<List<ChapterRef>>) {
    private val volumes: List<List<ChapterRef>> = volumes.map { it.toList() }

    init {
        val cids = this.volumes.flatten().map { it.cid }
        require(cids.size == cids.toSet().size) { "Chapter cids must be unique" }
    }

    val volumeCount: Int
        get() = volumes.size

    val chapterCount: Int
        get() = volumes.sumOf { it.size }

    fun first(): ChapterLocation? = findFrom(0, 0, step = 1)

    fun last(): ChapterLocation? = findFrom(volumes.lastIndex, Int.MAX_VALUE, step = -1)

    fun chapterAt(volumeIndex: Int, chapterIndex: Int): ChapterLocation? =
        locationAt(ChapterPosition(volumeIndex, chapterIndex))

    fun chapterAt(position: ChapterPosition): ChapterLocation? = locationAt(position)

    fun at(volumeIndex: Int, chapterIndex: Int): ChapterRef? =
        chapterAt(volumeIndex, chapterIndex)?.chapter

    fun locate(cid: String): ChapterLocation? {
        volumes.forEachIndexed { volumeIndex, chapters ->
            val chapterIndex = chapters.indexOfFirst { it.cid == cid }
            if (chapterIndex >= 0) {
                return ChapterLocation(volumeIndex, chapterIndex, chapters[chapterIndex])
            }
        }
        return null
    }

    fun next(current: ChapterRef): ChapterRef? =
        locate(current)?.let { next(it.position)?.chapter }

    fun previous(current: ChapterRef): ChapterRef? =
        locate(current)?.let { previous(it.position)?.chapter }

    fun next(current: ChapterLocation): ChapterLocation? = next(current.position)

    fun previous(current: ChapterLocation): ChapterLocation? = previous(current.position)

    fun next(volumeIndex: Int, chapterIndex: Int): ChapterLocation? =
        next(ChapterPosition(volumeIndex, chapterIndex))

    fun previous(volumeIndex: Int, chapterIndex: Int): ChapterLocation? =
        previous(ChapterPosition(volumeIndex, chapterIndex))

    fun next(current: ChapterPosition): ChapterLocation? {
        if (locationAt(current) == null) return null
        return findFrom(current.volumeIndex, current.chapterIndex + 1, step = 1)
    }

    fun previous(current: ChapterPosition): ChapterLocation? {
        if (locationAt(current) == null) return null
        return findFrom(current.volumeIndex, current.chapterIndex - 1, step = -1)
    }

    fun hasNext(current: ChapterRef): Boolean = next(current) != null

    fun hasPrevious(current: ChapterRef): Boolean = previous(current) != null

    private fun locate(current: ChapterRef): ChapterLocation? =
        locationAt(ChapterPosition(current.volumeIndex, current.chapterIndex))
            ?.takeIf { it.chapter.cid == current.cid }

    private fun locationAt(position: ChapterPosition): ChapterLocation? {
        val chapters = volumes.getOrNull(position.volumeIndex) ?: return null
        val chapter = chapters.getOrNull(position.chapterIndex) ?: return null
        return ChapterLocation(position.volumeIndex, position.chapterIndex, chapter)
    }

    private fun findFrom(volumeIndex: Int, chapterIndex: Int, step: Int): ChapterLocation? {
        var volume = volumeIndex
        var chapter = chapterIndex

        while (volume in volumes.indices) {
            val chapters = volumes[volume]
            if (chapters.isNotEmpty()) {
                val candidateIndex = if (step > 0) {
                    chapter.coerceAtLeast(0)
                } else {
                    chapter.coerceAtMost(chapters.lastIndex)
                }
                if (candidateIndex in chapters.indices) {
                    return ChapterLocation(volume, candidateIndex, chapters[candidateIndex])
                }
            }
            volume += step
            chapter = if (step > 0) 0 else Int.MAX_VALUE
        }
        return null
    }
}
