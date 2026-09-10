package com.cyh128.hikari_novel.ui.read.horizontal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cyh128.hikari_novel.data.model.ChapterRef
import com.cyh128.hikari_novel.data.model.ReaderAnchor
import com.cyh128.hikari_novel.data.model.ReaderOrientation
import com.cyh128.hikari_novel.reader.domain.ChapterNavigator
import com.cyh128.hikari_novel.reader.domain.ReaderSessionCoordinator
import com.cyh128.hikari_novel.util.ReaderAnchorMapper
import com.cyh128.hikari_novel.data.model.ReaderEffect
import com.cyh128.hikari_novel.data.model.ReaderUiState
import com.cyh128.hikari_novel.data.model.EmptyException
import com.cyh128.hikari_novel.data.model.Event
import com.cyh128.hikari_novel.data.model.Novel
import com.cyh128.hikari_novel.data.model.chapterRefAt
import com.cyh128.hikari_novel.data.model.chapterRefs
import com.cyh128.hikari_novel.data.repository.AppRepository
import com.cyh128.hikari_novel.data.repository.HorizontalReadRepository
import com.cyh128.hikari_novel.data.repository.ReadColorRepository
import com.drake.channel.sendEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.properties.Delegates

@HiltViewModel
class ReadViewModel @Inject constructor(
    private val horizontalReadRepository: HorizontalReadRepository,
    private val readColorRepository: ReadColorRepository,
    private val appRepository: AppRepository,
    private val sessionCoordinator: ReaderSessionCoordinator
) : ViewModel() {
    private val _readerState = MutableStateFlow<ReaderUiState>(ReaderUiState.Idle)
    val readerState = _readerState.asStateFlow()
    val readerEffects = MutableSharedFlow<ReaderEffect>(extraBufferCapacity = 8)
    private var loadJob: Job? = null

    var curChapterPos by Delegates.notNull<Int>()
    var curVolumePos by Delegates.notNull<Int>()

    private val aid get() = novel!!.aid
    private val cid get() = novel!!.volume[curVolumePos].chapters[curChapterPos].cid
    val chapterTitle get() = novel!!.volume[curVolumePos].chapters[curChapterPos].chapterTitle
    val curVolume get() = novel!!.volume[curVolumePos]

    var novel: Novel? = null
    lateinit var curNovelContent: String //当前章节的小说内容
    lateinit var curImages: List<String> //当前小说的插图的链接列表

    var isBarShown = true //上下栏是否显示
    var goToLatest = false

    val getByCid get() = horizontalReadRepository.getByCid(aid, cid)

    val isHorizontalReadFirstLaunch get() = appRepository.getIsHorizontalFirstLaunch()

    fun setIsHorizontalReadFirstLaunch(isHorizontalReadFirstLaunch: Boolean) {
        appRepository.setIsHorizontalFirstLaunch(isHorizontalReadFirstLaunch)
    }

    private fun currentRefOrNull(): ChapterRef? {
        val currentNovel = novel ?: return null
        val volumeIndex = runCatching { curVolumePos }.getOrNull() ?: return null
        val chapterIndex = runCatching { curChapterPos }.getOrNull() ?: return null
        return currentNovel.chapterRefAt(volumeIndex, chapterIndex)
    }

    private fun currentRef(): ChapterRef =
        currentRefOrNull() ?: error("Reader chapter has not been initialized")

    private fun configureSession() {
        sessionCoordinator.configure(novel?.chapterRefs().orEmpty(), currentRefOrNull())
    }

    fun currentChapterRef(): ChapterRef = currentRef()

    fun chapterNavigator(): ChapterNavigator {
        configureSession()
        return sessionCoordinator.chapterNavigator()
    }

    fun nextChapterLocation() = chapterNavigator().next(currentRef())

    fun previousChapterLocation() = chapterNavigator().previous(currentRef())

    fun selectChapter(volumeIndex: Int, chapterIndex: Int): Boolean {
        val target = chapterNavigator().chapterAt(volumeIndex, chapterIndex) ?: return false
        return loadChapter(target.chapter)
    }

    fun loadChapter(target: ChapterRef): Boolean {
        if (target.aid != novel?.aid) return false
        if (target.volumeIndex == curVolumePos && target.chapterIndex == curChapterPos) return false
        curVolumePos = target.volumeIndex
        curChapterPos = target.chapterIndex
        goToLatest = false
        getNovelContent()
        return true
    }

    fun getNovelContent(forceNetwork: Boolean = false) {
        configureSession()
        curNovelContent = ""
        val ref = currentRef()
        val request = sessionCoordinator.beginLoad(ref, append = false)
        loadJob?.cancel()
        _readerState.value = ReaderUiState.Loading(ref)
        loadJob = viewModelScope.launch(Dispatchers.IO) {
            sessionCoordinator.load(request, forceNetwork)
                .onSuccess { loaded ->
                    if (!sessionCoordinator.isCurrent(request)) return@onSuccess
                    curNovelContent = loaded.chapter.content
                    curImages = loaded.chapter.images
                    _readerState.value = ReaderUiState.Ready(loaded.chapter, loaded.source)
                    sendEvent(Event.LoadSuccessEvent, "event_horizontal_read_activity")
                    viewModelScope.launch(Dispatchers.IO) {
                        sessionCoordinator.prefetchNext(request)
                    }
                }.onFailure { failure ->
                    if (!sessionCoordinator.isCurrent(request)) return@onFailure
                    _readerState.value = ReaderUiState.Error(
                        ref,
                        failure.message ?: "加载失败",
                        hasCachedContent = false
                    )
                    if (failure is EmptyException) {
                        sendEvent(Event.EmptyContentEvent, "event_horizontal_read_activity")
                    } else {
                        sendEvent(Event.NetworkErrorEvent(failure.message), "event_horizontal_read_activity")
                    }
                }
        }
    }

    fun retryNovelContent() = getNovelContent(forceNetwork = true)

    fun enqueueDownloads(refs: List<ChapterRef>) {
        viewModelScope.launch { sessionCoordinator.enqueueDownloads(refs) }
    }

    //保存阅读记录
    suspend fun saveReadHistory(readPos: Int, maxNum: Int) {
        if (curNovelContent.isNotBlank()) {
            val percent = if (maxNum > 0) {
                (readPos.toFloat() / maxNum * 100).toInt().coerceIn(0, 100)
            } else {
                0
            }
            sessionCoordinator.saveProgress(
                progress = ReaderAnchorMapper.fromPercent(cid, curNovelContent, percent),
                ref = currentRef(),
                orientation = ReaderOrientation.Horizontal,
                legacyLocation = readPos
            )
        }
    }

    suspend fun saveReaderAnchor(anchor: ReaderAnchor) {
        if (curNovelContent.isNotBlank() && anchor.cid == cid) {
            sessionCoordinator.saveProgress(
                progress = ReaderAnchorMapper.clamp(anchor, curNovelContent),
                ref = currentRef(),
                orientation = ReaderOrientation.Horizontal,
                legacyLocation = null
            )
        }
    }

    suspend fun getReaderProgress() =
        sessionCoordinator.getProgress(currentRef(), curNovelContent, ReaderOrientation.Horizontal)

    fun setFontSize(size: Float) {
        horizontalReadRepository.setFontSize(size)
    }

    fun getFontSize() = horizontalReadRepository.getFontSize()

    fun getBottomFontSize() = horizontalReadRepository.getBottomFontSize()

    fun setBottomFontSize(size: Float) {
        horizontalReadRepository.setBottomFontSize(size)
    }

    fun setLineSpacing(lineSpacing: Float) {
        horizontalReadRepository.setLineSpacing(lineSpacing)
    }

    fun getLineSpacing() = horizontalReadRepository.getLineSpacing()

    fun getTextColorDay() = readColorRepository.getTextColorDay()

    fun getTextColorNight() = readColorRepository.getTextColorNight()

    fun getBgColorDay() = readColorRepository.getBgColorDay()

    fun getBgColorNight() = readColorRepository.getBgColorNight()

    fun getKeyDownSwitchChapter() = horizontalReadRepository.getKeyDownSwitchChapter()

    fun setKeyDownSwitchChapter(value: Boolean) {
        horizontalReadRepository.setKeyDownSwitchChapter(value)
    }

    fun getKeepScreenOn() = horizontalReadRepository.getKeepScreenOn()

    fun setKeepScreenOn(value: Boolean) {
        horizontalReadRepository.setKeepScreenOn(value)
    }

    fun getSwitchAnimation() = horizontalReadRepository.getSwitchAnimation()

    fun setSwitchAnimation(value: Boolean) {
        horizontalReadRepository.setSwitchAnimation(value)
    }

    fun getTabletDoublePage() = horizontalReadRepository.getTabletDoublePage()

    fun setTabletDoublePage(value: Boolean) {
        horizontalReadRepository.setTabletDoublePage(value)
    }

    fun getIsShowChapterReadHistory() = horizontalReadRepository.getIsShowChapterReadHistory()

    fun setIsShowChapterReadHistory(value: Boolean) {
        horizontalReadRepository.setIsShowChapterReadHistory(value)
    }

    fun getIsShowChapterReadHistoryWithoutConfirm() =
        horizontalReadRepository.getIsShowChapterReadHistoryWithoutConfirm()

    fun setIsShowChapterReadHistoryWithoutConfirm(value: Boolean) {
        horizontalReadRepository.setIsShowChapterReadHistoryWithoutConfirm(value)
    }
}
