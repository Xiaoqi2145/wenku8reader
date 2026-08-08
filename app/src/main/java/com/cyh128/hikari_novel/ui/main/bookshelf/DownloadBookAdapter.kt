package com.cyh128.hikari_novel.ui.main.bookshelf

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.cyh128.hikari_novel.data.source.local.database.reader.DownloadBookEntity
import com.cyh128.hikari_novel.databinding.ItemDownloadBookBinding

data class DownloadBookRow(val book: DownloadBookEntity, val completed: Int, val failed: Int)

class DownloadBookAdapter(
    private val onPause: (String) -> Unit,
    private val onDelete: (String) -> Unit,
    private val onBookClick: (String) -> Unit
) : RecyclerView.Adapter<DownloadBookAdapter.Holder>() {
    private var rows: List<DownloadBookRow> = emptyList()
    fun submitList(value: List<DownloadBookRow>) { rows = value; notifyDataSetChanged() }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(ItemDownloadBookBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    override fun getItemCount() = rows.size
    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(rows[position])
    inner class Holder(private val binding: ItemDownloadBookBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(row: DownloadBookRow) {
            val total = row.book.totalChapters.coerceAtLeast(1)
            val percent = (row.completed * 100 / total).coerceIn(0, 100)
            binding.tvDownloadTitle.text = row.book.title
            binding.tvDownloadProgress.text = if (row.failed > 0) "${row.completed}/$total · 失败 ${row.failed} 章" else "${row.completed}/$total"
            binding.pbDownload.progress = percent
            binding.bDownloadPause.setOnClickListener { onPause(row.book.aid) }
            binding.bDownloadDelete.setOnClickListener { onDelete(row.book.aid) }
            binding.root.setOnClickListener { onBookClick(row.book.aid) }
        }
    }
}
