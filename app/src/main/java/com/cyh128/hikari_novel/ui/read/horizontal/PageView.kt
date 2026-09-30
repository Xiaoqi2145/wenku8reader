package com.cyh128.hikari_novel.ui.read.horizontal

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.text.TextPaint
import android.util.AttributeSet
import android.view.MotionEvent
import android.widget.ViewFlipper
import androidx.core.view.size
import com.cyh128.hikari_novel.R
import com.cyh128.hikari_novel.data.model.HorizontalRead
import com.cyh128.hikari_novel.util.ReaderTapArea
import com.cyh128.hikari_novel.util.ReaderTouchJudge
import kotlin.reflect.KProperty

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
 * Modified by 15dd on 2024 - 增加了图片支持，删除了用不到的函数以及变量，优化了手势检测，优化了分页算法
 */

class PageView: ViewFlipper, IPageView {
    override var pageNum: Int by InvalidateAfterSet(1)                  //页数
    override var backgroundcolor: Int by InvalidateAfterSet(Color.WHITE)    //背景颜色

    /**
     * Reader page background (reading colour + GalIsland grid) handed to every
     * page child. Null keeps the flat [backgroundcolor] fill. See [drawPageBackground].
     */
    var pageBackground: android.graphics.drawable.Drawable? by InvalidateAfterSet(null)
    override var textColor: Int by InvalidateAfterSet(Color.BLACK)             //字体颜色
    override var txtFontType: Typeface by InvalidateAfterSet(Typeface.DEFAULT)  //正文字体类型
    override var rowSpace: Float by InvalidateAfterSet(1f)          //行距
    override var textSize: Float by InvalidateAfterSet(55f)               //正文部分默认画笔的大小
    override var bottomTextSize: Float by InvalidateAfterSet(45f)         //底部部分默认画笔的大小
    override var content: HorizontalRead by InvalidateAfterSet(HorizontalRead.empty()) //一个未分割章节,格式：章节名|正文
    /**
     * Page turn animation mode.
     * Values: "none", "sliding", "mimical"
     * See docs/reader-koodo-alignment.md P1-1.
     */
    var switchAnimation: String = "none"                             //翻页动画
    var title: String = ""                                           //章节名称

    var maxPageNum = 0                        //最大页数
    var pageFlag = 0                          //0刚进入view，1表示目录跳转，2表示下一页，3表示上一页

    var textArray = mutableListOf<MutableList<PageLine>>()
    var imageArray = listOf<String>()
    var doublePageEnabled: Boolean by InvalidateAfterSet(false)

    // ===== P0-3: Typography =====

    /** Paragraph spacing as a multiple of the font size, 0–4, step 0.1. */
    var paragraphSpacing: Float by InvalidateAfterSet(0f)

    /** Letter spacing in px, 0–20. Converted to em for the text paint. */
    var letterSpacing: Float by InvalidateAfterSet(0f)

    /**
     * Text alignment: "left", "justify" or "right".
     *
     * Named `textAlignMode` because [android.view.View.getTextAlignment] already
     * occupies `textAlignment` with an Int.
     */
    var textAlignMode: String by InvalidateAfterSet("left")

    /**
     * First-line indent in characters: 0 or 2.
     *
     * The default is 2 because the horizontal reader has always indented the
     * first line by two characters; exposing the setting must not change the
     * rendering for existing users.
     */
    var firstLineIndent: Int by InvalidateAfterSet(2)

    private val readerContentBottomInset by lazy {
        resources.getDimension(R.dimen.gi_reader_content_bottom_inset)
    }

    private lateinit var imageClick: (String) -> Unit

    private val touchJudge by lazy { ReaderTouchJudge(context) }
    private var touchStartX = 0f
    private var touchStartY = 0f
    private var ignoreCurrentGesture = false

    private var contentTopInset = 0
    private var contentBottomInset = 0

    /** Set while a re-pagination is queued, so repeats coalesce into one pass. */
    private var pendingRepaginate = false

    override var onNextChapter: IPageView.OnNextChapter? = null          //下一章
    override var onPreviousChapter: IPageView.OnPreviousChapter? = null  //上一章
    override var onCenterClick: IPageView.OnCenterClick? = null          //点击view中间部分
    override var onPageChange: IPageView.OnPageChange? = null            //当翻页时调用，向前向后翻页，同一章内翻页，翻至其他章节都会调用

    constructor(context: Context) : super(context) {
        init()
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        init()
    }

    private fun init() { //界面初始化
        clipToPadding = true
        showLoadingTip()
        showNext()
    }

