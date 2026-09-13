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
import androidx.core.os.BundleCompat
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.cyh128.hikari_novel.R
import com.cyh128.hikari_novel.data.model.ChapterRef
import com.cyh128.hikari_novel.data.model.Novel
import com.cyh128.hikari_novel.databinding.FragmentReadChapterCatalogBinding
import java.util.Locale

/**
 * Right-side chapter drawer shared by the reader and the detail-page downloader.
 *
 * The dialog window keeps the existing END-side behavior while the content is split
 * into a cover/volume rail and a remaining-height chapter list.
 */
class ReadChapterCatalogBottomSheet : DialogFragment() {
    var onChapterSelected: ((volumePos: Int, chapterPos: Int) -> Unit)? = null
    var onDownloadRequested: ((List<ChapterRef>) -> Unit)? = null

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
            onChapterClick = { volumePos, chapterPos ->
                onChapterSelected?.invoke(volumePos, chapterPos)
                    ?: parentFragmentManager.setFragmentResult(
                        RESULT_KEY,
                        Bundle().apply {
                            putInt(RESULT_VOLUME, volumePos)
                            putInt(RESULT_CHAPTER, chapterPos)
                        }
                    )
                dismiss()
            },
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
        super.onDestroyView()
        _binding = null
    }

    private fun bindingOrNull(): FragmentReadChapterCatalogBinding? = _binding

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
