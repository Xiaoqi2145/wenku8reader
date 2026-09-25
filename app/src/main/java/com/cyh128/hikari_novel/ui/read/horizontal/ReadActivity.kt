package com.cyh128.hikari_novel.ui.read.horizontal

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.Menu
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.OnBackPressedCallback
import androidx.core.graphics.toColorInt
import com.cyh128.hikari_novel.util.ReaderColorResolver
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.withStarted
import com.cyh128.hikari_novel.R
import com.cyh128.hikari_novel.base.BaseActivity
import com.cyh128.hikari_novel.data.model.Event
import com.cyh128.hikari_novel.data.model.HorizontalRead
import com.cyh128.hikari_novel.data.model.ReadParcel
import com.cyh128.hikari_novel.data.model.ReaderColorSwatches
import com.cyh128.hikari_novel.data.source.local.database.read_history.horizontal_read_history.HorizontalReadHistoryEntity
import com.cyh128.hikari_novel.databinding.ActivityHorizontalReadBinding
import com.cyh128.hikari_novel.databinding.ViewHorizontalReadConfigBinding

import com.cyh128.hikari_novel.ui.read.catalog.ReadChapterCatalogBottomSheet
import com.cyh128.hikari_novel.ui.read.common.ReaderChromeController
import com.cyh128.hikari_novel.ui.read.common.ReaderChromeGeometry
import com.cyh128.hikari_novel.ui.read.common.ReaderChromePalette
import com.cyh128.hikari_novel.ui.read.common.ReaderChromeTheme
import com.cyh128.hikari_novel.ui.read.common.ReaderGridDrawable
import com.cyh128.hikari_novel.ui.read.common.ReaderSliderRowBinder
import com.cyh128.hikari_novel.ui.read.common.ReaderWindowInsetsCoordinator

