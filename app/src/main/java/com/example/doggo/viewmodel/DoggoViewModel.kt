package com.example.doggo.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.doggo.data.HouseSitJob
import com.example.doggo.repository.HouseSitRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class DoggoViewModel(
    private val repository: HouseSitRepository
) : ViewModel() {

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _filterState = MutableStateFlow(FilterState())
    val filterState: StateFlow<FilterState> = _filterState.asStateFlow()

    val jobs: StateFlow<List<HouseSitJob>> = combine(
        repository.allActiveJobs,
        _filterState
    ) { jobs, filters ->
        jobs.filter { job ->
            val matchesFavorite = if (filters.onlyFavorites) job.isFavorited else true
            val matchesArchived = if (filters.includeArchived) true else !job.isArchived
            val matchesState = if (filters.selectedStates.isNotEmpty()) {
                filters.selectedStates.contains(job.state.uppercase())
            } else true
            
            val matchesDate = if (filters.startDate != null && filters.endDate != null) {
                // Overlap: jobStart <= filterEnd AND jobEnd >= filterStart
                job.startDate <= filters.endDate && job.endDate >= filters.startDate
            } else true

            matchesFavorite && matchesArchived && matchesState && matchesDate
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favoriteJobs = repository.favoriteJobs.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val archivedJobs = repository.archivedJobs.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            repository.refreshJobs()
            _isRefreshing.value = false
        }
    }

    fun toggleFavorite(job: HouseSitJob) {
        viewModelScope.launch {
            repository.updateFavorite(job.id, !job.isFavorited)
        }
    }

    fun archiveJob(job: HouseSitJob) {
        viewModelScope.launch {
            repository.updateArchived(job.id, true)
        }
    }

    fun setFilter(filters: FilterState) {
        _filterState.value = filters
    }

    data class FilterState(
        val onlyFavorites: Boolean = false,
        val includeArchived: Boolean = false,
        val selectedStates: Set<String> = emptySet(),
        val startDate: Long? = null,
        val endDate: Long? = null
    )
}
