package com.cyh128.hikari_novel.ui.read.catalog

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import androidx.core.os.BundleCompat
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.cyh128.hikari_novel.R
import com.cyh128.hikari_novel.data.model.ChapterRef
import com.cyh128.hikari_novel.data.model.Novel
import com.cyh128.hikari_novel.data.model.ReaderAnchor
import com.cyh128.hikari_novel.data.model.chapterRefs
import com.cyh128.hikari_novel.data.repository.ReaderRepository
import com.cyh128.hikari_novel.data.repository.reader.BookSearchHit
import com.cyh128.hikari_novel.data.repository.reader.BookSearchMatcher
import com.cyh128.hikari_novel.databinding.FragmentReadChapterCatalogBinding
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import java.util.Locale
import javax.inject.Inject

/**
 * Right-side chapter drawer shared by the reader and the detail-page downloader.
 *
 * The dialog window keeps the existing END-side behavior while the content is split
 * into a cover/volume rail and a remaining-height chapter list. The drawer hosts
 * three tabs — 目录 / 书签 / 搜索 — so bookmarks (P1-3) and in-book search (P1-4)
 * live next to the chapter list instead of in separate screens.
 */
@AndroidEntryPoint
class ReadChapterCatalogBottomSheet : DialogFragment() {
    /** Jump to a chapter, optionally at a specific anchor inside it. */
    var onPositionSelected: ((ReaderJumpTarget) -> Unit)? = null
    var onDownloadRequested: ((List<ChapterRef>) -> Unit)? = null

    @Inject
    lateinit var readerRepository: ReaderRepository

    private var _binding: FragmentReadChapterCatalogBinding? = null
    private val binding get() = _binding!!
    private lateinit var novel: Novel
    private var currentVolumePos = 0
    private var currentChapterPos = 0
    private var selectedVolumePos = 0
    private var allowDownload = false
    private var coverUrl = ""
    private var restoredSelectedCids: List<String> = emptyList()
    private var restoredVolumeScrollPosition = RecyclerViewPosition.UNSET
    private var restoredChapterScrollPosition = RecyclerViewPosition.UNSET
    private lateinit var volumeLayoutManager: LinearLayoutManager
    private lateinit var chapterLayoutManager: LinearLayoutManager

    // ===== P1-4: in-book search state =====

    private var searchJob: Job? = null
    private var searchQuery = ""
    private val hitsByCid = linkedMapOf<String, List<BookSearchHit>>()
    private val collapsedCids = mutableSetOf<String>()
    private val searchedCids = mutableSetOf<String>()
    private var scannedChapters = 0
    private var onlineRemaining = 0
    private var onlineTotal = 0
    private var onlineDone = 0
    private var searchAdapter: ReadSearchResultAdapter? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog =
        Dialog(requireContext()).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            setCanceledOnTouchOutside(true)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        novel = BundleCompat.getParcelable(requireArguments(), ARG_NOVEL, Novel::class.java)
            ?: Novel(aid = "", volume = emptyList())
        currentVolumePos = clampVolume(requireArguments().getInt(ARG_CURRENT_VOLUME))
        currentChapterPos = clampChapter(
            currentVolumePos,
            requireArguments().getInt(ARG_CURRENT_CHAPTER)
        )
        selectedVolumePos = if (savedInstanceState?.containsKey(ARG_SELECTED_VOLUME) == true) {
            clampVolume(savedInstanceState.getInt(ARG_SELECTED_VOLUME))
        } else {
            currentVolumePos
        }
        allowDownload = requireArguments().getBoolean(ARG_ALLOW_DOWNLOAD, false)
        coverUrl = requireArguments().getString(ARG_COVER_URL).orEmpty()
            .ifBlank { defaultCoverUrl(novel.aid) }
            .replaceFirst("http://", "https://")
        restoredSelectedCids = savedInstanceState?.getStringArrayList(ARG_SELECTED_CIDS).orEmpty()
        restoredVolumeScrollPosition = savedInstanceState?.getInt(
            ARG_VOLUME_SCROLL,
            RecyclerViewPosition.UNSET
        ) ?: RecyclerViewPosition.UNSET
        restoredChapterScrollPosition = savedInstanceState?.getInt(
            ARG_CHAPTER_SCROLL,
            RecyclerViewPosition.UNSET
        ) ?: RecyclerViewPosition.UNSET
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentReadChapterCatalogBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setDimAmount(BACKGROUND_DIM_AMOUNT)
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setGravity(Gravity.END)
            decorView.setPadding(0, 0, 0, 0)
            setWindowAnimations(R.style.Animation_GalIsland_ReadCatalog)
            setLayout(calculatePanelWidth(), WindowManager.LayoutParams.MATCH_PARENT)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.tvFReadChapterCatalogTitle.text = novel.title.ifBlank {
            getString(R.string.chapter_catalog)
        }
        binding.tvFReadChapterCatalogSubtitle.text = currentPositionSummary()
        binding.ivFReadChapterCatalogCover.contentDescription =
            novel.title.ifBlank { getString(R.string.cover) }
        Glide.with(binding.ivFReadChapterCatalogCover)
            .load(coverUrl)
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .placeholder(R.drawable.ic_empty)
            .error(R.drawable.ic_empty)
            .fitCenter()
            .into(binding.ivFReadChapterCatalogCover)

