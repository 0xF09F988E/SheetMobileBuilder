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

data class DashboardUiState(
    val collections: List<CollectionOption> = emptyList(),
    val selectedCollection: CollectionOption? = null,
    val fields: List<FieldDefinition> = emptyList(),
    val selectedField: FieldDefinition? = null,
    val selectedReviewStatus: String? = null,
    val reviewSummary: ReviewStatusDashboardSummary = ReviewStatusDashboardSummary(),
    val groupedCards: List<GroupedDashboardCard> = emptyList(),
    val hasGenerated: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
) {
    val hasDataSource: Boolean
        get() = selectedCollection != null

    val canGenerate: Boolean
        get() = selectedCollection != null && selectedField != null && !isLoading
}

class DashboardViewModel(
    private val repository: DashboardRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private var activeJob: Job? = null

    fun loadInitial() {
        activeJob?.cancel()
        activeJob = viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true, errorMessage = null) }
                val collections = repository.loadCollections()
                _uiState.value = DashboardUiState(
                    collections = collections,
                    selectedCollection = null,
                    fields = emptyList(),
                    selectedField = null,
                    selectedReviewStatus = null,
                    reviewSummary = ReviewStatusDashboardSummary(),
                    groupedCards = emptyList(),
                    hasGenerated = false,
                    isLoading = false
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = error.message
                    )
                }
            }
        }
    }

    fun selectCollection(collection: CollectionOption?) {
        val selected = collection ?: return
        if (_uiState.value.selectedCollection?.id == selected.id) return
        activeJob?.cancel()
        activeJob = viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true, errorMessage = null) }
                val fields = repository.loadFields(selected.id)
                    .filter { !it.isUniqueValue }
                _uiState.update {
                    it.copy(
                        selectedCollection = selected,
                        fields = fields,
                        selectedField = null,
                        selectedReviewStatus = null,
                        reviewSummary = ReviewStatusDashboardSummary(),
                        groupedCards = emptyList(),
                        hasGenerated = false,
                        isLoading = false,
                        errorMessage = null
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = error.message
                    )
                }
            }
        }
    }

    fun selectField(field: FieldDefinition?) {
        val selected = field ?: return
        if (_uiState.value.selectedField?.id == selected.id) return
        _uiState.update {
            it.copy(
                selectedField = selected,
                reviewSummary = ReviewStatusDashboardSummary(),
                groupedCards = emptyList(),
                hasGenerated = false,
                errorMessage = null
            )
        }
    }

    fun selectReviewStatus(reviewStatus: String?) {
        if (_uiState.value.selectedReviewStatus == reviewStatus) return
        _uiState.update {
            it.copy(
                selectedReviewStatus = reviewStatus,
                reviewSummary = ReviewStatusDashboardSummary(),
                groupedCards = emptyList(),
                hasGenerated = false,
                errorMessage = null
            )
        }
    }

    fun generate() {
        val selectedCollection = _uiState.value.selectedCollection ?: return
        val selectedField = _uiState.value.selectedField ?: return
        val selectedReviewStatus = _uiState.value.selectedReviewStatus

        activeJob?.cancel()
        activeJob = viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true, errorMessage = null) }
                val reviewSummary = repository.loadReviewSummary(selectedCollection.id)
                val groupedCards = repository.loadGroupedCards(
                    selectedCollection.id,
                    selectedField.id,
                    selectedReviewStatus
                )
                _uiState.update {
                    it.copy(
                        reviewSummary = reviewSummary,
                        groupedCards = groupedCards,
                        hasGenerated = true,
                        isLoading = false,
                        errorMessage = null
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
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

class DashboardViewModelFactory(
    private val repository: DashboardRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DashboardViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return DashboardViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
