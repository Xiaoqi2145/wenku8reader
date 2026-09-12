package com.cyh128.hikari_novel.ui.read.catalog

import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.cyh128.hikari_novel.R
import com.cyh128.hikari_novel.data.model.ChapterRef
import com.cyh128.hikari_novel.data.model.Novel
import com.cyh128.hikari_novel.databinding.ItemReadChapterCatalogChapterBinding

/** Shows chapters for one selected volume while retaining download selections globally. */
class ReadChapterCatalogAdapter(
    private val novel: Novel,
    currentVolumePos: Int,
    currentChapterPos: Int,
    private val onChapterClick: (volumePos: Int, chapterPos: Int) -> Unit,
    private val allowDownload: Boolean = false,
    private val onDownload: (List<ChapterRef>) -> Unit = {},
    selectedVolumePos: Int = currentVolumePos
) : RecyclerView.Adapter<ReadChapterCatalogAdapter.ChapterViewHolder>() {

    private var currentVolumePosition = currentVolumePos.coerceInOrZero(novel.volume.size)
    private var currentChapterPosition = currentChapterPos.coerceInOrZero(
        novel.volume.getOrNull(currentVolumePosition)?.chapters?.size ?: 0
    )
    private var selectedVolumePosition = selectedVolumePos.coerceInOrZero(novel.volume.size)
    private var chapterItems = buildChapterItems(selectedVolumePosition)
    private val selectedCids = mutableSetOf<String>()

    val currentAdapterPosition: Int
        get() = chapterItems.indexOfFirst { it.isCurrent }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChapterViewHolder =
        ChapterViewHolder(
            ItemReadChapterCatalogChapterBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )

    override fun getItemCount(): Int = chapterItems.size

    override fun onBindViewHolder(holder: ChapterViewHolder, position: Int) {
        holder.bind(chapterItems[position])
    }

    /** Swap the visible volume without losing checked chapters from other volumes. */
    fun submitVolume(volumePosition: Int) {
        val nextPosition = volumePosition.coerceInOrZero(novel.volume.size)
        if (nextPosition == selectedVolumePosition) return
        selectedVolumePosition = nextPosition
        chapterItems = buildChapterItems(nextPosition)
        notifyDataSetChanged()
    }

    fun setCurrentChapter(volumePosition: Int, chapterPosition: Int) {
        val nextVolume = volumePosition.coerceInOrZero(novel.volume.size)
        val nextChapter = chapterPosition.coerceInOrZero(
            novel.volume.getOrNull(nextVolume)?.chapters?.size ?: 0
        )
        if (nextVolume == currentVolumePosition && nextChapter == currentChapterPosition) return
        currentVolumePosition = nextVolume
        currentChapterPosition = nextChapter
        chapterItems = buildChapterItems(selectedVolumePosition)
        notifyDataSetChanged()
    }

    fun downloadSelected() {
        val refs = buildList {
            novel.volume.forEachIndexed { volumePosition, volume ->
                volume.chapters.forEachIndexed { chapterPosition, chapter ->
                    if (chapter.cid in selectedCids) {
                        add(
                            ChapterRef(
                                aid = novel.aid,
                                cid = chapter.cid,
                                volumeIndex = volumePosition,
                                chapterIndex = chapterPosition,
                                title = chapter.chapterTitle
                            )
                        )
                    }
                }
            }
        }
        onDownload(refs)
    }

    fun selectAll() {
        selectedCids.clear()
        novel.volume.forEach { volume ->
            volume.chapters.forEach { chapter -> selectedCids += chapter.cid }
        }
        notifyDataSetChanged()
    }

    fun selectedChapterCids(): ArrayList<String> = ArrayList(selectedCids)

    fun restoreSelectedChapterCids(cids: Collection<String>) {
        selectedCids.clear()
        selectedCids += cids
        notifyDataSetChanged()
    }

    private fun buildChapterItems(volumePosition: Int): List<ChapterItem> {
        val volume = novel.volume.getOrNull(volumePosition) ?: return emptyList()
        return volume.chapters.mapIndexed { chapterPosition, chapter ->
            ChapterItem(
                volumePos = volumePosition,
                chapterPos = chapterPosition,
                title = chapter.chapterTitle,
                isCurrent = volumePosition == currentVolumePosition &&
                    chapterPosition == currentChapterPosition,
                ref = ChapterRef(
                    aid = novel.aid,
                    cid = chapter.cid,
                    volumeIndex = volumePosition,
                    chapterIndex = chapterPosition,
                    title = chapter.chapterTitle
                )
            )
        }
    }

    private fun Int.coerceInOrZero(size: Int): Int =
        if (size == 0) 0 else coerceIn(0, size - 1)

    inner class ChapterViewHolder(
        private val binding: ItemReadChapterCatalogChapterBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: ChapterItem) {
            binding.root.isSelected = item.isCurrent
            binding.vIReadChapterCatalogCurrentMarker.visibility =
                if (item.isCurrent) View.VISIBLE else View.GONE
            binding.tvIReadChapterCatalogChapterNumber.text = binding.root.context.getString(
                R.string.chapter_number,
                item.chapterPos + 1
            )
            binding.tvIReadChapterCatalogChapterNumber.isSelected = item.isCurrent
            binding.tvIReadChapterCatalogChapterTitle.text = item.title
            binding.tvIReadChapterCatalogChapterTitle.isSelected = item.isCurrent
            binding.tvIReadChapterCatalogChapterTitle.typeface =
                if (item.isCurrent) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            binding.tvIReadChapterCatalogCurrent.isSelected = item.isCurrent
            binding.tvIReadChapterCatalogCurrent.visibility =
                if (item.isCurrent) View.VISIBLE else View.GONE

            binding.cbIReadChapterCatalogDownload.setOnCheckedChangeListener(null)
            binding.cbIReadChapterCatalogDownload.visibility =
                if (allowDownload) View.VISIBLE else View.GONE
            binding.cbIReadChapterCatalogDownload.isChecked = item.ref.cid in selectedCids
            binding.cbIReadChapterCatalogDownload.setOnCheckedChangeListener { _, checked ->
                if (checked) selectedCids += item.ref.cid else selectedCids -= item.ref.cid
            }
            binding.root.setOnClickListener {
                onChapterClick(item.volumePos, item.chapterPos)
            }
        }
    }

    data class ChapterItem(
        val volumePos: Int,
        val chapterPos: Int,
        val title: String,
        val isCurrent: Boolean,
        val ref: ChapterRef
    )
}
