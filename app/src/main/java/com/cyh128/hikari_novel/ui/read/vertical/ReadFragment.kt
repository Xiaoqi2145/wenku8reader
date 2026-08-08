package com.cyh128.hikari_novel.ui.read.vertical

import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.Rect
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.ViewTreeObserver
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.cyh128.hikari_novel.R
import com.cyh128.hikari_novel.base.BaseFragment
import com.cyh128.hikari_novel.data.model.Event
import com.cyh128.hikari_novel.databinding.FragmentVerticalReadBinding
import com.cyh128.hikari_novel.util.ReaderTapArea
import com.cyh128.hikari_novel.util.ReaderTouchJudge
import com.cyh128.hikari_novel.util.getIsInDarkMode
import com.cyh128.hikari_novel.util.startActivity
import com.drake.channel.receiveEvent
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import java.text.DecimalFormat

@AndroidEntryPoint
class ReadFragment : BaseFragment<FragmentVerticalReadBinding>() {
    private val viewModel by lazy { ViewModelProvider(requireActivity())[ReadViewModel::class.java] }
    private lateinit var touchJudge: ReaderTouchJudge
    private var readAdapter: ReadAdapter? = null
    private val interactiveChildRect = Rect()
    private var touchStartX = 0f
    private var touchStartY = 0f
    private var ignoreCurrentGesture = false
    private var touchStartedOnInteractiveChild = false
    private var hasRequestedNextChapter = false
    private var activeChapterStartOffset = 0

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        receiveEvent<Event>("event_vertical_read_fragment") { event ->
            when (event) {
                is Event.ChangeFontSizeEvent -> {
                    binding.tvFVRead.textSize = event.value
                }

                is Event.ChangeLineSpacingEvent -> {
                    binding.tvFVRead.setLineSpacing(event.value, 1f)
                }

                else -> {}
            }
        }

