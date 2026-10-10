package com.ahmad.nafs.ui.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ahmad.nafs.core.reports.ReportCursor
import com.ahmad.nafs.core.reports.ReportFilter
import com.ahmad.nafs.core.reports.ReportLoadResult
import com.ahmad.nafs.core.reports.ReportRepository
import com.ahmad.nafs.core.reports.ReportRules
import com.ahmad.nafs.core.reports.ReportSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ReportListUiState(
    val filter: ReportFilter = ReportFilter.ALL,
    val reports: List<ReportSummary> = emptyList(),
    val next: ReportCursor? = null,
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val isOffline: Boolean = false,
    val error: String? = null,
    val pagingError: String? = null,
)

@HiltViewModel
class ReportListViewModel @Inject constructor(private val repository: ReportRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(ReportListUiState())
    val uiState = _uiState.asStateFlow()
    private var userId: String? = null
    private var job: Job? = null
    private var generation = 0

    fun initialize(accountId: String) {
        if (userId == accountId) return
        job?.cancel()
        generation++
        userId = accountId
        _uiState.value = ReportListUiState()
        load(older = false)
    }

    fun selectFilter(filter: ReportFilter) {
        if (filter == _uiState.value.filter || userId == null) return
        job?.cancel()
        generation++
        _uiState.value = ReportListUiState(filter = filter)
        load(older = false)
    }

    fun refresh() {
        if (job?.isActive == true) return
        load(older = false)
    }

    fun loadMore() {
        val state = _uiState.value
        if (job?.isActive == true || state.isOffline || state.next == null) return
        load(older = true)
    }

    private fun load(older: Boolean) {
        val accountId = userId ?: return
        val filter = _uiState.value.filter
        val cursor = _uiState.value.next.takeIf { older }
        val request = ++generation
        _uiState.value = _uiState.value.copy(isLoading = !older, isLoadingMore = older, error = null, pagingError = null)
        job = viewModelScope.launch {
            val result = repository.load(accountId, filter, cursor)
            if (generation != request || userId != accountId || _uiState.value.filter != filter) return@launch
            val state = _uiState.value
            _uiState.value = when (result) {
                is ReportLoadResult.Loaded -> state.copy(
                    reports = if (older) (result.page.reports + state.reports).distinctBy { it.id }.sortedWith(ReportRules.newestFirst)
                        else result.page.reports,
                    next = result.page.next.takeUnless { result.isOffline },
                    isLoading = false, isLoadingMore = false, isOffline = result.isOffline,
                )
                is ReportLoadResult.Failure -> if (older) state.copy(isLoadingMore = false, pagingError = result.message)
                    else state.copy(isLoading = false, next = null, isOffline = state.reports.isNotEmpty(), error = result.message)
            }
        }
    }
}
