package com.cyh128.hikari_novel.data.source.local.database.reader

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Bookmark entry for the reader.
 * See docs/reader-koodo-alignment.md P1-3.
 */
@Entity(tableName = "bookmark")
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val aid: String,
    val cid: String,
    val volumeIndex: Int,
    val chapterIndex: Int,
    val paragraphIndex: Int,
    val charOffset: Int,
    val normalizedPercent: Int,
    val imageIndex: Int?,
    val excerpt: String,
    val createdAt: Long
)