        initView()
        initListener()
    }

    override fun onDestroyView() {
        readAdapter = null //防止内存泄漏
        super.onDestroyView()
    }

    private fun initView() {
        readAdapter = ReadAdapter(
            requireContext(),
            viewModel.curImages.toMutableList()
        ) { imageUrl ->
            startActivity<com.cyh128.hikari_novel.ui.other.PhotoViewActivity> {
                putExtra("url", imageUrl)
            }
        }
        binding.tvFVRead.text = viewModel.curNovelContent
        binding.rvFVRead.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = readAdapter
        }

        //初始化阅读器参数
        binding.tvFVRead.textSize = viewModel.getFontSize()
        binding.tvFVRead.setLineSpacing(viewModel.getLineSpacing(), 1f)

        if (getIsInDarkMode()) { //夜间模式
            binding.tvFVRead.setTextColor(Color.parseColor("#" + viewModel.getTextColorNight()))
            binding.tvFVReadEndTip.setTextColor(Color.parseColor("#" + viewModel.getTextColorNight()))
            binding.root.setBackgroundColor(Color.parseColor("#" + viewModel.getBgColorNight()))
        } else {
            binding.tvFVRead.setTextColor(Color.parseColor("#" + viewModel.getTextColorDay()))
            binding.tvFVReadEndTip.setTextColor(Color.parseColor("#" + viewModel.getTextColorDay()))
            binding.root.setBackgroundColor(Color.parseColor("#" + viewModel.getBgColorDay()))
        }

        binding.nsvFVRead.viewTreeObserver.addOnGlobalLayoutListener(
            object : ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    binding.nsvFVRead.viewTreeObserver.removeOnGlobalLayoutListener(this) //确保只调用一次
                    refreshProgressText()

                    lifecycleScope.launch {
                        viewModel.getReaderProgress()?.let { progress ->
                            val offset = progress.charOffset.coerceIn(0, binding.tvFVRead.text.length)
                            val layout = binding.tvFVRead.layout
                            if (layout != null) {
                                binding.nsvFVRead.scrollTo(0, layout.getLineTop(layout.getLineForOffset(offset)))
                            }
                            viewModel.goToLatest = false
                            return@launch
                        }
                        viewModel.getByCid.take(1).last()?.let {
                            if (viewModel.goToLatest) {
                                binding.nsvFVRead.scrollTo(0, it.location) //滚动到指定位置
                            } else {
                                if (viewModel.getIsShowChapterReadHistory()) {
                                    if (viewModel.getIsShowChapterReadHistoryWithoutConfirm()) {
                                        binding.nsvFVRead.scrollTo(0, it.location) //滚动到指定位置
                                    } else {
                                        MaterialAlertDialogBuilder(requireContext())
                                            .setTitle(R.string.history)
                                            .setIcon(R.drawable.ic_history)
                                            .setMessage(R.string.history_restore_tip)
                                            .setCancelable(false)
                                            .setNeutralButton(
                                                R.string.not_restore_and_close_forever
                                            ) { _, _ ->
                                                viewModel.setIsShowChapterReadHistory(false)
                                                (requireActivity() as ReadActivity).setRestoreChapterReadHistoryDisable()
                                            }
                                            .setNegativeButton(R.string.not_restore) { _, _ -> }
                                            .setPositiveButton(R.string.restore_chapter_read_history_with_confirm) { _, _ ->
                                                binding.nsvFVRead.scrollTo(0, it.location) //滚动到指定位置
                                            }
                                            .show()
                                    }
                                }
                            }
                        }
                    }
                }
            }
        )
    }

    fun appendCurrentChapter() {
        val prefix = "\n\n${viewModel.chapterTitle}\n\n"
        activeChapterStartOffset = binding.tvFVRead.text.length + prefix.length
        binding.tvFVRead.append(prefix + viewModel.curNovelContent)
        readAdapter?.appendImages(viewModel.curImages)
        binding.tvFVReadEndTip.text = getString(R.string.continue_reading)
        hasRequestedNextChapter = false
        binding.nsvFVRead.post { refreshProgressText() }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun initListener() {
        touchJudge = ReaderTouchJudge(requireContext())
        binding.nsvFVRead.setOnTouchListener { _, event ->
            handleReaderTouch(event)
            false
        }

        binding.nsvFVRead.setOnScrollChangeListener { _, _, _, _, _ ->
            refreshProgressText()
            maybeAutoLoadNextChapter()
        }

        binding.bFVReadPreviousChapter.setOnClickListener {
            (requireActivity() as ReadActivity).toPreviousChapter()
        }

        binding.bFVReadNextChapter.setOnClickListener {
            (requireActivity() as ReadActivity).toNextChapter()
        }
    }

    private fun handleReaderTouch(event: MotionEvent) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touchStartX = event.x
                touchStartY = event.y
                ignoreCurrentGesture = false
                touchStartedOnInteractiveChild = isInteractiveChildTouch(event)
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                ignoreCurrentGesture = true
            }

            MotionEvent.ACTION_UP -> {
                val deltaX = event.x - touchStartX
                val deltaY = event.y - touchStartY
                if (!ignoreCurrentGesture &&
                    !touchStartedOnInteractiveChild &&
                    touchJudge.isTap(deltaX, deltaY) &&
                    touchJudge.tapArea(touchStartX, binding.nsvFVRead.width) == ReaderTapArea.Center
                ) {
                    toggleReaderBar()
                }
                ignoreCurrentGesture = false
                touchStartedOnInteractiveChild = false
            }

            MotionEvent.ACTION_CANCEL -> {
                ignoreCurrentGesture = false
                touchStartedOnInteractiveChild = false
            }
        }
    }

    private fun isInteractiveChildTouch(event: MotionEvent): Boolean =
        listOf(binding.bFVReadPreviousChapter, binding.rvFVRead, binding.bFVReadNextChapter).any {
            it.getGlobalVisibleRect(interactiveChildRect) &&
                interactiveChildRect.contains(event.rawX.toInt(), event.rawY.toInt())
        }

    private fun toggleReaderBar() {
        if (viewModel.isBarShown) {
            (requireActivity() as ReadActivity).hideBar()
            viewModel.isBarShown = false
        } else {
            (requireActivity() as ReadActivity).showBar()
            viewModel.isBarShown = true
        }
    }

    private fun refreshProgressText() {
        val layout = binding.tvFVRead.layout ?: return
        val startLine = layout.getLineForOffset(activeChapterStartOffset.coerceIn(0, binding.tvFVRead.text.length))
        val endOffset = (activeChapterStartOffset + viewModel.curNovelContent.length).coerceIn(0, binding.tvFVRead.text.length)
        val endLine = layout.getLineForOffset(endOffset)
        val startY = layout.getLineTop(startLine)
        val endY = layout.getLineBottom(endLine).coerceAtLeast(startY + 1)
        val visibleBottom = binding.nsvFVRead.scrollY + binding.nsvFVRead.height
        var result = (((visibleBottom - startY).toFloat() / (endY - startY) * 100f).toInt()).coerceIn(0, 100).toString()
        viewModel.curReadPos = binding.nsvFVRead.scrollY
        viewModel.progressText.value = "$result%"
    }

    private fun maybeAutoLoadNextChapter() {
        if (hasRequestedNextChapter) return

        val contentView = binding.nsvFVRead.getChildAt(0) ?: return
        val canScroll = contentView.height > binding.nsvFVRead.height
        if (!canScroll) return

        val thresholdPx = (resources.displayMetrics.density * 24).toInt()
        val distanceToBottom =
            contentView.height - binding.nsvFVRead.height - binding.nsvFVRead.scrollY

        if (distanceToBottom <= thresholdPx) {
            hasRequestedNextChapter = (requireActivity() as ReadActivity).autoLoadNextChapter()
        }
    }

}
