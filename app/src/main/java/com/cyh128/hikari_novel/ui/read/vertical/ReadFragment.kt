package com.cyh128.hikari_novel.ui.read.vertical

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.text.Spannable
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.view.View
import android.view.ViewTreeObserver
import androidx.core.graphics.ColorUtils
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.cyh128.hikari_novel.R
import com.cyh128.hikari_novel.base.BaseFragment
import com.cyh128.hikari_novel.data.model.CachedChapter
import com.cyh128.hikari_novel.data.model.Event
import com.cyh128.hikari_novel.data.model.ReaderAnchor
import com.cyh128.hikari_novel.databinding.FragmentVerticalReadBinding
import com.cyh128.hikari_novel.reader.domain.ReaderPosition
import com.cyh128.hikari_novel.reader.domain.ReaderInsetCompensation
import com.cyh128.hikari_novel.ui.read.common.ReaderChromeTheme
import com.cyh128.hikari_novel.ui.read.common.ReaderGridDrawable
import com.cyh128.hikari_novel.util.ReaderTouchJudge
import com.cyh128.hikari_novel.util.ReaderColorResolver
import com.cyh128.hikari_novel.util.ReaderHaptics
import com.cyh128.hikari_novel.util.startActivity
import com.cyh128.hikari_novel.data.source.local.mmkv.AppConfig
import javax.inject.Inject
import com.drake.channel.receiveEvent
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch

/**
 * Vertical reader UI shell.
 *
 * Chapter composition, mapping, progress sampling, restoration, and touch
 * classification live in the focused collaborators in this package. This
 * Fragment only wires their callbacks to the existing Activity/ViewModel APIs.
 */
@AndroidEntryPoint
class ReadFragment : BaseFragment<FragmentVerticalReadBinding>() {
    private val viewModel by lazy { ViewModelProvider(requireActivity())[ReadViewModel::class.java] }
    private var readAdapter: ReadAdapter? = null
    private var readerController: VerticalReaderController? = null
    private var contentRenderer: VerticalContentRenderer? = null
    private var progressReporter: VerticalProgressReporter? = null
    private var scrollRestorer: VerticalScrollRestorer? = null
    private var touchHandler: VerticalReaderTouchHandler? = null
    private val renderedChapterCids = mutableSetOf<String>()

    /** 震动反馈开关（设置页可关）；读取时机在每次手势触发时，改动即时生效 */
    @Inject
    lateinit var appConfig: AppConfig

    /**
     * Scroll compensation owed for a pending inset change, in scroll-view pixels.
     *
     * Held as a single slot rather than a queue: toggling the menu twice in quick
     * succession used to stack two `post` runnables, and the second captured a
     * scrollY that the first had not compensated yet, which threw the text off by
     * a toolbar height.
     */
    private var pendingScrollCompensation: Int? = null

    /**
     * True while scrolling to compensate an inset change.
     *
     * That compensation restores the *same* visual position, so letting it reach
     * `progressReporter` would re-derive the chapter anchor from a position we are
     * in the middle of restoring — which is what made the status line percentage
     * flicker when the menu was toggled.
     */
    private var suppressScrollReport = false

    /**
     * Applies the pending compensation after layout but before the frame is drawn,
     * so the reflow and its compensation land in the same frame.
     *
     * Deferring to `post` instead drew one frame with the text displaced by a
     * toolbar height — the flicker this exists to prevent.
     */
    private val insetCompensator = ViewTreeObserver.OnPreDrawListener {
        pendingScrollCompensation?.let { target ->
            pendingScrollCompensation = null
            suppressScrollReport = true
            binding.nsvFVRead.scrollTo(0, target.coerceAtLeast(0))
            suppressScrollReport = false
        }
        true
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Installed before the first inset pass, which may already owe a
        // compensation.
        binding.nsvFVRead.viewTreeObserver.addOnPreDrawListener(insetCompensator)

        (requireActivity() as? ReadActivity)?.applyReaderContentInsets()

        receiveEvent<Event>("event_vertical_read_fragment") { event ->
            when (event) {
                // Every typography setting is applied through one applier that
                // reads the persisted values, so the live preview can never
                // diverge from what a re-render produces. See P0-3.
                is Event.ChangeFontSizeEvent,
                is Event.ChangeLineSpacingEvent,
                is Event.ChangeParagraphSpacingEvent,
                is Event.ChangeLetterSpacingEvent,
                is Event.ChangeTextAlignmentEvent,
                is Event.ChangeFirstLineIndentEvent -> applyTypography()

                else -> Unit
            }
        }

        initView()
        initReaderCollaborators()
        initListener()
        restoreInitialPosition()
    }

