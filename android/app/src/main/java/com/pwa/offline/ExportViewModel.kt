package com.pwa.offline

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ExportUiPhase {
    IDLE,
    COUNTING,
    EXPORTING
}

data class ExportUiState(
    val masterCollection: CollectionOption? = null,
    val fieldDefinitions: List<FieldDefinition> = emptyList(),
    val criterionOptions: List<ExportCriterionOption> = emptyList(),
    val selectedCriterionOption: ExportCriterionOption? = null,
    val delimiterOptions: List<ExportDelimiterOption> = emptyList(),
    val selectedDelimiterOption: ExportDelimiterOption? = null,
    val customDelimiterValue: String = "",
    val exportableRecordCount: Int = 0,
    val progress: ExportProgressState = ExportProgressState(),
    val lastResult: ExportExecutionResult? = null,
    val pendingShareArtifact: ShareExportArtifact? = null,
    val phase: ExportUiPhase = ExportUiPhase.IDLE,
    val errorMessage: String? = null,
    val cancelledByExit: Boolean = false
) {
    val isBusy: Boolean
        get() = phase == ExportUiPhase.EXPORTING

    val isCounting: Boolean
        get() = phase == ExportUiPhase.COUNTING

    val isReady: Boolean
        get() = masterCollection != null &&
            fieldDefinitions.isNotEmpty() &&
            selectedCriterionOption != null &&
            resolvedDelimiter != null

    val suggestedFileName: String
        get() {
            val collection = masterCollection ?: return "export.csv"
            val criterion = selectedCriterionOption?.criterion ?: ExportCriterion.default()
            return ExportConfig.buildFileName(collection, criterion)
        }

    val requiresCustomDelimiter: Boolean
        get() = selectedDelimiterOption?.delimiter == ExportDelimiter.CUSTOM

    val resolvedDelimiter: String?
        get() {
            val option = selectedDelimiterOption ?: return null
            return if (option.delimiter == ExportDelimiter.CUSTOM) {
                customDelimiterValue.takeIf { it.isNotBlank() }
            } else {
                option.delimiter.delimiter
            }
        }
}

