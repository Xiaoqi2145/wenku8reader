package com.cyh128.hikari_novel.ui.read.vertical

import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.widget.TextView
import com.cyh128.hikari_novel.data.model.CachedChapter

/** Minimal sink used by the renderer to append chapter images without owning UI state. */
fun interface VerticalImageSink {
    fun appendImages(images: List<String>)
}

/** Android text result containing title spans for display. */
data class VerticalRender(
    val chapter: CachedChapter,
    val text: SpannableStringBuilder,
    val prefixLength: Int,
    val images: List<String>
)

/**
 * Builds the visible vertical-reader text and title spans.
 *
 * Mapping, progress, and chapter session state intentionally do not belong here;
 * the pure [VerticalRenderModel] can be validated without Android, while the
 * Android adapter applies spans and updates TextViews/image sinks.
 */
class VerticalContentRenderer {
    fun renderModel(chapter: CachedChapter): VerticalRenderModel =
        VerticalRenderModelFactory.create(chapter)

    fun render(chapter: CachedChapter): VerticalRender {
        val model = renderModel(chapter)
        return renderText(model)
    }

    /** Render an already-mapped chapter without changing controller state. */
    fun render(chapter: VerticalChapterRender): VerticalRender =
        renderText(
            VerticalRenderModel(
                chapter = chapter.chapter,
                prefix = chapter.prefix,
                text = chapter.renderedText,
                prefixLength = chapter.prefix.length,
                images = chapter.chapter.images
            )
        )

    fun setInitialText(textView: TextView, render: VerticalRender) {
        textView.text = render.text
    }

    fun setInitialText(textView: TextView, chapterRender: VerticalChapterRender) {
        setInitialText(textView, render(chapterRender))
    }

    fun appendText(textView: TextView, render: VerticalRender) {
        textView.append(render.text)
    }

    fun appendText(textView: TextView, chapterRender: VerticalChapterRender) {
        appendText(textView, render(chapterRender))
    }

    fun appendImages(imageSink: VerticalImageSink?, images: List<String>) {
        if (images.isNotEmpty()) imageSink?.appendImages(images)
    }

    private fun renderText(model: VerticalRenderModel): VerticalRender {
        val text = SpannableStringBuilder(model.text)
        val titleStart = model.prefix.indexOf(model.chapter.ref.title)
        val titleEnd = titleStart + model.chapter.ref.title.length
        if (titleStart >= 0 && titleStart < text.length && titleEnd > titleStart) {
            text.setSpan(
                StyleSpan(Typeface.BOLD),
                titleStart,
                titleEnd.coerceAtMost(text.length),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            text.setSpan(
                RelativeSizeSpan(1.08f),
                titleStart,
                titleEnd.coerceAtMost(text.length),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        return VerticalRender(
            chapter = model.chapter,
            text = text,
            prefixLength = model.prefixLength,
            images = model.images
        )
    }
}
