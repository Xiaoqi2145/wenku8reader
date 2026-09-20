package com.cyh128.hikari_novel.ui.read.catalog

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.cyh128.hikari_novel.data.source.local.database.reader.BookmarkEntity
import com.cyh128.hikari_novel.databinding.ItemReadBookmarkBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A bookmark plus the chapter title resolved from the book.
 *
 * [BookmarkEntity] only stores indices, so the title has to be looked up by the
 * caller that owns the [com.cyh128.hikari_novel.data.model.Novel].
 */
data class BookmarkRow(
    val bookmark: BookmarkEntity,
    val chapterTitle: String
)

/** Bookmark list for the catalog drawer's bookmark tab. See P1-3. */
class ReadBookmarkAdapter(
    private val onOpen: (BookmarkRow) -> Unit,
    private val onDelete: (BookmarkRow) -> Unit
) : ListAdapter<BookmarkRow, ReadBookmarkAdapter.ViewHolder>(DIFF) {

    class ViewHolder(val binding: ItemReadBookmarkBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(
            ItemReadBookmarkBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val row = getItem(position)
        holder.binding.tvItemBookmarkTitle.text = row.chapterTitle
        holder.binding.tvItemBookmarkExcerpt.text = row.bookmark.excerpt
        holder.binding.tvItemBookmarkTime.text = TIME_FORMAT.format(Date(row.bookmark.createdAt))
        holder.binding.root.setOnClickListener { onOpen(row) }
        holder.binding.bItemBookmarkDelete.setOnClickListener { onDelete(row) }
    }

    private companion object {
        val TIME_FORMAT = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

        val DIFF = object : DiffUtil.ItemCallback<BookmarkRow>() {
            override fun areItemsTheSame(oldItem: BookmarkRow, newItem: BookmarkRow): Boolean =
                oldItem.bookmark.id == newItem.bookmark.id

            override fun areContentsTheSame(oldItem: BookmarkRow, newItem: BookmarkRow): Boolean =
                oldItem == newItem
        }
    }
}
