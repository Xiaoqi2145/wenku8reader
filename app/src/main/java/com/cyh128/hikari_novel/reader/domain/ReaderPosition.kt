package com.cyh128.hikari_novel.reader.domain

import com.cyh128.hikari_novel.data.model.ChapterRef
import com.cyh128.hikari_novel.data.model.ReaderAnchor

/** Stable, UI-independent representation of a reader location. */
data class ReaderPosition(
    val chapter: ChapterRef,
    val anchor: ReaderAnchor
)
