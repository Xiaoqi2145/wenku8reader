package com.cyh128.hikari_novel.data.source.local.database.reader

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chapter_cache")
data class ChapterCacheEntity(
    @PrimaryKey val cid: String,
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

@Entity(tableName = "reader_progress")
data class ReaderProgressEntity(
    @PrimaryKey val cid: String,
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

@Entity(tableName = "download_task")
data class DownloadTaskEntity(
    @PrimaryKey val cid: String,
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
