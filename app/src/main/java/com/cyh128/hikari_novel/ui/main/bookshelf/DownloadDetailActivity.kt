package com.cyh128.hikari_novel.ui.main.bookshelf

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.view.LayoutInflater
import android.view.ViewGroup
import com.cyh128.hikari_novel.data.repository.ReaderRepository
import com.cyh128.hikari_novel.data.source.local.database.reader.DownloadTaskEntity
import com.cyh128.hikari_novel.databinding.ActivityDownloadDetailBinding
import com.cyh128.hikari_novel.databinding.ItemDownloadChapterBinding
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class DownloadDetailActivity : AppCompatActivity() {
    @Inject lateinit var repository: ReaderRepository
    private lateinit var binding: ActivityDownloadDetailBinding
    private val adapter = ChapterTaskAdapter({ cid -> lifecycleScope.launch { repository.retryChapter(cid) } }, { cid -> lifecycleScope.launch { repository.deleteChapterDownload(cid) } })

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDownloadDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.tbDownloadDetail.setNavigationOnClickListener { finish() }
        binding.rvDownloadDetail.layoutManager = LinearLayoutManager(this)
        binding.rvDownloadDetail.adapter = adapter
        val aid = intent.getStringExtra("aid") ?: return
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                repository.observeDownloads(aid).collect { adapter.submitList(it) }
            }
        }
    }
}

private class ChapterTaskAdapter(private val onRetry: (String) -> Unit, private val onDelete: (String) -> Unit) : RecyclerView.Adapter<ChapterTaskAdapter.Holder>() {
    private var tasks = emptyList<DownloadTaskEntity>()
    fun submitList(value: List<DownloadTaskEntity>) { tasks = value; notifyDataSetChanged() }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(ItemDownloadChapterBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    override fun getItemCount() = tasks.size
    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(tasks[position])
    inner class Holder(private val binding: ItemDownloadChapterBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(task: DownloadTaskEntity) {
            binding.tvDownloadChapterTitle.text = task.title
            binding.tvDownloadChapterStatus.text = "${task.progress}% · ${task.status}"
            binding.bDownloadChapterAction.text = if (task.status == "COMPLETED") "删除" else "重试"
            binding.bDownloadChapterAction.setOnClickListener { if (task.status == "COMPLETED") onDelete(task.cid) else onRetry(task.cid) }
        }
    }
}
