package com.cyh128.hikari_novel.ui.read.horizontal

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.MotionEvent
import androidx.appcompat.widget.AppCompatImageView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.target.Target
import com.bumptech.glide.request.transition.Transition
import com.cyh128.hikari_novel.R
import com.cyh128.hikari_novel.util.ReaderTapArea
import com.cyh128.hikari_novel.util.ReaderTouchJudge

/*
 * Copyright 2018 ya-b
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * This file includes code from NetNovelReader under the Apache License, Version 2.0.
 * The original source can be found at: https://github.com/ya-b/NetNovelReader/
 *
 * Modified by 15dd on 2024 - 增加图片支持
 */

class PageImage : AppCompatImageView {
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
    var mTextColor: Int = Color.BLACK
    var mTxtFontType: Typeface = Typeface.DEFAULT
    var mBottomTextSize = 45f
    var mTextSize = 55f
        set(value) {
            if (value > 20f) {
                field = value
            }
        }
    var mRowSpace = 2f  //行距

    var mTitle: String? = null
    var mPageNum: Int = 0
    var mSecondPageNum: Int = 0
    var mMaxPageNum: Int = 0

    /**
     * Tablet double-page mode: this page shows two illustrations side by side,
     * one per half of the viewport, instead of stretching one across the whole
     * page. [mSecondImageurl] is null on the last illustration of an odd-length
     * section, which leaves the right half empty.
     */
    var mDoublePageMode = false
    var mSecondImageurl: String? = null

    private val readerContentBottomInset by lazy {
        resources.getDimension(com.cyh128.hikari_novel.R.dimen.gi_reader_content_bottom_inset)
    }

    var imageRect: RectF? = null
    var secondImageRect: RectF? = null
    var mImageurl: String? = null
    lateinit var mImageClick: (String) -> Unit
    lateinit var mOnNextPage: () -> Unit
    lateinit var mOnPreviousPage: () -> Unit
    var mOnCenterClick: IPageView.OnCenterClick? = null

    private val touchJudge by lazy { ReaderTouchJudge(context) }
    private var touchStartX = 0f
    private var touchStartY = 0f
    private var ignoreCurrentGesture = false

    private var imageBitmap: Bitmap? = null
    private var secondImageBitmap: Bitmap? = null
    private var isLoadFailed = false
    private var isSecondLoadFailed = false

    /** The URL each cached bitmap came from, so a re-bind reloads a new page. */
    private var loadedUrl: String? = null
    private var loadedSecondUrl: String? = null

