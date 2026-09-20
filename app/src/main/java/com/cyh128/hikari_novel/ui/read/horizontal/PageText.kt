package com.cyh128.hikari_novel.ui.read.horizontal

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import com.cyh128.hikari_novel.R

/*
 * Copyright 2018 ya-b
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * This file includes code from NetNovelReader under the Apache License, Version 2.0.
 * The original source can be found at: https://github.com/ya-b/NetNovelReader/
 */

class PageText : View {
    private val mPaint by lazy {
        Paint().apply {
            isAntiAlias = true
            isDither = true
            isFilterBitmap = true
        }
    }

    var mBgColor = Color.WHITE

    /** Reader page background (reading colour + GalIsland grid); null = flat mBgColor. */
    var mPageBackground: android.graphics.drawable.Drawable? = null
    var mTextArray: MutableList<PageLine>? = null
    var mSecondTextArray: MutableList<PageLine>? = null
    var mTextColor: Int = Color.BLACK
    var mTxtFontType: Typeface = Typeface.DEFAULT
    var mBottomTextSize = 45f
    var mTextSize = 55f
        set(value) {
            if (value > 20f) {
                field = value
            }
        }
    var mRowSpace = 2f

    /** Letter spacing in px; converted to em for the paint. */
    var mLetterSpacing = 0f

    /** Text alignment: "left", "justify" or "right". */
    var mTextAlignment = "left"

    var mTitle: String? = null
    var mPageNum: Int = 0
    var mSecondPageNum: Int = 0
    var mMaxPageNum: Int = 0
    var mDoublePageMode = false

    private val readerContentBottomInset by lazy {
        resources.getDimension(R.dimen.gi_reader_content_bottom_inset)
    }

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    )

    override fun onDraw(canvas: Canvas) {
        mPaint.color = mTextColor
        mPaint.typeface = mTxtFontType
        mPaint.textSize = getFooterTextSize()
        // The footer keeps the plain metrics; letter spacing only applies to the
        // chapter body below.
        mPaint.letterSpacing = 0f
        drawPageBackground(canvas, mPageBackground, mBgColor)

        val currentPage = if (mPageNum > mMaxPageNum) 0 else mPageNum
        val pageText = if (mDoublePageMode && mSecondPageNum > 0) {
            "$currentPage-$mSecondPageNum/$mMaxPageNum"
        } else {
            "$currentPage/$mMaxPageNum"
        }
        drawFooter(canvas, pageText)

        if (mTextArray == null || mMaxPageNum < 1) return

        mPaint.textSize = mTextSize
        mPaint.letterSpacing = if (mTextSize <= 0f) 0f else mLetterSpacing / mTextSize
        val pageWidth = if (mDoublePageMode) width / 2f else width.toFloat()
        drawPage(canvas, mTextArray, 0f, pageWidth)
        if (mDoublePageMode) {
            mSecondTextArray?.let {
                drawPage(canvas, it, pageWidth, pageWidth)
            }
        }
    }

    private fun drawPage(
        canvas: Canvas,
        textArray: MutableList<PageLine>?,
        pageLeft: Float,
        pageWidth: Float
    ) {
        if (textArray == null) return
        val left = pageLeft + getMarginLeft(pageWidth)
        val textWidth = getTextWidth(pageWidth).toFloat()
        val lineHeight = mTextSize * mRowSpace
        var y = getMarginTop(textArray)

        for (line in textArray) {
            y += line.spacingBefore
            val drawn = line.text.replace(" ", "  ")
            when (mTextAlignment) {
                "right" -> canvas.drawText(
                    drawn,
                    left + textWidth - mPaint.measureText(drawn),
                    y,
                    mPaint
                )

                "justify" -> if (line.isParagraphEnd || drawn.length < 2) {
                    // A paragraph's last line keeps its natural width.
                    canvas.drawText(drawn, left + line.indent, y, mPaint)
                } else {
                    drawJustified(canvas, drawn, left, textWidth, y)
                }

                else -> canvas.drawText(drawn, left + line.indent, y, mPaint)
            }
            y += lineHeight
        }
    }

    /**
     * Stretches one line to the full text width by distributing the slack over
     * the gaps between glyphs. CJK text has no spaces to expand, so per-glyph
     * drawing is the only way to justify it.
     */
    private fun drawJustified(canvas: Canvas, text: String, left: Float, width: Float, y: Float) {
        val natural = mPaint.measureText(text)
        val gap = (width - natural) / (text.length - 1)
        if (gap <= 0f) {
            canvas.drawText(text, left, y, mPaint)
            return
        }
        var x = left
        for (char in text) {
            val glyph = char.toString()
            canvas.drawText(glyph, x, y, mPaint)
            x += mPaint.measureText(glyph) + gap
        }
    }

    private fun drawFooter(canvas: Canvas, pageText: String) {
        val footerMargin = getMarginLeft(width.toFloat()).coerceAtLeast(16f)
        val footerY = height - readerContentBottomInset - getFooterBottomPadding()
        val pageTextWidth = mPaint.measureText(pageText)
        val titleMaxWidth = (width - footerMargin * 3f - pageTextWidth).coerceAtLeast(0f)
        val titleText = fitFooterText(mTitle.orEmpty(), titleMaxWidth)
        val originalAlpha = mPaint.alpha

        mPaint.alpha = (originalAlpha * 0.55f).toInt()
        if (titleText.isNotEmpty()) {
            canvas.drawText(titleText, footerMargin, footerY, mPaint)
        }
        canvas.drawText(pageText, width - pageTextWidth - footerMargin, footerY, mPaint)
        mPaint.alpha = originalAlpha
    }

    private fun fitFooterText(text: String, maxWidth: Float): String {
        if (text.isBlank() || maxWidth <= 0f) return ""
        if (mPaint.measureText(text) <= maxWidth) return text

        val ellipsis = "..."
        val ellipsisWidth = mPaint.measureText(ellipsis)
        if (ellipsisWidth >= maxWidth) return ""

        val keepCount = mPaint.breakText(text, true, maxWidth - ellipsisWidth, null)
        return text.take(keepCount).trimEnd() + ellipsis
    }

    private fun getMarginLeft(pageWidth: Float): Float {
        val count = getTextWidth(pageWidth) / mTextSize.toInt()
        return (pageWidth - count * mTextSize) / 2
    }

    private fun getMarginTop(lines: MutableList<PageLine>): Float {
        val lineHeight = mTextSize * mRowSpace
        val blockHeight = lines.sumOf { (lineHeight + it.spacingBefore).toDouble() }.toFloat()
        return (getContentHeight() - blockHeight) / 2 + mTextSize
    }

    private fun getTextWidth(pageWidth: Float): Int =
        (pageWidth * if (mDoublePageMode) 0.90f else 0.96f).toInt()

    private fun getTextHeight(): Int = (getContentHeight() * 0.94f).toInt()

    private fun getContentHeight(): Float =
        (height - readerContentBottomInset - getFooterReservedHeight())
            .coerceAtLeast(mTextSize * mRowSpace)

    private fun getFooterTextSize(): Float = mBottomTextSize.coerceAtMost(mTextSize * 0.55f)

    private fun getFooterReservedHeight(): Float =
        (getFooterTextSize() * 2.2f).coerceAtLeast(mTextSize * 1.2f)

    private fun getFooterBottomPadding(): Float =
        (getFooterTextSize() * 0.55f).coerceAtLeast(12f)
}
