package com.photoeditor.ui.adapter

import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.photoeditor.databinding.ItemPhotoBinding

/**
 * 结果适配器
 */
class ResultsAdapter(
    private val onItemClick: (String) -> Unit,
    private val onShareClick: (String) -> Unit
) : ListAdapter<String, ResultsAdapter.ResultViewHolder>(DIFF_CALLBACK) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ResultViewHolder {
        val binding = ItemPhotoBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ResultViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ResultViewHolder, position: Int) {
        val uri = getItem(position)
        holder.bind(uri)
    }

    inner class ResultViewHolder(
        private val binding: ItemPhotoBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(uriString: String) {
            Glide.with(binding.root.context)
                .load(Uri.parse(uriString))
                .centerCrop()
                .into(binding.imagePhoto)

            binding.root.setOnClickListener {
                onItemClick(uriString)
            }

            binding.btnShare.setOnClickListener {
                onShareClick(uriString)
            }
        }
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<String>() {
            override fun areItemsTheSame(oldItem: String, newItem: String): Boolean {
                return oldItem == newItem
            }

            override fun areContentsTheSame(oldItem: String, newItem: String): Boolean {
                return oldItem == newItem
            }
        }
    }
}
