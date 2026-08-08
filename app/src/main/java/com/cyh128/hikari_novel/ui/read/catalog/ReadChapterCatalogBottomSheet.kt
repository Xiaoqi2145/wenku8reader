package com.cyh128.hikari_novel.ui.read.catalog

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.cyh128.hikari_novel.data.model.Novel
import com.cyh128.hikari_novel.data.model.ChapterRef
import com.cyh128.hikari_novel.databinding.FragmentReadChapterCatalogBinding

class ReadChapterCatalogBottomSheet :
    DialogFragment() {
    var onChapterSelected: ((volumePos: Int, chapterPos: Int) -> Unit)? = null
    var onDownloadRequested: ((List<ChapterRef>) -> Unit)? = null

    private var _binding: FragmentReadChapterCatalogBinding? = null
    private val binding get() = _binding!!
    private lateinit var novel: Novel
    private var currentVolumePos = 0
    private var currentChapterPos = 0
    private var allowDownload = false

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog =
        Dialog(requireContext()).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            setCanceledOnTouchOutside(true)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        novel = requireArguments().getParcelable(ARG_NOVEL)!!
        currentVolumePos = requireArguments().getInt(ARG_CURRENT_VOLUME)
        currentChapterPos = requireArguments().getInt(ARG_CURRENT_CHAPTER)
        allowDownload = requireArguments().getBoolean(ARG_ALLOW_DOWNLOAD, false)
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
            attributes = attributes.apply {
                gravity = Gravity.END
            }
            setLayout(calculatePanelWidth(), WindowManager.LayoutParams.MATCH_PARENT)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val layoutManager = LinearLayoutManager(requireContext())
        val adapter = ReadChapterCatalogAdapter(
            novel = novel,
            currentVolumePos = currentVolumePos,
            currentChapterPos = currentChapterPos,
            onChapterClick = { volumePos, chapterPos ->
                onChapterSelected?.invoke(volumePos, chapterPos)
                dismiss()
            },
            onDownload = { refs -> onDownloadRequested?.invoke(refs) }
        )

        binding.rvFReadChapterCatalog.layoutManager = layoutManager
        binding.rvFReadChapterCatalog.adapter = adapter
        binding.bFReadChapterCatalogClose.setOnClickListener { dismiss() }
        binding.bFReadChapterCatalogDownload.visibility = if (allowDownload) View.VISIBLE else View.GONE
        binding.bFReadChapterCatalogDownload.setOnClickListener {
            adapter.downloadSelected()
            dismiss()
        }

        if (adapter.currentAdapterPosition >= 0) {
            binding.rvFReadChapterCatalog.post {
                layoutManager.scrollToPositionWithOffset(
                    adapter.currentAdapterPosition,
                    binding.rvFReadChapterCatalog.height / 3
                )
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun calculatePanelWidth(): Int {
        val screenWidth = resources.displayMetrics.widthPixels
        val maxWidth = (resources.displayMetrics.density * MAX_PANEL_WIDTH_DP).toInt()
        val proportionalWidth = (screenWidth * PANEL_WIDTH_RATIO).toInt()
        return minOf(proportionalWidth, maxWidth)
    }

    companion object {
        private const val ARG_NOVEL = "novel"
        private const val ARG_CURRENT_VOLUME = "current_volume"
        private const val ARG_CURRENT_CHAPTER = "current_chapter"
        private const val ARG_ALLOW_DOWNLOAD = "allow_download"
        private const val PANEL_WIDTH_RATIO = 0.86f
        private const val MAX_PANEL_WIDTH_DP = 420
        private const val BACKGROUND_DIM_AMOUNT = 0.32f

        fun newInstance(
            novel: Novel,
            currentVolumePos: Int,
            currentChapterPos: Int,
            allowDownload: Boolean = false
        ): ReadChapterCatalogBottomSheet =
            ReadChapterCatalogBottomSheet().apply {
                arguments = Bundle().apply {
                    putParcelable(ARG_NOVEL, novel)
                    putInt(ARG_CURRENT_VOLUME, currentVolumePos)
                putInt(ARG_CURRENT_CHAPTER, currentChapterPos)
                putBoolean(ARG_ALLOW_DOWNLOAD, allowDownload)
                }
            }
    }
}
