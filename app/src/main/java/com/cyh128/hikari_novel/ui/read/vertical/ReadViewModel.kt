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
import com.cyh128.hikari_novel.reader.domain.ReaderSessionCoordinator
import com.cyh128.hikari_novel.util.ReaderAnchorMapper
import com.cyh128.hikari_novel.data.model.ReaderEffect
import com.cyh128.hikari_novel.data.model.ReaderUiState
import com.cyh128.hikari_novel.data.model.EmptyException
import com.cyh128.hikari_novel.data.model.Event
import com.cyh128.hikari_novel.data.model.Novel
import com.cyh128.hikari_novel.data.model.chapterRefAt
import com.cyh128.hikari_novel.data.model.chapterRefs
import com.cyh128.hikari_novel.data.repository.ReadColorRepository
import com.cyh128.hikari_novel.data.repository.VerticalReadRepository
import com.cyh128.hikari_novel.data.repository.AppRepository
import com.drake.channel.sendEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import kotlin.properties.Delegates

@HiltViewModel
class ReadViewModel @Inject constructor(
    private val verticalReadRepository: VerticalReadRepository,
    private val readColorRepository: ReadColorRepository,
    private val appRepository: AppRepository,
    private val sessionCoordinator: ReaderSessionCoordinator
) : ViewModel() {
    private val _readerState = MutableStateFlow<ReaderUiState>(ReaderUiState.Idle)
    val readerState = _readerState.asStateFlow()
    val readerEffects = MutableSharedFlow<ReaderEffect>(extraBufferCapacity = 8)
    private var loadJob: Job? = null
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

    var isBarShown = true //上下栏是否显示（与进入阅读页时的 showBar() 保持一致）
    var progressText = MutableLiveData<String>()

    val isVerticalReadFirstLaunch get() = appRepository.getIsVerticalFirstLaunch()

    fun setIsVerticalReadFirstLaunch(value: Boolean) {
        appRepository.setIsVerticalFirstLaunch(value)
    }

    private data class VerticalProgressPosition(
        val position: ReaderPosition,
        val content: String,
        val legacyLocation: Int?
    )

    /** Latest mapped position in the rendered vertical session. */
    private var latestVerticalPosition: VerticalProgressPosition? = null
    private var loadedChapter: CachedChapter? = null
    private val verticalSessionController = VerticalReaderController()

    val getByCid get() = verticalReadRepository.getByCid(aid, cid)

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

    fun sessionLimitReached(): Boolean = verticalSessionController.sessionLimitReached

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
        configureSession()
        return sessionCoordinator.chapterNavigator()
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
        configureSession()
        curNovelContent = ""
        if (!appendResult) {
            latestVerticalPosition = null
            loadedChapter = null
            verticalSessionController.reset()
        }
        progressText.value = "0%"
        val ref = currentRef()
        val request = sessionCoordinator.beginLoad(ref, append = appendResult)
        loadJob?.cancel()
        _readerState.value = ReaderUiState.Loading(ref)
        loadJob = viewModelScope.launch(Dispatchers.IO) {
            sessionCoordinator.load(request, forceNetwork)
                .onSuccess { loaded ->
                    if (!sessionCoordinator.isCurrent(request)) return@onSuccess
                    curNovelContent = loaded.chapter.content
                    curImages = loaded.chapter.images
                    loadedChapter = loaded.chapter
                    verticalSessionController.acceptLoaded(loaded.chapter, append = appendResult)
                    _readerState.value = ReaderUiState.Ready(loaded.chapter, loaded.source)
                    sendEvent(
                        Event.VerticalReaderLoadSuccessEvent(
                            aid = ref.aid,
                            cid = ref.cid,
                            generation = request.generation,
                            append = appendResult
                        ),
                        "event_vertical_read_activity"
                    )
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
                    sendEvent(
                        Event.VerticalReaderLoadErrorEvent(
                            aid = ref.aid,
                            cid = ref.cid,
                            generation = request.generation,
                            message = failure.message,
                            empty = failure is EmptyException
                        ),
                        "event_vertical_read_activity"
                    )
                }
        }
    }

    fun acceptsLoadEvent(aid: String, cid: String, generation: Long): Boolean =
        sessionCoordinator.acceptsLoadEvent(generation, aid, cid)

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
        viewModelScope.launch { sessionCoordinator.enqueueDownloads(refs) }
    }

    /** Retain the mapped chapter/content pair and update the visible percentage. */
    fun updateVerticalProgress(
        position: ReaderPosition,
        displayPercent: Int,
        content: String,
        legacyLocation: Int? = null
    ) {
        progressText.value = "${displayPercent.coerceIn(0, 100)}%"
        if (position.chapter.aid == novel?.aid && position.anchor.cid == position.chapter.cid) {
            latestVerticalPosition = VerticalProgressPosition(
                position = position,
                content = content,
                legacyLocation = legacyLocation
            )
        }
    }

    //保存阅读记录
    suspend fun saveReadHistory() {
        val mapped = latestVerticalPosition
        if (mapped != null && mapped.content.isNotBlank()) {
            sessionCoordinator.saveProgress(
                progress = mapped.position.anchor,
                ref = mapped.position.chapter,
                orientation = ReaderOrientation.Vertical,
                // The reporter supplies a chapter-local legacy position when available.
                legacyLocation = mapped.legacyLocation
            )
            return
        }
        if (curNovelContent.isBlank()) return

        val ref = currentRef()
        val percent = progressText.value?.substringBefore("%")?.toIntOrNull()?.coerceIn(0, 100) ?: 0
        sessionCoordinator.saveProgress(
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
        content: String,
        legacyLocation: Int? = null
    ) {
        if (position.anchor.cid != position.chapter.cid || content.isBlank()) return
        sessionCoordinator.saveProgress(
            progress = ReaderAnchorMapper.clamp(position.anchor, content),
            ref = position.chapter,
            orientation = ReaderOrientation.Vertical,
            legacyLocation = legacyLocation
        )
    }

    /** Persist from the ViewModel scope so Fragment recreation cannot cancel the write. */
    fun persistPosition(
        position: ReaderPosition,
        content: String,
        legacyLocation: Int? = null
    ): Job = viewModelScope.launch(Dispatchers.IO) {
        savePosition(position, content, legacyLocation)
    }

    suspend fun getReaderProgress() =
        sessionCoordinator.getProgress(currentRef(), curNovelContent, ReaderOrientation.Vertical)

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
