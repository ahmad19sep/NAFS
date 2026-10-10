package com.ahmad.nafs.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ahmad.nafs.core.export.DataExportResult
import com.ahmad.nafs.core.export.DataExporter
import com.ahmad.nafs.core.export.ExportProgress
import com.ahmad.nafs.core.export.ExportTables
import com.ahmad.nafs.core.export.UnavailableTable
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface DataExportState {
    data object Idle : DataExportState

    data class Running(val progress: ExportProgress) : DataExportState

    data class Ready(
        val filePath: String,
        val fileName: String,
        val sizeBytes: Long,
        val rowCount: Long,
        val unavailable: List<UnavailableTable>,
    ) : DataExportState

    data class Failed(val message: String) : DataExportState
}

/** "Download my data". Closing the sheet doesn't stop an export; leaving Profile does, and nothing is kept half-written. */
@HiltViewModel
class DataExportViewModel @Inject constructor(private val exporter: DataExporter) : ViewModel() {
    private val _state = MutableStateFlow<DataExportState>(DataExportState.Idle)
    val state: StateFlow<DataExportState> = _state.asStateFlow()
    private var job: Job? = null

    /** Progress arrives from a background thread; a cancelled run must not overwrite what came after it. */
    private val generation = AtomicInteger(0)

    fun start(userId: String) {
        if (job?.isActive == true) return
        val run = generation.incrementAndGet()
        _state.value = DataExportState.Running(ExportProgress(0, ExportTables.all.size, ExportTables.all.first().label, 0))
        job = viewModelScope.launch {
            val result = exporter.export(userId) { progress ->
                if (generation.get() == run) _state.value = DataExportState.Running(progress)
            }
            if (generation.get() != run) return@launch
            _state.value = when (result) {
                is DataExportResult.Ready -> DataExportState.Ready(
                    filePath = result.file.path,
                    fileName = result.file.name,
                    sizeBytes = result.sizeBytes,
                    rowCount = result.summary.totalRows,
                    unavailable = result.summary.unavailable,
                )
                is DataExportResult.Failed -> DataExportState.Failed(result.message)
            }
        }
    }

    fun cancel() {
        generation.incrementAndGet()
        job?.cancel()
        job = null
        _state.value = DataExportState.Idle
    }

    /** Back to the start once a finished or failed export has been dealt with; a running one is left alone. */
    fun reset() {
        if (job?.isActive != true) _state.value = DataExportState.Idle
    }

    fun shareFailed() {
        _state.value = DataExportState.Failed("NAFS couldn't open the share sheet. Prepare the file again and retry.")
    }
}