        volumeLayoutManager = LinearLayoutManager(requireContext())
        val volumeAdapter = ReadChapterCatalogVolumeAdapter(
            volumes = novel.volume,
            initialSelectedVolume = selectedVolumePos,
            onVolumeSelected = ::selectVolume
        )
        binding.rvFReadChapterCatalogVolumes.layoutManager = volumeLayoutManager
        binding.rvFReadChapterCatalogVolumes.adapter = volumeAdapter
        binding.rvFReadChapterCatalogVolumes.overScrollMode = View.OVER_SCROLL_ALWAYS

        chapterLayoutManager = LinearLayoutManager(requireContext())
        val chapterAdapter = ReadChapterCatalogAdapter(
            novel = novel,
            currentVolumePos = currentVolumePos,
            currentChapterPos = currentChapterPos,
            selectedVolumePos = selectedVolumePos,
            onChapterClick = { volumePos, chapterPos -> requestJump(volumePos, chapterPos, null) },
            allowDownload = allowDownload,
            onDownload = { refs -> onDownloadRequested?.invoke(refs) }
        )
        if (restoredSelectedCids.isNotEmpty()) {
            chapterAdapter.restoreSelectedChapterCids(restoredSelectedCids)
        }
        binding.rvFReadChapterCatalog.layoutManager = chapterLayoutManager
        binding.rvFReadChapterCatalog.adapter = chapterAdapter
        binding.rvFReadChapterCatalog.overScrollMode = View.OVER_SCROLL_ALWAYS

        binding.tvFReadChapterCatalogVolumeCount.text = getString(
            R.string.catalog_volume_count_total,
            novel.volume.size
        )
        binding.bFReadChapterCatalogClose.setOnClickListener { dismiss() }
        binding.bFReadChapterCatalogSelectAll.visibility =
            if (allowDownload) View.VISIBLE else View.GONE
        binding.bFReadChapterCatalogDownload.visibility =
            if (allowDownload) View.VISIBLE else View.GONE
        binding.bFReadChapterCatalogSelectAll.setOnClickListener { chapterAdapter.selectAll() }
        binding.bFReadChapterCatalogDownload.setOnClickListener {
            chapterAdapter.downloadSelected()
            dismiss()
        }
        updateChapterCount()
        setupTabs()
        setupBookmarkTab()
        setupSearchTab()

