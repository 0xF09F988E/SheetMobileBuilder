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

enum class CloneDataPhase {
    IDLE,
    LOADING,
    EXPORTING,
    IMPORTING
}

data class CloneDataUiState(
    val localSummary: CloneSnapshotSummary? = null,
    val phase: CloneDataPhase = CloneDataPhase.IDLE,
    val errorMessage: String? = null,
    val lastExportResult: CloneExportResult? = null,
    val lastImportResult: CloneImportResult? = null,
    val restartRequired: Boolean = false
) {
    val isBusy: Boolean
        get() = phase == CloneDataPhase.LOADING ||
            phase == CloneDataPhase.EXPORTING ||
            phase == CloneDataPhase.IMPORTING
}

class CloneDataViewModel(
    private val repository: CloneDataRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CloneDataUiState())
    val uiState: StateFlow<CloneDataUiState> = _uiState.asStateFlow()

    private var activeJob: Job? = null

    fun loadInitialState() {
        if (_uiState.value.localSummary != null && _uiState.value.phase != CloneDataPhase.LOADING) {
            return
        }
        loadSummary()
    }

    fun exportBackup(targetUri: Uri) {
        activeJob?.cancel()
        activeJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    phase = CloneDataPhase.EXPORTING,
                    errorMessage = null,
                    lastExportResult = null
                )
            }
            try {
                val result = repository.exportBackup(targetUri)
                val summary = repository.loadLocalSummary()
                _uiState.update {
                    it.copy(
                        localSummary = summary,
                        phase = CloneDataPhase.IDLE,
                        lastExportResult = result,
                        errorMessage = null
                    )
                }
            } catch (cancelled: CancellationException) {
                _uiState.update { it.copy(phase = CloneDataPhase.IDLE) }
                throw cancelled
            } catch (error: Throwable) {
                _uiState.update {
                    it.copy(
                        phase = CloneDataPhase.IDLE,
                        errorMessage = error.message
                    )
                }
            }
        }
    }

    fun importBackup(sourceUri: Uri) {
        activeJob?.cancel()
        activeJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    phase = CloneDataPhase.IMPORTING,
                    errorMessage = null,
                    lastImportResult = null,
                    restartRequired = false
                )
            }
            try {
                val result = repository.importBackup(sourceUri)
                _uiState.update {
                    it.copy(
                        localSummary = result.restoredSummary,
                        phase = CloneDataPhase.IDLE,
                        lastImportResult = result,
                        restartRequired = true,
                        errorMessage = null
                    )
                }
            } catch (cancelled: CancellationException) {
                _uiState.update { it.copy(phase = CloneDataPhase.IDLE) }
                throw cancelled
            } catch (error: Throwable) {
                _uiState.update {
                    it.copy(
                        phase = CloneDataPhase.IDLE,
                        errorMessage = error.message
                    )
                }
            }
        }
    }

    fun clearRestartRequired() {
        _uiState.update { it.copy(restartRequired = false) }
    }

    private fun loadSummary() {
        activeJob?.cancel()
        activeJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    phase = CloneDataPhase.LOADING,
                    errorMessage = null
                )
            }
            try {
                val summary = repository.loadLocalSummary()
                _uiState.update {
                    it.copy(
                        localSummary = summary,
                        phase = CloneDataPhase.IDLE,
                        errorMessage = null
                    )
                }
            } catch (cancelled: CancellationException) {
                _uiState.update { it.copy(phase = CloneDataPhase.IDLE) }
                throw cancelled
            } catch (error: Throwable) {
                _uiState.update {
                    it.copy(
                        phase = CloneDataPhase.IDLE,
                        errorMessage = error.message
                    )
                }
            }
        }
    }

    override fun onCleared() {
        activeJob?.cancel()
        repository.close()
        super.onCleared()
    }
}

class CloneDataViewModelFactory(
    private val repository: CloneDataRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CloneDataViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return CloneDataViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
