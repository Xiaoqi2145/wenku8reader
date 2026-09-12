package com.cyh128.hikari_novel.ui.read.catalog

import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.cyh128.hikari_novel.R
import com.cyh128.hikari_novel.data.model.Volume
import com.cyh128.hikari_novel.databinding.ItemReadChapterCatalogVolumeBinding

/** Displays the compact, vertically scrollable volume rail in the reader catalog. */
class ReadChapterCatalogVolumeAdapter(
    private val volumes: List<Volume>,
    initialSelectedVolume: Int,
    private val onVolumeSelected: (Int) -> Unit
) : RecyclerView.Adapter<ReadChapterCatalogVolumeAdapter.VolumeViewHolder>() {

    private var selectedVolumePosition = initialSelectedVolume.coerceInOrZero(volumes.size)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VolumeViewHolder =
        VolumeViewHolder(
            ItemReadChapterCatalogVolumeBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )

    override fun getItemCount(): Int = volumes.size

    override fun onBindViewHolder(holder: VolumeViewHolder, position: Int) {
        holder.bind(volumes[position], position)
    }

    fun setSelectedVolume(position: Int, notify: Boolean = true) {
        val nextPosition = position.coerceInOrZero(volumes.size)
        if (nextPosition == selectedVolumePosition) return
        val previousPosition = selectedVolumePosition
        selectedVolumePosition = nextPosition
        if (notify) {
            if (previousPosition in volumes.indices) notifyItemChanged(previousPosition)
            if (nextPosition in volumes.indices) notifyItemChanged(nextPosition)
        }
    }

    private fun Int.coerceInOrZero(size: Int): Int =
        if (size == 0) 0 else coerceIn(0, size - 1)

    inner class VolumeViewHolder(
        private val binding: ItemReadChapterCatalogVolumeBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(volume: Volume, position: Int) {
            val selected = position == selectedVolumePosition
            binding.root.isSelected = selected
            binding.vIReadChapterCatalogVolumeIndicator.visibility =
                if (selected) android.view.View.VISIBLE else android.view.View.GONE

            binding.tvIReadChapterCatalogVolumeTitle.text = volume.volumeTitle.ifBlank {
                binding.root.context.getString(R.string.volume_number, position + 1)
            }
            binding.tvIReadChapterCatalogVolumeTitle.isSelected = selected
            binding.tvIReadChapterCatalogVolumeTitle.typeface =
                if (selected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            binding.tvIReadChapterCatalogVolumeCount.text = binding.root.context.getString(
                R.string.catalog_volume_count,
                volume.chapters.size
            )
            binding.tvIReadChapterCatalogVolumeCount.isSelected = selected
            binding.tvIReadChapterCatalogVolumeCount.typeface =
                if (selected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            binding.root.contentDescription = binding.tvIReadChapterCatalogVolumeTitle.text
            binding.root.setOnClickListener {
                val adapterPosition = bindingAdapterPosition
                if (adapterPosition == RecyclerView.NO_POSITION) return@setOnClickListener
                if (adapterPosition != selectedVolumePosition) {
                    setSelectedVolume(adapterPosition)
                    onVolumeSelected(adapterPosition)
                }
            }
        }
    }
}
