package com.cyh128.hikari_novel.ui.read.vertical

import android.graphics.Paint
import android.os.Build
import android.text.Spannable
import android.text.Spanned
import android.text.style.LeadingMarginSpan
import android.text.style.LineHeightSpan
import android.view.Gravity
import android.widget.TextView

/**
 * Immutable typography snapshot for the vertical reader.
 *
 * Units follow the vertical reader's own scale (see
 * `docs/galisland-visual-drift-audit.md` R-4 — the horizontal and vertical
 * panels intentionally use different units):
 * - [fontSizeSp] is sp, as consumed by [TextView.setTextSize]
 * - every other size is px
 */
data class VerticalTypography(
    val fontSizeSp: Float,
    val lineSpacingExtraPx: Float,
    val paragraphSpacingPx: Float,
    val letterSpacingPx: Float,
    val alignJustify: Boolean,
    val firstLineIndentChars: Int
) {
    companion object {
        fun from(
            fontSizeSp: Float,
            lineSpacingExtraPx: Float,
            paragraphSpacingPx: Float,
            letterSpacingPx: Float,
            textAlignment: String,
            firstLineIndentChars: Int
        ) = VerticalTypography(
            fontSizeSp = fontSizeSp,
            lineSpacingExtraPx = lineSpacingExtraPx,
            paragraphSpacingPx = paragraphSpacingPx,
            letterSpacingPx = letterSpacingPx,
            alignJustify = textAlignment == "justify",
            firstLineIndentChars = firstLineIndentChars
        )
    }
}

/**
 * Applies [VerticalTypography] to the vertical reader's TextView.
 *
 * The reader renders one concatenated [android.text.SpannableStringBuilder] for
 * the whole session, so paragraph spacing and first-line indent are expressed as
 * spans rather than by editing the text: spans never shift character offsets,
 * which keeps `ReaderAnchor.charOffset` valid across typography changes.
 *
 * [TextView.setLineSpacing] only applies one leading to every line, so it stays
 * the mechanism for 行距; paragraph spacing is layered on top for the first line
 * of each paragraph.
 */
object VerticalTypographyApplier {

    /** Mirrors the `@hide` `TextView.JUSTIFICATION_MODE_NONE`. */
    private const val JUSTIFICATION_MODE_NONE = 0

    /** Mirrors the `@hide` `TextView.JUSTIFICATION_MODE_INTER_WORD`. */
    private const val JUSTIFICATION_MODE_INTER_WORD = 1

    fun apply(
        textView: TextView,
        typography: VerticalTypography,
        headingRanges: List<IntRange> = emptyList()
    ) {
        textView.textSize = typography.fontSizeSp
        textView.setLineSpacing(typography.lineSpacingExtraPx, 1f)
        textView.letterSpacing = emFromPx(typography.letterSpacingPx, textView.textSize)
        textView.gravity = Gravity.START
        applyJustification(textView, typography.alignJustify)
        applyParagraphSpans(textView, typography, headingRanges)
    }

    /**
     * Justified text needs `justificationMode`, which only exists from API 26.
     * On API 24/25 there is no platform justification, so the text stays
     * start-aligned — a silent, documented fallback rather than a fake one.
     *
     * The mode constants are `@hide` in `android.widget.TextView`, so their
     * public values are mirrored above; `setJustificationMode` itself is public.
     */
    private fun applyJustification(textView: TextView, alignJustify: Boolean) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        textView.justificationMode = if (alignJustify) {
            JUSTIFICATION_MODE_INTER_WORD
        } else {
            JUSTIFICATION_MODE_NONE
        }
    }

    private fun applyParagraphSpans(
        textView: TextView,
        typography: VerticalTypography,
        headingRanges: List<IntRange>
    ) {
        val text = textView.text
        if (text !is Spannable) return

        clearSpans(text)
        if (typography.paragraphSpacingPx <= 0f && typography.firstLineIndentChars <= 0) return

        val indentPx = (typography.firstLineIndentChars * textView.textSize).toInt()
        paragraphRanges(text).forEach { range ->
            // A chapter heading is not a body paragraph: it already carries the
            // blank lines from the renderer prefix, and indenting a centred-looking
            // bold heading reads as a mistake.
            if (headingRanges.any { it.first <= range.last && range.first <= it.last }) {
                return@forEach
            }
            if (typography.paragraphSpacingPx > 0f) {
                // One span per paragraph, attached to its first character only:
                // LineHeightSpan grows every line it covers, so covering the
                // whole paragraph would space out the whole paragraph.
                text.setSpan(
                    ParagraphSpacingSpan(typography.paragraphSpacingPx),
                    range.first,
                    range.first + 1,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
            if (indentPx > 0) {
                text.setSpan(
                    LeadingMarginSpan.Standard(indentPx, 0),
                    range.first,
                    range.last + 1,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }
    }

    private fun clearSpans(text: Spannable) {
        if (text.length <= 0) return
        text.getSpans(0, text.length, ParagraphSpacingSpan::class.java).forEach { text.removeSpan(it) }
        text.getSpans(0, text.length, LeadingMarginSpan::class.java).forEach { text.removeSpan(it) }
    }

    /**
     * Non-blank paragraph ranges, `\n` excluded. Blank lines are skipped: they
     * already contribute a full empty line, and adding a paragraph gap on top of
     * them would double the visual break.
     */
    internal fun paragraphRanges(text: CharSequence): List<IntRange> {
        val ranges = mutableListOf<IntRange>()
        var start = 0
        var index = 0
        while (index <= text.length) {
            if (index == text.length || text[index] == '\n') {
                if (index > start) ranges += start until index
                start = index + 1
            }
            index++
        }
        return ranges
    }

    /** [TextView.setLetterSpacing] is expressed in em, the setting is in px. */
    private fun emFromPx(px: Float, textSizePx: Float): Float =
        if (textSizePx <= 0f) 0f else px / textSizePx

    /**
     * Adds [extraPx] above the line it is attached to.
     *
     * `ascent` is negative above the baseline, so subtracting makes the line
     * taller without moving the baseline relative to the text.
     */
    private class ParagraphSpacingSpan(private val extraPx: Float) : LineHeightSpan {
        override fun chooseHeight(
            text: CharSequence,
            start: Int,
            end: Int,
            spanStartVertical: Int,
            lineHeight: Int,
            fontMetricsInt: Paint.FontMetricsInt
        ) {
            val extra = extraPx.toInt()
            if (extra <= 0) return
            fontMetricsInt.ascent -= extra
        }
    }
}
