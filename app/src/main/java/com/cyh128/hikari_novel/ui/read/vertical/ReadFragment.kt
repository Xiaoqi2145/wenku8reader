package com.cyh128.hikari_novel.ui.read.vertical

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.ViewTreeObserver
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
import com.cyh128.hikari_novel.util.ReaderTouchJudge
import com.cyh128.hikari_novel.util.getIsInDarkMode
import com.cyh128.hikari_novel.util.startActivity
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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        (requireActivity() as? ReadActivity)?.applyReaderContentInsets()

        receiveEvent<Event>("event_vertical_read_fragment") { event ->
            when (event) {
                is Event.ChangeFontSizeEvent -> {
                    val anchor = progressReporter?.latest()?.anchor
                    binding.tvFVRead.textSize = event.value
                    anchor?.let { scrollRestorer?.restoreAfterLayout(it) }
                }
                is Event.ChangeLineSpacingEvent -> {
                    val anchor = progressReporter?.latest()?.anchor
                    binding.tvFVRead.setLineSpacing(event.value, 1f)
                    anchor?.let { scrollRestorer?.restoreAfterLayout(it) }
                }
                else -> Unit
            }
        }

        initView()
        initReaderCollaborators()
        initListener()
        restoreInitialPosition()
    }

    override fun onDestroyView() {
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

        binding.tvFVRead.textSize = viewModel.getFontSize()
        binding.tvFVRead.setLineSpacing(viewModel.getLineSpacing(), 1f)

        if (getIsInDarkMode()) {
            binding.tvFVRead.setTextColor(Color.parseColor("#" + viewModel.getTextColorNight()))
            binding.tvFVReadEndTip.setTextColor(Color.parseColor("#" + viewModel.getTextColorNight()))
            binding.root.setBackgroundColor(Color.parseColor("#" + viewModel.getBgColorNight()))
        } else {
            binding.tvFVRead.setTextColor(Color.parseColor("#" + viewModel.getTextColorDay()))
            binding.tvFVReadEndTip.setTextColor(Color.parseColor("#" + viewModel.getTextColorDay()))
            binding.root.setBackgroundColor(Color.parseColor("#" + viewModel.getBgColorDay()))
        }
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

    /** Flush the latest anchor before Activity navigation or lifecycle stop. */
    fun flushProgress() {
        progressReporter?.flush()
    }

    /**
     * Applies the measured reader chrome safe area without changing the
     * horizontal page margins. When the reader is already scrolled, compensate
     * for a top-inset change so the visible text anchor stays stable.
     */
    fun setReaderContentInsets(top: Int, bottom: Int) {
        val scrollView = binding.nsvFVRead
        val oldTop = scrollView.paddingTop
        val oldScrollY = scrollView.scrollY
        val safeTop = top.coerceAtLeast(0)
        val safeBottom = bottom.coerceAtLeast(0)
        if (oldTop == safeTop && scrollView.paddingBottom == safeBottom) return

        scrollView.setPadding(
            scrollView.paddingLeft,
            safeTop,
            scrollView.paddingRight,
            safeBottom
        )
        val delta = safeTop - oldTop
        if (oldScrollY > 0 && delta != 0) {
            scrollView.post {
                scrollView.scrollTo(0, (oldScrollY + delta).coerceAtLeast(0))
            }
        }
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

    fun appendCurrentChapter(): Boolean {
        val controller = readerController ?: return false
        val renderer = contentRenderer ?: return false
        val chapter = viewModel.currentCachedChapter()
        if (renderedChapterCids.contains(chapter.ref.cid)) return true

        val rendered = controller.render(chapter)
            ?: controller.acceptLoaded(chapter, append = true)
        renderer.appendText(binding.tvFVRead, rendered)
        renderer.appendImages(readAdapter, chapter.images)
        renderedChapterCids += chapter.ref.cid
        controller.onChapterAppended()
        binding.tvFVReadEndTip.text = getString(R.string.continue_reading)
        binding.nsvFVRead.post { progressReporter?.report() }
        return true
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun initListener() {
        touchHandler = VerticalReaderTouchHandler(
            touchTarget = binding.nsvFVRead,
            interactiveChildren = listOf(
                binding.bFVReadPreviousChapter,
                binding.rvFVRead,
                binding.bFVReadNextChapter
            ),
            touchJudge = ReaderTouchJudge(requireContext()),
            onCenterTap = ::toggleReaderBar
        )
        binding.nsvFVRead.setOnTouchListener { _, event ->
            touchHandler?.onTouch(event) ?: false
        }

        binding.nsvFVRead.setOnScrollChangeListener { _, _, _, _, _ ->
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
        binding.root.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
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
}