import com.cyh128.hikari_novel.util.ReaderStatusText
import com.cyh128.hikari_novel.util.ReaderHaptics
import com.cyh128.hikari_novel.util.startActivity
import com.cyh128.hikari_novel.data.source.local.mmkv.AppConfig
import javax.inject.Inject
import com.drake.channel.receiveEvent
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.slider.Slider
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ReadActivity : BaseActivity<ActivityHorizontalReadBinding>() {
    private val viewModel by lazy { ViewModelProvider(this)[ReadViewModel::class.java] }
    private lateinit var bottomViewBinding: ViewHorizontalReadConfigBinding
    private lateinit var bottomSheetDialog: BottomSheetDialog
    private lateinit var chromeController: ReaderChromeController
    private lateinit var windowInsetsCoordinator: ReaderWindowInsetsCoordinator
    private var saveReadHistoryJob: Job? = null
    private val readerColorResolver by lazy { ReaderColorResolver(viewModel.readColorRepository(), this) }

    /** 震动反馈开关（设置页可关）；读取时机在每次手势触发时，改动即时生效 */
    @Inject
    lateinit var appConfig: AppConfig

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (viewModel.isBarShown) {
                    hideBar()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        setSupportActionBar(binding.tbAHRead)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.tbAHRead.setNavigationOnClickListener { finish() }
        supportActionBar?.setTitle(R.string.loading)
        supportFragmentManager.setFragmentResultListener(
            ReadChapterCatalogBottomSheet.RESULT_KEY,
            this
        ) { _, result ->
            jumpToChapter(
                result.getInt(ReadChapterCatalogBottomSheet.RESULT_VOLUME),
                result.getInt(ReadChapterCatalogBottomSheet.RESULT_CHAPTER)
            )
        }

        if (viewModel.novel == null) {
            intent.getParcelableExtra<ReadParcel>("data")!!.also { data ->
                viewModel.novel = data.novel
                viewModel.curVolumePos = data.curVolumePos
                viewModel.curChapterPos = data.curChapterPos
                viewModel.goToLatest = data.goToLatest
            }
        }

        receiveEvent<Event>("event_horizontal_read_activity") { event ->
            when (event) {
                Event.LoadSuccessEvent -> {
                    lifecycle.withStarted {

                        binding.pvAHRead.content = HorizontalRead(
                            viewModel.chapterTitle,
                            viewModel.curNovelContent,
                            viewModel.curImages
                        )
                        supportActionBar?.title = viewModel.chapterTitle
                        supportActionBar?.subtitle = viewModel.novel?.title.orEmpty()

                        lifecycleScope.launch {
                            // A catalog jump (bookmark or search hit) outranks any
                            // remembered position: it is the explicit user intent.
                            viewModel.consumePendingJump()?.let { anchor ->
                                viewModel.goToLatest = false
                                binding.pvAHRead.post { applyPageFromPercent(anchor.normalizedPercent) }
                                return@launch
                            }
                            viewModel.getReaderProgress()?.let { progress ->
                                binding.pvAHRead.post { applyPageFromPercent(progress.normalizedPercent) }
                                viewModel.goToLatest = false
                                return@launch
                            }
                            viewModel.getByCid.take(1).last()?.let {
                                if (viewModel.goToLatest) {
                                    validatePageNum(it)
                                    viewModel.goToLatest = false
                                } else {
                                    if (viewModel.getIsShowChapterReadHistory()) {
                                        if (viewModel.getIsShowChapterReadHistoryWithoutConfirm()) {
                                            validatePageNum(it)
                                        } else {
                                            MaterialAlertDialogBuilder(this@ReadActivity)
                                                .setTitle(R.string.history)
                                                .setIcon(R.drawable.ic_history)
                                                .setMessage(R.string.history_restore_tip)
                                                .setCancelable(false)
                                                .setNeutralButton(
                                                    R.string.not_restore_and_close_forever
                                                ) { _, _ ->
                                                    viewModel.setIsShowChapterReadHistory(false)
                                                    bottomViewBinding.sVHReadConfigRestoreChapterReadHistory.isChecked = false
                                                }
                                                .setNegativeButton(R.string.not_restore) { _, _ -> }
                                                .setPositiveButton(R.string.restore_chapter_read_history_with_confirm) { _, _ ->
                                                    validatePageNum(it)
                                                }
                                                .show()
                                        }
                                    }
                                }
                            }
                        }

                        if (viewModel.curImages.isNotEmpty()) {
                            Snackbar.make(
                                binding.root,
                                R.string.have_image_tip,
                                Snackbar.LENGTH_INDEFINITE
                            ).apply {
                                setAnchorView(binding.barAHRead.root) //使它出现在bottomAppBar的上面，避免遮挡内容
                                setAction(R.string.ok) {}
                                show()
                            }
                        }
                        setBottomBarIsEnable(true)
                        hideBar()
                        refreshBookmarkState()
                    }
                }

                is Event.NetworkErrorEvent -> {
                    Snackbar.make(
                        binding.root,
                        event.msg ?: getString(R.string.network_error_msg),
                        Snackbar.LENGTH_INDEFINITE
                    ).apply {
                        setAnchorView(binding.barAHRead.root)
                        setAction(R.string.retry) { viewModel.retryNovelContent() }
                        show()
                    }
                    setBottomBarIsEnable(false)
                }

                Event.EmptyContentEvent -> {
                    MaterialAlertDialogBuilder(this@ReadActivity)
                        .setTitle(R.string.empty_content)
                        .setIcon(R.drawable.ic_warning)
                        .setMessage(R.string.server_empty_content)
                        .setCancelable(false)
                        .setPositiveButton(R.string.ok) { _, _ -> finish() }
                        .show()
                }

                else -> {}
            }

        }

        initView()
        initListener()

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    refreshStatusBar()
                    delay(60_000L - (System.currentTimeMillis() % 60_000L))
                }
            }
        }

        binding.pvAHRead.showLoadingTip()
        viewModel.getNovelContent()
    }

    override fun onDestroy() {
        viewModel.goToLatest = true
        super.onDestroy()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_act_v_h_read, menu)
        // Menu icons can only be tinted once they exist, so this runs here
        // rather than in applyReaderChrome().
        ReaderChromeTheme.tintMenuIcons(menu, currentChromePalette())
        return true
    }

    //音量上下键拦截
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (viewModel.getKeyDownSwitchChapter()) {
            when (keyCode) {
                KeyEvent.KEYCODE_VOLUME_UP -> {
                    binding.pvAHRead.pageToPrevious()
                    return true
                }

                KeyEvent.KEYCODE_VOLUME_DOWN -> {
                    binding.pvAHRead.pageToNext()
                    return true
                }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun initView() {
        refreshStatusBar()
        updateProgressPercent(0, 0)
        // The reader page is two-state: fold the legacy "follow the App theme"
        // value into a concrete Day/Night before anything reads it.
        viewModel.readColorRepository()
            .convergeReaderThemeMode(ReaderColorResolver.isSystemDarkMode(this))
        // One-shot: a day background still on the old pure-white default moves to
        // the warm off-white the grid surface was designed for.
        viewModel.readColorRepository().migrateDefaultDayBackground()
        // One-shot: the pre-axis custom colour list splits onto the two axes.
        viewModel.readColorRepository().migrateLegacyCustomColors()
        //初始化阅读器的背景颜色和字体颜色
        binding.pvAHRead.backgroundcolor = readerColorResolver.bgColorHex().toColorInt()
        binding.pvAHRead.textColor = readerColorResolver.textColorHex().toColorInt()
        binding.pvAHRead.pageBackground = ReaderGridDrawable.create(
            binding.root,
            readerColorResolver.bgColorHex()
        )

        setBottomBarIsEnable(false)

        if (viewModel.isHorizontalReadFirstLaunch) {
            binding.iAHReadGuide.root.setOnClickListener {
                binding.iAHReadGuide.root.visibility = View.GONE
                viewModel.setIsHorizontalReadFirstLaunch(false)
            }
        } else binding.iAHReadGuide.root.visibility = View.GONE

        // Chrome colours come from the reader palette, not the App theme, so a
        // white page in a dark App theme no longer sits under dark bars.
        applyReaderChrome()
        chromeController = ReaderChromeController(
            window = window,
            chromeHost = binding.root,
            toolbar = binding.tbAHRead,
            bottomDock = binding.readBottomDock,
            statusLine = binding.statusAHRead.root,
            shownBarColor = { currentChromePalette().surface },
            hiddenBarColor = ::getHideBarColor,
            onVisibilityChanged = { viewModel.isBarShown = it },
            onContentBoundsChanged = ::applyReaderContentInsets
        )
        windowInsetsCoordinator = ReaderWindowInsetsCoordinator(
            window = window,
            root = binding.root,
            toolbar = binding.tbAHRead,
            bottomDock = binding.readBottomDock,
            statusLine = binding.statusAHRead.root
        ) {
            chromeController.onWindowGeometryChanged()
        }
        windowInsetsCoordinator.install()

        bottomViewBinding = ViewHorizontalReadConfigBinding.inflate(LayoutInflater.from(this))
        bottomSheetDialog = BottomSheetDialog(this).apply {
            setContentView(bottomViewBinding.root)
            dismissWithAnimation = true
        }

        // R4-3: one shared slider row binder for both panels, with min/max labels.
        ReaderSliderRowBinder.bind(
            bottomViewBinding.sliderFontSize,
            getString(R.string.font_size), 30f, 100f, 1f, viewModel.getFontSize()
        ) {
            binding.pvAHRead.textSize = it
            viewModel.setFontSize(it)
        }
        ReaderSliderRowBinder.bind(
            bottomViewBinding.sliderBottomTextSize,
            getString(R.string.progress_text_size), 1f, 100f, 1f, viewModel.getBottomFontSize()
        ) {
            binding.pvAHRead.bottomTextSize = it
            viewModel.setBottomFontSize(it)
        }
        ReaderSliderRowBinder.bind(
            bottomViewBinding.sliderLineSpacing,
            getString(R.string.line_spacing), 1f, 5f, 0.1f, viewModel.getLineSpacing()
        ) {
            binding.pvAHRead.rowSpace = it
            viewModel.setLineSpacing(it)
        }
        ReaderSliderRowBinder.bind(
            bottomViewBinding.sliderParagraphSpacing,
            getString(R.string.paragraph_spacing), 0f, 4f, 0.1f, viewModel.getParagraphSpacing()
        ) {
            binding.pvAHRead.paragraphSpacing = it
            viewModel.setParagraphSpacing(it)
        }
        ReaderSliderRowBinder.bind(
            bottomViewBinding.sliderLetterSpacing,
            getString(R.string.letter_spacing), 0f, 20f, 1f, viewModel.getLetterSpacing()
        ) {
            binding.pvAHRead.letterSpacing = it
            viewModel.setLetterSpacing(it)
        }

        // R4-1 / R4-2: the two colour axes.
        setupColorAxes()
        bottomViewBinding.sVHReadConfigKeyDownSwitchChapter.isChecked = viewModel.getKeyDownSwitchChapter()
        // P1-1: Set animation mode toggle group
        when (viewModel.getSwitchAnimationMode()) {
            "sliding" -> bottomViewBinding.animationModeGroup.check(R.id.b_animation_sliding)
            "mimical" -> bottomViewBinding.animationModeGroup.check(R.id.b_animation_mimical)
            else -> bottomViewBinding.animationModeGroup.check(R.id.b_animation_none)
        }
        bottomViewBinding.sVHReadConfigTabletDoublePage.isChecked = viewModel.getTabletDoublePage()
        bottomViewBinding.sVHReadConfigTabletDoublePage.isEnabled =
            resources.configuration.screenWidthDp >= 600 ||
                resources.configuration.screenWidthDp == Configuration.SCREEN_WIDTH_DP_UNDEFINED
        bottomViewBinding.sVHReadConfigRestoreChapterReadHistory.isChecked = viewModel.getIsShowChapterReadHistory()
        bottomViewBinding.sVHReadConfigKeepScreenOn.isChecked = viewModel.getKeepScreenOn()

        bottomViewBinding.sVHReadConfigRestoreChapterReadHistoryWithoutConfirm.isChecked = viewModel.getIsShowChapterReadHistoryWithoutConfirm()
        bottomViewBinding.sVHReadConfigRestoreChapterReadHistoryWithoutConfirm.isEnabled = viewModel.getIsShowChapterReadHistory()

        if (viewModel.getKeepScreenOn()) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) //保持屏幕常亮
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        binding.pvAHRead.rowSpace = viewModel.getLineSpacing()
        binding.pvAHRead.textSize = viewModel.getFontSize()
        binding.pvAHRead.bottomTextSize = viewModel.getBottomFontSize()
        binding.pvAHRead.switchAnimation = viewModel.getSwitchAnimationMode()
        binding.pvAHRead.doublePageEnabled = viewModel.getTabletDoublePage()
        // P0-3: Typography must be in place before the chapter content is set,
        // otherwise the first pagination pass would use the defaults.
        binding.pvAHRead.paragraphSpacing = viewModel.getParagraphSpacing()
        binding.pvAHRead.letterSpacing = viewModel.getLetterSpacing()
        binding.pvAHRead.textAlignMode = viewModel.getTextAlignment()
        binding.pvAHRead.firstLineIndent = viewModel.getFirstLineIndent()

        setColor()
        showBar()
        refreshBookmarkState()
    }

    private fun setColor() {
        val bgColorHex = readerColorResolver.bgColorHex()
        binding.pvAHRead.textColor = readerColorResolver.textColorHex().toColorInt()
        binding.pvAHRead.backgroundcolor = bgColorHex.toColorInt()
        // The page sits on the shared GalIsland grid over the reading colour, so
        // the reader matches the browsing surfaces instead of being a flat fill.
        binding.pvAHRead.pageBackground = ReaderGridDrawable.create(binding.root, bgColorHex)
        applyReaderChrome()
    }

    /** Chrome colours follow the reading colours; the menu accent follows the App theme. */
    private fun currentChromePalette(): ReaderChromePalette =
        ReaderChromeTheme.paletteFor(
            binding.root,
            readerColorResolver.bgColorHex(),
            readerColorResolver.textColorHex()
        )

    /**
     * Re-colours the toolbar, status line and bottom dock, then repaints the
     * system bars. Safe to call before the chrome controller exists.
     */
    private fun applyReaderChrome() {
        ReaderChromeTheme.apply(
            context = this,
            toolbar = binding.tbAHRead,
            statusLine = binding.statusAHRead.root,
            bottomDock = binding.readBottomDock,
            palette = currentChromePalette()
        )
        if (::chromeController.isInitialized) chromeController.refreshSystemBars()
    }

    private fun applyReaderContentInsets() {
        val root = binding.root
        if (root.height <= 0) {
            root.post(::applyReaderContentInsets)
            return
        }

        val statusLine = binding.statusAHRead.root
        val bottomDock = binding.readBottomDock
        val toolbar = binding.tbAHRead
        val fallbackBottom = resources.getDimensionPixelSize(R.dimen.gi_reader_content_bottom_inset)
        val statusLineTop = statusLine.top.takeIf { it > 0 }
            ?: (root.height - statusLine.measuredHeight).coerceAtLeast(root.height - fallbackBottom)
        val bottomDockTop = bottomDock.top.takeIf { it > 0 }
            ?: (root.height - bottomDock.measuredHeight).coerceAtLeast(0)
        val toolbarBottom = toolbar.bottom.takeIf { it > 0 } ?: toolbar.measuredHeight
        val insets = ReaderChromeGeometry.contentInsets(
            parentHeight = root.height,
            toolbarBottom = toolbarBottom,
            bottomDockTop = bottomDockTop,
            statusLineTop = statusLineTop,
            chromeShown = viewModel.isBarShown
        )
        binding.pvAHRead.setContentInsets(insets.top, insets.bottom)
    }

    private fun initListener() {
        binding.tbAHRead.setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.menu_hide_bar -> {
                    hideBar()
                    true
                }

                else -> false
            }
        }

        binding.pvAHRead.setImageClickListener { imageUrl ->
            startActivity<com.cyh128.hikari_novel.ui.other.PhotoViewActivity> {
                putExtra("url", imageUrl)
            }
        }

        binding.pvAHRead.onCenterClick = object : IPageView.OnCenterClick {
            override fun onCenterClick() {
                ReaderHaptics.tap(binding.root, appConfig.isVibrationEnabled)
                if (viewModel.isBarShown) {
                    hideBar()
                } else {
                    showBar()
                }
            }
        }

        binding.pvAHRead.onNextChapter = object : IPageView.OnNextChapter {
            override fun onNextChapter() = toNextChapter()
        }

        binding.pvAHRead.onPreviousChapter = object : IPageView.OnPreviousChapter {
            override fun onPreviousChapter() = toPreviousChapter()
        }

        binding.pvAHRead.onPageChange = object : IPageView.OnPageChange {
            override fun onPageChange(index: Int) {
                saveReadHistoryJob?.cancel()
                saveReadHistoryJob = lifecycleScope.launch {
                    delay(400)
                    viewModel.saveReadHistory(binding.pvAHRead.pageNum, binding.pvAHRead.maxPageNum)
                }

                try {
                    if (binding.pvAHRead.maxPageNum == 1) {
                        binding.barAHRead.readProgressSlider.visibility = View.INVISIBLE
                        binding.barAHRead.readPageProgress.text =
                            getString(R.string.page_progress, 1, 1)
                        return
                    } else {
                        binding.barAHRead.readProgressSlider.visibility = View.VISIBLE
                    }

                    binding.barAHRead.readProgressSlider.valueTo = binding.pvAHRead.maxPageNum.toFloat()
                    binding.barAHRead.readProgressSlider.value = index.toFloat()
                    binding.barAHRead.readPageProgress.text = getString(
                        R.string.page_progress,
                        index,
                        binding.pvAHRead.maxPageNum
                    )
                    updateProgressPercent(index, binding.pvAHRead.maxPageNum)
                } catch (e: IllegalArgumentException) { //防止历史记录的页数大于当前章节的最大页数
                    binding.barAHRead.readProgressSlider.value = 1f
                    binding.pvAHRead.pageNum = 1

                    saveReadHistoryJob?.cancel()
                    saveReadHistoryJob = lifecycleScope.launch { viewModel.saveReadHistory(binding.pvAHRead.pageNum, binding.pvAHRead.maxPageNum) }

                    Toast.makeText(this@ReadActivity,R.string.chapter_current_page_error, Toast.LENGTH_SHORT).show()
                }
            }
        }

        binding.barAHRead.readProgressSlider.addOnSliderTouchListener(
            object : Slider.OnSliderTouchListener {
                override fun onStartTrackingTouch(slider: Slider) {}
                override fun onStopTrackingTouch(slider: Slider) {
                    ReaderHaptics.tap(slider, appConfig.isVibrationEnabled)
                    binding.pvAHRead.pageNum = slider.value.toInt()
                    updateProgressPercent(slider.value.toInt(), binding.pvAHRead.maxPageNum)
                }
            }
        )







        // R4-1: Save the typed pair onto its own axis.
        bottomViewBinding.iColorSection.bSaveCustomColor.setOnClickListener { saveCustomColor(isBackground = true) }

        // R4-1: Restore both colour pairs to the factory defaults.
        bottomViewBinding.iColorSection.bRestoreDefaults.setOnClickListener { restoreColorDefaults() }

        // P1-3: Bookmark toggle. The icon mirrors whether the visible chapter is bookmarked.
        binding.barAHRead.readBookmark.setOnClickListener {
            lifecycleScope.launch {
                val added = viewModel.toggleBookmark(
                    binding.pvAHRead.pageNum,
                    binding.pvAHRead.maxPageNum
                )
                Snackbar.make(
                    binding.root,
                    if (added) R.string.bookmark_added else R.string.bookmark_removed,
                    Snackbar.LENGTH_SHORT
                ).show()
                refreshBookmarkState()
            }
        }

        // P1-2: Show page progress label, and let it jump to an arbitrary page.
        binding.barAHRead.readPageProgress.visibility = View.VISIBLE
        binding.barAHRead.readPageProgress.setOnClickListener { showJumpToPageDialog() }
        binding.barAHRead.readChapterProgress.setOnClickListener { showChapterCatalog() }

        binding.barAHRead.readSettings.setOnClickListener { bottomSheetDialog.show() }
        binding.barAHRead.readCatalog.setOnClickListener { showChapterCatalog() }
        binding.barAHRead.readNight.setOnClickListener {
            viewModel.toggleReaderThemeMode()
            setColor()
        }
        bottomViewBinding.bHReadConfigVertical.setOnClickListener {
            lifecycleScope.launch {
                viewModel.saveReadHistory(binding.pvAHRead.pageNum, binding.pvAHRead.maxPageNum)
                startActivity<com.cyh128.hikari_novel.ui.read.vertical.ReadActivity> {
                    putExtra("data", ReadParcel(viewModel.novel!!, viewModel.curVolumePos, viewModel.curChapterPos, false))
                }
                finish()
            }
        }

        binding.barAHRead.readNextChapter.setOnClickListener {
            ReaderHaptics.tap(it, appConfig.isVibrationEnabled)
            toNextChapter()
        }

        binding.barAHRead.readPreviousChapter.setOnClickListener {
            ReaderHaptics.tap(it, appConfig.isVibrationEnabled)
            toPreviousChapter()
        }

        bottomViewBinding.sVHReadConfigKeyDownSwitchChapter.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setKeyDownSwitchChapter(isChecked)
        }

        bottomViewBinding.sVHReadConfigKeepScreenOn.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setKeepScreenOn(isChecked)
            if (isChecked) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) //保持屏幕常亮
            else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        // P1-1: Animation mode toggle group (none/sliding/mimical)
        bottomViewBinding.animationModeGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val mode = when (checkedId) {
                R.id.b_animation_none -> "none"
                R.id.b_animation_sliding -> "sliding"
                R.id.b_animation_mimical -> "mimical"
                else -> "none"
            }
            viewModel.setSwitchAnimationMode(mode)
            binding.pvAHRead.switchAnimation = mode
        }

        // P0-3: Text alignment toggle
        bottomViewBinding.textAlignmentGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val alignment = when (checkedId) {
                R.id.b_alignment_left -> "left"
                R.id.b_alignment_justify -> "justify"
                R.id.b_alignment_right -> "right"
                else -> "left"
            }
            viewModel.setTextAlignment(alignment)
            binding.pvAHRead.textAlignMode = alignment
        }
        when (viewModel.getTextAlignment()) {
            "justify" -> bottomViewBinding.textAlignmentGroup.check(R.id.b_alignment_justify)
            "right" -> bottomViewBinding.textAlignmentGroup.check(R.id.b_alignment_right)
            else -> bottomViewBinding.textAlignmentGroup.check(R.id.b_alignment_left)
        }

        // P0-3: First line indent toggle
        bottomViewBinding.firstLineIndentGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val indent = when (checkedId) {
                R.id.b_indent_none -> 0
                R.id.b_indent_two_chars -> 2
                else -> 0
            }
            viewModel.setFirstLineIndent(indent)
            binding.pvAHRead.firstLineIndent = indent
        }
        when (viewModel.getFirstLineIndent()) {
            2 -> bottomViewBinding.firstLineIndentGroup.check(R.id.b_indent_two_chars)
            else -> bottomViewBinding.firstLineIndentGroup.check(R.id.b_indent_none)
        }

        bottomViewBinding.sVHReadConfigTabletDoublePage.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setTabletDoublePage(isChecked)
            binding.pvAHRead.doublePageEnabled = isChecked
        }

        bottomViewBinding.sVHReadConfigRestoreChapterReadHistory.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setIsShowChapterReadHistory(isChecked)
            bottomViewBinding.sVHReadConfigRestoreChapterReadHistoryWithoutConfirm.isEnabled = isChecked
        }

        bottomViewBinding.sVHReadConfigRestoreChapterReadHistoryWithoutConfirm.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setIsShowChapterReadHistoryWithoutConfirm(isChecked)
        }
    }

    private fun showChapterCatalog() {
        val novel = viewModel.novel ?: return
        ReadChapterCatalogBottomSheet
            .newInstance(novel, viewModel.curVolumePos, viewModel.curChapterPos)
            .apply {
                onPositionSelected = { target ->
                    viewModel.setPendingJump(target.anchor)
                    jumpToChapter(target.volumePos, target.chapterPos)
                }
                onDownloadRequested = { refs ->
                    if (refs.isEmpty()) {
                        Snackbar.make(binding.root, R.string.download_chapter, Snackbar.LENGTH_SHORT).show()
                    } else {
                        viewModel.enqueueDownloads(refs)
                        Snackbar.make(binding.root, R.string.download_queued, Snackbar.LENGTH_SHORT).show()
                    }
                }
            }
            .show(supportFragmentManager, "horizontal_read_chapter_catalog")
    }

    override fun onStop() {
        saveReadHistoryJob?.cancel()
        lifecycleScope.launch {
            viewModel.saveReadHistory(binding.pvAHRead.pageNum, binding.pvAHRead.maxPageNum)
        }
        super.onStop()
    }

    private fun jumpToChapter(volumePos: Int, chapterPos: Int) {
        if (!viewModel.selectChapter(volumePos, chapterPos)) return
        binding.pvAHRead.showLoadingTip()
        setBottomBarIsEnable(false)
    }

    private fun toPreviousChapter() {
        lifecycleScope.launch {
            val target = viewModel.previousChapterLocation()
            if (target == null) {
                Snackbar.make(
                    binding.root,
                    R.string.no_previous_chapter,
                    Snackbar.LENGTH_INDEFINITE
                ).apply {
                    setAnchorView(binding.barAHRead.root)
                    setAction(R.string.ok) {}
                    show()
                }
                return@launch
            }
            if (!viewModel.loadChapter(target)) return@launch
            binding.pvAHRead.showLoadingTip()
            setBottomBarIsEnable(false)
        }
    }

    private fun toNextChapter() {
        lifecycleScope.launch {
            val target = viewModel.nextChapterLocation()
            if (target == null) {
                Snackbar.make(binding.root, R.string.no_next_chapter, Snackbar.LENGTH_INDEFINITE)
                    .apply {
                        setAnchorView(binding.barAHRead.root)
                        setAction(R.string.ok) {}
                        show()
                    }
                return@launch
            }
            if (!viewModel.loadChapter(target)) return@launch
            binding.pvAHRead.showLoadingTip()
            setBottomBarIsEnable(false)
        }
    }

    private fun showBar() { //显示上下栏
        chromeController.show()
    }

    private fun hideBar() { //隐藏上下栏
        chromeController.hide()
    }

    private fun getHideBarColorString() = readerColorResolver.bgColorHex()

    private fun getHideBarColor(): Int = getHideBarColorString().toColorInt()

    /**
     * R4-1 / R4-2: wires the background and text colour axes.
     *
     * Each axis is independent — picking a background only changes the
     * background — except for Koodo's two linkage rules, which
     * [ReaderColorSwatches.linkedTextColorFor] owns.
     */
    private fun setupColorAxes() {
        bottomViewBinding.iColorSection.swatchRowBg.onColorPicked = { hex -> pickColor(hex, isBackground = true) }
        bottomViewBinding.iColorSection.swatchRowText.onColorPicked = { hex -> pickColor(hex, isBackground = false) }
        bottomViewBinding.iColorSection.swatchRowBg.onColorRemoved = { hex -> removeCustomColor(hex, isBackground = true) }
        bottomViewBinding.iColorSection.swatchRowText.onColorRemoved = { hex -> removeCustomColor(hex, isBackground = false) }
        // "Add" saves whatever the hex box currently holds onto that axis; the
        // box is the only free-form colour input, so reusing it avoids a second
        // dialog for the same job.
        bottomViewBinding.iColorSection.swatchRowBg.onAddRequested = { saveCustomColor(isBackground = true) }
        bottomViewBinding.iColorSection.swatchRowText.onAddRequested = { saveCustomColor(isBackground = false) }
        refreshColorAxes()
    }

    /** Applies one axis pick, then re-renders and re-marks the rows. */
    private fun pickColor(hex: String, isBackground: Boolean) {
        val repository = viewModel.readColorRepository()
        if (isBackground) {
            repository.setCurrentBgColor(hex)
            // Keep the pair readable: a dark background forces white text, and
            // white text on a white background turns black.
            ReaderColorSwatches.linkedTextColorFor(hex, repository.currentTextColor())?.let {
                repository.setCurrentTextColor(it)
            }
        } else {
            repository.setCurrentTextColor(hex)
        }
        setColor()
        updateColorInputs()
        refreshColorAxes()
    }

    private fun removeCustomColor(hex: String, isBackground: Boolean) {
        val repository = viewModel.readColorRepository()
        if (isBackground) repository.removeCustomBgColor(hex) else repository.removeCustomTextColor(hex)
        Snackbar.make(binding.root, R.string.reader_color_removed, Snackbar.LENGTH_SHORT).show()
        refreshColorAxes()
    }

    /** Re-reads both axes from storage and re-renders the swatch rows. */
    private fun refreshColorAxes() {
        val repository = viewModel.readColorRepository()
        val ring = readerColorResolver.textColorHex().toColorInt()
        val accent = MaterialColors.getColor(binding.root, androidx.appcompat.R.attr.colorPrimary)

        bottomViewBinding.iColorSection.swatchRowBg.apply {
            setPalette(ring, accent)
            submit(
                ReaderColorSwatches.axis(ReaderColorSwatches.BACKGROUNDS, repository.getCustomBgColors()),
                repository.currentBgColor()
            )
        }
        bottomViewBinding.iColorSection.swatchRowText.apply {
            setPalette(ring, accent)
            submit(
                ReaderColorSwatches.axis(ReaderColorSwatches.TEXTS, repository.getCustomTextColors()),
                repository.currentTextColor()
            )
        }
    }

    /**
     * R4-1: saves the hex boxes as custom colours.
     *
     * [isBackground] null means "both axes" — that is what the panel's Save
     * button does, since the two boxes are edited side by side. The per-axis
     * "add" affordances pass an explicit axis instead.
     *
     * Both boxes are validated together so a half-typed value cannot be saved
     * by accident.
     */
    private fun saveCustomColor(isBackground: Boolean? = null) {
        val textColor = bottomViewBinding.iColorSection.editTextColor.text.toString().trim()
        val bgColor = bottomViewBinding.iColorSection.editBgColor.text.toString().trim()
        if (!HEX_PATTERN.matches(textColor) || !HEX_PATTERN.matches(bgColor)) {
            Snackbar.make(binding.root, R.string.color_format_invalid, Snackbar.LENGTH_SHORT).show()
            return
        }
        val repository = viewModel.readColorRepository()
        val savedBg = isBackground != false && repository.addCustomBgColor(bgColor)
        val savedText = isBackground != true && repository.addCustomTextColor(textColor)
        val saved = savedBg || savedText
        val message = when {
            !saved -> R.string.custom_color_save_failed
            isBackground == true -> R.string.reader_color_saved_bg
            isBackground == false -> R.string.reader_color_saved_text
            else -> R.string.custom_color_saved
        }
        Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
        refreshColorAxes()
    }

    /** R4-1: Restore both colour pairs to the factory defaults. */
    private fun restoreColorDefaults() {
        viewModel.readColorRepository().restoreDefaults()
        setColor()
        updateColorInputs()
        refreshColorAxes()
        Snackbar.make(binding.root, R.string.color_restored, Snackbar.LENGTH_SHORT).show()
    }

    /** Update the hex input fields with current colors. */
    private fun updateColorInputs() {
        bottomViewBinding.iColorSection.editTextColor.setText(readerColorResolver.textColorHex().removePrefix("#"))
        bottomViewBinding.iColorSection.editBgColor.setText(readerColorResolver.bgColorHex().removePrefix("#"))
    }

    /** P1-2: Ask for a page number and jump to it, clamped to the chapter range. */
    private fun showJumpToPageDialog() {
        val maxPageNum = binding.pvAHRead.maxPageNum
        if (maxPageNum <= 0) return
        val input = android.widget.EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            hint = getString(R.string.page_number_hint)
            setText(binding.pvAHRead.pageNum.toString())
            setSelection(text.length)
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.jump_to_page_range, maxPageNum))
            .setView(input)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.jump) { _, _ ->
                val target = input.text.toString().toIntOrNull() ?: return@setPositiveButton
                binding.pvAHRead.pageNum = target.coerceIn(1, maxPageNum)
            }
            .show()
    }

    /** P1-3: Mirror the bookmark state of the visible chapter on the bottom bar icon. */
    private fun refreshBookmarkState() {
        lifecycleScope.launch {
            val bookmarked = viewModel.hasBookmarkForCurrentChapter()
            binding.barAHRead.readBookmark.setIconResource(
                if (bookmarked) {
                    R.drawable.ic_baseline_collections_bookmark
                } else {
                    R.drawable.ic_outline_collections_bookmark
                }
            )
        }
    }

    private fun setBottomBarIsEnable(value: Boolean) {
        binding.barAHRead.readNextChapter.isEnabled = value
        binding.barAHRead.readPreviousChapter.isEnabled = value
        binding.barAHRead.readProgressSlider.isEnabled = value
    }

    /**
     * Maps a chapter-local percentage onto a page number.
     *
     * Used both by progress restoration and by catalog jumps (bookmarks, search
     * hits), so the two paths cannot drift apart.
     */
    private fun applyPageFromPercent(percent: Int) {
        val maxPageNum = binding.pvAHRead.maxPageNum
        if (maxPageNum <= 0) return
        binding.pvAHRead.pageNum = ((percent / 100f) * maxPageNum).toInt().coerceIn(1, maxPageNum)
    }

    /**
     * P1-2: the bottom bar keeps "chapter x/y" next to the slider; the status
     * line's bottom-right corner shows only the percentage.
     *
     * That percentage is the *current chapter's* progress — [percent] is derived
     * from [pageNum] over the chapter's own page count, not the whole book.
     */
    private fun updateProgressPercent(pageNum: Int, maxPageNum: Int) {
        val percent = if (maxPageNum > 0) (pageNum * 100 / maxPageNum).coerceIn(0, 100) else 0
        val chapterText = viewModel.chapterProgressText()
        binding.barAHRead.readChapterProgress.text = chapterText
        binding.statusAHRead.readStatusProgress.text =
            getString(R.string.reader_status_progress, percent)
    }

    /** 常显状态行：时间与电量，在分钟边界刷新。 */
    private fun refreshStatusBar() {
        binding.statusAHRead.readStatusTime.text = ReaderStatusText.time()
        val battery = ReaderStatusText.batteryLevel(this)
        binding.statusAHRead.readStatusBattery.text = if (battery == null) "--" else "$battery%"
    }

    private fun validatePageNum(data: HorizontalReadHistoryEntity) {
        if (data.location > binding.pvAHRead.maxPageNum) {
            binding.pvAHRead.pageNum = 1
            MaterialAlertDialogBuilder(this@ReadActivity)
                .setTitle(R.string.warning)
                .setIcon(R.drawable.ic_warning)
                .setMessage(R.string.chapter_current_page_error)
                .setCancelable(false)
                .setPositiveButton(R.string.ok) { _, _ -> }
                .show()
        } else {
            binding.pvAHRead.pageNum = data.location
        }
    }

    private companion object {
        /** 6-digit or 3-digit hex without the leading '#'. */
        val HEX_PATTERN = Regex("^([A-Fa-f0-9]{6}|[A-Fa-f0-9]{3})$")
    }
}
