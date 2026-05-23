package com.pwa.offline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ImportUiPhase {
    IDLE,
    INSPECTING,
    VALIDATING,
    IMPORTING,
    CLEARING
}

data class ImportUiState(
    val tableOptions: List<CollectionOption> = emptyList(),
    val workbookSheets: List<WorkbookSheet> = emptyList(),
    val selectedTableOption: CollectionOption? = null,
    val selectedSheet: WorkbookSheet? = null,
    val selectedHeaderRowIndex: Int = ImportConfig.defaultHeaderRowIndex,
    val expectedFieldNames: List<String> = emptyList(),
    val selectedFileLabel: String = "",
    val validationResult: ImportValidationResult? = null,
    val importResult: ImportExecutionResult? = null,
    val progress: ImportProgressState = ImportProgressState(),
    val phase: ImportUiPhase = ImportUiPhase.IDLE,
    val errorMessage: String? = null,
    val cancelledByExit: Boolean = false,
    val cancelledManually: Boolean = false
) {
    val isBusy: Boolean
        get() = phase != ImportUiPhase.IDLE
}

class ImportViewModel(
    private val repository: ImportRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ImportUiState())
    val uiState: StateFlow<ImportUiState> = _uiState.asStateFlow()

    private var currentJob: Job? = null

    fun loadInitialState() {
        if (_uiState.value.tableOptions.isNotEmpty()) return
        viewModelScope.launch {
            val tableOptions = repository.loadTableOptions()
            val selected = tableOptions.firstOrNull() ?: CollectionOption.EMPTY
            val expected = selected.id.takeIf { it >= 0 }?.let { id ->
                repository.loadFieldDefinitions(id).map { it.displayName }
            }.orEmpty()
            _uiState.update {
                it.copy(
                    tableOptions = tableOptions,
                    selectedTableOption = selected,
                    expectedFieldNames = expected
                )
            }
        }
    }

    fun setSelectedFileLabel(label: String) {
        _uiState.update {
            it.copy(
                selectedFileLabel = label,
                validationResult = null,
                importResult = null,
                progress = ImportProgressState(),
                errorMessage = null,
                cancelledByExit = false,
                cancelledManually = false
            )
        }
    }

    fun selectTable(option: CollectionOption?) {
        viewModelScope.launch {
            val selected = option ?: CollectionOption.EMPTY
            val expected = selected.id.takeIf { it >= 0 }?.let { id ->
                repository.loadFieldDefinitions(id).map { it.displayName }
            }.orEmpty()
            _uiState.update {
                it.copy(
                    selectedTableOption = selected,
                    expectedFieldNames = expected,
                    validationResult = null,
                    importResult = null,
                    progress = ImportProgressState(),
                    errorMessage = null,
                    cancelledManually = false
                )
            }
        }
    }

    fun selectSheet(sheet: WorkbookSheet?) {
        _uiState.update {
            it.copy(
                selectedSheet = sheet,
                validationResult = null,
                importResult = null,
                progress = ImportProgressState(),
                errorMessage = null,
                cancelledManually = false
            )
        }
    }

    fun selectHeaderRow(index: Int) {
        _uiState.update {
            it.copy(
                selectedHeaderRowIndex = index,
                validationResult = null,
                importResult = null,
                progress = ImportProgressState(),
                errorMessage = null,
                cancelledManually = false
            )
        }
    }

    fun inspectWorkbook(file: File) {
        launchPhase(ImportUiPhase.INSPECTING) {
            val inspection = repository.inspectWorkbook(file)
            _uiState.update {
                it.copy(
                    workbookSheets = inspection.sheets,
                    selectedSheet = inspection.sheets.firstOrNull(),
                    errorMessage = null,
                    cancelledByExit = false,
                    cancelledManually = false
                )
            }
        }
    }

    fun validateSelection(file: File) {
        val state = _uiState.value
        val collectionId = state.selectedTableOption?.id ?: return
        val sheet = state.selectedSheet ?: return
        if (collectionId < 0) return

        launchPhase(ImportUiPhase.VALIDATING) {
            val result = repository.validateSelection(
                file = file,
                collectionId = collectionId,
                sheet = sheet,
                headerRowIndex = state.selectedHeaderRowIndex
            )
            _uiState.update {
                it.copy(
                    validationResult = result,
                    importResult = null,
                    progress = ImportProgressState(),
                    errorMessage = null,
                    cancelledByExit = false,
                    cancelledManually = false
                )
            }
        }
    }

    fun importFile(file: File) {
        val state = _uiState.value
        val collectionId = state.selectedTableOption?.id ?: return
        val validation = state.validationResult?.validationState ?: return
        if (collectionId < 0 || !validation.isValid) return

        launchPhase(ImportUiPhase.IMPORTING) {
            val result = repository.importFile(
                file = file,
                collectionId = collectionId,
                validationState = validation,
                onProgress = { processedRows, importedRows, skippedRows, totalRows, elapsedMs ->
                    _uiState.update {
                        it.copy(
                            progress = ImportProgressState(
                                processedRows = processedRows,
                                importedRows = importedRows,
                                skippedRows = skippedRows,
                                metrics = ProgressState(
                                    processedUnits = processedRows,
                                    totalUnits = totalRows,
                                    elapsedMs = elapsedMs
                                )
                            )
                        )
                    }
                }
            )
            _uiState.update {
                it.copy(
                    importResult = result,
                    progress = ImportProgressState(
                        processedRows = result.processedRows,
                        importedRows = result.importedRows,
                        skippedRows = result.skippedRows,
                        metrics = ProgressState(
                            processedUnits = result.processedRows,
                            totalUnits = result.processedRows,
                            elapsedMs = result.elapsedMs
                        )
                    ),
                    errorMessage = null,
                    cancelledByExit = false,
                    cancelledManually = false
                )
            }
        }
    }

    fun clearCollectionData() {
        val collection = _uiState.value.selectedTableOption ?: return
        if (collection.id < 0) return

        launchPhase(ImportUiPhase.CLEARING) {
            repository.clearCollectionData(collection.id)
            _uiState.update {
                it.copy(
                    importResult = null,
                    progress = ImportProgressState(),
                    errorMessage = null,
                    cancelledByExit = false,
                    cancelledManually = false
                )
            }
        }
    }

    fun cancelImport(cancelledByExit: Boolean = false) {
        if (_uiState.value.phase != ImportUiPhase.IMPORTING) return
        _uiState.update {
            it.copy(
                cancelledByExit = cancelledByExit,
                cancelledManually = !cancelledByExit
            )
        }
        currentJob?.cancel()
    }

    private fun launchPhase(
        phase: ImportUiPhase,
        block: suspend () -> Unit
    ) {
        currentJob?.cancel()
        currentJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    phase = phase,
                    errorMessage = null,
                    cancelledByExit = false,
                    cancelledManually = false
                )
            }
            try {
                block()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                _uiState.update {
                    it.copy(errorMessage = error.message)
                }
            } finally {
                _uiState.update { it.copy(phase = ImportUiPhase.IDLE) }
            }
        }
    }

    override fun onCleared() {
        currentJob?.cancel()
        repository.close()
        super.onCleared()
    }
}

class ImportViewModelFactory(
    private val repository: ImportRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ImportViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ImportViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
