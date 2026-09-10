package com.cyh128.hikari_novel.ui.read.vertical

import android.widget.TextView
import androidx.core.widget.NestedScrollView
import com.cyh128.hikari_novel.data.model.ChapterRef
import com.cyh128.hikari_novel.data.model.ReaderAnchor
import com.cyh128.hikari_novel.reader.domain.DebouncedPersistence
import com.cyh128.hikari_novel.reader.domain.VerticalProgressCalculator
import com.cyh128.hikari_novel.reader.domain.VerticalProgressSample
import kotlinx.coroutines.CoroutineScope

/** A visible position plus the stable chapter-local anchor used for persistence. */
data class VerticalProgress(
    val chapter: ChapterRef,
    val content: String,
    val anchor: ReaderAnchor,
    val displayPercent: Int,
    val globalOffset: Int,
    val scrollY: Int,
    /** Per-chapter legacy scroll coordinate for older app versions. */
    val legacyLocation: Int
)

/**
 * Converts the visible center of a vertical reader into a chapter anchor.
 *
 * The Android geometry adapter stays here; callers receive a model callback and
 * do not need to know about TextView.Layout or ScrollView coordinates. Saves are
 * coalesced so a long scroll cannot issue one database write per pixel.
 */
class VerticalProgressReporter(
    private val scrollView: NestedScrollView,
    private val textView: TextView,
    private val controller: VerticalReaderController,
    scope: CoroutineScope,
    private val saveDelayMillis: Long = DEFAULT_SAVE_DELAY_MILLIS,
    private val onProgress: (VerticalProgress) -> Unit,
    onPersist: (VerticalProgress) -> Unit
) {
    private val progressCalculator = VerticalProgressCalculator(controller.blockMapper)
    private val persistence = DebouncedPersistence(
        scope = scope,
        delayMillis = saveDelayMillis,
        persist = onPersist
    )
    private var latestProgress: VerticalProgress? = null
    private var lastReportedGlobalOffset: Int? = null
    private var lastReportedImageIndex: Int? = null

    fun report(imageIndex: Int? = null) {
        calculate(imageIndex)?.let { progress ->
            val progressImageIndex = progress.anchor.imageIndex
            if (progress.globalOffset == lastReportedGlobalOffset &&
                progressImageIndex == lastReportedImageIndex
            ) {
                return
            }
            lastReportedGlobalOffset = progress.globalOffset
            lastReportedImageIndex = progressImageIndex
            latestProgress = progress
            onProgress(progress)
            persistence.submit(progress)
        }
    }

    /** Persist the most recent sample immediately, for lifecycle transitions. */
    fun flush() {
        persistence.flush()
    }

    fun cancel() {
        persistence.cancel()
        latestProgress = null
        lastReportedGlobalOffset = null
        lastReportedImageIndex = null
    }

    fun latest(): VerticalProgress? = latestProgress

    private fun calculate(imageIndex: Int?): VerticalProgress? {
        val layout = textView.layout ?: return null
        if (layout.lineCount == 0 || controller.isEmpty) return null

        val visibleY = (scrollView.scrollY + scrollView.height / 2 - textView.top)
            .coerceIn(0, textView.height)
        val line = layout.getLineForVertical(visibleY).coerceIn(0, layout.lineCount - 1)
        val globalOffset = layout.getOffsetForHorizontal(line, 0f)
            .coerceIn(0, textView.text.length)
        val mappedChapter = controller.chapterAt(globalOffset) ?: return null
        val block = controller.block(mappedChapter.ref.cid) ?: return null

        val startLine = layout.getLineForOffset(
            block.startOffset.coerceIn(0, textView.text.length)
        )
        val endLine = layout.getLineForOffset(
            block.endOffsetExclusive.coerceIn(0, textView.text.length)
        )
        val startY = layout.getLineTop(startLine)
        val endY = layout.getLineBottom(endLine).coerceAtLeast(startY + 1)
        val legacyLocation = (scrollView.scrollY - textView.top - startY)
            .coerceIn(0, (endY - startY).coerceAtLeast(0))
        val geometryPercent = ((visibleY - startY) * 100 / (endY - startY))
            .coerceIn(0, 100)

        val mapped = progressCalculator.map(
            VerticalProgressSample(
                globalOffset = globalOffset,
                displayPercent = maxOf(mappedChapter.normalizedPercent, geometryPercent),
                scrollY = scrollView.scrollY,
                imageIndex = imageIndex
            )
        ) ?: return null
        return VerticalProgress(
            chapter = mapped.chapter,
            content = mapped.content,
            anchor = mapped.anchor,
            displayPercent = mapped.displayPercent,
            globalOffset = mapped.globalOffset,
            scrollY = mapped.scrollY,
            legacyLocation = legacyLocation
        )
    }

    companion object {
        const val DEFAULT_SAVE_DELAY_MILLIS = 400L
    }
}
