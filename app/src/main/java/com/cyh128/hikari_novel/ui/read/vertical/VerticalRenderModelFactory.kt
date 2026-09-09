package com.cyh128.hikari_novel.ui.read.vertical

import com.cyh128.hikari_novel.data.model.CachedChapter

/** Pure builder for vertical chapter text metadata. */
object VerticalRenderModelFactory {
    fun create(chapter: CachedChapter, prefix: String = VerticalReaderController.chapterPrefix(chapter.ref.title)) =
        VerticalRenderModel(
            chapter = chapter,
            prefix = prefix,
            text = prefix + chapter.content,
            prefixLength = prefix.length,
            images = chapter.images.toList()
        )
}