    /**
     * Retires an in-flight load when the page moves on.
     *
     * The two halves are tracked separately because they are loaded
     * independently; one counter would make the second request invalidate the
     * first one's result.
     */
    private var firstRequestGeneration = 0
    private var secondRequestGeneration = 0

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    )

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        // All gestures are handled here; the reader-wide tap/swipe policy lives in
        // PageView, which forwards to this view only while an image page is up.
        handleReaderTouchEvent(event)
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun handleReaderTouchEvent(event: MotionEvent) {
        imageRect = getBitmapRectF(imageBitmap, 0f, pageWidth())
        secondImageRect = if (mDoublePageMode) {
            getBitmapRectF(secondImageBitmap, pageWidth(), pageWidth())
        } else {
            null
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touchStartX = event.x
                touchStartY = event.y
                ignoreCurrentGesture = false
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                ignoreCurrentGesture = true
            }

            MotionEvent.ACTION_UP -> {
                if (!ignoreCurrentGesture) {
                    handleGesture(event.x - touchStartX, event.y - touchStartY)
                }
                ignoreCurrentGesture = false
            }

            MotionEvent.ACTION_CANCEL -> {
                ignoreCurrentGesture = false
            }
        }
    }

    /** The width one page occupies: half the view in double-page mode. */
    private fun pageWidth(): Float = if (mDoublePageMode) width / 2f else width.toFloat()

    private fun handleGesture(deltaX: Float, deltaY: Float) {
        if (touchJudge.isTap(deltaX, deltaY)) {
            performClick()
            when (touchJudge.tapArea(touchStartX, width)) {
                ReaderTapArea.Previous -> mOnPreviousPage()
                ReaderTapArea.Center -> handleCenterTap()
                ReaderTapArea.Next -> mOnNextPage()
            }
        } else if (touchJudge.isHorizontalSwipe(deltaX, deltaY)) {
            if (deltaX < 0) mOnNextPage() else mOnPreviousPage()
        }
    }

    private fun handleCenterTap() {
        val onSecondHalf = secondImageRect?.contains(touchStartX, touchStartY) == true
        when {
            // Each half opens its own illustration, so a spread's right-hand
            // image is not shadowed by the left one.
            onSecondHalf -> mSecondImageurl?.let { mImageClick(it) }
            imageRect?.contains(touchStartX, touchStartY) == true ->
                mImageurl?.let { mImageClick(it) }
            // Nothing drawn yet: the tap means "open this page's illustration".
            imageBitmap == null && secondImageBitmap == null ->
                mImageurl?.let { mImageClick(it) }
            else -> mOnCenterClick?.onCenterClick()
        }
    }

    override fun onDraw(canvas: Canvas) {
        mPaint.color = mTextColor                             //字体颜色
        mPaint.typeface = mTxtFontType
        mPaint.textSize = getFooterTextSize()
        drawPageBackground(canvas, mPageBackground, mBgColor)

        //底部右下角绘制：章节相关信息    格式为:   第 XXX 章节 YYY章节名  ：  n / 该章节总共页数
        val pageText = if (mDoublePageMode && mSecondPageNum > 0) {
            "${if (mPageNum > mMaxPageNum) 0 else mPageNum}-$mSecondPageNum/$mMaxPageNum"
        } else {
            "${if (mPageNum > mMaxPageNum) 0 else mPageNum}/$mMaxPageNum"
        }
        drawFooter(canvas, pageText)

        val pageWidth = if (mDoublePageMode) width / 2f else width.toFloat()
        drawImage(canvas, imageBitmap, isLoadFailed, 0f, pageWidth)
        // An odd-length illustration section ends with an empty right half; that
        // is a blank page, not a failed load, so no message is drawn there.
        if (mDoublePageMode && mSecondImageurl != null) {
            drawImage(canvas, secondImageBitmap, isSecondLoadFailed, pageWidth, pageWidth)
        }
        // The hit rects follow whatever was actually drawn.
        imageRect = getBitmapRectF(imageBitmap, 0f, pageWidth)
        secondImageRect = if (mDoublePageMode && mSecondImageurl != null) {
            getBitmapRectF(secondImageBitmap, pageWidth, pageWidth)
        } else {
            null
        }
    }

    /**
     * Paints one illustration, or its loading/failed message, into the
     * [pageLeft]..[pageLeft]+[pageWidth] half of the view.
     *
     * The bitmap is drawn with a matrix instead of [super.onDraw] because the
     * image view spans the whole viewport while each half is its own page.
     */
    private fun drawImage(
        canvas: Canvas,
        bitmap: Bitmap?,
        failed: Boolean,
        pageLeft: Float,
        pageWidth: Float
    ) {
        val rect = getBitmapRectF(bitmap, pageLeft, pageWidth)
        if (rect == null) {
            val message = context.getString(
                if (failed) R.string.image_load_failed else R.string.image_loading
            )
            val fontMetrics = mPaint.fontMetrics
            val textHeight = fontMetrics.top - fontMetrics.bottom
            // Set the body text size before measuring, otherwise the message is
            // centred using the footer metrics and drifts off centre.
            mPaint.textSize = mTextSize
            val textWidth = mPaint.measureText(message)
            val pivotX = pageLeft + (pageWidth - textWidth) / 2
            val pivotY = (height - textHeight) / 2
            canvas.drawText(message, pivotX, pivotY, mPaint)
            mPaint.textSize = getFooterTextSize()
            return
        }
        canvas.drawBitmap(bitmap!!, null, rect, mPaint)
    }

    private fun drawFooter(canvas: Canvas, pageText: String) {
        val footerMargin = getMarginLeft().coerceAtLeast(16f)
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

    @SuppressLint("CheckResult")
    fun updateImage() {
        // Each half gets its own request id: a load that finishes after the page
        // has moved on must not install its bitmap over the new page's
        // illustration. The two halves are independent, so the ids are separate.
        val firstRequest = ++firstRequestGeneration
        val firstUrl = mImageurl
        loadImage(
            url = firstUrl,
            alreadyLoaded = loadedUrl == firstUrl && imageBitmap != null,
            onReady = { bitmap ->
                if (firstRequest != firstRequestGeneration || mImageurl != firstUrl) {
                    return@loadImage
                }
                loadedUrl = firstUrl
                imageBitmap = bitmap
                isLoadFailed = false
                invalidate()
            },
            onFailed = {
                if (firstRequest != firstRequestGeneration || mImageurl != firstUrl) {
                    return@loadImage
                }
                loadedUrl = firstUrl
                imageBitmap = null
                isLoadFailed = true
                invalidate()
            }
        )

        // The right-hand illustration only exists in double-page mode; in
        // single-page mode a stale bitmap from a previous spread must not linger.
        if (mDoublePageMode && mSecondImageurl != null) {
            val secondRequest = ++secondRequestGeneration
            val secondUrl = mSecondImageurl
            loadImage(
                url = secondUrl,
                alreadyLoaded = loadedSecondUrl == secondUrl && secondImageBitmap != null,
                onReady = { bitmap ->
                    if (secondRequest != secondRequestGeneration || mSecondImageurl != secondUrl) {
                        return@loadImage
                    }
                    loadedSecondUrl = secondUrl
                    secondImageBitmap = bitmap
                    isSecondLoadFailed = false
                    invalidate()
                },
                onFailed = {
                    if (secondRequest != secondRequestGeneration || mSecondImageurl != secondUrl) {
                        return@loadImage
                    }
                    loadedSecondUrl = secondUrl
                    secondImageBitmap = null
                    isSecondLoadFailed = true
                    invalidate()
                }
            )
        } else {
            // Bumping the generation retires any in-flight right-half load.
            secondRequestGeneration++
            secondImageBitmap = null
            loadedSecondUrl = null
            isSecondLoadFailed = false
        }
    }

    /**
     * Loads one illustration.
     *
     * The target is view-independent on purpose: loading two illustrations
     * through `.into(this)` would make Glide cancel the first request when the
     * second one starts, because both would claim the same view target. It also
     * means the drawable Glide decodes is never installed on the view — [onDraw]
     * paints the halves itself.
     *
     * Each decode is bounded to one page's box, so a double-page spread does not
     * decode two full-viewport bitmaps.
     */
    private fun loadImage(
        url: String?,
        alreadyLoaded: Boolean,
        onReady: (Bitmap) -> Unit,
        onFailed: () -> Unit
    ) {
        // A page with no URL is empty, never "still loading": it must clear any
        // bitmap left over from the previous spread.
        if (url.isNullOrBlank()) {
            onFailed()
            return
        }
        if (alreadyLoaded) return

        // Glide rejects a 0x0 target, and this can run before the first layout.
        // SIZE_ORIGINAL is the documented "no override" value.
        val targetWidth = pageWidth().toInt().takeIf { it > 0 } ?: Target.SIZE_ORIGINAL
        val targetHeight = height.takeIf { it > 0 } ?: Target.SIZE_ORIGINAL

        Glide.with(this)
            .asBitmap()
            .load(url)
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .into(object : CustomTarget<Bitmap>(targetWidth, targetHeight) {
                override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) =
                    onReady(resource)

                override fun onLoadCleared(placeholder: Drawable?) = Unit

                override fun onLoadFailed(errorDrawable: Drawable?) = onFailed()
            })
    }

    /**
     * Where one illustration lands inside the half of the view that [pageLeft]
     * and [pageWidth] describe.
     *
     * The scale type is resolved against that half, not the whole view, so two
     * side-by-side illustrations are fitted independently.
     */
    private fun getBitmapRectF(bitmap: Bitmap?, pageLeft: Float, pageWidth: Float): RectF? {
        if (bitmap == null) return null

        val viewWidth = pageWidth
        val viewHeight = height.toFloat()

        val bitmapWidth = bitmap.width.toFloat()
        val bitmapHeight = bitmap.height.toFloat()

        // FIT_XY is the one mode that scales the two axes independently, so it
        // cannot share the single-scale path below.
        if (scaleType == ScaleType.FIT_XY) {
            return RectF(pageLeft, 0f, pageLeft + viewWidth, viewHeight)
        }

        val scale: Float
        val dx: Float
        val dy: Float

        when (scaleType) {
            ScaleType.CENTER_CROP -> {
                if (bitmapWidth * viewHeight > viewWidth * bitmapHeight) {
                    scale = viewHeight / bitmapHeight
                    dx = (viewWidth - bitmapWidth * scale) * 0.5f
                    dy = 0f
                } else {
                    scale = viewWidth / bitmapWidth
                    dx = 0f
                    dy = (viewHeight - bitmapHeight * scale) * 0.5f
                }
            }

            ScaleType.CENTER_INSIDE -> {
                if (bitmapWidth <= viewWidth && bitmapHeight <= viewHeight) {
                    scale = 1f
                    dx = (viewWidth - bitmapWidth) * 0.5f
                    dy = (viewHeight - bitmapHeight) * 0.5f
                } else {
                    if (bitmapWidth * viewHeight > viewWidth * bitmapHeight) {
                        scale = viewWidth / bitmapWidth
                        dx = 0f
                        dy = (viewHeight - bitmapHeight * scale) * 0.5f
                    } else {
                        scale = viewHeight / bitmapHeight
                        dx = (viewWidth - bitmapWidth * scale) * 0.5f
                        dy = 0f
                    }
                }
            }

            ScaleType.FIT_CENTER, ScaleType.CENTER -> {
                if (bitmapWidth * viewHeight > viewWidth * bitmapHeight) {
                    scale = viewWidth / bitmapWidth
                    dx = 0f
                    dy = (viewHeight - bitmapHeight * scale) * 0.5f
                } else {
                    scale = viewHeight / bitmapHeight
                    dx = (viewWidth - bitmapWidth * scale) * 0.5f
                    dy = 0f
                }
            }

            else -> {
                // 默认情况（例如ScaleType.FIT_START, FIT_END等）
                if (bitmapWidth * viewHeight > viewWidth * bitmapHeight) {
                    scale = viewWidth / bitmapWidth
                    dx = 0f
                    dy = (viewHeight - bitmapHeight * scale) * 0.5f
                } else {
                    scale = viewHeight / bitmapHeight
                    dx = (viewWidth - bitmapWidth * scale) * 0.5f
                    dy = 0f
                }
            }
        }

        return RectF(
            pageLeft + dx,
            dy,
            pageLeft + dx + bitmapWidth * scale,
            dy + bitmapHeight * scale
        )
    }

    //左边距
    private fun getMarginLeft(): Float {
        val count = getTextWidth() / mTextSize.toInt()   //一行字数
        return (width - count * mTextSize) / 2
    }

    //正文区域宽度
    private fun getTextWidth(): Int = (width * 0.96f).toInt()

    private fun getFooterTextSize(): Float = mBottomTextSize.coerceAtMost(mTextSize * 0.55f)

    private fun getFooterBottomPadding(): Float =
        (getFooterTextSize() * 0.55f).coerceAtLeast(12f)
}