        binding.rvFReadChapterCatalogVolumes.post {
            if (restoredVolumeScrollPosition != RecyclerViewPosition.UNSET) {
                volumeLayoutManager.scrollToPosition(restoredVolumeScrollPosition)
            } else {
                centerSelectedVolume()
            }
        }
        binding.rvFReadChapterCatalog.post {
            if (restoredChapterScrollPosition != RecyclerViewPosition.UNSET) {
                chapterLayoutManager.scrollToPosition(restoredChapterScrollPosition)
            } else {
                centerCurrentChapter(chapterAdapter)
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(ARG_SELECTED_VOLUME, selectedVolumePos)
        (bindingOrNull()?.rvFReadChapterCatalog?.adapter as? ReadChapterCatalogAdapter)?.let { adapter ->
            outState.putStringArrayList(ARG_SELECTED_CIDS, adapter.selectedChapterCids())
        }
        if (this::volumeLayoutManager.isInitialized) {
            outState.putInt(
                ARG_VOLUME_SCROLL,
                volumeLayoutManager.findFirstVisibleItemPosition()
            )
        }
        if (this::chapterLayoutManager.isInitialized) {
            outState.putInt(
                ARG_CHAPTER_SCROLL,
                chapterLayoutManager.findFirstVisibleItemPosition()
            )
        }
        super.onSaveInstanceState(outState)
    }

    override fun onDestroyView() {
        searchJob?.cancel()
        searchAdapter = null
        super.onDestroyView()
        _binding = null
    }

    private fun bindingOrNull(): FragmentReadChapterCatalogBinding? = _binding

    /**
     * Single jump entry point for the chapter list, the bookmark tab and the
     * search results. Readers register [onPositionSelected] and can restore an
     * anchor; the detail page only listens for a fragment result and has none.
     */
    private fun requestJump(
        volumePos: Int,
        chapterPos: Int,
        anchor: ReaderAnchor? = null,
        highlightLength: Int = 0
    ) {
        val callback = onPositionSelected
        if (callback != null) {
            callback.invoke(
                ReaderJumpTarget(
                    volumePos = volumePos,
                    chapterPos = chapterPos,
                    anchor = anchor,
                    highlightLength = highlightLength
                )
            )
        } else {
            parentFragmentManager.setFragmentResult(
                RESULT_KEY,
                Bundle().apply {
                    putInt(RESULT_VOLUME, volumePos)
                    putInt(RESULT_CHAPTER, chapterPos)
                }
            )
        }
        dismiss()
    }

    // ===== P1-3 / P1-4: tabs =====

    private fun setupTabs() {
        val tabs = binding.tlFReadChapterCatalog
        tabs.addTab(tabs.newTab().setText(R.string.chapter_catalog))
        tabs.addTab(tabs.newTab().setText(R.string.bookmark))
        tabs.addTab(tabs.newTab().setText(R.string.search_in_book))
        tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) = showTab(tab.position)
            override fun onTabUnselected(tab: TabLayout.Tab) = Unit
            override fun onTabReselected(tab: TabLayout.Tab) = Unit
        })
        showTab(TAB_CATALOG)
    }

    private fun showTab(index: Int) {
        binding.llFReadChapterCatalogGroup.isVisible = index == TAB_CATALOG
        binding.llFReadChapterCatalogBookmarks.isVisible = index == TAB_BOOKMARKS
        binding.llFReadChapterCatalogSearch.isVisible = index == TAB_SEARCH
        if (index == TAB_SEARCH) binding.etFReadChapterCatalogSearch.requestFocus()
    }

    // ===== P1-3: bookmark tab =====

    private fun setupBookmarkTab() {
        val adapter = ReadBookmarkAdapter(
            onOpen = { row ->
                requestJump(
                    row.bookmark.volumeIndex,
                    row.bookmark.chapterIndex,
                    ReaderAnchor(
                        cid = row.bookmark.cid,
                        paragraphIndex = row.bookmark.paragraphIndex,
                        charOffset = row.bookmark.charOffset,
                        normalizedPercent = row.bookmark.normalizedPercent,
                        imageIndex = row.bookmark.imageIndex
                    )
                )
            },
            onDelete = { row ->
                viewLifecycleOwner.lifecycleScope.launch {
                    readerRepository.deleteBookmark(row.bookmark.id)
                    _binding?.let {
                        Snackbar.make(it.root, R.string.bookmark_deleted, Snackbar.LENGTH_SHORT).show()
                    }
                }
            }
        )
        binding.rvFReadChapterCatalogBookmarks.layoutManager = LinearLayoutManager(requireContext())
        binding.rvFReadChapterCatalogBookmarks.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                readerRepository.observeBookmarks(novel.aid).collect { bookmarks ->
                    val rows = bookmarks.map { bookmark ->
                        BookmarkRow(
                            bookmark = bookmark,
                            chapterTitle = chapterTitleOf(bookmark.volumeIndex, bookmark.chapterIndex)
                        )
                    }
                    adapter.submitList(rows)
                    binding.tvFReadChapterCatalogBookmarkCount.text =
                        getString(R.string.bookmark_count, rows.size)
                    binding.tvFReadChapterCatalogBookmarkEmpty.isVisible = rows.isEmpty()
                }
            }
        }
    }

    private fun chapterTitleOf(volumePos: Int, chapterPos: Int): String =
        novel.volume.getOrNull(volumePos)?.chapters?.getOrNull(chapterPos)?.chapterTitle
            ?: getString(R.string.chapter_catalog)

    // ===== P1-4: in-book search =====

    private fun setupSearchTab() {
        val adapter = ReadSearchResultAdapter(
            onToggleGroup = { header ->
                if (!collapsedCids.remove(header.ref.cid)) collapsedCids += header.ref.cid
                publishSearchRows()
            },
            onOpenHit = { row ->
                requestJump(
                    volumePos = row.hit.ref.volumeIndex,
                    chapterPos = row.hit.ref.chapterIndex,
                    anchor = ReaderAnchor(
                        cid = row.hit.ref.cid,
                        paragraphIndex = 0,
                        charOffset = row.hit.charOffset,
                        normalizedPercent = row.hit.normalizedPercent
                    ),
                    highlightLength = row.hit.matchLength
                )
            }
        )
        searchAdapter = adapter
        binding.rvFReadChapterCatalogSearch.layoutManager = LinearLayoutManager(requireContext())
        binding.rvFReadChapterCatalogSearch.adapter = adapter

        binding.etFReadChapterCatalogSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                startCachedSearch(binding.etFReadChapterCatalogSearch.text.toString())
                true
            } else {
                false
            }
        }
        binding.bFReadChapterCatalogSearchOnline.setOnClickListener {
            if (searchJob?.isActive == true) {
                searchJob?.cancel()
            } else {
                startOnlineSearch()
            }
        }
    }

    /**
     * Phase 1: search the bodies already on disk, in one query.
     *
     * Uncached chapters are deliberately *not* fetched here — a long book would
     * turn a keystroke into hundreds of network calls. They are offered as an
     * explicit follow-up action instead.
     */
    private fun startCachedSearch(rawQuery: String) {
        val query = BookSearchMatcher.normalizeQuery(rawQuery)
        searchJob?.cancel()
        searchQuery = query
        hitsByCid.clear()
        collapsedCids.clear()
        searchedCids.clear()
        scannedChapters = 0
        onlineRemaining = 0
        onlineTotal = 0
        onlineDone = 0
        publishSearchRows()

        if (query.isEmpty()) {
            binding.tvFReadChapterCatalogSearchStatus.text = getString(R.string.search_need_query)
            binding.bFReadChapterCatalogSearchOnline.isVisible = false
            return
        }

        binding.bFReadChapterCatalogSearchOnline.isVisible = false
        binding.tvFReadChapterCatalogSearchStatus.text = getString(R.string.search_loading)
        searchJob = viewLifecycleOwner.lifecycleScope.launch {
            val cached = readerRepository.cachedChapterContents(novel.aid)
            val refs = orderedRefs()
            var lastPublish = 0L
            refs.forEachIndexed { index, ref ->
                cached[ref.cid]?.let { content ->
                    searchedCids += ref.cid
                    val hits = BookSearchMatcher.hits(ref, content, query)
                    if (hits.isNotEmpty()) hitsByCid[ref.cid] = hits
                }
                scannedChapters = index + 1
                val now = System.currentTimeMillis()
                if (now - lastPublish >= PROGRESS_PUBLISH_INTERVAL_MS) {
                    lastPublish = now
                    binding.tvFReadChapterCatalogSearchStatus.text =
                        getString(R.string.search_status_scanning, scannedChapters, refs.size)
                    publishSearchRows()
                    yield()
                }
            }
            onlineRemaining = refs.count { it.cid !in cached }
            publishSearchRows()
            updateSearchStatus(complete = onlineRemaining == 0)
            binding.bFReadChapterCatalogSearchOnline.isVisible = onlineRemaining > 0
        }
    }

    /**
     * Phase 2, opt-in: fetch the chapters that are not cached yet, cache-first,
     * and keep scanning. Cancellable at any point.
     */
    private fun startOnlineSearch() {
        val query = searchQuery
        if (query.isEmpty() || searchJob?.isActive == true) return
        val pending = orderedRefs().filterNot { it.cid in searchedCids }
        if (pending.isEmpty()) return
        onlineTotal = pending.size
        onlineDone = 0
        binding.bFReadChapterCatalogSearchOnline.setText(R.string.cancel)
        searchJob = viewLifecycleOwner.lifecycleScope.launch {
            try {
                var lastPublish = 0L
                for (ref in pending) {
                    val content = readerRepository.loadChapterContent(ref)
                    searchedCids += ref.cid
                    if (content != null) {
                        val hits = BookSearchMatcher.hits(ref, content, query)
                        if (hits.isNotEmpty()) hitsByCid[ref.cid] = hits
                    }
                    onlineDone++
                    val now = System.currentTimeMillis()
                    if (now - lastPublish >= PROGRESS_PUBLISH_INTERVAL_MS) {
                        lastPublish = now
                        binding.tvFReadChapterCatalogSearchStatus.text =
                            getString(R.string.search_status_online, onlineDone, onlineTotal)
                        publishSearchRows()
                        yield()
                    }
                }
            } finally {
                // The view may already be gone when this job is cancelled.
                _binding?.let {
                    onlineRemaining = orderedRefs().count { ref -> ref.cid !in searchedCids }
                    it.bFReadChapterCatalogSearchOnline.setText(R.string.search_online_remaining)
                    it.bFReadChapterCatalogSearchOnline.isVisible = onlineRemaining > 0
                    publishSearchRows()
                    updateSearchStatus(complete = onlineRemaining == 0)
                }
            }
        }
    }

    private fun updateSearchStatus(complete: Boolean) {
        val total = orderedRefs().size
        val hitCount = hitsByCid.values.sumOf { it.size }
        binding.tvFReadChapterCatalogSearchStatus.text = if (complete) {
            getString(R.string.search_status_complete, total, hitCount)
        } else {
            getString(R.string.search_status_partial, scannedChapters, total, hitCount)
        }
    }

    /** Rebuilds the grouped result rows in book order. */
    private fun publishSearchRows() {
        val adapter = searchAdapter ?: return
        val rows = mutableListOf<SearchRow>()
        orderedRefs().forEach { ref ->
            val hits = hitsByCid[ref.cid] ?: return@forEach
            val expanded = ref.cid !in collapsedCids
            rows += SearchRow.Header(ref = ref, hitCount = hits.size, expanded = expanded)
            if (expanded) hits.forEach { rows += SearchRow.Hit(it) }
        }
        adapter.submitList(rows)
        binding.tvFReadChapterCatalogSearchEmpty.isVisible =
            rows.isEmpty() && searchQuery.isNotEmpty() && searchJob?.isActive != true
    }

    /** Chapters in reading order; also the display order of search groups. */
    private fun orderedRefs(): List<ChapterRef> = novel.chapterRefs().flatten()

    private fun selectVolume(volumePosition: Int) {
        selectedVolumePos = clampVolume(volumePosition)
        (binding.rvFReadChapterCatalog.adapter as? ReadChapterCatalogAdapter)
            ?.submitVolume(selectedVolumePos)
        updateChapterCount()
        binding.rvFReadChapterCatalog.post {
            (binding.rvFReadChapterCatalog.adapter as? ReadChapterCatalogAdapter)?.let { adapter ->
                if (selectedVolumePos == currentVolumePos) {
                    centerCurrentChapter(adapter)
                } else {
                    chapterLayoutManager.scrollToPosition(0)
                }
            }
        }
        binding.rvFReadChapterCatalogVolumes.post { centerSelectedVolume() }
    }

    private fun updateChapterCount() {
        val chapterCount = novel.volume.getOrNull(selectedVolumePos)?.chapters?.size ?: 0
        binding.tvFReadChapterCatalogChapterCount.text = getString(
            R.string.catalog_chapter_count,
            chapterCount
        )
    }

    private fun centerSelectedVolume() {
        if (!this::volumeLayoutManager.isInitialized || novel.volume.isEmpty()) return
        val list = binding.rvFReadChapterCatalogVolumes
        val itemHeight = resources.getDimensionPixelSize(R.dimen.gi_catalog_volume_item_height)
        val topPadding = list.paddingTop
        val bottomPadding = list.paddingBottom
        val centerOffset = ((list.height - itemHeight) / 2).coerceAtLeast(topPadding)
        val endOffset = (list.height - itemHeight - bottomPadding).coerceAtLeast(topPadding)
        val offset = when {
            selectedVolumePos <= 0 -> topPadding
            selectedVolumePos >= novel.volume.lastIndex -> endOffset
            else -> centerOffset
        }
        volumeLayoutManager.scrollToPositionWithOffset(selectedVolumePos, offset)
    }

    private fun centerCurrentChapter(adapter: ReadChapterCatalogAdapter) {
        val position = adapter.currentAdapterPosition
        if (position < 0) {
            chapterLayoutManager.scrollToPosition(0)
            return
        }
        val offset = (binding.rvFReadChapterCatalog.height / 3).coerceAtLeast(
            resources.getDimensionPixelSize(R.dimen.gi_space_sm)
        )
        chapterLayoutManager.scrollToPositionWithOffset(position, offset)
    }

    private fun currentPositionSummary(): String =
        if (novel.volume.isEmpty()) {
            getString(R.string.chapter_catalog)
        } else {
            getString(
                R.string.catalog_current_position,
                currentVolumePos + 1,
                currentChapterPos + 1
            )
        }

    private fun clampVolume(position: Int): Int =
        if (novel.volume.isEmpty()) 0 else position.coerceIn(0, novel.volume.lastIndex)

    private fun clampChapter(volumePosition: Int, position: Int): Int {
        val chapterCount = novel.volume.getOrNull(volumePosition)?.chapters?.size ?: 0
        return if (chapterCount == 0) 0 else position.coerceIn(0, chapterCount - 1)
    }

    private fun calculatePanelWidth(): Int {
        val windowWidth = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            dialog?.window?.windowManager?.currentWindowMetrics?.bounds?.width()
        } else {
            null
        } ?: resources.displayMetrics.widthPixels
        val maxWidth = (resources.displayMetrics.density * MAX_PANEL_WIDTH_DP).toInt()
        val proportionalWidth = (windowWidth * PANEL_WIDTH_RATIO).toInt()
        return minOf(proportionalWidth, maxWidth)
    }

    private fun defaultCoverUrl(aid: String): String {
        if (aid.isBlank()) return ""
        val directory = if (aid.length <= 3) "0" else aid.first().toString()
        return String.format(
            Locale.US,
            "https://img.wenku8.com/image/%s/%s/%ss.jpg",
            directory,
            aid,
            aid
        )
    }

    private object RecyclerViewPosition {
        const val UNSET = -1
    }

    companion object {
        const val RESULT_KEY = "read_chapter_catalog_result"
        const val RESULT_VOLUME = "read_chapter_catalog_volume"
        const val RESULT_CHAPTER = "read_chapter_catalog_chapter"

        private const val ARG_NOVEL = "novel"
        private const val ARG_CURRENT_VOLUME = "current_volume"
        private const val ARG_CURRENT_CHAPTER = "current_chapter"
        private const val ARG_ALLOW_DOWNLOAD = "allow_download"
        private const val ARG_COVER_URL = "cover_url"
        private const val ARG_SELECTED_VOLUME = "selected_volume"
        private const val ARG_SELECTED_CIDS = "selected_cids"
        private const val ARG_VOLUME_SCROLL = "volume_scroll"
        private const val ARG_CHAPTER_SCROLL = "chapter_scroll"
        private const val PANEL_WIDTH_RATIO = 0.86f
        private const val MAX_PANEL_WIDTH_DP = 420
        private const val BACKGROUND_DIM_AMOUNT = 0.32f

        /** Tab order must match [setupTabs]. */
        private const val TAB_CATALOG = 0
        private const val TAB_BOOKMARKS = 1
        private const val TAB_SEARCH = 2

        /** Search progress repaints at most this often, to keep scanning smooth. */
        private const val PROGRESS_PUBLISH_INTERVAL_MS = 200L

        fun newInstance(
            novel: Novel,
            currentVolumePos: Int,
            currentChapterPos: Int,
            allowDownload: Boolean = false,
            coverUrl: String? = null
        ): ReadChapterCatalogBottomSheet =
            ReadChapterCatalogBottomSheet().apply {
                arguments = Bundle().apply {
                    putParcelable(ARG_NOVEL, novel)
                    putInt(ARG_CURRENT_VOLUME, currentVolumePos)
                    putInt(ARG_CURRENT_CHAPTER, currentChapterPos)
                    putBoolean(ARG_ALLOW_DOWNLOAD, allowDownload)
                    if (!coverUrl.isNullOrBlank()) putString(ARG_COVER_URL, coverUrl)
                }
            }
    }
}
