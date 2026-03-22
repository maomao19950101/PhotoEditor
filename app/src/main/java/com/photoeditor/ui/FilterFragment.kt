package com.photoeditor.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.photoeditor.data.model.FilterStyle
import com.photoeditor.databinding.FragmentFilterBinding
import com.photoeditor.ui.adapter.FilterAdapter
import com.photoeditor.ui.viewmodel.FilterViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * 滤镜选择Fragment
 */
@AndroidEntryPoint
class FilterFragment : Fragment() {

    private var _binding: FragmentFilterBinding? = null
    private val binding get() = _binding!!

    private val viewModel: FilterViewModel by viewModels()
    private lateinit var filterAdapter: FilterAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFilterBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupRecyclerView()
        observeData()
        
        // 加载滤镜
        viewModel.loadFilters()
    }

    private fun setupRecyclerView() {
        filterAdapter = FilterAdapter(
            onFilterClick = { filter ->
                viewModel.selectFilter(filter)
            }
        )
        
        binding.recyclerFilters.apply {
            layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
            adapter = filterAdapter
            setHasFixedSize(true)
        }
    }

    private fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.filters.collect { filters ->
                        filterAdapter.submitList(filters)
                    }
                }
                
                launch {
                    viewModel.selectedFilter.collect { filter ->
                        filterAdapter.setSelectedFilter(filter?.id)
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

/**
 * 滤镜选择ViewModel
 */
class FilterViewModel : androidx.lifecycle.ViewModel() {
    private val _filters = MutableStateFlow<List<FilterStyle>>(emptyList())
    val filters = _filters.asStateFlow()
    
    private val _selectedFilter = MutableStateFlow<FilterStyle?>(null)
    val selectedFilter = _selectedFilter.asStateFlow()
    
    fun loadFilters() {
        _filters.value = FilterStyle.getPresetFilters()
    }
    
    fun selectFilter(filter: FilterStyle) {
        _selectedFilter.value = filter
    }
}

private fun <T> MutableStateFlow<T>.asStateFlow(): StateFlow<T> = this
