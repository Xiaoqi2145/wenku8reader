package com.cyh128.hikari_novel.ui.read.vertical

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.WindowManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.OnBackPressedCallback
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.Lifecycle
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.withStarted
import com.cyh128.hikari_novel.R
import com.cyh128.hikari_novel.base.BaseActivity
import com.cyh128.hikari_novel.data.model.Event
import com.cyh128.hikari_novel.data.model.ReaderUiState
import com.cyh128.hikari_novel.data.model.ReadParcel
import com.cyh128.hikari_novel.databinding.ActivityVerticalReadBinding
import com.cyh128.hikari_novel.databinding.ViewVerticalReadConfigBinding
import com.cyh128.hikari_novel.ui.other.LoadingView
import com.cyh128.hikari_novel.ui.read.SelectColorActivity
import com.cyh128.hikari_novel.ui.read.catalog.ReadChapterCatalogBottomSheet
import com.cyh128.hikari_novel.ui.read.common.ReaderChromeController
import com.cyh128.hikari_novel.ui.read.common.ReaderWindowInsetsCoordinator
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ReadActivity : BaseActivity<ActivityVerticalReadBinding>() {
    private lateinit var bottomViewBinding: ViewVerticalReadConfigBinding
    private lateinit var bottomSheetDialog: BottomSheetDialog
    private lateinit var chromeController: ReaderChromeController
    private lateinit var windowInsetsCoordinator: ReaderWindowInsetsCoordinator
    private val viewModel by lazy { ViewModelProvider(this)[ReadViewModel::class.java] }
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
        return true
    }

    private fun initView() {
        refreshStatusBar()
        val showBarColor = MaterialColors.getColor(
            binding.root,
            R.attr.giSurfaceReaderChrome
        )
        chromeController = ReaderChromeController(
            window = window,
            chromeHost = binding.root,
            toolbar = binding.tbAVRead,
            bottomDock = binding.readBottomDock,
            statusLine = binding.statusAVRead.root,
            shownBarColor = showBarColor,
            hiddenBarColor = ::getHideBarColor,
            onVisibilityChanged = { viewModel.isBarShown = it }
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
        bottomViewBinding.sVVReadConfigFontSize.value = viewModel.getFontSize()
        bottomViewBinding.sVVReadConfigLineSpacing.value = viewModel.getLineSpacing()
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
        if (viewModel.readerState.value is ReaderUiState.Ready) {
            showContent()
        } else if (viewModel.readerState.value !is ReaderUiState.Loading) {
            showLoading()
        }
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
        viewModel.progressText.observe(this) {
            binding.statusAVRead.readStatusProgress.text = it
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

        binding.barAVRead.readSettings.setOnClickListener { bottomSheetDialog.show() }
        binding.barAVRead.readCatalog.setOnClickListener { showChapterCatalog() }
        binding.barAVRead.readNight.setOnClickListener {
            AppCompatDelegate.setDefaultNightMode(
                if (getIsInDarkMode()) AppCompatDelegate.MODE_NIGHT_NO else AppCompatDelegate.MODE_NIGHT_YES
            )
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

        bottomViewBinding.sVVReadConfigFontSize.addOnSliderTouchListener(
            object : Slider.OnSliderTouchListener {
                override fun onStartTrackingTouch(slider: Slider) {}
                override fun onStopTrackingTouch(slider: Slider) {
                    viewModel.setFontSize(bottomViewBinding.sVVReadConfigFontSize.value)
                }
            }
        )

        bottomViewBinding.sVVReadConfigLineSpacing.addOnSliderTouchListener(
            object : Slider.OnSliderTouchListener {
                override fun onStartTrackingTouch(slider: Slider) {}
                override fun onStopTrackingTouch(slider: Slider) {
                    viewModel.setLineSpacing(bottomViewBinding.sVVReadConfigLineSpacing.value)
                }
            }
        )

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

        val activityResultLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                //当SelectColorActivity退出后所要做的语句体
                if (result.resultCode == RESULT_OK) { //刷新界面，使设置的文字颜色和背景颜色生效
                    supportFragmentManager
                        .beginTransaction()
                        .replace(R.id.fcv_a_v_read, ReadFragment())
                        .commit()
                }
            }

        bottomViewBinding.bVVReadConfigChangeColor.setOnClickListener {
            activityResultLauncher.launch(
                Intent(
                    this,
                    SelectColorActivity::class.java
                )
            )
        }
    }

    private fun showChapterCatalog() {
        val novel = viewModel.novel ?: return
        val visiblePosition = viewModel.latestVerticalPosition()
        val visibleVolume = visiblePosition?.chapter?.volumeIndex ?: viewModel.curVolumePos
        val visibleChapter = visiblePosition?.chapter?.chapterIndex ?: viewModel.curChapterPos
        ReadChapterCatalogBottomSheet
            .newInstance(novel, visibleVolume, visibleChapter)
            .apply {
                onChapterSelected = { volumePos, chapterPos ->
                    jumpToChapter(volumePos, chapterPos)
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

    private fun showContent() { //显示小说正文内容
        supportFragmentManager
            .beginTransaction()
            .replace(R.id.fcv_a_v_read, ReadFragment())
            .commit()
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

    private fun getHideBarColorString() = getIsInDarkMode().let {
        if (it) "#" + viewModel.getBgColorNight()
        else "#" + viewModel.getBgColorDay()
    }

    private fun getHideBarColor(): Int = android.graphics.Color.parseColor(getHideBarColorString())

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

    private fun setBottomBarIsEnable(isEnable: Boolean) {
        binding.barAVRead.readNextChapter.isEnabled = isEnable
        binding.barAVRead.readPreviousChapter.isEnabled = isEnable
        binding.barAVRead.readProgressSlider.isEnabled = isEnable
    }
}
