package com.cyh128.hikari_novel.data.model

data class ChapterRef(
    val aid: String,
    val cid: String,
    val volumeIndex: Int,
    val chapterIndex: Int,
    val title: String
)

enum class ContentSource { MEMORY_CACHE, DISK_CACHE, NETWORK, STALE_CACHE }

data class CachedChapter(
    val ref: ChapterRef,
    val content: String,
    val images: List<String>,
    val updatedAt: Long,
    val isDownloaded: Boolean = false
)

data class ReaderAnchor(
    val cid: String,
    val paragraphIndex: Int,
    val charOffset: Int,
    val normalizedPercent: Int,
    val imageIndex: Int? = null
)

sealed interface ReaderUiState {
    data object Idle : ReaderUiState
    data class Loading(val chapter: ChapterRef) : ReaderUiState
    data class Ready(
        val chapter: CachedChapter,
        val source: ContentSource,
        val isRefreshing: Boolean = false
    ) : ReaderUiState
    data class Error(
        val chapter: ChapterRef,
        val message: String,
        val canRetry: Boolean = true,
        val hasCachedContent: Boolean = false
    ) : ReaderUiState
}

sealed interface ReaderEffect {
    data class Message(val text: String) : ReaderEffect
    data object PreviousChapterUnavailable : ReaderEffect
    data object NextChapterUnavailable : ReaderEffect
}