    override fun onDestroyView() {
        val observer = binding.nsvFVRead.viewTreeObserver
        if (observer.isAlive) observer.removeOnPreDrawListener(insetCompensator)
        pendingScrollCompensation = null
        progressReporter?.flush()
        progressReporter?.cancel()
        touchHandler?.cancel()
        scrollRestorer?.clear()
        readAdapter = null
        readerController = null
        contentRenderer = null
        progressReporter = null
        scrollRestorer = null
        touchHandler = null
        super.onDestroyView()
    }

    private fun initView() {
        val controller = viewModel.verticalController()
        val renderer = VerticalContentRenderer()
        readerController = controller
        contentRenderer = renderer

        val existingSession = controller.loadedChapters
        val session = existingSession.ifEmpty {
            listOf(viewModel.currentCachedChapter())
        }
        val initialRender = if (existingSession.isEmpty()) {
            controller.acceptLoaded(session.first(), append = false)
        } else {
            controller.render(session.first())
                ?: error("Vertical session is missing its first chapter block")
        }

        readAdapter = ReadAdapter(
            session.flatMap { it.images }.toMutableList()
        ) { imageUrl ->
            startActivity<com.cyh128.hikari_novel.ui.other.PhotoViewActivity> {
                putExtra("url", imageUrl)
            }
        }

        renderer.setInitialText(binding.tvFVRead, initialRender)
        renderedChapterCids.clear()
        renderedChapterCids += session.first().ref.cid
        session.drop(1).forEach { chapter ->
            controller.render(chapter)?.let { render ->
                renderer.appendText(binding.tvFVRead, render)
            }
            renderedChapterCids += chapter.ref.cid
        }
        // Rendering a retained session completes any pending append request.
        controller.onChapterAppended()
        if (session.size > 1) {
            binding.tvFVReadEndTip.text = getString(R.string.continue_reading)
        }
        binding.rvFVRead.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = readAdapter
        }

        applyTypography()

