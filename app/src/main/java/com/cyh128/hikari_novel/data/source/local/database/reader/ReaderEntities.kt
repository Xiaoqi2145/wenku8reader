package com.cyh128.hikari_novel.data.source.local.database.reader

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chapter_cache", primaryKeys = ["aid", "cid"])
data class ChapterCacheEntity(
    val cid: String,
    val aid: String,
    val volumeIndex: Int,
    val chapterIndex: Int,
    val title: String,
    val content: String,
    val imagesJson: String,
    val updatedAt: Long,
    val isDownloaded: Boolean,
    val sizeBytes: Long
)

@Entity(tableName = "reader_progress", primaryKeys = ["aid", "cid"])
data class ReaderProgressEntity(
    val cid: String,
    val aid: String,
    val volumeIndex: Int,
    val chapterIndex: Int,
    val paragraphIndex: Int,
    val charOffset: Int,
    val normalizedPercent: Int,
    val imageIndex: Int?,
    val updatedAt: Long,
    val isLatest: Boolean
)

@Entity(tableName = "download_task", primaryKeys = ["aid", "cid"])
data class DownloadTaskEntity(
    val cid: String,
    val aid: String,
    val volumeIndex: Int,
    val chapterIndex: Int,
    val title: String,
    val status: String,
    val progress: Int,
    val error: String?,
    val updatedAt: Long
)

@Entity(tableName = "download_book")
data class DownloadBookEntity(
    @PrimaryKey val aid: String,
    val title: String,
    val imageUrl: String?,
    val totalChapters: Int,
    val updatedAt: Long
)

/**
 * Projection for full-book search: one query returns every cached chapter body
 * for a book, instead of one lookup per chapter.
 *
 * See docs/reader-koodo-alignment.md P1-4.
 */
data class CachedChapterContent(
    val cid: String,
    val content: String
)
