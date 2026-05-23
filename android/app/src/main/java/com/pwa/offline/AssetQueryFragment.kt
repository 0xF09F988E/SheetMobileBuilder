package com.pwa.offline

import android.content.Context
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import com.pwa.offline.dialogs.FieldEditDialogHelper
import kotlinx.coroutines.launch

class AssetQueryFragment : Fragment() {

    private val viewModel: AssetQueryViewModel by viewModels {
        AssetQueryViewModelFactory(
            AssetQueryRepository(
                AppDatabaseHelper(requireContext().applicationContext)
            )
        )
    }

    private lateinit var searchInput: EditText
    private lateinit var recordMetaText: TextView
    private lateinit var reviewStateText: TextView
    private lateinit var changedFieldsText: TextView
    private lateinit var statusText: TextView
    private lateinit var searchButton: Button
    private lateinit var clearSearchButton: Button
    private lateinit var confirmButton: Button
    private lateinit var readOnlyActionsContainer: LinearLayout
    private lateinit var fieldsRecyclerView: RecyclerView
    private lateinit var rootView: View
    private lateinit var fieldAdapter: AssetFieldAdapter
    private var lastToastStatus: AssetQueryStatus? = null
    private var lastRenderedDetail: AssetRecordDetail? = null
    private var currentDetail: AssetRecordDetail? = null