        val resolver = colorResolver()
        applyReaderColors(resolver.textColorHex(), resolver.bgColorHex())
    }

    /** Single construction point for the reader colour resolver. */
    private fun colorResolver() = ReaderColorResolver(viewModel.readColorRepository(), requireContext())

    /**
     * Applies the persisted typography to the rendered session and keeps the
     * visible anchor stable. Paragraph spacing and first-line indent are spans
     * over the concatenated text, so they must be re-applied whenever new
     * chapter text is appended.
     */
    private fun applyTypography(preserveAnchor: Boolean = true) {
        val anchor = progressReporter?.latest()?.anchor
        VerticalTypographyApplier.apply(
            binding.tvFVRead,
            VerticalTypography.from(
                fontSizeSp = viewModel.getFontSize(),
                lineSpacingExtraPx = viewModel.getLineSpacing(),
                paragraphSpacingPx = viewModel.getParagraphSpacing(),
                letterSpacingPx = viewModel.getLetterSpacing(),
                textAlignment = viewModel.getTextAlignment(),
                firstLineIndentChars = viewModel.getFirstLineIndent()
            ),
            contentRenderer?.headingRanges().orEmpty()
        )
        if (preserveAnchor) anchor?.let { scrollRestorer?.restoreAfterLayout(it) }
    }

    private fun initReaderCollaborators() {
        val controller = readerController ?: return
        val saveScope = lifecycleScope

        progressReporter = VerticalProgressReporter(
            scrollView = binding.nsvFVRead,
            textView = binding.tvFVRead,
            controller = controller,
            scope = saveScope,
            onProgress = { progress ->
                viewModel.updateVerticalProgress(
                    position = ReaderPosition(progress.chapter, progress.anchor),
                    displayPercent = progress.displayPercent,
                    content = progress.content,
                    legacyLocation = progress.legacyLocation
                )
            },
            onPersist = { progress ->
                // A continuous session can report either the old or new block.
                // Persist the mapped ref/content pair, never the ViewModel's
                // transient loading chapter.
                viewModel.persistPosition(
                    position = ReaderPosition(progress.chapter, progress.anchor),
                    content = progress.content,
                    legacyLocation = progress.legacyLocation
                )
            }
        )

        scrollRestorer = VerticalScrollRestorer(
            scrollView = binding.nsvFVRead,
            textView = binding.tvFVRead,
            controller = controller
        ) {
            progressReporter?.report()
        }

    }

    /** Apply reader colors to the text view, end tip, and root background. */
    fun applyReaderColors(textColor: String, bgColor: String) {
        binding.tvFVRead.setTextColor(Color.parseColor(textColor))
        binding.tvFVReadEndTip.setTextColor(Color.parseColor(textColor))
        // The page sits on the shared GalIsland grid over the reading colour,
        // so the reader matches the browsing surfaces instead of being a flat fill.
        binding.root.background = ReaderGridDrawable.create(binding.root, bgColor)
    }

    /** Flush the latest anchor before Activity navigation or lifecycle stop. */
    fun flushProgress() {
        progressReporter?.flush()
    }

    /**
     * Applies the measured reader chrome safe area without changing the
     * horizontal page margins.
     *
     * Showing or hiding the menu changes the reserved top inset by a toolbar
     * height, which reflows the text. The scroll offset is compensated so the
     * visible anchor does not move — but the compensation is *not* applied here:
     * `setPadding` only requests a layout, so scrolling now would be clamped
     * against the stale content height. It is handed to [insetCompensator], which
     * runs after layout and before the frame is drawn, so the reflow and the
     * compensation are never visible as two separate positions.
     */
    fun setReaderContentInsets(top: Int, bottom: Int) {
        val scrollView = binding.nsvFVRead
        val oldTop = scrollView.paddingTop
        val safeTop = top.coerceAtLeast(0)
        val safeBottom = bottom.coerceAtLeast(0)
        if (oldTop == safeTop && scrollView.paddingBottom == safeBottom) return

        // A compensation still waiting to be drawn is superseded, not queued: its
        // target is the position the user currently sees, so the new one must
        // build on it rather than on the not-yet-compensated scrollY.
        val topDelta = safeTop - oldTop

        scrollView.setPadding(
            scrollView.paddingLeft,
            safeTop,
            scrollView.paddingRight,
            safeBottom
        )
        pendingScrollCompensation = ReaderInsetCompensation.target(
            currentScrollY = scrollView.scrollY,
            pendingTarget = pendingScrollCompensation,
            topDelta = topDelta
        )
    }

    /**
     * 滚动到已加载内容的指定百分比位置，供底栏进度滑块调用。
     * 连续章节是按滚动比例上报进度的，这里按同一比例反推 scrollY。
     */
    fun scrollToPercent(percent: Int) {
        val scrollView = binding.nsvFVRead
        val content = scrollView.getChildAt(0) ?: return
        val range = content.height - scrollView.height
        if (range <= 0) return
        val target = (range * percent.coerceIn(0, 100) / 100f).toInt()
        scrollView.scrollTo(0, target)
        scrollView.post { progressReporter?.report() }
    }

    /**
     * Briefly marks a search hit in the rendered session.
     *
     * The anchor's offset is chapter-local; the mapper turns it into an offset of
     * the concatenated session text, which is what the TextView actually holds.
     * The tint is derived from the reader colours, so it stays visible on every
     * background the user can pick.
     */
    private fun highlightAnchor(anchor: ReaderAnchor, matchLength: Int) {
        val controller = readerController ?: return
        val textView = binding.tvFVRead
        val text = textView.text as? Spannable ?: return
        val start = controller.globalOffset(anchor) ?: return
        val end = (start + matchLength).coerceAtMost(text.length)
        if (start < 0 || end <= start) return

        val resolver = colorResolver()
        val palette = ReaderChromeTheme.paletteFor(
            binding.root,
            resolver.bgColorHex(),
            resolver.textColorHex()
        )
        val span = BackgroundColorSpan(
            ColorUtils.setAlphaComponent(palette.onSurface, HIGHLIGHT_ALPHA)
        )
        text.setSpan(span, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        textView.postDelayed({
            (textView.text as? Spannable)?.removeSpan(span)
        }, HIGHLIGHT_DURATION_MS)
    }

    fun appendCurrentChapter(): Boolean {        val controller = readerController ?: return false
        val renderer = contentRenderer ?: return false
        val chapter = viewModel.currentCachedChapter()
        if (renderedChapterCids.contains(chapter.ref.cid)) return true

        val rendered = controller.render(chapter)
            ?: controller.acceptLoaded(chapter, append = true)
        renderer.appendText(binding.tvFVRead, rendered)
        renderer.appendImages(readAdapter, chapter.images)
        renderedChapterCids += chapter.ref.cid
        controller.onChapterAppended()
        // Re-apply so the appended paragraphs get their spacing/indent spans.
        // No anchor restore: the new text only extends the session below.
        applyTypography(preserveAnchor = false)
        binding.tvFVReadEndTip.text = getString(R.string.continue_reading)
        binding.nsvFVRead.post { progressReporter?.report() }
        return true
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun initListener() {
        // Continuous scroll means the only gesture here is "tap toggles the
        // chrome". The screen edges deliberately do not switch chapters any
        // more; the bottom bar and the chapter buttons do that explicitly.
        touchHandler = VerticalReaderTouchHandler(
            interactiveChildren = listOf(
                binding.bFVReadPreviousChapter,
                binding.rvFVRead,
                binding.bFVReadNextChapter
            ),
            touchJudge = ReaderTouchJudge(requireContext()),
            onToggleChrome = ::toggleReaderBar
        )
        binding.nsvFVRead.setOnTouchListener { _, event ->
            touchHandler?.onTouch(event) ?: false
        }

        binding.nsvFVRead.setOnScrollChangeListener { _, _, _, _, _ ->
            // The inset compensation is a programmatic scroll that restores the
            // same visual position. Re-deriving the anchor from it made the
            // percentage flicker, and re-evaluating the auto-load threshold would
            // let it append a chapter in the middle of a pre-draw pass. A real
            // scroll lands here again anyway, and the logical position is
            // unchanged, so skipping both loses nothing.
            if (suppressScrollReport) return@setOnScrollChangeListener
            progressReporter?.report()
            maybeAutoLoadNextChapter()
        }

        binding.bFVReadPreviousChapter.setOnClickListener {
            (requireActivity() as ReadActivity).toPreviousChapter()
        }

        binding.bFVReadNextChapter.setOnClickListener {
            (requireActivity() as ReadActivity).toNextChapter()
        }
    }

    private fun restoreInitialPosition() {
        binding.nsvFVRead.viewTreeObserver.addOnGlobalLayoutListener(
            object : ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    binding.nsvFVRead.viewTreeObserver.removeOnGlobalLayoutListener(this)
                    lifecycleScope.launch {
                        // A catalog jump (bookmark or search hit) outranks any
                        // remembered position: it is the explicit user intent.
                        viewModel.consumePendingJump()?.let { (anchor, highlightLength) ->
                            viewModel.goToLatest = false
                            scrollRestorer?.restore(anchor)
                            if (highlightLength > 0) highlightAnchor(anchor, highlightLength)
                            return@launch
                        }

                        viewModel.latestVerticalPosition()?.let { position ->
                            if (readerController?.contains(position.anchor.cid) == true) {
                                scrollRestorer?.restore(position.anchor)
                                viewModel.goToLatest = false
                                return@launch
                            }
                        }

                        val progress = viewModel.getReaderProgress()
                        if (progress != null) {
                            scrollRestorer?.restore(
                                ReaderAnchor(
                                    cid = progress.cid,
                                    paragraphIndex = progress.paragraphIndex,
                                    charOffset = progress.charOffset,
                                    normalizedPercent = progress.normalizedPercent,
                                    imageIndex = progress.imageIndex
                                )
                            )
                            viewModel.goToLatest = false
                            return@launch
                        }

                        viewModel.getByCid.take(1).last()
                            ?.takeIf { it.aid == viewModel.novel?.aid }
                            ?.let { history ->
                            if (viewModel.goToLatest) {
                                scrollRestorer?.restoreLegacy(history.location)
                                viewModel.goToLatest = false
                            } else if (viewModel.getIsShowChapterReadHistory()) {
                                if (viewModel.getIsShowChapterReadHistoryWithoutConfirm()) {
                                    scrollRestorer?.restoreLegacy(history.location)
                                } else {
                                    MaterialAlertDialogBuilder(requireContext())
                                        .setTitle(R.string.history)
                                        .setIcon(R.drawable.ic_history)
                                        .setMessage(R.string.history_restore_tip)
                                        .setCancelable(false)
                                        .setNeutralButton(R.string.not_restore_and_close_forever) { _, _ ->
                                            viewModel.setIsShowChapterReadHistory(false)
                                            (requireActivity() as ReadActivity)
                                                .setRestoreChapterReadHistoryDisable()
                                        }
                                        .setNegativeButton(R.string.not_restore) { _, _ ->
                                            progressReporter?.report()
                                        }
                                        .setPositiveButton(R.string.restore_chapter_read_history_with_confirm) { _, _ ->
                                            scrollRestorer?.restoreLegacy(history.location)
                                        }
                                        .show()
                                }
                            } else {
                                progressReporter?.report()
                            }
                        } ?: progressReporter?.report()
                    }
                }
            }
        )
    }

    private fun toggleReaderBar() {
        ReaderHaptics.tap(binding.root, appConfig.isVibrationEnabled)
        if (viewModel.isBarShown) {
            (requireActivity() as ReadActivity).hideBar()
        } else {
            (requireActivity() as ReadActivity).showBar()
        }
    }

    private fun maybeAutoLoadNextChapter() {
        val controller = readerController ?: return
        if (!controller.canRequestNextChapter()) return

        val contentView = binding.nsvFVRead.getChildAt(0) ?: return
        // Short chapters do not recursively append the entire book in one pass.
        if (contentView.height <= binding.nsvFVRead.height) return

        val thresholdPx = maxOf(
            (binding.nsvFVRead.height * 0.8f).toInt(),
            (resources.displayMetrics.density * 320).toInt()
        )
        val distanceToBottom =
            contentView.height - binding.nsvFVRead.height - binding.nsvFVRead.scrollY

        if (distanceToBottom <= thresholdPx && !viewModel.sessionLimitReached()) {
            // Set the guard before starting the load. A memory-cache hit may
            // complete synchronously, so marking it after the request races the
            // controller's append acknowledgement.
            controller.markNextChapterRequested()
            if (!(requireActivity() as ReadActivity).autoLoadNextChapter()) {
                controller.onChapterAppended()
            }
        }
    }

    private companion object {
        /** Search-hit highlight: a tint of the reader text colour, so it works on any background. */
        const val HIGHLIGHT_ALPHA = 0x33

        /** Long enough to find the hit, short enough not to linger. */
        const val HIGHLIGHT_DURATION_MS = 2500L
    }
}
