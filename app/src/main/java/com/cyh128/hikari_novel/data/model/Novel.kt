package com.cyh128.hikari_novel.data.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class Chapter(
    val chapterTitle: String,
    val url: String,
    val cid: String
): Parcelable

@Parcelize
data class Volume(
    var volumeTitle: String,
    var chapters: List<Chapter>
): Parcelable {
    companion object {
        fun empty() = Volume(
            "",
            mutableListOf()
        )
    }
}

@Parcelize
data class Novel(
    val aid: String,
    val volume: List<Volume>,
    val title: String = ""
): Parcelable

/** Canonical chapter references shared by both reader orientations. */
fun Novel.chapterRefs(): List<List<ChapterRef>> = volume.mapIndexed { volumeIndex, item ->
    item.chapters.mapIndexed { chapterIndex, chapter ->
        ChapterRef(
            aid = aid,
            cid = chapter.cid,
            volumeIndex = volumeIndex,
            chapterIndex = chapterIndex,
            title = chapter.chapterTitle
        )
    }
}

fun Novel.chapterRefAt(volumeIndex: Int, chapterIndex: Int): ChapterRef? =
    chapterRefs().getOrNull(volumeIndex)?.getOrNull(chapterIndex)
