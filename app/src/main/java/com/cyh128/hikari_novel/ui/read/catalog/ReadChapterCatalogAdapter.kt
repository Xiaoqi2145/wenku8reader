package com.cyh128.hikari_novel.ui.read.catalog

import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.cyh128.hikari_novel.R
import com.cyh128.hikari_novel.data.model.Novel
import com.cyh128.hikari_novel.data.model.ChapterRef
import com.cyh128.hikari_novel.databinding.ItemReadChapterCatalogChapterBinding
import com.cyh128.hikari_novel.databinding.ItemReadChapterCatalogHeaderBinding

class ReadChapterCatalogAdapter(
    novel: Novel,
    currentVolumePos: Int,
    currentChapterPos: Int,
    private val onChapterClick: (volumePos: Int, chapterPos: Int) -> Unit,
    private val allowDownload: Boolean = false,
    private val onDownload: (List<ChapterRef>) -> Unit = {}
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private val items = buildItems(novel, currentVolumePos, currentChapterPos)
    private val selectedCids = mutableSetOf<String>()
    val currentAdapterPosition = items.indexOfFirst {
        it is CatalogItem.ChapterItem && it.isCurrent
    }

    override fun getItemViewType(position: Int): Int =
        when (items[position]) {
            is CatalogItem.VolumeItem -> VIEW_TYPE_VOLUME
            is CatalogItem.ChapterItem -> VIEW_TYPE_CHAPTER
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder =
        if (viewType == VIEW_TYPE_VOLUME) {
            VolumeViewHolder(
                ItemReadChapterCatalogHeaderBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
            )
        } else {
            ChapterViewHolder(
                ItemReadChapterCatalogChapterBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
            )
        }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is CatalogItem.VolumeItem -> (holder as VolumeViewHolder).bind(item)
            is CatalogItem.ChapterItem -> (holder as ChapterViewHolder).bind(item)
        }
    }

    fun downloadSelected() {
        onDownload(items.filterIsInstance<CatalogItem.ChapterItem>()
            .filter { it.ref.cid in selectedCids }
            .map { it.ref })
    }

    private class VolumeViewHolder(
        private val binding: ItemReadChapterCatalogHeaderBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: CatalogItem.VolumeItem) {
            binding.root.text =
                item.title.ifBlank {
                    binding.root.context.getString(R.string.volume_number, item.volumePos + 1)
                }
        }
    }

    private inner class ChapterViewHolder(
        private val binding: ItemReadChapterCatalogChapterBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: CatalogItem.ChapterItem) {
            binding.tvIReadChapterCatalogChapterTitle.text = item.title
            binding.tvIReadChapterCatalogChapterTitle.typeface =
                if (item.isCurrent) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            binding.tvIReadChapterCatalogCurrent.visibility =
                if (item.isCurrent) View.VISIBLE else View.GONE
            binding.cbIReadChapterCatalogDownload.setOnCheckedChangeListener(null)
            binding.cbIReadChapterCatalogDownload.visibility = if (allowDownload) View.VISIBLE else View.GONE
            binding.cbIReadChapterCatalogDownload.isChecked = item.ref.cid in selectedCids
            binding.cbIReadChapterCatalogDownload.setOnCheckedChangeListener { _, checked ->
                if (checked) selectedCids += item.ref.cid else selectedCids -= item.ref.cid
            }
            binding.root.setOnClickListener {
                onChapterClick(item.volumePos, item.chapterPos)
            }
        }
    }

    private sealed class CatalogItem {
        data class VolumeItem(
            val volumePos: Int,
            val title: String
        ) : CatalogItem()

        data class ChapterItem(
            val volumePos: Int,
            val chapterPos: Int,
            val title: String,
            val isCurrent: Boolean,
            val ref: ChapterRef
        ) : CatalogItem()
    }

    private companion object {
        const val VIEW_TYPE_VOLUME = 0
        const val VIEW_TYPE_CHAPTER = 1

        fun buildItems(
            novel: Novel,
            currentVolumePos: Int,
            currentChapterPos: Int
        ): List<CatalogItem> = buildList {
            novel.volume.forEachIndexed { volumeIndex, volume ->
                add(CatalogItem.VolumeItem(volumeIndex, volume.volumeTitle))
                volume.chapters.forEachIndexed { chapterIndex, chapter ->
                    add(
                        CatalogItem.ChapterItem(
                            volumeIndex,
                            chapterIndex,
                            chapter.chapterTitle,
                            volumeIndex == currentVolumePos && chapterIndex == currentChapterPos,
                            ChapterRef(novel.aid, chapter.cid, volumeIndex, chapterIndex, chapter.chapterTitle)
                        )
                    )
                }
            }
        }
    }
}
