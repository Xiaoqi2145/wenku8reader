package com.cyh128.hikari_novel.ui.read.vertical

import android.animation.Animator
import android.content.Intent
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.Menu
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.activity.result.contract.ActivityResultContracts
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
import com.cyh128.hikari_novel.util.getIsInDarkMode
import com.cyh128.hikari_novel.util.ReaderStatusText
import com.cyh128.hikari_novel.util.startActivity
import com.drake.channel.receiveEvent
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.slider.Slider
import com.google.android.material.snackbar.Snackbar
import com.gyf.immersionbar.ImmersionBar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.properties.Delegates

@AndroidEntryPoint
class ReadActivity : BaseActivity<ActivityVerticalReadBinding>() {
    private lateinit var bottomViewBinding: ViewVerticalReadConfigBinding
    private lateinit var bottomSheetDialog: BottomSheetDialog
    private val viewModel by lazy { ViewModelProvider(this)[ReadViewModel::class.java] }
    private var showBarColor by Delegates.notNull<Int>()
    /** 用户正在拖动进度滑块时暂停外部回写，避免抖动 */
    private var progressSliderTracking = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
                    delay(30_000)
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
        TypedValue().also {  //上下栏颜色初始化
            theme.resolveAttribute(
                com.google.android.material.R.attr.colorSurfaceContainer,
                it,
                true
            )
            showBarColor = it.resourceId
        }

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
                    scrollToPercent(slider.value.toInt())
                }
            }
        )

        //设置底栏菜单按钮监听
        binding.barAVRead.readPreviousChapter.setOnClickListener {
            toPreviousChapter()
        }

        binding.barAVRead.readNextChapter.setOnClickListener {
            toNextChapter()
        }

        binding.barAVRead.readConfig.setOnClickListener {
            bottomSheetDialog.show()
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
                        it.putString("color", getHideBarColor())
                    }
                }
            )
            .commit()
        binding.statusAVRead.readStatusProgress.text = "0%"
        viewModel.progressText.value = "0%"
    }

    fun showBar() { //显示上下栏
        binding.tbAVRead.animate().translationY(0f)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .setListener(object : Animator.AnimatorListener {
                override fun onAnimationStart(animation: Animator) {
                    ImmersionBar.with(this@ReadActivity)
                        .statusBarColor(showBarColor)
                        .navigationBarColor(showBarColor)
                        .fitsSystemWindows(true)
                        .autoDarkModeEnable(true)
                        .init()
                }

                override fun onAnimationEnd(animation: Animator) {}
                override fun onAnimationCancel(animation: Animator) {}
                override fun onAnimationRepeat(animation: Animator) {}
            })
        binding.barAVRead.root.animate().translationY(0f)
            .setInterpolator(AccelerateDecelerateInterpolator())
        viewModel.isBarShown = true
    }

    fun hideBar() { //隐藏上下栏
        binding.tbAVRead.animate().translationY(-(binding.tbAVRead.bottom.toFloat()))
            .setInterpolator(AccelerateDecelerateInterpolator())
            .setListener(object : Animator.AnimatorListener {
                override fun onAnimationStart(animation: Animator) {}
                override fun onAnimationEnd(animation: Animator) {
                    ImmersionBar.with(this@ReadActivity)
                        .statusBarColor(getHideBarColor())
                        .navigationBarColor(getHideBarColor())
                        .fitsSystemWindows(true)
                        .autoDarkModeEnable(true)
                        .init()
                }

                override fun onAnimationCancel(animation: Animator) {}
                override fun onAnimationRepeat(animation: Animator) {}
            })
        //按实际位置计算，保证底栏（含底部边距）完全移出屏幕
        binding.barAVRead.root.animate()
            .translationY((binding.root.height - binding.barAVRead.root.top).toFloat())
            .setInterpolator(AccelerateDecelerateInterpolator())
        viewModel.isBarShown = false
    }

    fun setRestoreChapterReadHistoryDisable() {
        bottomViewBinding.sVVReadConfigRestoreChapterReadHistory.isChecked = false
    }

    private fun getHideBarColor() = getIsInDarkMode().let {
        if (it) { //夜间模式
            "#" + viewModel.getBgColorNight()
        } else {
            "#" + viewModel.getBgColorDay()
        }
    }

    /** 常显状态行：时间与电量，每 30 秒刷新一次（百分比由进度回写） */
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
        binding.barAVRead.readConfig.isEnabled = isEnable
        binding.barAVRead.readNextChapter.isEnabled = isEnable
        binding.barAVRead.readPreviousChapter.isEnabled = isEnable
        binding.barAVRead.readProgressSlider.isEnabled = isEnable
    }
}
