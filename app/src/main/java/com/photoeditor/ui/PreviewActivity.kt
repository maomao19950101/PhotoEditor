package com.photoeditor.ui

import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.photoeditor.databinding.ActivityPreviewBinding

/**
 * 预览界面
 */
class PreviewActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPreviewBinding

    companion object {
        const val EXTRA_IMAGE_URI = "extra_image_uri"
        const val EXTRA_ORIGINAL_URI = "extra_original_uri"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPreviewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val imageUri = intent.getStringExtra(EXTRA_IMAGE_URI)
        val originalUri = intent.getStringExtra(EXTRA_ORIGINAL_URI)

        imageUri?.let {
            Glide.with(this)
                .load(Uri.parse(it))
                .into(binding.imagePreview)
        }

        binding.btnCompare.setOnTouchListener { _, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    originalUri?.let {
                        Glide.with(this)
                            .load(Uri.parse(it))
                            .into(binding.imagePreview)
                    }
                    true
                }
                android.view.MotionEvent.ACTION_UP,
                android.view.MotionEvent.ACTION_CANCEL -> {
                    imageUri?.let {
                        Glide.with(this)
                            .load(Uri.parse(it))
                            .into(binding.imagePreview)
                    }
                    true
                }
                else -> false
            }
        }

        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.btnShare.setOnClickListener {
            shareImage(imageUri)
        }
    }

    private fun shareImage(imageUri: String?) {
        imageUri ?: return
        val intent = android.content.Intent(Intent.ACTION_SEND).apply {
            type = "image/*"
            putExtra(Intent.EXTRA_STREAM, Uri.parse(imageUri))
        }
        startActivity(Intent.createChooser(intent, "分享图片"))
    }
}