package com.cyh128.hikari_novel.ui.read.horizontal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cyh128.hikari_novel.data.model.ChapterRef
import com.cyh128.hikari_novel.data.model.ReaderAnchor
import com.cyh128.hikari_novel.reader.domain.ChapterNavigator
import com.cyh128.hikari_novel.util.ReaderAnchorMapper
import com.cyh128.hikari_novel.data.model.ReaderEffect
import com.cyh128.hikari_novel.data.model.ReaderUiState
import com.cyh128.hikari_novel.data.model.EmptyException
import com.cyh128.hikari_novel.data.model.Event
import com.cyh128.hikari_novel.data.model.Novel
import com.cyh128.hikari_novel.data.repository.AppRepository
import com.cyh128.hikari_novel.data.repository.HorizontalReadRepository
import com.cyh128.hikari_novel.data.repository.ReadColorRepository
import com.cyh128.hikari_novel.data.repository.Wenku8Repository
import com.cyh128.hikari_novel.data.repository.ReaderRepository
import com.cyh128.hikari_novel.data.source.local.database.read_history.horizontal_read_history.HorizontalReadHistoryEntity
import com.drake.channel.sendEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import kotlin.properties.Delegates

@HiltViewModel
class ReadViewModel @Inject constructor(
    private val wenku8Repository: Wenku8Repository,
    private val horizontalReadRepository: HorizontalReadRepository,
    private val readColorRepository: ReadColorRepository,
    private val appRepository: AppRepository,
    private val readerRepository: ReaderRepository
) : ViewModel() {
    private val _readerState = MutableStateFlow<ReaderUiState>(ReaderUiState.Idle)
    val readerState = _readerState.asStateFlow()
    val readerEffects = MutableSharedFlow<ReaderEffect>(extraBufferCapacity = 8)
    private var loadJob: Job? = null
    private var loadGeneration = 0L
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

    val getByCid get() = horizontalReadRepository.getByCid(cid)

    val isHorizontalReadFirstLaunch get() = appRepository.getIsHorizontalFirstLaunch()

    fun setIsHorizontalReadFirstLaunch(isHorizontalReadFirstLaunch: Boolean) {
        appRepository.setIsHorizontalFirstLaunch(isHorizontalReadFirstLaunch)
    }

    private fun currentRef(): ChapterRef = ChapterRef(aid, cid, curVolumePos, curChapterPos, chapterTitle)

    fun currentChapterRef(): ChapterRef = currentRef()

    fun chapterNavigator(): ChapterNavigator {
        val currentNovel = novel ?: return ChapterNavigator(emptyList())
        return ChapterNavigator(currentNovel.volume.mapIndexed { volumeIndex, volume ->
            volume.chapters.mapIndexed { chapterIndex, chapter ->
                ChapterRef(
                    aid = currentNovel.aid,
                    cid = chapter.cid,
                    volumeIndex = volumeIndex,
                    chapterIndex = chapterIndex,
                    title = chapter.chapterTitle
                )
            }
        })
    }

    fun nextChapterLocation() = chapterNavigator().next(currentRef())

    fun previousChapterLocation() = chapterNavigator().previous(currentRef())

    fun getNovelContent(forceNetwork: Boolean = false) {
        curNovelContent = ""
        val ref = currentRef()
        val generation = ++loadGeneration
        loadJob?.cancel()
        _readerState.value = ReaderUiState.Loading(ref)
        loadJob = viewModelScope.launch(Dispatchers.IO) {
            readerRepository.load(ref, forceNetwork)
                .onSuccess { (chapter, source) ->
                    if (!isCurrentLoad(generation, ref)) return@onSuccess
                    curNovelContent = chapter.content
                    curImages = chapter.images
                    _readerState.value = ReaderUiState.Ready(chapter, source)
                    sendEvent(Event.LoadSuccessEvent, "event_horizontal_read_activity")
                    nextRef()?.let { next ->
                        viewModelScope.launch(Dispatchers.IO) { readerRepository.prefetch(next) }
                    }
                }.onFailure { failure ->
                    if (!isCurrentLoad(generation, ref)) return@onFailure
                    _readerState.value = ReaderUiState.Error(ref, failure.message ?: "加载失败", hasCachedContent = false)
                    if (failure is EmptyException) sendEvent(Event.EmptyContentEvent, "event_horizontal_read_activity")
                    else sendEvent(Event.NetworkErrorEvent(failure.message), "event_horizontal_read_activity")
                }
        }
    }

    private fun isCurrentLoad(generation: Long, ref: ChapterRef): Boolean =
        generation == loadGeneration && novel != null && currentRef() == ref

    fun retryNovelContent() = getNovelContent(forceNetwork = true)

    fun enqueueDownloads(refs: List<ChapterRef>) {
        viewModelScope.launch { readerRepository.enqueueDownloads(refs) }
    }

    private fun nextRef(): ChapterRef? = nextChapterLocation()?.chapter

    //保存阅读记录
    suspend fun saveReadHistory(readPos: Int, maxNum: Int) {
        if (curNovelContent.isNotBlank()) {
            val percent = if (maxNum > 0) (readPos.toFloat() / maxNum * 100).toInt().coerceIn(0, 100) else 0
            horizontalReadRepository.addOrReplace(
                aid,
                HorizontalReadHistoryEntity(
                    cid,
                    aid,
                    curVolumePos,
                    curChapterPos,
                    readPos,
                    percent,
                    true
                )
            )
            readerRepository.saveProgress(
                ReaderAnchorMapper.fromPercent(cid, curNovelContent, percent),
                currentRef()
            )
        }
    }

    suspend fun saveReaderAnchor(anchor: ReaderAnchor) {
        if (curNovelContent.isNotBlank() && anchor.cid == cid) {
            readerRepository.saveProgress(ReaderAnchorMapper.clamp(anchor, curNovelContent), currentRef())
        }
    }

    suspend fun getReaderProgress() = readerRepository.getProgress(cid)

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
