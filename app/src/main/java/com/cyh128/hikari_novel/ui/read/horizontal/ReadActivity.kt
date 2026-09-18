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
import androidx.appcompat.app.AppCompatDelegate
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
import com.cyh128.hikari_novel.data.source.local.database.read_history.horizontal_read_history.HorizontalReadHistoryEntity
import com.cyh128.hikari_novel.databinding.ActivityHorizontalReadBinding
import com.cyh128.hikari_novel.databinding.ViewHorizontalReadConfigBinding
import com.cyh128.hikari_novel.ui.read.SelectColorActivity
import com.cyh128.hikari_novel.ui.read.catalog.ReadChapterCatalogBottomSheet
import com.cyh128.hikari_novel.ui.read.common.ReaderChromeController
import com.cyh128.hikari_novel.ui.read.common.ReaderChromeGeometry
import com.cyh128.hikari_novel.ui.read.common.ReaderWindowInsetsCoordinator
import com.cyh128.hikari_novel.util.ThemeHelper
import com.cyh128.hikari_novel.util.getIsInDarkMode
import com.cyh128.hikari_novel.util.ReaderStatusText
import com.cyh128.hikari_novel.util.startActivity
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
                            viewModel.getReaderProgress()?.let { progress ->
                                binding.pvAHRead.post {
                                    if (binding.pvAHRead.maxPageNum > 0) {
                                        binding.pvAHRead.pageNum =
                                            ((progress.normalizedPercent / 100f) * binding.pvAHRead.maxPageNum)
                                                .toInt().coerceIn(1, binding.pvAHRead.maxPageNum)
                                    }
                                }
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
        binding.statusAHRead.readStatusProgress.text = "0%"
        //初始化阅读器的背景颜色和字体颜色
        binding.pvAHRead.backgroundcolor =
            if (ThemeHelper.isDarkMode()) ("#" + viewModel.getBgColorNight()).toColorInt()
            else ("#" + viewModel.getBgColorDay()).toColorInt()
        binding.pvAHRead.textColor =
            if (ThemeHelper.isDarkMode()) ("#" + viewModel.getTextColorNight()).toColorInt()
            else ("#" + viewModel.getTextColorDay()).toColorInt()

        setBottomBarIsEnable(false)

        if (viewModel.isHorizontalReadFirstLaunch) {
            binding.iAHReadGuide.root.setOnClickListener {
                binding.iAHReadGuide.root.visibility = View.GONE
                viewModel.setIsHorizontalReadFirstLaunch(false)
            }
        } else binding.iAHReadGuide.root.visibility = View.GONE

        val showBarColor = MaterialColors.getColor(
            binding.root,
            R.attr.giSurfaceReaderChrome
        )
        chromeController = ReaderChromeController(
            window = window,
            chromeHost = binding.root,
            toolbar = binding.tbAHRead,
            bottomDock = binding.readBottomDock,
            statusLine = binding.statusAHRead.root,
            shownBarColor = showBarColor,
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

        bottomViewBinding.sVHReadConfigFontSize.value = viewModel.getFontSize()
        bottomViewBinding.sVHReadConfigBottomTextSize.value = viewModel.getBottomFontSize()
        bottomViewBinding.sVHReadConfigLineSpacing.value = viewModel.getLineSpacing()
        bottomViewBinding.sVHReadConfigKeyDownSwitchChapter.isChecked = viewModel.getKeyDownSwitchChapter()
        bottomViewBinding.sVHReadConfigSwitchAnimation.isChecked = viewModel.getSwitchAnimation()
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
        binding.pvAHRead.switchAnimation = viewModel.getSwitchAnimation()
        binding.pvAHRead.doublePageEnabled = viewModel.getTabletDoublePage()

        setColor()
        showBar()
    }

    private fun setColor() {
        if (getIsInDarkMode()) { //夜间模式
            binding.pvAHRead.textColor = ("#" + viewModel.getTextColorNight()).toColorInt()
            binding.pvAHRead.backgroundcolor = ("#" + viewModel.getBgColorNight()).toColorInt()
        } else {
            binding.pvAHRead.textColor = ("#" + viewModel.getTextColorDay()).toColorInt()
            binding.pvAHRead.backgroundcolor = ("#" + viewModel.getBgColorDay()).toColorInt()
        }
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

        val activityResultLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                //当SelectColorActivity退出后所要做的语句体
                if (result.resultCode == RESULT_OK) { //刷新界面，使设置的文字颜色和背景颜色生效
                    setColor()
                }
            }

        binding.pvAHRead.setImageClickListener { imageUrl ->
            startActivity<com.cyh128.hikari_novel.ui.other.PhotoViewActivity> {
                putExtra("url", imageUrl)
            }
        }

        binding.pvAHRead.onCenterClick = object : IPageView.OnCenterClick {
            override fun onCenterClick() {
                binding.root.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
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
                        return
                    } else {
                        binding.barAHRead.readProgressSlider.visibility = View.VISIBLE
                    }

                    binding.barAHRead.readProgressSlider.valueTo = binding.pvAHRead.maxPageNum.toFloat()
                    binding.barAHRead.readProgressSlider.value = index.toFloat()
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
                    slider.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                    binding.pvAHRead.pageNum = slider.value.toInt()
                    updateProgressPercent(slider.value.toInt(), binding.pvAHRead.maxPageNum)
                }
            }
        )

        bottomViewBinding.sVHReadConfigFontSize.addOnSliderTouchListener(
            object : Slider.OnSliderTouchListener {
                override fun onStartTrackingTouch(slider: Slider) {}

                override fun onStopTrackingTouch(slider: Slider) {
                    binding.pvAHRead.textSize = slider.value
                    viewModel.setFontSize(slider.value)
                }
            }
        )

        bottomViewBinding.sVHReadConfigBottomTextSize.addOnSliderTouchListener(
            object : Slider.OnSliderTouchListener {
                override fun onStartTrackingTouch(slider: Slider) {}

                override fun onStopTrackingTouch(slider: Slider) {
                    binding.pvAHRead.bottomTextSize = slider.value
                    viewModel.setBottomFontSize(slider.value)
                }
            }
        )

        bottomViewBinding.sVHReadConfigLineSpacing.addOnSliderTouchListener(
            object : Slider.OnSliderTouchListener {
                override fun onStartTrackingTouch(slider: Slider) {}

                override fun onStopTrackingTouch(slider: Slider) {
                    binding.pvAHRead.rowSpace = slider.value
                    viewModel.setLineSpacing(slider.value)
                }
            }
        )

        bottomViewBinding.bVHReadConfigChangeColor.setOnClickListener {
            activityResultLauncher.launch(
                Intent(
                    this,
                    SelectColorActivity::class.java
                )
            )
        }

        binding.barAHRead.readSettings.setOnClickListener { bottomSheetDialog.show() }
        binding.barAHRead.readCatalog.setOnClickListener { showChapterCatalog() }
        binding.barAHRead.readNight.setOnClickListener {
            AppCompatDelegate.setDefaultNightMode(
                if (getIsInDarkMode()) AppCompatDelegate.MODE_NIGHT_NO else AppCompatDelegate.MODE_NIGHT_YES
            )
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
            it.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
            toNextChapter()
        }

        binding.barAHRead.readPreviousChapter.setOnClickListener {
            it.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
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

        bottomViewBinding.sVHReadConfigSwitchAnimation.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setSwitchAnimation(isChecked)
            binding.pvAHRead.switchAnimation = isChecked
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
                onChapterSelected = { volumePos, chapterPos ->
                    jumpToChapter(volumePos, chapterPos)
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

    private fun getHideBarColorString() = getIsInDarkMode().let {
        if (it) "#" + viewModel.getBgColorNight()
        else "#" + viewModel.getBgColorDay()
    }

    private fun getHideBarColor(): Int = getHideBarColorString().toColorInt()

    private fun setBottomBarIsEnable(value: Boolean) {
        binding.barAHRead.readNextChapter.isEnabled = value
        binding.barAHRead.readPreviousChapter.isEnabled = value
        binding.barAHRead.readProgressSlider.isEnabled = value
    }

    /** 与竖排保持一致，用百分比文字补充说明滑块进度 */
    private fun updateProgressPercent(pageNum: Int, maxPageNum: Int) {
        val percent = if (maxPageNum > 0) (pageNum * 100 / maxPageNum).coerceIn(0, 100) else 0
        binding.statusAHRead.readStatusProgress.text = "$percent%"
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
}
