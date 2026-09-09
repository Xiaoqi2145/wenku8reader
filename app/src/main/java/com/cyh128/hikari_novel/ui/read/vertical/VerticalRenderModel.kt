package com.cyh128.hikari_novel.ui.read.vertical

import com.cyh128.hikari_novel.data.model.CachedChapter

/** Pure render description shared by tests and the Android text adapter. */
data class VerticalRenderModel(
    val chapter: CachedChapter,
    val prefix: String,
    val text: String,
    val prefixLength: Int,
    val images: List<String>
)
