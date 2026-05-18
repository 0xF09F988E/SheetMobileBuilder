package com.pwa.offline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class RecordCreateStatus {
    EMPTY,
    READY,
    LOADING_FIELDS,
    SAVING,
    SAVED,
    ERROR
}

data class RecordCreateUiState(
    val collectionOptions: List<CollectionOption> = emptyList(),
    val selectedCollection: CollectionOption? = null,
    val fieldDefinitions: List<FieldDefinition> = emptyList(),
    val formFields: List<AssetFieldValue> = emptyList(),
    val formVersion: Long = 0L,
    val isBusy: Boolean = false,
    val status: RecordCreateStatus = RecordCreateStatus.EMPTY,
    val savedRecordLabel: String? = null,
    val errorMessage: String? = null
)

class RecordCreateViewModel(
    private val repository: RecordCreateRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(RecordCreateUiState())
    val uiState: StateFlow<RecordCreateUiState> = _uiState.asStateFlow()

    fun loadInitial() {
        viewModelScope.launch {
            try {
                val collections = repository.loadCollections()
                val selected = collections.firstOrNull()
                val fields = selected?.let { repository.loadFieldDefinitions(it.id) }.orEmpty()
                _uiState.value = RecordCreateUiState(
                    collectionOptions = collections,
                    selectedCollection = selected,
                    fieldDefinitions = fields,
                    formFields = buildEmptyFormFields(fields),
                    formVersion = 1L,
                    status = if (collections.isEmpty()) RecordCreateStatus.EMPTY else RecordCreateStatus.READY
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                _uiState.value = RecordCreateUiState(
                    status = RecordCreateStatus.ERROR,
                    errorMessage = error.message
                )
            }
        }
    }

    fun selectCollection(collectionId: Long) {
        val selected = _uiState.value.collectionOptions.firstOrNull { it.id == collectionId } ?: return
        viewModelScope.launch {
            try {
                _uiState.update {
                    it.copy(
                        selectedCollection = selected,
                        fieldDefinitions = emptyList(),
                        formFields = emptyList(),
                        isBusy = true,
                        status = RecordCreateStatus.LOADING_FIELDS,
                        savedRecordLabel = null,
                        errorMessage = null
                    )
                }
                val fields = repository.loadFieldDefinitions(selected.id)
                _uiState.update {
                    it.copy(
                        selectedCollection = selected,
                        fieldDefinitions = fields,
                        formFields = buildEmptyFormFields(fields),
                        formVersion = it.formVersion + 1L,
                        isBusy = false,
                        status = RecordCreateStatus.READY
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                _uiState.update {
                    it.copy(
                        isBusy = false,
                        status = RecordCreateStatus.ERROR,
                        errorMessage = error.message
                    )
                }
            }
        }
    }

    fun save(updates: Map<Long, String>, locationMeta: ActionLocationMeta? = null) {
        val state = _uiState.value
        val collection = state.selectedCollection ?: return
        val fields = state.fieldDefinitions
        viewModelScope.launch {
            try {
                _uiState.update {
                    it.copy(
                        isBusy = true,
                        status = RecordCreateStatus.SAVING,
                        savedRecordLabel = null,
                        errorMessage = null
                    )
                }
                repository.createRecord(collection.id, fields, updates, locationMeta)
                val savedLabel = buildSavedRecordLabel(fields, updates)
                _uiState.update {
                    it.copy(
                        isBusy = false,
                        status = RecordCreateStatus.SAVED,
                        savedRecordLabel = savedLabel,
                        formFields = buildEmptyFormFields(fields),
                        formVersion = it.formVersion + 1L
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                _uiState.update {
                    it.copy(
                        isBusy = false,
                        status = RecordCreateStatus.ERROR,
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
                val suggestions = repository.searchOptionSuggestions(sourceCollectionId, normalizedQuery, 20)
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
        repository.close()
        super.onCleared()
    }

    private fun buildEmptyFormFields(fields: List<FieldDefinition>): List<AssetFieldValue> {
        return fields.map { field ->
            AssetFieldValue(
                fieldId = field.id,
                fieldDisplayName = field.displayName,
                fieldSlug = field.slug,
                fieldType = field.fieldType,
                isLookupKey = field.isLookupKey,
                isUniqueValue = field.isUniqueValue,
                isRequiredValue = field.isRequiredValue,
                value = "",
                optionSourceCollectionId = field.optionSourceCollectionId
            )
        }
    }

    private fun buildSavedRecordLabel(
        fields: List<FieldDefinition>,
        updates: Map<Long, String>
    ): String? {
        val orderedCandidates = buildList {
            addAll(fields.filter { it.optionDisplayRole == "primary" })
            addAll(fields.filter { it.isLookupKey && it.optionDisplayRole != "primary" })
            addAll(fields.filter { it.isUniqueValue && !it.isLookupKey && it.optionDisplayRole != "primary" })
            addAll(fields.filter { it.isRequiredValue && !it.isLookupKey && !it.isUniqueValue && it.optionDisplayRole != "primary" })
            addAll(fields.filter { !it.isLookupKey && !it.isUniqueValue && !it.isRequiredValue && it.optionDisplayRole != "primary" })
        }
        return orderedCandidates
            .firstNotNullOfOrNull { field ->
                updates[field.id]?.trim()?.takeIf { it.isNotEmpty() }
            }
    }
}

class RecordCreateViewModelFactory(
    private val repository: RecordCreateRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(RecordCreateViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return RecordCreateViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