    /**
     * Keeps the rendered page inside the measured reader chrome bounds. The
     * child page views inherit these paddings, so text, images and loading
     * states share one safe region without duplicating inset math.
     */
    fun setContentInsets(top: Int, bottom: Int) {
        val safeTop = top.coerceAtLeast(0)
        val safeBottom = bottom.coerceAtLeast(0)
        if (safeTop == contentTopInset && safeBottom == contentBottomInset) return
        contentTopInset = safeTop
        contentBottomInset = safeBottom
        setPadding(paddingLeft, safeTop, paddingRight, safeBottom)
        // The pages are laid out inside this padding, so a new inset changes how
        // much text fits. Without re-splitting here, showing the chrome left the
        // old, taller pages in place and the first lines were drawn off the top.
        repaginateSoon()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // Split-screen resize changes the viewport without changing the content,
        // so the chapter has to be re-cut for the new page size. (Rotation
        // recreates the activity, which re-sets the content and paginates there.)
        if (oldw > 0 && oldh > 0 && (w != oldw || h != oldh)) repaginateSoon()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        // Gestures are handled in one place only: [handleReaderTouchEvent]
        // forwards image pages to their own view and classifies the rest with
        // ReaderTouchJudge. The previous second tap/swipe implementation below it
        // was unreachable and has been removed (see
        // docs/reader-gesture-mode-audit.md P2-1).
        handleReaderTouchEvent(event)
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun handleReaderTouchEvent(event: MotionEvent) {
        if (currentView is PageImage) {
            currentView.dispatchTouchEvent(event)
            return
        }
        if (currentView is PageLoading) return

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

    private fun handleGesture(deltaX: Float, deltaY: Float) {
        if (touchJudge.isTap(deltaX, deltaY)) {
            performClick()
            when (touchJudge.tapArea(touchStartX, width)) {
                ReaderTapArea.Previous -> pageToPrevious()
                ReaderTapArea.Center -> {
                    inAnimation = null
                    outAnimation = null
                    onCenterClick?.onCenterClick()
                    pageFlag = 1
                }
                ReaderTapArea.Next -> pageToNext()
            }
        } else if (touchJudge.isHorizontalSwipe(deltaX, deltaY)) {
            if (deltaX < 0) pageToNext() else pageToPrevious()
        }
    }

    private fun isDoublePageActive(): Boolean =
        doublePageEnabled &&
            resources.configuration.screenWidthDp >= 600 &&
            width > height

    /** The spread the reader is on: [pageNum] plus its right-hand page, if any. */
    private fun currentSpread(): HorizontalPageGeometry.Spread =
        HorizontalPageGeometry.spreadAt(
            page = pageNum,
            textPageCount = textArray.size,
            totalPages = maxPageNum,
            doublePage = isDoublePageActive()
        )

    private fun getForwardPageStep(): Int =
        HorizontalPageGeometry.forwardStep(
            page = pageNum,
            textPageCount = textArray.size,
            totalPages = maxPageNum,
            doublePage = isDoublePageActive()
        )

    private fun getPreviousPageStart(): Int =
        HorizontalPageGeometry.previousStart(pageNum, textArray.size, isDoublePageActive())

    private fun normalizeDoublePageStart(page: Int): Int =
        HorizontalPageGeometry.spreadStartContaining(page, textArray.size, isDoublePageActive())

    fun pageToNext() {
        applyPageAnimation(true)
        pageFlag = 2
        val nextPage = pageNum + getForwardPageStep()
        if (nextPage <= maxPageNum) {
            pageNum = nextPage
        } else {
            onNextChapter?.onNextChapter()
        }
    }

    fun pageToPrevious() {
        applyPageAnimation(false)
        pageFlag = 3
        if (pageNum < 2) {
            onPreviousChapter?.onPreviousChapter()
        } else {
            val previousPage = getPreviousPageStart()
            if (previousPage < 1) {
                onPreviousChapter?.onPreviousChapter()
            } else {
                pageNum = previousPage
            }
        }
    }

    /** Apply the configured page turn animation based on the current mode. */
    private fun applyPageAnimation(toNext: Boolean) {
        when (switchAnimation) {
            "sliding" -> {
                if (toNext) {
                    setInAnimation(context, R.anim.slide_in_right)
                    setOutAnimation(context, R.anim.slide_out_left)
                } else {
                    setInAnimation(context, R.anim.slide_in_left)
                    setOutAnimation(context, R.anim.slide_out_right)
                }
            }
            "mimical" -> {
                // Mimical (mimetic) animation uses flip-style transitions
                if (toNext) {
                    setInAnimation(context, R.anim.slide_in_right)
                    setOutAnimation(context, R.anim.slide_out_left)
                } else {
                    setInAnimation(context, R.anim.slide_in_left)
                    setOutAnimation(context, R.anim.slide_out_right)
                }
            }
            else -> {
                inAnimation = null
                outAnimation = null
            }
        }
    }

    fun showLoadingTip() {
        if (this.currentView !is PageLoading) {
            this.removeAllViews()
            PageLoading(context).apply {
                mBgColor = backgroundcolor
                mPageBackground = pageBackground
                mTextSize = textSize
                mTextColor = textColor
                mTxtFontType = txtFontType
                this@PageView.addView(this)
            }
        }
    }

    fun setImageClickListener(listener: (String) -> Unit) {
        imageClick = listener
    }

    private fun displayView() {
        initPage()

        if (pageNum <= textArray.size) {
            val pageTextAnother = if (displayedChild == 0) 1 else 0
            (getChildAt(pageTextAnother) as PageText).apply {
                mBgColor = backgroundcolor
                mPageBackground = pageBackground
                if (maxPageNum > 0) {
                    if (pageNum > textArray.size) pageNum = textArray.size
                    if (pageNum == 0) pageNum = 1
                    mTextArray = textArray[pageNum - 1]
                    // A text page only ever pairs with the next text page, so an
                    // odd-length text section ends with a half-empty spread
                    // instead of pairing its last page with the first image.
                    val secondPageNum = currentSpread().second ?: 0
                    mSecondTextArray = if (secondPageNum > 0) {
                        textArray[secondPageNum - 1]
                    } else {
                        null
                    }
                    mSecondPageNum = secondPageNum
                }
                mRowSpace = rowSpace
                mTextSize = textSize
                mPageNum = pageNum
                mMaxPageNum = maxPageNum
                mDoublePageMode = isDoublePageActive()
                mTextColor = textColor
                mTitle = title
                mTxtFontType = txtFontType
                mBottomTextSize = bottomTextSize
                mLetterSpacing = letterSpacing
                mTextAlignment = textAlignMode
            }
            displayedChild = pageTextAnother
        } else if (textArray.size < pageNum && pageNum <= maxPageNum) {
            val pageImageAnother = pageNum - textArray.size + 1
            val imageIndex = pageNum - textArray.size - 1
            val imageUrl = imageArray[imageIndex].trim()
            // One illustration page renders its own image plus, in double-page
            // mode, the next one; both halves are fitted independently.
            val secondPageNum = currentSpread().second ?: 0
            val secondImageUrl = if (secondPageNum > 0) {
                imageArray.getOrNull(secondPageNum - textArray.size - 1)?.trim()
            } else {
                null
            }
            (getChildAt(pageImageAnother) as PageImage).apply {
                mBgColor = backgroundcolor
                mPageBackground = pageBackground
                mImageurl = imageUrl
                mDoublePageMode = isDoublePageActive()
                mSecondImageurl = secondImageUrl
                mRowSpace = rowSpace
                mTextSize = textSize
                mPageNum = pageNum
                mSecondPageNum = secondPageNum
                mMaxPageNum = maxPageNum
                mTextColor = textColor
                mTitle = title
                mTxtFontType = txtFontType
                mBottomTextSize = bottomTextSize
                mImageClick = imageClick
                mOnCenterClick = onCenterClick
                mOnNextPage = { pageToNext() }
                mOnPreviousPage = { pageToPrevious() }
                updateImage()
            }
            displayedChild = pageImageAnother
        }
    }

    private fun initPage() {
        if (this.currentView is PageLoading) {
            this.removeAllViews()
            repeat(TEXT_PAGE_CHILDREN) {
                addView(newTextPage())
            }
        }

        // One image child per illustration. This has to *top up* rather than run
        // once: a later chapter can carry more illustrations than the first one
        // that had any, and displayView would otherwise index a missing child.
        if (this.size >= TEXT_PAGE_CHILDREN) {
            val missing = imageArray.size - (this.size - TEXT_PAGE_CHILDREN)
            repeat(missing.coerceAtLeast(0)) {
                addView(newImagePage())
            }
        }
    }

    private fun newTextPage() = PageText(context).apply {
        mBgColor = backgroundcolor
        mPageBackground = pageBackground
        mTextSize = textSize
        mBottomTextSize = bottomTextSize
        mRowSpace = rowSpace
        mPageNum = 0
        mMaxPageNum = 0
        mTextColor = textColor
        mTxtFontType = txtFontType
    }

    private fun newImagePage() = PageImage(context).apply {
        mBgColor = backgroundcolor
        mPageBackground = pageBackground
        mTextSize = textSize
        mBottomTextSize = bottomTextSize
        mRowSpace = rowSpace
        mPageNum = 0
        mMaxPageNum = 0
        mTextColor = textColor
        mTxtFontType = txtFontType
    }

    //正文区域宽度
    private fun getTextWidth(): Int {
        val pageWidth = if (isDoublePageActive()) width / 2f else width.toFloat()
        return (pageWidth * if (isDoublePageActive()) 0.90f else 0.96f).toInt()
    }

    //正文区域高度
    private fun getTextHeight(): Int = (getContentHeight() * 0.94f).toInt()

    private fun getContentHeight(): Float =
        HorizontalPageGeometry.contentHeight(
            viewportHeight = height,
            // The page child is laid out inside this padding, so the paginator
            // has to budget for the same box the renderer draws into.
            paddingTop = paddingTop,
            paddingBottom = paddingBottom,
            bottomInset = readerContentBottomInset,
            footerReserve = getFooterReservedHeight(),
            minimum = textSize * rowSpace
        )

    /**
     * The reading position as a 0..1 fraction of the chapter.
     *
     * Re-pagination must restore a *fraction*, never the raw page number: the
     * same page number points at different text once the pages are re-cut.
     */
    private fun currentFraction(): Float {
        if (maxPageNum <= 0 || pageNum <= 0) return 0f
        return (pageNum.toFloat() / maxPageNum).coerceIn(0f, 1f)
    }

    /**
     * Re-paginates once the view is no longer being laid out.
     *
     * [setContentInsets] and [onSizeChanged] can both fire inside a layout pass,
     * where re-splitting and reassigning `pageNum` would mutate the page children
     * mid-traversal. Posting keeps that work on the next frame instead.
     */
    private fun repaginateSoon() {
        if (pendingRepaginate) return
        pendingRepaginate = true
        post {
            pendingRepaginate = false
            repaginate(currentFraction())
        }
    }

    /**
     * Re-cuts the chapter for the current geometry and keeps the reader on the
     * same text.
     *
     * @param fraction the reading position to restore, 0..1.
     */
    private fun repaginate(fraction: Float) {
        if (width < 1 || height < 1) return
        // Before the chapter arrives there is nothing to cut, and running the
        // paginator would replace the loading page with an empty text page.
        if (content.content.isEmpty()) return
        textArray = splitContent(content.content)
        imageArray = content.imageList
        maxPageNum = if (content.content.length <= title.length + 1) {
            0
        } else {
            textArray.size + imageArray.size
        }
        // Writing pageNum runs the property setter, which notifies onPageChange
        // and repaints. When the position happens to land on the same page
        // number there is no notification, so the repaint is done here instead —
        // the page's *content* changed even though its number did not.
        val restored = HorizontalPageGeometry.pageForFraction(fraction, maxPageNum)
        if (restored != pageNum) pageNum = restored else displayView()
    }

    private fun getFooterTextSize(): Float = bottomTextSize.coerceAtMost(textSize * 0.55f)

    private fun getFooterReservedHeight(): Float =
        (getFooterTextSize() * 2.2f).coerceAtLeast(textSize * 1.2f)

    private fun splitContent(chapterContent: String?): MutableList<MutableList<PageLine>> {
        if (chapterContent.isNullOrEmpty() || getTextWidth() == 0) return mutableListOf()
        title = content.title
        val textPaint = TextPaint().apply {
            textSize = this@PageView.textSize
            isAntiAlias = true
            // Letter spacing must be applied while measuring, otherwise the
            // split lines would overflow the page once drawn.
            letterSpacing = emFromPx(this@PageView.letterSpacing, this@PageView.textSize)
        }

        val textWidth = getTextWidth() //可用的文字宽度
        val textHeight = getTextHeight() //可用的文字高度
        val lineHeight = this@PageView.textSize * rowSpace
        val paragraphGap = paragraphSpacing * this@PageView.textSize
        val indentWidth = firstLineIndent.coerceAtLeast(0) * this@PageView.textSize

        val lines = mutableListOf<PageLine>()

        //预处理内容，去除多余空行
        preprocessContent(chapterContent).forEach { paragraph ->
            if (paragraph.isEmpty()) {
                lines.add(PageLine("", isParagraphEnd = true)) //空行直接添加
                return@forEach
            }

            var remainingText = paragraph
            var isFirstLine = true //标记段落的首行

            while (remainingText.isNotEmpty()) {
                var lineWidth = if (isFirstLine) indentWidth else 0f
                var charIndex = 0

                //逐字符测量宽度
                while (charIndex < remainingText.length) {
                    val charWidth = textPaint.measureText(remainingText[charIndex].toString())
                    if (lineWidth + charWidth > textWidth) break //超出宽度，停止测量
                    lineWidth += charWidth
                    charIndex++
                }

                // A single glyph wider than the page must still advance, or the
                // loop would never terminate.
                if (charIndex == 0) charIndex = 1

                val lineText = remainingText.substring(0, charIndex)
                remainingText = remainingText.substring(charIndex)
                lines.add(
                    PageLine(
                        text = lineText,
                        indent = if (isFirstLine) indentWidth else 0f,
                        // The very first line of the chapter sits under the top
                        // margin, so it gets no paragraph gap.
                        spacingBefore = if (isFirstLine && lines.isNotEmpty()) paragraphGap else 0f,
                        isParagraphEnd = remainingText.isEmpty()
                    )
                )
                isFirstLine = false //处理完首行后，标记为非首行
            }
        }

        // 按行高累积分页：段间距让每行高度不再一致，不能再按固定行数切页
        val result = mutableListOf<MutableList<PageLine>>()
        var currentPage = mutableListOf<PageLine>()
        var usedHeight = 0f
        for (line in lines) {
            // A page break swallows the paragraph gap: the new page already
            // starts at the top margin.
            val spacing = if (currentPage.isEmpty()) 0f else line.spacingBefore
            if (currentPage.isNotEmpty() && usedHeight + lineHeight + spacing > textHeight) {
                result.add(currentPage)
                currentPage = mutableListOf()
                currentPage.add(line.copy(spacingBefore = 0f))
                usedHeight = lineHeight
            } else {
                currentPage.add(if (spacing == line.spacingBefore) line else line.copy(spacingBefore = 0f))
                usedHeight += lineHeight + spacing
            }
        }
        if (currentPage.isNotEmpty()) result.add(currentPage)

        return result
    }

    /** [android.graphics.Paint.setLetterSpacing] is in em, the setting is in px. */
    private fun emFromPx(px: Float, sizePx: Float): Float =
        if (sizePx <= 0f) 0f else px / sizePx

    private companion object {
        /** The two text page children every chapter needs, one per spread half. */
        const val TEXT_PAGE_CHILDREN = 2
    }

    private fun preprocessContent(content: String): List<String> {
        val lines = content.lines() //按行分割内容
        val processedLines = mutableListOf<String>()

        var consecutiveEmptyLines = 0
        for (line in lines) {
            if (line.isBlank()) {
                //空行计数
                consecutiveEmptyLines++
            } else {
                //遇到正常内容时，重置空行计数
                consecutiveEmptyLines = 0
                processedLines.add(line.trimEnd()) //去除行尾多余空格
            }

            //如果连续空行小于 1 行，保留一行
            if (consecutiveEmptyLines == 1) {
                processedLines.add("")
            }
        }

        //去除开头和结尾的空行
        return processedLines.dropWhile { it.isBlank() }.dropLastWhile { it.isBlank() }
    }


    inner class InvalidateAfterSet<T>(@Volatile var value: T) {
        operator fun getValue(thisRef: Any?, property: KProperty<*>): T = value

        @Synchronized
        operator fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
            this.value = value
            when (property.name) {
                "rowSpace", "textSize", "bottomTextSize", "doublePageEnabled",
                "paragraphSpacing", "letterSpacing", "firstLineIndent" -> {
                    if (width < 1) return
                    // Capture the position before the re-cut; the page count
                    // changes with the typography.
                    repaginate(currentFraction())
                }

                "content" -> {
                    if (width < 1) return
                    textArray = splitContent(content.content)
                    imageArray = content.imageList
                    maxPageNum = if (content.content.length <= title.length + 1
                        || content.content.isEmpty()
                    ) 0 else textArray.size + imageArray.size
                    pageNum = when (pageFlag) {
                        0 -> if (maxPageNum == 0) 0 else if (pageNum == 0) 1 else pageNum
                        1, 2 -> if (maxPageNum == 0) 0 else 1
                        3 -> maxPageNum
                        else -> 1
                    }

                }
                "pageNum" -> {
                    val normalizedPage = normalizeDoublePageStart(pageNum)
                    if (normalizedPage != pageNum) {
                        @Suppress("UNCHECKED_CAST")
                        this.value = normalizedPage as T
                    }
                    onPageChange?.onPageChange(pageNum)
                    displayView()
                }

                else -> {
                    displayView()
                }  //刷新view
            }
        }
    }
}
