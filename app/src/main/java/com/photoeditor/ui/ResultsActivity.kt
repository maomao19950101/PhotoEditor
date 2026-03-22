package com.photoeditor.ui

import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import com.photoeditor.databinding.ActivityResultsBinding
import com.photoeditor.ui.adapter.ResultsAdapter

/**
 * 结果展示界面
 */
class ResultsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityResultsBinding
    private lateinit var resultsAdapter: ResultsAdapter

    companion object {
        const val EXTRA_RESULT_URIS = "extra_result_uris"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityResultsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupRecyclerView()
        loadResults()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "处理结果"

        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun setupRecyclerView() {
        resultsAdapter = ResultsAdapter(
            onItemClick = { uri ->
                previewImage(uri)
            },
            onShareClick = { uri ->
                shareImage(uri)
            }
        )

        binding.recyclerResults.apply {
            layoutManager = GridLayoutManager(this@ResultsActivity, 2)
            adapter = resultsAdapter
        }
    }

    private fun loadResults() {
        val uris = intent.getStringArrayExtra(EXTRA_RESULT_URIS)?.toList() ?: emptyList()
        resultsAdapter.submitList(uris)
        binding.tvResultCount.text = "共 ${uris.size} 张"
    }

    private fun previewImage(uri: String) {
        // 打开预览
        val intent = android.content.Intent(this, PreviewActivity::class.java).apply {
            putExtra(PreviewActivity.EXTRA_IMAGE_URI, uri)
        }
        startActivity(intent)
    }

    private fun shareImage(uri: String) {
        val intent = android.content.Intent(Intent.ACTION_SEND).apply {
            type = "image/*"
            putExtra(Intent.EXTRA_STREAM, Uri.parse(uri))
        }
        startActivity(Intent.createChooser(intent, "分享图片"))
    }

    private fun shareAllImages() {
        val uris = resultsAdapter.currentList.map { Uri.parse(it) }
        val intent = android.content.Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "image/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
        }
        startActivity(Intent.createChooser(intent, "分享所有图片"))
    }
}