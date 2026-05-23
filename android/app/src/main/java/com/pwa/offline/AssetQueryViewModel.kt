package com.pwa.offline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class AssetQueryStatus {
    EMPTY,
    LOOKUP_REQUIRED,
    READY,
    SEARCHING,
    NOT_FOUND,
    READ_ONLY,
    EDITING,
    CONFIRMING,
    CONFIRMED,
    SAVING,
    SAVED,
    ERROR
}

data class AssetQueryUiState(
    val collectionId: Long? = null,
    val lookupFields: List<FieldDefinition> = emptyList(),
    val recordDetail: AssetRecordDetail? = null,
    val isEditing: Boolean = false,
    val isBusy: Boolean = false,
    val status: AssetQueryStatus = AssetQueryStatus.EMPTY,
    val errorMessage: String? = null
)

class AssetQueryViewModel(
    private val repository: AssetQueryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AssetQueryUiState())
    val uiState: StateFlow<AssetQueryUiState> = _uiState.asStateFlow()

    private var activeJob: Job? = null

    fun loadInitial() {
        activeJob?.cancel()
        activeJob = viewModelScope.launch {
            try {
                val master = repository.loadMasterCollection()
                if (master == null) {
                    _uiState.value = AssetQueryUiState(
                        collectionId = null,
                        lookupFields = emptyList(),
                        recordDetail = null,
                        isEditing = false,
                        isBusy = false,
                        status = AssetQueryStatus.EMPTY
                    )
                    return@launch
                }
                val lookupFields = repository.loadLookupFields(master.id)
                _uiState.value = AssetQueryUiState(
                    collectionId = master.id,
                    lookupFields = lookupFields,
                    recordDetail = null,
                    isEditing = false,
                    isBusy = false,
                    status = if (lookupFields.isEmpty()) AssetQueryStatus.LOOKUP_REQUIRED else AssetQueryStatus.READY
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                _uiState.update {
                    it.copy(
                        isBusy = false,
                        status = AssetQueryStatus.ERROR,
                        errorMessage = error.message
                    )
                }
            }
        }
    }

    fun search(query: String) {
        val state = _uiState.value
        val collectionId = state.collectionId ?: return
        if (state.lookupFields.isEmpty()) {
            _uiState.update { it.copy(status = AssetQueryStatus.LOOKUP_REQUIRED) }
            return
        }
        if (query.isBlank()) {
            _uiState.update { it.copy(status = AssetQueryStatus.READY) }
            return
        }

        activeJob?.cancel()
        activeJob = viewModelScope.launch {
            try {
                _uiState.update {
                    it.copy(
                        isBusy = true,
                        status = AssetQueryStatus.SEARCHING,
                        errorMessage = null
                    )
                }
                val detail = repository.findRecord(collectionId, query)
                _uiState.update {
                    if (detail == null) {
                        it.copy(
                            recordDetail = null,
                            isEditing = false,
                            isBusy = false,
                            status = AssetQueryStatus.NOT_FOUND
                        )
                    } else {
                        it.copy(
                            recordDetail = detail,
                            isEditing = false,
                            isBusy = false,
                            status = AssetQueryStatus.READ_ONLY
                        )
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                _uiState.update {
                    it.copy(
                        isBusy = false,
                        status = AssetQueryStatus.ERROR,
                        errorMessage = error.message
                    )
                }
            }
        }
    }

    fun clearResult() {
        _uiState.update {
            it.copy(
                recordDetail = null,
                isEditing = false,
                status = if (it.lookupFields.isEmpty()) AssetQueryStatus.LOOKUP_REQUIRED else AssetQueryStatus.READY,
                errorMessage = null
            )
        }
    }

    fun enterEditMode() {
        if (_uiState.value.recordDetail == null) return
        _uiState.update {
            it.copy(
                isEditing = true,
                status = AssetQueryStatus.EDITING,
                errorMessage = null
            )
        }
    }

    fun exitEditMode() {
        if (_uiState.value.recordDetail == null) return
        _uiState.update {
            it.copy(
                isEditing = false,
                status = AssetQueryStatus.READ_ONLY,
                errorMessage = null
            )
        }
    }

    fun save(updates: Map<Long, String>) {
        val detail = _uiState.value.recordDetail ?: return
        activeJob?.cancel()
        activeJob = viewModelScope.launch {
            try {
                _uiState.update {
                    it.copy(
                        isBusy = true,
                        status = AssetQueryStatus.SAVING,
                        errorMessage = null
                    )
                }
                val fields = repository.listFieldDefinitions(detail.collectionId)
                val refreshed = repository.updateRecord(
                    recordId = detail.recordId,
                    collectionId = detail.collectionId,
                    fields = fields,
                    updates = updates
                )
                _uiState.update {
                    it.copy(
                        recordDetail = refreshed ?: detail,
                        isEditing = false,
                        isBusy = false,
                        status = AssetQueryStatus.SAVED
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                _uiState.update {
                    it.copy(
                        isBusy = false,
                        status = AssetQueryStatus.ERROR,
                        errorMessage = error.message
                    )
                }
            }
        }
    }

    fun markConforme() {
        val detail = _uiState.value.recordDetail ?: return
        activeJob?.cancel()
        activeJob = viewModelScope.launch {
            try {
                _uiState.update {
                    it.copy(
                        isBusy = true,
                        status = AssetQueryStatus.CONFIRMING,
                        errorMessage = null
                    )
                }
                val refreshed = repository.markConforme(
                    detail.recordId,
                    detail.collectionId
                )
                _uiState.update {
                    it.copy(
                        recordDetail = refreshed ?: detail,
                        isEditing = false,
                        isBusy = false,
                        status = AssetQueryStatus.CONFIRMED
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                _uiState.update {
                    it.copy(
                        isBusy = false,
                        status = AssetQueryStatus.ERROR,
                        errorMessage = error.message
                    )
                }
            }
        }
    }

    fun requestOptionSuggestions(
        sourceCollectionId: Long,
        query: String,
        onResult: (List<OptionSuggestion>) -> Unit
    ) {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isNotEmpty() && normalizedQuery.length < 2) {
            onResult(emptyList())
            return
        }

        viewModelScope.launch {
            try {
                val suggestions = repository.searchOptionSuggestions(
                    collectionId = sourceCollectionId,
                    query = normalizedQuery,
                    limit = 20
                )
                withContext(Dispatchers.Main) {
                    onResult(suggestions)
                }
            } catch (_: Throwable) {
                withContext(Dispatchers.Main) {
                    onResult(emptyList())
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

class AssetQueryViewModelFactory(
    private val repository: AssetQueryRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AssetQueryViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return AssetQueryViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
