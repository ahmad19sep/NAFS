package com.ahmad.nafs.ui.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ahmad.nafs.core.reports.AiReport
import com.ahmad.nafs.core.reports.ReportDetailResult
import com.ahmad.nafs.core.reports.ReportRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ReportDetailUiState(
    val report: AiReport? = null,
    val isLoading: Boolean = true,
    val isOffline: Boolean = false,
    val notFound: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class ReportDetailViewModel @Inject constructor(private val repository: ReportRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(ReportDetailUiState())
    val uiState = _uiState.asStateFlow()
    private var identity: Pair<String, String>? = null
    private var job: Job? = null
    private var generation = 0

    fun initialize(userId: String, reportId: String) {
        val nextIdentity = userId to reportId
        if (identity == nextIdentity) return
        job?.cancel()
        generation++
        identity = nextIdentity
        _uiState.value = ReportDetailUiState()
        refresh()
    }

    fun refresh() {
        val requested = identity ?: return
        if (job?.isActive == true) return
        val request = ++generation
        _uiState.value = _uiState.value.copy(isLoading = true, error = null, notFound = false)
        job = viewModelScope.launch {
            val result = repository.detail(requested.first, requested.second)
            if (generation != request || identity != requested) return@launch
            _uiState.value = when (result) {
                is ReportDetailResult.Loaded -> ReportDetailUiState(report = result.report, isLoading = false, isOffline = result.isOffline)
                ReportDetailResult.NotFound -> ReportDetailUiState(isLoading = false, notFound = true)
                is ReportDetailResult.Failure -> _uiState.value.copy(isLoading = false, isOffline = _uiState.value.report != null, error = result.message)
            }
        }
    }
}
