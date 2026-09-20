package com.cyh128.hikari_novel.ui.read.catalog

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.cyh128.hikari_novel.R
import com.cyh128.hikari_novel.data.model.ChapterRef
import com.cyh128.hikari_novel.data.repository.reader.BookSearchHit
import com.cyh128.hikari_novel.databinding.ItemReadSearchHeaderBinding
import com.cyh128.hikari_novel.databinding.ItemReadSearchHitBinding

/**
 * One row of the in-book search result list.
 *
 * The list is chapter-grouped; the group's expanded state lives with the caller
 * so this adapter stays a pure renderer. See P1-4.
 */
sealed interface SearchRow {
    val key: String

    data class Header(
        val ref: ChapterRef,
        val hitCount: Int,
        val expanded: Boolean
    ) : SearchRow {
        override val key: String get() = "header:${ref.cid}"
    }

    data class Hit(val hit: BookSearchHit) : SearchRow {
        override val key: String get() = "hit:${hit.ref.cid}:${hit.charOffset}"
    }
}

/** Chapter-grouped result list for the catalog drawer's search tab. See P1-4. */
class ReadSearchResultAdapter(
    private val onToggleGroup: (SearchRow.Header) -> Unit,
    private val onOpenHit: (SearchRow.Hit) -> Unit
) : ListAdapter<SearchRow, RecyclerView.ViewHolder>(DIFF) {

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is SearchRow.Header -> TYPE_HEADER
        is SearchRow.Hit -> TYPE_HIT
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_HEADER) {
            HeaderHolder(ItemReadSearchHeaderBinding.inflate(inflater, parent, false))
        } else {
            HitHolder(ItemReadSearchHitBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = getItem(position)) {
            is SearchRow.Header -> (holder as HeaderHolder).bind(row)
            is SearchRow.Hit -> (holder as HitHolder).bind(row)
        }
    }

    inner class HeaderHolder(private val binding: ItemReadSearchHeaderBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(row: SearchRow.Header) {
            val context = binding.root.context
            binding.tvItemSearchHeaderTitle.text = row.ref.title
            binding.tvItemSearchHeaderCount.text =
                context.getString(R.string.search_hits_in_chapter, row.hitCount)
            // The count doubles as the collapse affordance: no extra chevron
            // asset is needed and the state stays readable either way.
            binding.tvItemSearchHeaderCount.alpha = if (row.expanded) 1f else 0.5f
            binding.root.setOnClickListener { onToggleGroup(row) }
        }
    }

    inner class HitHolder(private val binding: ItemReadSearchHitBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(row: SearchRow.Hit) {
            binding.tvItemSearchHitSnippet.text = row.hit.snippet
            binding.root.setOnClickListener { onOpenHit(row) }
        }
    }

    private companion object {
        const val TYPE_HEADER = 0
        const val TYPE_HIT = 1

        val DIFF = object : DiffUtil.ItemCallback<SearchRow>() {
            override fun areItemsTheSame(oldItem: SearchRow, newItem: SearchRow): Boolean =
                oldItem.key == newItem.key

            override fun areContentsTheSame(oldItem: SearchRow, newItem: SearchRow): Boolean =
                oldItem == newItem
        }
    }
}