class ExportViewModel(
    private val repository: ExportRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ExportUiState())
    val uiState: StateFlow<ExportUiState> = _uiState.asStateFlow()

    private var activeJob: Job? = null

    fun loadInitialState() {
        if (_uiState.value.criterionOptions.isNotEmpty()) return
        activeJob?.cancel()
        activeJob = viewModelScope.launch {
            try {
                val master = repository.loadMasterCollection()
                val criteria = repository.loadCriterionOptions()
                val selectedCriterion = criteria.firstOrNull()
                val delimiters = repository.loadDelimiterOptions()
                val selectedDelimiter = delimiters.firstOrNull { it.delimiter == ExportDelimiter.default() }
                    ?: delimiters.firstOrNull()
                val fields = master?.let { repository.loadFieldDefinitions(it.id) }.orEmpty()
                val exportableCount = if (master != null && selectedCriterion != null) {
                    repository.countRecords(master.id, selectedCriterion.criterion)
                } else {
                    0
                }
                _uiState.value = ExportUiState(
                    masterCollection = master,
                    fieldDefinitions = fields,
                    criterionOptions = criteria,
                    selectedCriterionOption = selectedCriterion,
                    delimiterOptions = delimiters,
                    selectedDelimiterOption = selectedDelimiter,
                    exportableRecordCount = exportableCount
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                _uiState.update { it.copy(errorMessage = error.message) }
            }
        }
    }

    fun selectCriterion(option: ExportCriterionOption?) {
        val selected = option ?: return
        val master = _uiState.value.masterCollection ?: return
        if (_uiState.value.selectedCriterionOption?.criterion == selected.criterion) return

        activeJob?.cancel()
        activeJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    selectedCriterionOption = selected,
                    phase = ExportUiPhase.COUNTING,
                    errorMessage = null,
                    lastResult = null,
                    pendingShareArtifact = null,
                    cancelledByExit = false
                )
            }
            try {
                val exportableCount = repository.countRecords(master.id, selected.criterion)
                _uiState.update {
                    it.copy(
                        exportableRecordCount = exportableCount,
                        phase = ExportUiPhase.IDLE
                    )
                }
            } catch (cancelled: CancellationException) {
                _uiState.update { it.copy(phase = ExportUiPhase.IDLE) }
                throw cancelled
            } catch (error: Throwable) {
                _uiState.update {
                    it.copy(
                        phase = ExportUiPhase.IDLE,
                        errorMessage = error.message
                    )
                }
            }
        }
    }

    fun selectDelimiter(option: ExportDelimiterOption?) {
        val selected = option ?: return
        _uiState.update {
            it.copy(
                selectedDelimiterOption = selected,
                customDelimiterValue = if (selected.delimiter == ExportDelimiter.CUSTOM) it.customDelimiterValue else "",
                errorMessage = null,
                lastResult = null,
                pendingShareArtifact = null,
                cancelledByExit = false
            )
        }
    }

    fun updateCustomDelimiter(rawValue: String) {
        _uiState.update {
            it.copy(
                customDelimiterValue = rawValue.take(3),
                errorMessage = null
            )
        }
    }

    fun exportToUri(uri: Uri) {
        val state = _uiState.value
        val master = state.masterCollection ?: return
        val criterion = state.selectedCriterionOption?.criterion ?: return
        val delimiter = state.resolvedDelimiter ?: run {
            _uiState.update { it.copy(errorMessage = repository.delimiterRequiredMessage()) }
            return
        }
        val fields = state.fieldDefinitions
        if (fields.isEmpty()) return

        launchExport {
            repository.exportToUri(
                uri = uri,
                collection = master,
                fields = fields,
                criterion = criterion,
                delimiter = delimiter,
                onProgress = ::updateProgress
            )
        }
    }

    fun exportToShareFile() {
        val state = _uiState.value
        val master = state.masterCollection ?: return
        val criterion = state.selectedCriterionOption?.criterion ?: return
        val delimiter = state.resolvedDelimiter ?: run {
            _uiState.update { it.copy(errorMessage = repository.delimiterRequiredMessage()) }
            return
        }
        val fields = state.fieldDefinitions
        if (fields.isEmpty()) return

        launchExport {
            val (result, artifact) = repository.exportToShareFile(
                collection = master,
                fields = fields,
                criterion = criterion,
                delimiter = delimiter,
                onProgress = ::updateProgress
            )
            _uiState.update { current ->
                current.copy(
                    lastResult = result,
                    pendingShareArtifact = artifact,
                    errorMessage = null,
                    cancelledByExit = false
                )
            }
            result
        }
    }

    fun clearPendingShareArtifact() {
        _uiState.update { it.copy(pendingShareArtifact = null) }
    }

    fun cancelExport(cancelledByExit: Boolean = false) {
        if (_uiState.value.phase != ExportUiPhase.EXPORTING) return
        _uiState.update { it.copy(cancelledByExit = cancelledByExit) }
        activeJob?.cancel()
    }

    private fun launchExport(block: suspend () -> ExportExecutionResult) {
        activeJob?.cancel()
        activeJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    phase = ExportUiPhase.EXPORTING,
                    progress = ExportProgressState(),
                    errorMessage = null,
                    lastResult = null,
                    pendingShareArtifact = null,
                    cancelledByExit = false
                )
            }
            try {
                val result = block()
                _uiState.update {
                    it.copy(
                        phase = ExportUiPhase.IDLE,
                        progress = ExportProgressState(
                            exportedRows = result.exportedRows,
                            metrics = ProgressState(
                                processedUnits = result.exportedRows,
                                totalUnits = result.exportedRows,
                                elapsedMs = result.elapsedMs
                            )
                        ),
                        lastResult = result
                    )
                }
            } catch (cancelled: CancellationException) {
                _uiState.update { it.copy(phase = ExportUiPhase.IDLE) }
                throw cancelled
            } catch (error: Throwable) {
                _uiState.update {
                    it.copy(
                        phase = ExportUiPhase.IDLE,
                        errorMessage = error.message
                    )
                }
            }
        }
    }

    private fun updateProgress(processedRows: Int, totalRows: Int, elapsedMs: Long) {
        _uiState.update {
            it.copy(
                progress = ExportProgressState(
                    exportedRows = processedRows,
                    metrics = ProgressState(
                        processedUnits = processedRows,
                        totalUnits = totalRows,
                        elapsedMs = elapsedMs
                    )
                )
            )
        }
    }

    override fun onCleared() {
        activeJob?.cancel()
        repository.close()
        super.onCleared()
    }
}

class ExportViewModelFactory(
    private val repository: ExportRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ExportViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ExportViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