    override fun onCreateView(
        inflater: android.view.LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_asset_query, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        rootView = view.findViewById(R.id.assetQueryRoot)
        searchInput = view.findViewById(R.id.assetSearchInput)
        recordMetaText = view.findViewById(R.id.recordMetaText)
        reviewStateText = view.findViewById(R.id.assetReviewStateText)
        changedFieldsText = view.findViewById(R.id.assetChangedFieldsText)
        statusText = view.findViewById(R.id.assetStatusText)
        searchButton = view.findViewById(R.id.searchAssetButton)
        clearSearchButton = view.findViewById(R.id.clearSearchButton)
        confirmButton = view.findViewById(R.id.confirmAssetButton)
        readOnlyActionsContainer = view.findViewById(R.id.readOnlyActionsContainer)
        fieldsRecyclerView = view.findViewById(R.id.assetFieldsRecyclerView)

        fieldAdapter = AssetFieldAdapter(
            emptyValueLabel = getString(R.string.asset_empty_value),
            booleanLabel = getString(R.string.asset_boolean_label),
            labelFormatter = ::buildFieldLabel,
            onFieldFocused = ::ensureFieldVisible,
            optionSuggestionsProvider = ::loadOptionSuggestions,
            onReadFieldSelected = ::showFieldEditor
        )
        fieldsRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        fieldsRecyclerView.adapter = fieldAdapter

        searchButton.setOnClickListener { submitSearch() }
        searchInput.setOnEditorActionListener { _, actionId, event ->
            val isImeSearchAction = actionId == EditorInfo.IME_ACTION_SEARCH ||
                actionId == EditorInfo.IME_ACTION_DONE
            val isHardwareEnter = actionId == EditorInfo.IME_NULL &&
                event?.keyCode == KeyEvent.KEYCODE_ENTER &&
                event.action == KeyEvent.ACTION_DOWN

            if (isImeSearchAction || isHardwareEnter) {
                submitSearch()
                true
            } else {
                false
            }
        }
        clearSearchButton.setOnClickListener {
            searchInput.text?.clear()
            viewModel.clearResult()
        }
        confirmButton.setOnClickListener {
            viewModel.markConforme()
        }

        applyKeyboardInsets()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect(::renderState)
            }
        }

        viewModel.loadInitial()
    }

    private fun renderState(state: AssetQueryUiState) {
        renderStatusToast(state.status)
        val detail = state.recordDetail
        currentDetail = detail

        if (detail == null) {
            recordMetaText.text = getString(R.string.asset_record_meta_empty)
            reviewStateText.visibility = View.GONE
            changedFieldsText.visibility = View.GONE
            fieldAdapter.clear()
            lastRenderedDetail = null
        } else {
            recordMetaText.text = buildLastActionText(detail)
            reviewStateText.text = buildReviewState(detail)
            reviewStateText.visibility = View.VISIBLE
            val changedFieldsSummary = buildChangedFieldsSummary(detail)
            changedFieldsText.text = changedFieldsSummary
            changedFieldsText.visibility =
                if (changedFieldsSummary.isBlank()) View.GONE else View.VISIBLE
            if (detail != lastRenderedDetail) {
                fieldAdapter.submitFields(detail.fields, false)
                lastRenderedDetail = detail
            }
        }

        readOnlyActionsContainer.visibility = View.VISIBLE
        confirmButton.isEnabled = detail != null && !state.isBusy
        searchButton.isEnabled = !state.isBusy
        clearSearchButton.isEnabled = !state.isBusy

        statusText.text = when (state.status) {
            AssetQueryStatus.EMPTY -> getString(R.string.asset_status_empty)
            AssetQueryStatus.LOOKUP_REQUIRED -> getString(R.string.asset_status_lookup_required)
            AssetQueryStatus.READY -> getString(R.string.asset_status_ready_search)
            AssetQueryStatus.SEARCHING -> getString(R.string.asset_status_searching)
            AssetQueryStatus.NOT_FOUND -> getString(R.string.asset_status_not_found)
            AssetQueryStatus.READ_ONLY,
            AssetQueryStatus.EDITING -> getString(R.string.asset_status_read_only)
            AssetQueryStatus.CONFIRMING -> getString(R.string.asset_status_confirming)
            AssetQueryStatus.CONFIRMED -> getString(R.string.asset_status_confirmed)
            AssetQueryStatus.SAVING -> getString(R.string.asset_status_saving)
            AssetQueryStatus.SAVED -> getString(R.string.asset_status_saved)
            AssetQueryStatus.ERROR -> state.errorMessage ?: getString(R.string.asset_status_save_error)
        }
    }

    private fun renderStatusToast(status: AssetQueryStatus) {
        if (status == lastToastStatus) return
        lastToastStatus = status
        val messageRes = when (status) {
            AssetQueryStatus.CONFIRMED -> R.string.asset_status_confirmed
            AssetQueryStatus.SAVED -> R.string.asset_status_saved
            else -> null
        } ?: return
        Snackbar.make(rootView, getString(messageRes), Snackbar.LENGTH_SHORT).show()
    }

    private fun hideKeyboard() {
        searchInput.clearFocus()
        val inputMethodManager =
            requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        inputMethodManager.hideSoftInputFromWindow(searchInput.windowToken, 0)
    }

    private fun submitSearch() {
        hideKeyboard()
        viewModel.search(searchInput.text.toString().trim())
    }

    private fun applyKeyboardInsets() {
        val baseListBottom = fieldsRecyclerView.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(rootView) { _, windowInsets ->
            val imeBottom = windowInsets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            val systemBottom = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
            val extraBottom = (imeBottom - systemBottom).coerceAtLeast(0)
            fieldsRecyclerView.updatePadding(bottom = baseListBottom + extraBottom)
            windowInsets
        }
        ViewCompat.requestApplyInsets(rootView)
    }

    private fun ensureFieldVisible(focusedView: View) {
        fieldsRecyclerView.postDelayed({
            focusedView.requestRectangleOnScreen(
                android.graphics.Rect(0, 0, focusedView.width, focusedView.height),
                true
            )
        }, 150)
    }

    private fun buildFieldLabel(field: AssetFieldValue): String = field.fieldDisplayName

    private fun showFieldEditor(field: AssetFieldValue) {
        val detail = currentDetail ?: return
        if (viewModel.uiState.value.isBusy) return
        if (detail.fields.none { it.fieldId == field.fieldId }) return
        FieldEditDialogHelper.show(
            context = requireContext(),
            inflater = layoutInflater,
            field = field,
            loadOptionSuggestions = ::loadOptionSuggestions
        ) { rawValue, dialog ->
            saveSingleField(field, rawValue, dialog)
        }
    }

    private fun saveSingleField(
        field: AssetFieldValue,
        rawValue: String,
        dialog: android.app.Dialog
    ) {
        if (viewModel.uiState.value.isBusy) return
        viewModel.save(
            mapOf(
                field.fieldId to FieldValueTextNormalizer.normalizeForSave(field.fieldType, rawValue)
            )
        )
        dialog.dismiss()
    }

    private fun buildReviewState(detail: AssetRecordDetail): String {
        return resolveReviewStatusLabel(detail.reviewStatus)
    }

    private fun buildChangedFieldsSummary(detail: AssetRecordDetail): String {
        if (detail.changedFieldsText.isBlank()) return ""
        return getString(R.string.asset_review_changed_fields, detail.changedFieldsText)
    }

    private fun buildLastActionText(detail: AssetRecordDetail): String {
        val actionLabel = when (detail.reviewAction) {
            ReviewActionCodes.CREATED_MANUAL -> getString(R.string.asset_last_action_created)
            ReviewActionCodes.EDIT_SAVED -> getString(R.string.asset_last_action_edited)
            ReviewActionCodes.CONFIRMED_MANUAL -> getString(R.string.asset_last_action_confirmed)
            ReviewActionCodes.IMPORTED -> getString(R.string.asset_last_action_imported)
            else -> getString(R.string.asset_last_action_unknown)
        }
        val rawTimestamp = when (detail.reviewAction) {
            ReviewActionCodes.CONFIRMED_MANUAL -> detail.reviewedAt
            ReviewActionCodes.CREATED_MANUAL,
            ReviewActionCodes.IMPORTED -> detail.createdAt
            else -> detail.updatedAt
        }
        val formattedTimestamp = TimestampFormatters.sqliteUtcToDeviceMx(rawTimestamp)
        return if (formattedTimestamp.isBlank()) {
            getString(R.string.asset_last_action_value, actionLabel)
        } else {
            getString(R.string.asset_last_action_value_with_time, actionLabel, formattedTimestamp)
        }
    }

    private fun resolveReviewStatusLabel(statusCode: String): String {
        return when (statusCode) {
            ReviewStatusCodes.PENDING -> getString(R.string.asset_review_status_pending)
            ReviewStatusCodes.CONFIRMED -> getString(R.string.asset_review_status_confirmed)
            ReviewStatusCodes.UPDATED -> getString(R.string.asset_review_status_updated)
            else -> statusCode.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        }
    }

    private fun loadOptionSuggestions(
        field: AssetFieldValue,
        query: String,
        onResult: (List<OptionSuggestion>) -> Unit
    ) {
        val sourceCollectionId = field.optionSourceCollectionId ?: run {
            onResult(emptyList())
            return
        }
        viewModel.requestOptionSuggestions(sourceCollectionId, query, onResult)
    }
}
