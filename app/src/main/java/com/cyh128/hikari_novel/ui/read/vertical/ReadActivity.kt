package com.cyh128.hikari_novel.ui.read.vertical


import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.WindowManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.OnBackPressedCallback
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.Lifecycle
import androidx.core.graphics.toColorInt
import com.cyh128.hikari_novel.util.ReaderColorResolver
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.withStarted
import com.cyh128.hikari_novel.R
import com.cyh128.hikari_novel.base.BaseActivity
import com.cyh128.hikari_novel.data.model.Event
import com.cyh128.hikari_novel.data.model.ReaderColorSwatches
import com.cyh128.hikari_novel.data.model.ReaderUiState
import com.cyh128.hikari_novel.data.model.ReadParcel
import com.cyh128.hikari_novel.databinding.ActivityVerticalReadBinding
import com.cyh128.hikari_novel.databinding.ViewVerticalReadConfigBinding
import com.cyh128.hikari_novel.ui.other.LoadingView

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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ReadActivity : BaseActivity<ActivityVerticalReadBinding>() {
    private lateinit var bottomViewBinding: ViewVerticalReadConfigBinding
    private lateinit var bottomSheetDialog: BottomSheetDialog
    private lateinit var chromeController: ReaderChromeController
    private lateinit var windowInsetsCoordinator: ReaderWindowInsetsCoordinator
    private val viewModel by lazy { ViewModelProvider(this)[ReadViewModel::class.java] }
    private val readerColorResolver by lazy { ReaderColorResolver(viewModel.readColorRepository(), this) }
    /** 用户正在拖动进度滑块时暂停外部回写，避免抖动 */
    private var progressSliderTracking = false

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

        if (viewModel.novel == null) {
            val parcel = intent.getParcelableExtra<ReadParcel>("data")
            if (parcel == null) {
                finish()
                return
            }
            viewModel.novel = parcel.novel
            viewModel.curVolumePos = parcel.curVolumePos
            viewModel.curChapterPos = parcel.curChapterPos
            viewModel.goToLatest = parcel.goToLatest
        }

        setSupportActionBar(binding.tbAVRead)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.tbAVRead.setNavigationOnClickListener { finish() }
        supportFragmentManager.setFragmentResultListener(
            ReadChapterCatalogBottomSheet.RESULT_KEY,
            this
        ) { _, result ->
            jumpToChapter(
                result.getInt(ReadChapterCatalogBottomSheet.RESULT_VOLUME),
                result.getInt(ReadChapterCatalogBottomSheet.RESULT_CHAPTER)
            )
        }


        receiveEvent<Event>("event_vertical_read_activity") { event ->
            when (event) {
                is Event.VerticalReaderLoadSuccessEvent -> {
                    if (viewModel.acceptsLoadEvent(event.aid, event.cid, event.generation)) {
                        lifecycle.withStarted {
                            if (!viewModel.acceptsLoadEvent(event.aid, event.cid, event.generation)) {
                                return@withStarted
                            }
                            supportActionBar?.title = viewModel.chapterTitle
                            supportActionBar?.subtitle = viewModel.novel?.title.orEmpty()
                            if (event.append) {
                                val fragment = supportFragmentManager
                                    .findFragmentById(R.id.fcv_a_v_read) as? ReadFragment
                                if (fragment == null || fragment.view == null ||
                                    !fragment.appendCurrentChapter()
                                ) {
                                    // The ViewModel has already accepted the chapter
                                    // into its durable session; a new Fragment can
                                    // render the complete session safely.
                                    showContent()
                                }
                                setBottomBarIsEnable(true)
                                return@withStarted
                            }
                            showContent()
                            if (viewModel.curImages.isNotEmpty()) {
                                Snackbar.make(
                                    binding.root,
                                    R.string.have_image_tip,
                                    Snackbar.LENGTH_INDEFINITE
                                ).apply {
                                    setAnchorView(binding.barAVRead.root)
                                    setAction(R.string.ok) {}
                                    show()
                                }
                            }
                            setBottomBarIsEnable(true)
                            hideBar()
                        }
                    }
                }

                is Event.VerticalReaderLoadErrorEvent -> {
                    if (viewModel.acceptsLoadEvent(event.aid, event.cid, event.generation)) {
                        if (event.empty) {
                            MaterialAlertDialogBuilder(this@ReadActivity)
                                .setTitle(R.string.empty_content)
                                .setIcon(R.drawable.ic_warning)
                                .setMessage(R.string.server_empty_content)
                                .setCancelable(false)
                                .setPositiveButton(R.string.ok) { _, _ -> finish() }
                                .show()
                        } else {
                            Snackbar.make(
                                binding.root,
                                event.message ?: getString(R.string.network_error_msg),
                                Snackbar.LENGTH_INDEFINITE
                            ).apply {
                                setAnchorView(binding.barAVRead.root)
                                setAction(R.string.retry) { viewModel.retryNovelContent() }
                                show()
                            }
                        }
                        setBottomBarIsEnable(false)
                    }
                }

                else -> Unit
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

        viewModel.loadInitialContentIfNeeded()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_act_v_h_read, menu)
        // Menu icons can only be tinted once they exist, so this runs here
        // rather than in applyReaderChrome().
        ReaderChromeTheme.tintMenuIcons(menu, currentChromePalette())
        return true
    }

    private fun initView() {
        refreshStatusBar()
        // The reader page is two-state: fold the legacy "follow the App theme"
        // value into a concrete Day/Night before anything reads it.
        viewModel.readColorRepository()
            .convergeReaderThemeMode(ReaderColorResolver.isSystemDarkMode(this))
        // One-shot: a day background still on the old pure-white default moves to
        // the warm off-white the grid surface was designed for.
        viewModel.readColorRepository().migrateDefaultDayBackground()
        // One-shot: the pre-axis custom colour list splits onto the two axes.
        viewModel.readColorRepository().migrateLegacyCustomColors()
        // Chrome colours come from the reader palette, not the App theme, so a
        // white page in a dark App theme no longer sits under dark bars.
        applyReaderChrome()
        chromeController = ReaderChromeController(
            window = window,
            chromeHost = binding.root,
            toolbar = binding.tbAVRead,
            bottomDock = binding.readBottomDock,
            statusLine = binding.statusAVRead.root,
            shownBarColor = { currentChromePalette().surface },
            hiddenBarColor = ::getHideBarColor,
            onVisibilityChanged = { viewModel.isBarShown = it },
            onContentBoundsChanged = ::applyReaderContentInsets
        )
        windowInsetsCoordinator = ReaderWindowInsetsCoordinator(
            window = window,
            root = binding.root,
            toolbar = binding.tbAVRead,
            bottomDock = binding.readBottomDock,
            statusLine = binding.statusAVRead.root
        ) {
            chromeController.onWindowGeometryChanged()
        }
        windowInsetsCoordinator.install()

        bottomViewBinding = ViewVerticalReadConfigBinding.inflate(LayoutInflater.from(this))
        bottomSheetDialog = BottomSheetDialog(this).apply {
            setContentView(bottomViewBinding.root)
            dismissWithAnimation = true
        }

        bottomViewBinding.sVVReadConfigRestoreChapterReadHistory.isChecked = viewModel.getIsShowChapterReadHistory()
        // R4-3: one shared slider row binder for both panels, with min/max labels.
        ReaderSliderRowBinder.bind(
            bottomViewBinding.sliderFontSize,
            getString(R.string.font_size), 1f, 50f, 1f, viewModel.getFontSize()
        ) { viewModel.setFontSize(it) }
        ReaderSliderRowBinder.bind(
            bottomViewBinding.sliderLineSpacing,
            getString(R.string.line_spacing), 1f, 50f, 1f, viewModel.getLineSpacing()
        ) { viewModel.setLineSpacing(it) }
        ReaderSliderRowBinder.bind(
            bottomViewBinding.sliderParagraphSpacing,
            getString(R.string.paragraph_spacing), 0f, 40f, 1f, viewModel.getParagraphSpacing()
        ) { viewModel.setParagraphSpacing(it) }
        ReaderSliderRowBinder.bind(
            bottomViewBinding.sliderLetterSpacing,
            getString(R.string.letter_spacing), 0f, 20f, 1f, viewModel.getLetterSpacing()
        ) { viewModel.setLetterSpacing(it) }

        // R4-1 / R4-2: the two colour axes.
        setupColorAxes()

        bottomViewBinding.sVVReadConfigKeepScreenOn.isChecked = viewModel.getKeepScreenOn()

        bottomViewBinding.sVVReadConfigRestoreChapterReadHistoryWithoutConfirm.isChecked = viewModel.getIsShowChapterReadHistoryWithoutConfirm()
        bottomViewBinding.sVVReadConfigRestoreChapterReadHistoryWithoutConfirm.isEnabled = viewModel.getIsShowChapterReadHistory()

        if (viewModel.isVerticalReadFirstLaunch) {
            binding.iAVReadGuide.root.setOnClickListener {
                binding.iAVReadGuide.root.visibility = android.view.View.GONE
                viewModel.setIsVerticalReadFirstLaunch(false)
            }
        } else {
            binding.iAVReadGuide.root.visibility = android.view.View.GONE
        }

        if (viewModel.getKeepScreenOn()) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) //保持屏幕常亮
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setBottomBarIsEnable(false)
        showBar()
        refreshBookmarkState()
        if (viewModel.readerState.value is ReaderUiState.Ready) {
            showContent()
        } else if (viewModel.readerState.value !is ReaderUiState.Loading) {
            showLoading()
        }
    }

    internal fun applyReaderContentInsets() {
        val root = binding.root
        if (root.height <= 0) {
            root.post(::applyReaderContentInsets)
            return
        }

        val statusLine = binding.statusAVRead.root
        val bottomDock = binding.readBottomDock
        val toolbar = binding.tbAVRead
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
        (supportFragmentManager.findFragmentById(R.id.fcv_a_v_read) as? ReadFragment)
            ?.setReaderContentInsets(insets.top, insets.bottom)
    }

    private fun initListener() {
        binding.tbAVRead.setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.menu_hide_bar -> {
                    hideBar()
                    true
                }

                else -> false
            }
        }

        // ProgressReporter owns throttling and anchor persistence. The Activity
        // only mirrors the display value and no longer schedules a second write.
        // P1-2: the bottom bar keeps "chapter x/y" next to the slider; the status
        // line's bottom-right corner shows only the percentage. That percentage is
        // the *current chapter's* progress (the reporter derives it from the
        // chapter-local anchor/geometry), never the whole book.
        viewModel.progressText.observe(this) {
            val chapterPercent = parsePercent(it).toInt()
            binding.statusAVRead.readStatusProgress.text =
                getString(R.string.reader_status_progress, chapterPercent)
            if (!progressSliderTracking) {
                binding.barAVRead.readProgressSlider.value = parsePercent(it)
            }
        }

        // 竖排为连续滚动，滑块按已加载内容的滚动比例定位
        binding.barAVRead.readProgressSlider.addOnSliderTouchListener(
            object : Slider.OnSliderTouchListener {
                override fun onStartTrackingTouch(slider: Slider) {
                    progressSliderTracking = true
                }

                override fun onStopTrackingTouch(slider: Slider) {
                    progressSliderTracking = false
                    ReaderHaptics.tap(slider, appConfig.isVibrationEnabled)
                    scrollToPercent(slider.value.toInt())
                }
            }
        )

        //设置底栏菜单按钮监听
        binding.barAVRead.readPreviousChapter.setOnClickListener {
            ReaderHaptics.tap(it, appConfig.isVibrationEnabled)
            toPreviousChapter()
        }

        binding.barAVRead.readNextChapter.setOnClickListener {
            ReaderHaptics.tap(it, appConfig.isVibrationEnabled)
            toNextChapter()
        }

        // P1-2: Tapping the chapter counter opens the catalog at the visible chapter.
        binding.barAVRead.readChapterProgress.setOnClickListener { showChapterCatalog() }

        // P1-3: Bookmark toggle. The icon mirrors whether the visible chapter is bookmarked.
        binding.barAVRead.readBookmark.setOnClickListener {
            lifecycleScope.launch {
                val added = viewModel.toggleBookmark()
                Snackbar.make(
                    binding.root,
                    if (added) R.string.bookmark_added else R.string.bookmark_removed,
                    Snackbar.LENGTH_SHORT
                ).show()
                refreshBookmarkState()
            }
        }

        binding.barAVRead.readSettings.setOnClickListener { bottomSheetDialog.show() }
        binding.barAVRead.readCatalog.setOnClickListener { showChapterCatalog() }
        binding.barAVRead.readNight.setOnClickListener {
            viewModel.toggleReaderThemeMode()
            applyReaderColors()
        }
        bottomViewBinding.bVReadConfigHorizontal.setOnClickListener {
            (supportFragmentManager.findFragmentById(R.id.fcv_a_v_read) as? ReadFragment)
                ?.flushProgress()
            lifecycleScope.launch {
                startActivity<com.cyh128.hikari_novel.ui.read.horizontal.ReadActivity> {
                    putExtra("data", ReadParcel(viewModel.novel!!, viewModel.curVolumePos, viewModel.curChapterPos, false))
                }
                finish()
            }
        }





        // P0-3: Text alignment toggle
        bottomViewBinding.textAlignmentGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            when (checkedId) {
                R.id.b_alignment_left -> viewModel.setTextAlignment("left")
                R.id.b_alignment_justify -> viewModel.setTextAlignment("justify")
            }
        }
        when (viewModel.getTextAlignment()) {
            "justify" -> bottomViewBinding.textAlignmentGroup.check(R.id.b_alignment_justify)
            else -> bottomViewBinding.textAlignmentGroup.check(R.id.b_alignment_left)
        }

        // P0-3: First line indent toggle
        bottomViewBinding.firstLineIndentGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            when (checkedId) {
                R.id.b_indent_none -> viewModel.setFirstLineIndent(0)
                R.id.b_indent_two_chars -> viewModel.setFirstLineIndent(2)
            }
        }
        when (viewModel.getFirstLineIndent()) {
            2 -> bottomViewBinding.firstLineIndentGroup.check(R.id.b_indent_two_chars)
            else -> bottomViewBinding.firstLineIndentGroup.check(R.id.b_indent_none)
        }

        bottomViewBinding.sVVReadConfigKeepScreenOn.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setKeepScreenOn(isChecked)
            if (isChecked) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) //保持屏幕常亮
            else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        bottomViewBinding.sVVReadConfigRestoreChapterReadHistory.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setIsShowChapterReadHistory(isChecked)
            bottomViewBinding.sVVReadConfigRestoreChapterReadHistoryWithoutConfirm.isEnabled = isChecked
        }

        bottomViewBinding.sVVReadConfigRestoreChapterReadHistoryWithoutConfirm.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setIsShowChapterReadHistoryWithoutConfirm(isChecked)
        }

        // R4-1: Save both typed colours onto their axes at once.
        bottomViewBinding.iColorSection.bSaveCustomColor.setOnClickListener { saveCustomColor() }

        // R4-1: Restore both colour pairs to the factory defaults.
        bottomViewBinding.iColorSection.bRestoreDefaults.setOnClickListener { restoreColorDefaults() }
    }

    private fun showChapterCatalog() {
        val novel = viewModel.novel ?: return
        val visiblePosition = viewModel.latestVerticalPosition()
        val visibleVolume = visiblePosition?.chapter?.volumeIndex ?: viewModel.curVolumePos
        val visibleChapter = visiblePosition?.chapter?.chapterIndex ?: viewModel.curChapterPos
        ReadChapterCatalogBottomSheet
            .newInstance(novel, visibleVolume, visibleChapter)
            .apply {
                onPositionSelected = { target ->
                    viewModel.setPendingJump(target.anchor, target.highlightLength)
                    jumpToChapter(target.volumePos, target.chapterPos)
                }
            }
            .show(supportFragmentManager, "vertical_read_chapter_catalog")
    }

    override fun onStop() {
        flushReaderProgress()
        super.onStop()
    }

    private fun flushReaderProgress() {
        (supportFragmentManager.findFragmentById(R.id.fcv_a_v_read) as? ReadFragment)
            ?.flushProgress()
    }

    private fun jumpToChapter(volumePos: Int, chapterPos: Int) {
        flushReaderProgress()
        if (!viewModel.selectChapter(volumePos, chapterPos)) return
        showLoading()
        setBottomBarIsEnable(false)
    }

    fun toPreviousChapter() { //切换至上一章
        flushReaderProgress()
        lifecycleScope.launch {
            val target = viewModel.previousChapterLocation()
            if (target == null) {
                Snackbar.make(
                    binding.root,
                    R.string.no_previous_chapter,
                    Snackbar.LENGTH_INDEFINITE
                ).apply {
                    setAnchorView(binding.barAVRead.root)
                    setAction(R.string.ok) {}
                    show()
                }
                return@launch
            }
            if (!viewModel.loadChapter(target)) return@launch
            showLoading()
            setBottomBarIsEnable(false)
        }
    }

    fun toNextChapter() { //切换至下一章
        flushReaderProgress()
        lifecycleScope.launch {
            val target = viewModel.nextChapterLocation()
            if (target == null) {
                Snackbar.make(binding.root, R.string.no_next_chapter, Snackbar.LENGTH_INDEFINITE)
                    .apply {
                        setAnchorView(binding.barAVRead.root)
                        setAction(R.string.ok) {}
                        show()
                    }
                return@launch
            }
            if (!viewModel.loadChapter(target)) return@launch
            showLoading()
            setBottomBarIsEnable(false)
        }
    }

    fun autoLoadNextChapter(): Boolean {
        (supportFragmentManager.findFragmentById(R.id.fcv_a_v_read) as? ReadFragment)
            ?.flushProgress()
        val requested = viewModel.requestNextChapterLoad()
        if (requested) setBottomBarIsEnable(true)
        return requested
    }

    private fun updateColorInputsAndChapterProgress() {
        updateColorInputs()
        updateChapterProgress()
    }

    private fun showContent() { //显示小说正文内容
        supportFragmentManager
            .beginTransaction()
            .replace(R.id.fcv_a_v_read, ReadFragment())
            .commit()
        updateChapterProgress()
        refreshBookmarkState()
    }

    private fun showLoading() { //显示正在加载
        supportFragmentManager
            .beginTransaction()
            .replace(
                R.id.fcv_a_v_read,
                LoadingView().apply {
                    arguments = Bundle().also {
                        it.putString("color", getHideBarColorString())
                    }
                }
            )
            .commit()
        binding.statusAVRead.readStatusProgress.text =
            getString(R.string.reader_status_progress, 0)
        viewModel.progressText.value = "0%"
    }

    fun showBar() { //显示上下栏
        chromeController.show()
    }

    fun hideBar() { //隐藏上下栏
        chromeController.hide()
    }

    fun setRestoreChapterReadHistoryDisable() {
        bottomViewBinding.sVVReadConfigRestoreChapterReadHistory.isChecked = false
    }

    private fun getHideBarColorString() = readerColorResolver.bgColorHex()

    private fun getHideBarColor(): Int = android.graphics.Color.parseColor(getHideBarColorString())

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
            toolbar = binding.tbAVRead,
            statusLine = binding.statusAVRead.root,
            bottomDock = binding.readBottomDock,
            palette = currentChromePalette()
        )
        if (::chromeController.isInitialized) chromeController.refreshSystemBars()
    }

    /** Re-apply reader colors (text + bg) to the content area. */
    private fun applyReaderColors() {
        val fragment = supportFragmentManager
            .findFragmentById(R.id.fcv_a_v_read) as? ReadFragment
        fragment?.applyReaderColors(readerColorResolver.textColorHex(), readerColorResolver.bgColorHex())
        // Also update the loading view background if visible
        (supportFragmentManager.findFragmentById(R.id.fcv_a_v_read) as? com.cyh128.hikari_novel.ui.other.LoadingView)
            ?.view
            ?.background = ReaderGridDrawable.create(
                binding.root,
                readerColorResolver.bgColorHex()
            )
        applyReaderChrome()
    }

    /** 常显状态行：时间与电量，在分钟边界刷新（百分比由进度回写）。 */
    private fun refreshStatusBar() {
        binding.statusAVRead.readStatusTime.text = ReaderStatusText.time()
        val battery = ReaderStatusText.batteryLevel(this)
        binding.statusAVRead.readStatusBattery.text = if (battery == null) "--" else "$battery%"
    }

    /** 把 "45%" 形式的进度文本换算成滑块取值 */
    private fun parsePercent(text: String?): Float =
        text?.trim()?.removeSuffix("%")?.toFloatOrNull()?.coerceIn(0f, 100f) ?: 0f

    /** 跳转到已加载内容的指定百分比位置 */
    private fun scrollToPercent(percent: Int) {
        (supportFragmentManager.findFragmentById(R.id.fcv_a_v_read) as? ReadFragment)
            ?.scrollToPercent(percent)
    }

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
        applyReaderColors()
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
        val resolver = readerColorResolver
        val ring = resolver.textColorHex().toColorInt()
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
        applyReaderColors()
        updateColorInputs()
        refreshColorAxes()
        Snackbar.make(binding.root, R.string.color_restored, Snackbar.LENGTH_SHORT).show()
    }

    /** Update the hex input fields with current colors. */
    private fun updateColorInputs() {
        val resolver = readerColorResolver
        bottomViewBinding.iColorSection.editTextColor.setText(resolver.textColorHex().removePrefix("#"))
        bottomViewBinding.iColorSection.editBgColor.setText(resolver.bgColorHex().removePrefix("#"))
    }

    /** P1-2: Update chapter progress text in the bottom bar. */
    private fun updateChapterProgress() {
        binding.barAVRead.readChapterProgress.text = viewModel.chapterProgressText()
    }

    /** P1-3: Mirror the bookmark state of the visible chapter on the bottom bar icon. */
    private fun refreshBookmarkState() {
        lifecycleScope.launch {
            val bookmarked = viewModel.hasBookmarkForCurrentChapter()
            binding.barAVRead.readBookmark.setIconResource(
                if (bookmarked) {
                    R.drawable.ic_baseline_collections_bookmark
                } else {
                    R.drawable.ic_outline_collections_bookmark
                }
            )
        }
    }

    private fun setBottomBarIsEnable(isEnable: Boolean) {
        binding.barAVRead.readNextChapter.isEnabled = isEnable
        binding.barAVRead.readPreviousChapter.isEnabled = isEnable
        binding.barAVRead.readProgressSlider.isEnabled = isEnable
    }

    private companion object {
        /** 6-digit or 3-digit hex without the leading '#'. */
        val HEX_PATTERN = Regex("^([A-Fa-f0-9]{6}|[A-Fa-f0-9]{3})$")
    }
}
