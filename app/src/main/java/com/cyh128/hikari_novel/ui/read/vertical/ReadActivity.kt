package com.cyh128.hikari_novel.ui.read.vertical


import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.WindowManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.OnBackPressedCallback
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.Lifecycle
import com.cyh128.hikari_novel.util.ReaderColorResolver
import com.cyh128.hikari_novel.util.ReaderColorSwatch
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.withStarted
import com.cyh128.hikari_novel.R
import com.cyh128.hikari_novel.base.BaseActivity
import com.cyh128.hikari_novel.data.model.Event
import com.cyh128.hikari_novel.data.model.ReaderColorPreset
import com.cyh128.hikari_novel.data.model.ReaderColorPresets
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
import com.cyh128.hikari_novel.ui.read.common.ReaderWindowInsetsCoordinator
import com.cyh128.hikari_novel.util.ReaderStatusText
import com.cyh128.hikari_novel.util.startActivity
import com.drake.channel.receiveEvent
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
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
        // P0-4: Slider rows with value display and real-time preview
        setupSliderRow(bottomViewBinding.sliderFontSize, getString(R.string.font_size), 1f, 50f, 1f, viewModel.getFontSize()) {
            viewModel.setFontSize(it)
        }
        setupSliderRow(bottomViewBinding.sliderLineSpacing, getString(R.string.line_spacing), 1f, 50f, 1f, viewModel.getLineSpacing()) {
            viewModel.setLineSpacing(it)
        }
        setupSliderRow(bottomViewBinding.sliderParagraphSpacing, getString(R.string.paragraph_spacing), 0f, 40f, 1f, viewModel.getParagraphSpacing()) {
            viewModel.setParagraphSpacing(it)
        }
        setupSliderRow(bottomViewBinding.sliderLetterSpacing, getString(R.string.letter_spacing), 0f, 20f, 1f, viewModel.getLetterSpacing()) {
            viewModel.setLetterSpacing(it)
        }

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
        // P1-2: Unified progress semantics: slider = loaded-content scroll ratio,
        // status line = chapter x/y + full-book %.
        viewModel.progressText.observe(this) {
            val fullBookPercent = parsePercent(it).toInt()
            binding.statusAVRead.readStatusProgress.text = getString(
                R.string.reader_status_progress,
                viewModel.chapterProgressText(),
                fullBookPercent
            )
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
                    slider.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                    scrollToPercent(slider.value.toInt())
                }
            }
        )

        //设置底栏菜单按钮监听
        binding.barAVRead.readPreviousChapter.setOnClickListener {
            it.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
            toPreviousChapter()
        }

        binding.barAVRead.readNextChapter.setOnClickListener {
            it.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
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

        // P0-2: Preset colour buttons
        setupPresetButtons(
            listOf(
                bottomViewBinding.bPresetColor1,
                bottomViewBinding.bPresetColor2,
                bottomViewBinding.bPresetColor3,
                bottomViewBinding.bPresetColor4
            )
        )

        // P0-2: Save custom color
        bottomViewBinding.bVVReadConfigSaveCustomColor.setOnClickListener { saveCustomColor() }

        // P0-2: Restore defaults
        bottomViewBinding.bVVReadConfigRestoreDefaults.setOnClickListener { restoreColorDefaults() }
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
        binding.statusAVRead.readStatusProgress.text = "0%"
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

    /** Chrome colours follow the reading colours, not the App theme. */
    private fun currentChromePalette(): ReaderChromePalette =
        ReaderChromePalette.from(
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
            ?.setBackgroundColor(android.graphics.Color.parseColor(readerColorResolver.bgColorHex()))
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
     * Configure a reusable slider row with label, value display, range, and real-time preview.
     * P0-4: Real-time preview via onValueChange.
     */
    private fun setupSliderRow(
        binding: com.cyh128.hikari_novel.databinding.ViewReaderSliderRowBinding,
        label: String,
        valueFrom: Float,
        valueTo: Float,
        stepSize: Float,
        initialValue: Float,
        onValueChange: (Float) -> Unit
    ) {
        binding.sliderRowLabel.text = label
        binding.sliderRowSlider.valueFrom = valueFrom
        binding.sliderRowSlider.valueTo = valueTo
        binding.sliderRowSlider.stepSize = stepSize
        binding.sliderRowSlider.value = initialValue
        binding.sliderRowValue.setText(initialValue.toString())

        // Real-time preview: onValueChange fires during drag
        binding.sliderRowSlider.addOnChangeListener { _, value, _ ->
            onValueChange(value)
            binding.sliderRowValue.setText(value.toString())
        }

        // Keyboard input: submit on focus loss
        binding.sliderRowValue.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val newValue = binding.sliderRowValue.text.toString().toFloatOrNull()
                if (newValue != null) {
                    val clamped = newValue.coerceIn(valueFrom, valueTo)
                    binding.sliderRowSlider.value = clamped
                    onValueChange(clamped)
                    binding.sliderRowValue.setText(clamped.toString())
                } else {
                    binding.sliderRowValue.setText(binding.sliderRowSlider.value.toString())
                }
            }
        }
    }

    /**
     * P0-2: Wire the four colour presets.
     *
     * The swatch mirrors Koodo's colour blocks, so the presets are identifiable
     * without tapping each one; the label and content description carry the
     * index and the preset name for accessibility.
     */
    private fun setupPresetButtons(buttons: List<MaterialButton>) {
        buttons.forEachIndexed { index, button ->
            val preset = ReaderColorPresets.PRESETS.getOrNull(index) ?: return@forEachIndexed
            button.text = getString(R.string.color_preset_label, index + 1)
            button.icon = ReaderColorSwatch.create(this, preset.bgColor, preset.textColor)
            button.contentDescription = preset.name
            button.setOnClickListener { applyPreset(index) }
        }
    }

    /** P0-2: Apply a preset color by index. */
    private fun applyPreset(index: Int) {
        val preset = ReaderColorPresets.PRESETS.getOrNull(index) ?: return
        viewModel.readColorRepository().applyPreset(preset)
        applyReaderColors()
        updateColorInputs()
        Snackbar.make(binding.root, getString(R.string.color_preset_applied, preset.name), Snackbar.LENGTH_SHORT).show()
    }

    /** P0-2: Save current hex colors as a custom preset. */
    private fun saveCustomColor() {
        val textColor = bottomViewBinding.editTextColor.text.toString().trim()
        val bgColor = bottomViewBinding.editBgColor.text.toString().trim()
        if (!HEX_PATTERN.matches(textColor) || !HEX_PATTERN.matches(bgColor)) {
            Snackbar.make(binding.root, R.string.color_format_invalid, Snackbar.LENGTH_SHORT).show()
            return
        }
        val preset = ReaderColorPreset(
            name = getString(
                R.string.custom_color_name,
                java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault())
                    .format(java.util.Date())
            ),
            textColor = textColor,
            bgColor = bgColor
        )
        val result = viewModel.readColorRepository().addCustomColor(preset)
        Snackbar.make(
            binding.root,
            if (result) R.string.custom_color_saved else R.string.custom_color_save_failed,
            Snackbar.LENGTH_SHORT
        ).show()
    }

    /** P0-2: Restore default colors. */
    private fun restoreColorDefaults() {
        viewModel.readColorRepository().restoreDefaults()
        applyReaderColors()
        updateColorInputs()
        Snackbar.make(binding.root, R.string.color_restored, Snackbar.LENGTH_SHORT).show()
    }

    /** Update the hex input fields with current colors. */
    private fun updateColorInputs() {
        val resolver = readerColorResolver
        bottomViewBinding.editTextColor.setText(resolver.textColorHex().removePrefix("#"))
        bottomViewBinding.editBgColor.setText(resolver.bgColorHex().removePrefix("#"))
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
