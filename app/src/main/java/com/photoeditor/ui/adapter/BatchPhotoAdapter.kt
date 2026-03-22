package com.photoeditor.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.photoeditor.data.model.PhotoItem
import com.photoeditor.databinding.ItemPhotoBinding

/**
 * 批量处理照片适配器
 */
class BatchPhotoAdapter(
    private val onRemoveClick: (PhotoItem) -> Unit
) : ListAdapter<PhotoItem, BatchPhotoAdapter.PhotoViewHolder>(PHOTO_DIFF_CALLBACK) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoViewHolder {
        val binding = ItemPhotoBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return PhotoViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PhotoViewHolder, position: Int) {
        val photo = getItem(position)
        holder.bind(photo)
    }

    inner class PhotoViewHolder(
        private val binding: ItemPhotoBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(photo: PhotoItem) {
            Glide.with(binding.root.context)
                .load(photo.uri)
                .centerCrop()
                .into(binding.imagePhoto)

            binding.tvPhotoName.text = photo.name
            binding.tvPhotoSize.text = photo.getSizeString()

            // 显示进度
            if (photo.isProcessing) {
                binding.progressBar.progress = photo.processingProgress
                binding.tvProgress.text = "${photo.processingProgress}%"
            }

            binding.btnRemove.setOnClickListener {
                onRemoveClick(photo)
            }
        }
    }

    companion object {
        private val PHOTO_DIFF_CALLBACK = object : DiffUtil.ItemCallback<PhotoItem>() {
            override fun areItemsTheSame(oldItem: PhotoItem, newItem: PhotoItem): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(oldItem: PhotoItem, newItem: PhotoItem): Boolean {
                return oldItem == newItem
            }
        }
    }
}
