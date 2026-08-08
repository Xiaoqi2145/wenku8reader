package com.cyh128.hikari_novel.ui.main.bookshelf

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.cyh128.hikari_novel.R
import com.cyh128.hikari_novel.data.repository.ReaderRepository
import com.cyh128.hikari_novel.databinding.ActivityDownloadBinding
import com.cyh128.hikari_novel.util.startActivity
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job

@AndroidEntryPoint
class DownloadActivity : AppCompatActivity() {
    @Inject lateinit var repository: ReaderRepository
    private lateinit var binding: ActivityDownloadBinding
    private lateinit var adapter: DownloadBookAdapter
    private val taskJobs = mutableMapOf<String, Job>()
    private val taskMap = mutableMapOf<String, List<com.cyh128.hikari_novel.data.source.local.database.reader.DownloadTaskEntity>>()
    private var books = emptyList<com.cyh128.hikari_novel.data.source.local.database.reader.DownloadBookEntity>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDownloadBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.tbDownload.setNavigationOnClickListener { finish() }
        adapter = DownloadBookAdapter(
            onPause = { aid -> lifecycleScope.launch { repository.pauseBook(aid) } },
            onDelete = { aid -> lifecycleScope.launch { repository.deleteBookDownloads(aid) } },
            onBookClick = { aid ->
                startActivity<DownloadDetailActivity> { putExtra("aid", aid) }
            }
        )
        binding.rvDownload.layoutManager = LinearLayoutManager(this)
        binding.rvDownload.adapter = adapter
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                repository.observeDownloadBooks().collect { books ->
                    this@DownloadActivity.books = books
                    taskJobs.values.forEach { it.cancel() }
                    taskJobs.clear()
                    books.forEach { book ->
                        taskJobs[book.aid] = launch {
                            repository.observeDownloads(book.aid).collect { tasks ->
                                taskMap[book.aid] = tasks
                                submitRows()
                            }
                        }
                    }
                    submitRows()
                }
            }
        }
    }

    private fun submitRows() {
        adapter.submitList(books.map { book ->
            val tasks = taskMap[book.aid].orEmpty()
            DownloadBookRow(book, tasks.count { it.status == "COMPLETED" }, tasks.count { it.status == "FAILED" })
        })
    }
}
