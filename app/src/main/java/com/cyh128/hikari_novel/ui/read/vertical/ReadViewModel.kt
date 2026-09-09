package com.cyh128.hikari_novel.ui.read.vertical

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cyh128.hikari_novel.data.model.ChapterRef
import com.cyh128.hikari_novel.data.model.ReaderAnchor
import com.cyh128.hikari_novel.data.model.ReaderOrientation
import com.cyh128.hikari_novel.reader.domain.ChapterNavigator
import com.cyh128.hikari_novel.util.ReaderAnchorMapper
import com.cyh128.hikari_novel.data.model.ReaderEffect
import com.cyh128.hikari_novel.data.model.ReaderUiState
import com.cyh128.hikari_novel.data.model.EmptyException
import com.cyh128.hikari_novel.data.model.Event
import com.cyh128.hikari_novel.data.model.Novel
import com.cyh128.hikari_novel.data.repository.ReadColorRepository
import com.cyh128.hikari_novel.data.repository.VerticalReadRepository
import com.cyh128.hikari_novel.data.repository.Wenku8Repository
import com.cyh128.hikari_novel.data.repository.ReaderRepository
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
    private val verticalReadRepository: VerticalReadRepository,
    private val readColorRepository: ReadColorRepository,
    private val readerRepository: ReaderRepository
) : ViewModel() {
    private val _readerState = MutableStateFlow<ReaderUiState>(ReaderUiState.Idle)
    val readerState = _readerState.asStateFlow()
    val readerEffects = MutableSharedFlow<ReaderEffect>(extraBufferCapacity = 8)
    private var loadJob: Job? = null
    private var loadGeneration = 0L
    var curChapterPos by Delegates.notNull<Int>()
    var curVolumePos by Delegates.notNull<Int>()
    var goToLatest = false //是否是上次阅读的章节

    lateinit var curNovelContent: String //当前章节的小说内容
    lateinit var curImages: List<String> //当前小说的插图的链接列表

    var novel: Novel? = null

    private val aid get() = novel!!.aid
    private val cid get() = novel!!.volume[curVolumePos].chapters[curChapterPos].cid
    val chapterTitle get() = novel!!.volume[curVolumePos].chapters[curChapterPos].chapterTitle
    val curVolume get() = novel!!.volume[curVolumePos]

    var isBarShown = false //上下栏是否显示

    var progressText = MutableLiveData<String>()
    var appendCurrentChapter = false

    var curReadPos = 0 //阅读位置

    val getByCid get() = verticalReadRepository.getByCid(cid)

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
                    sendEvent(Event.LoadSuccessEvent, "event_vertical_read_activity")
                    nextRef()?.let { next ->
                        viewModelScope.launch(Dispatchers.IO) { readerRepository.prefetch(next) }
                    }
                }.onFailure { failure ->
                    if (!isCurrentLoad(generation, ref)) return@onFailure
                    _readerState.value = ReaderUiState.Error(ref, failure.message ?: "加载失败", hasCachedContent = false)
                    if (failure is EmptyException) sendEvent(Event.EmptyContentEvent, "event_vertical_read_activity")
                    else sendEvent(Event.NetworkErrorEvent(failure.message), "event_vertical_read_activity")
                }
        }
    }

    private fun isCurrentLoad(generation: Long, ref: ChapterRef): Boolean =
        generation == loadGeneration && novel != null && currentRef() == ref

    fun retryNovelContent() = getNovelContent(forceNetwork = true)

    fun enqueueDownloads(refs: List<ChapterRef>) {
        viewModelScope.launch { readerRepository.enqueueDownloads(refs) }
    }

    private fun nextRef(): ChapterRef? = nextChapterLocation()

    //保存阅读记录
    suspend fun saveReadHistory() {
        if (curNovelContent.isNotBlank()) {
            val percent = progressText.value?.substringBefore("%")?.toIntOrNull()?.coerceIn(0, 100) ?: 0
            readerRepository.saveProgress(
                progress = ReaderAnchorMapper.fromPercent(cid, curNovelContent, percent),
                ref = currentRef(),
                orientation = ReaderOrientation.Vertical,
                legacyLocation = curReadPos
            )
        }
    }

    suspend fun saveReaderAnchor(anchor: ReaderAnchor) {
        if (curNovelContent.isNotBlank() && anchor.cid == cid) {
            readerRepository.saveProgress(
                progress = ReaderAnchorMapper.clamp(anchor, curNovelContent),
                ref = currentRef(),
                orientation = ReaderOrientation.Vertical,
                legacyLocation = null
            )
        }
    }

    suspend fun getReaderProgress() =
        readerRepository.getProgress(currentRef(), curNovelContent, ReaderOrientation.Vertical)

    fun setFontSize(size: Float) {
        viewModelScope.launch {
            verticalReadRepository.setFontSize(size)
            sendEvent(Event.ChangeFontSizeEvent(size), "event_vertical_read_fragment")
        }
    }

    fun getFontSize() = verticalReadRepository.getFontSize()

    fun setLineSpacing(lineSpacing: Float) {
        viewModelScope.launch {
            verticalReadRepository.setLineSpacing(lineSpacing)
            sendEvent(Event.ChangeLineSpacingEvent(lineSpacing), "event_vertical_read_fragment")
        }
    }

    fun getLineSpacing() = verticalReadRepository.getLineSpacing()

    fun getKeepScreenOn() = verticalReadRepository.getKeepScreenOn()

    fun setKeepScreenOn(value: Boolean) {
        verticalReadRepository.setKeepScreenOn(value)
    }

    fun getTextColorDay() = readColorRepository.getTextColorDay()

    fun getTextColorNight() = readColorRepository.getTextColorNight()

    fun getBgColorDay() = readColorRepository.getBgColorDay()

    fun getBgColorNight() = readColorRepository.getBgColorNight()

    fun getIsShowChapterReadHistory() = verticalReadRepository.getIsShowChapterReadHistory()

    fun setIsShowChapterReadHistory(value: Boolean) {
        verticalReadRepository.setIsShowChapterReadHistory(value)
    }

    fun getIsShowChapterReadHistoryWithoutConfirm() =
        verticalReadRepository.getIsShowChapterReadHistoryWithoutConfirm()

    fun setIsShowChapterReadHistoryWithoutConfirm(value: Boolean) {
        verticalReadRepository.setIsShowChapterReadHistoryWithoutConfirm(value)
    }
}
