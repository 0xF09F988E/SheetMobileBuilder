package com.pwa.offline

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

data class BrowseUiState(
    val masterCollection: CollectionOption? = null,
    val currentPage: Int = 0,
    val totalRecords: Int = 0,
    val records: List<RecordPreview> = emptyList(),
    val isLoading: Boolean = false,
    val hasMasterTable: Boolean = true,
    val actionMessage: String? = null,
    val actionMessageNonce: Long = 0L
) {
    val totalPages: Int
        get() = if (totalRecords == 0) 0 else ((totalRecords - 1) / PAGE_SIZE) + 1

    companion object {
        const val PAGE_SIZE = 50
    }
}

class BrowseViewModel(
    private val repository: BrowseRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(BrowseUiState())
    val uiState: StateFlow<BrowseUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    fun loadInitial() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true) }
                val master = repository.loadMasterCollection()
                if (master == null) {
                    _uiState.update {
                        it.copy(
                            masterCollection = null,
                            records = emptyList(),
                            totalRecords = 0,
                            currentPage = 0,
                            hasMasterTable = false,
                            isLoading = false
                        )
                    }
                    return@launch
                }
                loadPageInternal(master, 0)
            } catch (cancelled: CancellationException) {
                throw cancelled
            }
        }
    }

    fun goPrevious() {
        val state = _uiState.value
        val master = state.masterCollection ?: return
        if (state.currentPage <= 0 || state.isLoading) return
        loadPage(master, state.currentPage - 1)
    }

    fun goNext() {
        val state = _uiState.value
        val master = state.masterCollection ?: return
        if (state.isLoading) return
        if ((state.currentPage + 1) * BrowseUiState.PAGE_SIZE >= state.totalRecords) return
        loadPage(master, state.currentPage + 1)
    }

    fun refresh() {
        val state = _uiState.value
        val master = state.masterCollection
        if (master == null) {
            loadInitial()
        } else {
            loadPage(master, state.currentPage)
        }
    }

    fun deleteRecord(record: RecordPreview) {
        val state = _uiState.value
        val master = state.masterCollection ?: return
        if (state.isLoading) return
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true) }
                repository.deleteRecord(record.recordId)
                val totalRecords = repository.countRecords(master.id)
                val maxPage = if (totalRecords == 0) 0 else ((totalRecords - 1) / BrowseUiState.PAGE_SIZE)
                val targetPage = state.currentPage.coerceAtMost(maxPage)
                val records = repository.loadPage(master.id, targetPage, BrowseUiState.PAGE_SIZE)
                _uiState.update {
                    it.copy(
                        masterCollection = master,
                        currentPage = targetPage,
                        totalRecords = totalRecords,
                        records = records,
                        hasMasterTable = true,
                        isLoading = false,
                        actionMessage = record.title.ifBlank { "Registro" },
                        actionMessageNonce = it.actionMessageNonce + 1L
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            }
        }
    }

    fun clearActionMessage() {
        _uiState.update { it.copy(actionMessage = null) }
    }

    private fun loadPage(master: CollectionOption, page: Int) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            try {
                loadPageInternal(master, page)
            } catch (cancelled: CancellationException) {
                throw cancelled
            }
        }
    }

    private suspend fun loadPageInternal(master: CollectionOption, page: Int) {
        _uiState.update {
            it.copy(
                masterCollection = master,
                isLoading = true,
                hasMasterTable = true
            )
        }
        val totalRecords = repository.countRecords(master.id)
        val records = repository.loadPage(master.id, page, BrowseUiState.PAGE_SIZE)
        _uiState.update {
            it.copy(
                masterCollection = master,
                currentPage = page,
                totalRecords = totalRecords,
                records = records,
                hasMasterTable = true,
                isLoading = false
            )
        }
    }

    override fun onCleared() {
        loadJob?.cancel()
        repository.close()
        super.onCleared()
    }
}

class BrowseViewModelFactory(
    private val repository: BrowseRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(BrowseViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return BrowseViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
