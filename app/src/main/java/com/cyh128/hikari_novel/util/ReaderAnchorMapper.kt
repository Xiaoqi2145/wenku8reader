package com.cyh128.hikari_novel.util

import com.cyh128.hikari_novel.data.model.ReaderAnchor

object ReaderAnchorMapper {
    fun fromPercent(cid: String, content: String, percent: Int): ReaderAnchor {
        val safePercent = percent.coerceIn(0, 100)
        val offset = (content.length * safePercent / 100f).toInt().coerceIn(0, content.length)
        return ReaderAnchor(cid, content.take(offset).count { it == '\n' }, offset, safePercent)
    }

    fun clamp(anchor: ReaderAnchor, content: String): ReaderAnchor {
        val offset = anchor.charOffset.coerceIn(0, content.length)
        val percent = if (content.isEmpty()) 0 else (offset * 100 / content.length).coerceIn(0, 100)
        return anchor.copy(
            paragraphIndex = content.take(offset).count { it == '\n' },
            charOffset = offset,
            normalizedPercent = percent
        )
    }
}
