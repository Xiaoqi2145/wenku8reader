package com.cyh128.hikari_novel.ui.read.vertical

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cyh128.hikari_novel.data.model.CachedChapter
import com.cyh128.hikari_novel.data.model.ChapterRef
import com.cyh128.hikari_novel.data.model.ReaderAnchor
import com.cyh128.hikari_novel.data.model.ReaderOrientation
import com.cyh128.hikari_novel.reader.domain.ChapterNavigator
import com.cyh128.hikari_novel.reader.domain.ReaderPosition
import com.cyh128.hikari_novel.util.ReaderAnchorMapper
import com.cyh128.hikari_novel.data.model.ReaderEffect
import com.cyh128.hikari_novel.data.model.ReaderUiState
import com.cyh128.hikari_novel.data.model.EmptyException
import com.cyh128.hikari_novel.data.model.Event
import com.cyh128.hikari_novel.data.model.Novel
import com.cyh128.hikari_novel.data.repository.ReadColorRepository
import com.cyh128.hikari_novel.data.repository.VerticalReadRepository
import com.cyh128.hikari_novel.data.repository.ReaderRepository
import com.drake.channel.sendEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import kotlin.properties.Delegates

@HiltViewModel
class ReadViewModel @Inject constructor(
    private val verticalReadRepository: VerticalReadRepository,
    private val readColorRepository: ReadColorRepository,
    private val readerRepository: ReaderRepository
) : ViewModel() {
    private val _readerState = MutableStateFlow<ReaderUiState>(ReaderUiState.Idle)
    val readerState = _readerState.asStateFlow()
    val readerEffects = MutableSharedFlow<ReaderEffect>(extraBufferCapacity = 8)
    private var loadJob: Job? = null
    private var loadGeneration = 0L
    private var lastLoadWasAppend = false
    var curChapterPos by Delegates.notNull<Int>()
    var curVolumePos by Delegates.notNull<Int>()
    var goToLatest = false //是否是上次阅读的章节

    var curNovelContent: String = "" //当前章节的小说内容
    var curImages: List<String> = emptyList() //当前小说的插图的链接列表

    var novel: Novel? = null

    private val aid get() = novel!!.aid
    private val cid get() = novel!!.volume[curVolumePos].chapters[curChapterPos].cid
    val chapterTitle get() = novel!!.volume[curVolumePos].chapters[curChapterPos].chapterTitle
    val curVolume get() = novel!!.volume[curVolumePos]

    var isBarShown = false //上下栏是否显示

    var progressText = MutableLiveData<String>()

    private data class VerticalProgressPosition(
        val position: ReaderPosition,
        val content: String
    )

    /** Latest mapped position in the rendered vertical session. */
    private var latestVerticalPosition: VerticalProgressPosition? = null
    private var loadedChapter: CachedChapter? = null
    private val verticalSessionController = VerticalReaderController()

    val getByCid get() = verticalReadRepository.getByCid(cid)

    private fun currentRef(): ChapterRef = ChapterRef(aid, cid, curVolumePos, curChapterPos, chapterTitle)

    fun currentChapterRef(): ChapterRef = currentRef()

    /** Controller/session retained across Fragment recreation and configuration changes. */
    fun verticalController(): VerticalReaderController = verticalSessionController

    /** All chapters currently visible in the continuous vertical session. */
    fun verticalSessionChapters(): List<CachedChapter> = verticalSessionController.loadedChapters

    /** Latest in-memory position survives Fragment recreation within this Activity. */
    fun latestVerticalPosition(): ReaderPosition? = latestVerticalPosition?.position

    /** Return the current Phase-3 load result for renderer/controller integration. */
    fun currentCachedChapter(): CachedChapter =
        loadedChapter?.takeIf { it.ref == currentRef() }
            ?: CachedChapter(
                ref = currentRef(),
                content = curNovelContent,
                images = curImages,
                updatedAt = System.currentTimeMillis()
            )

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

    /** Select and load a chapter as a replacement session. */
    fun selectChapter(volumeIndex: Int, chapterIndex: Int): Boolean {
        val target = chapterNavigator().chapterAt(volumeIndex, chapterIndex) ?: return false
        return loadChapter(target.chapter)
    }

    /** Load a navigated chapter without exposing index mutation to the Activity. */
    fun loadChapter(target: ChapterRef): Boolean {
        if (target.aid != novel?.aid) return false
        if (target.volumeIndex == curVolumePos && target.chapterIndex == curChapterPos) return false
        curVolumePos = target.volumeIndex
        curChapterPos = target.chapterIndex
        goToLatest = false
        getNovelContent()
        return true
    }

    /** Load the initial chapter only when no request/result is already active. */
    fun loadInitialContentIfNeeded() {
        when (_readerState.value) {
            ReaderUiState.Idle -> getNovelContent()
            is ReaderUiState.Error -> getNovelContent(
                forceNetwork = false,
                append = lastLoadWasAppend
            )
            else -> Unit
        }
    }

    fun getNovelContent(
        forceNetwork: Boolean = false,
        append: Boolean = false
    ) {
        val appendResult = append
        lastLoadWasAppend = appendResult
        curNovelContent = ""
        if (!appendResult) {
            latestVerticalPosition = null
            loadedChapter = null
            verticalSessionController.reset()
        }
        progressText.value = "0%"
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
                    loadedChapter = chapter
                    verticalSessionController.acceptLoaded(chapter, append = appendResult)
                    _readerState.value = ReaderUiState.Ready(chapter, source)
                    sendEvent(
                        Event.VerticalReaderLoadSuccessEvent(
                            aid = ref.aid,
                            cid = ref.cid,
                            generation = generation,
                            append = appendResult
                        ),
                        "event_vertical_read_activity"
                    )
                    nextRef()?.let { next ->
                        viewModelScope.launch(Dispatchers.IO) { readerRepository.prefetch(next) }
                    }
                }.onFailure { failure ->
                    if (!isCurrentLoad(generation, ref)) return@onFailure
                    _readerState.value = ReaderUiState.Error(ref, failure.message ?: "加载失败", hasCachedContent = false)
                    sendEvent(
                        Event.VerticalReaderLoadErrorEvent(
                            aid = ref.aid,
                            cid = ref.cid,
                            generation = generation,
                            message = failure.message,
                            empty = failure is EmptyException
                        ),
                        "event_vertical_read_activity"
                    )
                }
        }
    }

    private fun isCurrentLoad(generation: Long, ref: ChapterRef): Boolean =
        generation == loadGeneration && novel != null && currentRef() == ref

    fun acceptsLoadEvent(aid: String, cid: String, generation: Long): Boolean =
        generation == loadGeneration && novel?.aid == aid && novel?.let { currentRef().cid == cid } == true

    fun retryNovelContent() = getNovelContent(forceNetwork = true, append = lastLoadWasAppend)

    /** Request a continuous append without exposing a mutable load-mode flag to the UI. */
    fun requestNextChapterLoad(): Boolean {
        val target = nextChapterLocation() ?: return false
        curVolumePos = target.volumeIndex
        curChapterPos = target.chapterIndex
        goToLatest = false
        getNovelContent(append = true)
        return true
    }

    fun enqueueDownloads(refs: List<ChapterRef>) {
        viewModelScope.launch { readerRepository.enqueueDownloads(refs) }
    }

    private fun nextRef(): ChapterRef? = nextChapterLocation()

    /** Retain the mapped chapter/content pair and update the visible percentage. */
    fun updateVerticalProgress(
        position: ReaderPosition,
        displayPercent: Int,
        content: String
    ) {
        progressText.value = "${displayPercent.coerceIn(0, 100)}%"
        if (position.chapter.aid == novel?.aid && position.anchor.cid == position.chapter.cid) {
            latestVerticalPosition = VerticalProgressPosition(
                position = position.copy(
                    anchor = ReaderAnchorMapper.clamp(position.anchor, content)
                ),
                content = content
            )
        }
    }

    //保存阅读记录
    suspend fun saveReadHistory() {
        val mapped = latestVerticalPosition
        if (mapped != null && mapped.content.isNotBlank()) {
            readerRepository.saveProgress(
                progress = ReaderAnchorMapper.clamp(mapped.position.anchor, mapped.content),
                ref = mapped.position.chapter,
                orientation = ReaderOrientation.Vertical,
                // ScrollView pixels are deliberately not persisted.
                legacyLocation = null
            )
            return
        }
        if (curNovelContent.isBlank()) return

        val ref = currentRef()
        val percent = progressText.value?.substringBefore("%")?.toIntOrNull()?.coerceIn(0, 100) ?: 0
        readerRepository.saveProgress(
            progress = ReaderAnchorMapper.fromPercent(ref.cid, curNovelContent, percent),
            ref = ref,
            orientation = ReaderOrientation.Vertical,
            legacyLocation = null
        )
    }

    suspend fun saveReaderAnchor(anchor: ReaderAnchor) {
        if (curNovelContent.isNotBlank() && anchor.cid == cid) {
            saveReaderAnchor(anchor, currentRef(), curNovelContent)
        }
    }

    suspend fun saveReaderAnchor(
        anchor: ReaderAnchor,
        chapter: ChapterRef,
        content: String
    ) {
        savePosition(ReaderPosition(chapter, anchor), content)
    }

    suspend fun savePosition(
        position: ReaderPosition,
        content: String
    ) {
        if (position.anchor.cid != position.chapter.cid || content.isBlank()) return
        readerRepository.saveProgress(
            progress = ReaderAnchorMapper.clamp(position.anchor, content),
            ref = position.chapter,
            orientation = ReaderOrientation.Vertical,
            legacyLocation = null
        )
    }

    /** Persist from the ViewModel scope so Fragment recreation cannot cancel the write. */
    fun persistPosition(
        position: ReaderPosition,
        content: String
    ): Job = viewModelScope.launch(Dispatchers.IO) {
        savePosition(position, content)
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
