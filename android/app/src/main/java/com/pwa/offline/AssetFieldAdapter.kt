package com.pwa.offline

import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import android.text.Editable
import android.text.TextWatcher
import com.pwa.offline.dialogs.FieldEditDialogHelper

class AssetFieldAdapter(
    private val emptyValueLabel: String,
    private val booleanLabel: String,
    private val labelFormatter: (AssetFieldValue) -> String,
    private val onFieldFocused: (View) -> Unit,
    private val optionSuggestionsProvider: (AssetFieldValue, String, (List<OptionSuggestion>) -> Unit) -> Unit,
    private val onReadFieldSelected: ((AssetFieldValue) -> Unit)? = null,
    private val compactEditing: Boolean = false,
    private val onListFieldEditRequested: ((AssetFieldValue, String, (String) -> Unit) -> Unit)? = null
) : RecyclerView.Adapter<AssetFieldAdapter.AssetFieldViewHolder>() {

    companion object {
        private const val LIST_QUERY_MIN_LENGTH = 2
        private val LIST_INPUT_WATCHER_TAG_KEY = R.id.assetFieldListInputView
    }

    private var fields: List<AssetFieldValue> = emptyList()
    private var isEditing: Boolean = false

    private val textInputs = linkedMapOf<Long, TextInputEditText>()
    private val listInputs = linkedMapOf<Long, AutoCompleteTextView>()
    private val switchInputs = linkedMapOf<Long, SwitchCompat>()
    private val booleanTouched = linkedMapOf<Long, Boolean>()
    private val listSelectionInProgress = linkedMapOf<Long, Boolean>()
    private val listWatcherMuted = linkedMapOf<Long, Boolean>()
    private val listSelectionLocked = linkedMapOf<Long, Boolean>()

    init {
        setHasStableIds(true)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AssetFieldViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_asset_field, parent, false)
        return AssetFieldViewHolder(view)
    }

    override fun getItemId(position: Int): Long = fields[position].fieldId

    override fun onBindViewHolder(holder: AssetFieldViewHolder, position: Int) {
        val field = fields[position]
        holder.bind(field, isEditing)
    }

    override fun getItemCount(): Int = fields.size

    fun submitFields(fields: List<AssetFieldValue>, isEditing: Boolean, forceRefresh: Boolean = false) {
        if (!forceRefresh && this.fields == fields && this.isEditing == isEditing) {
            return
        }
        val previousFields = this.fields
        val previousEditing = this.isEditing
        textInputs.clear()
        listInputs.clear()
        switchInputs.clear()
        booleanTouched.clear()
        listSelectionInProgress.clear()
        listWatcherMuted.clear()
        listSelectionLocked.clear()
        this.fields = fields
        this.isEditing = isEditing
        if (forceRefresh || previousEditing != isEditing) {
            notifyDataSetChanged()
            return
        }
        DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize(): Int = previousFields.size

            override fun getNewListSize(): Int = fields.size

            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                return previousFields[oldItemPosition].fieldId == fields[newItemPosition].fieldId
            }

            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                return previousFields[oldItemPosition] == fields[newItemPosition]
            }
        }).dispatchUpdatesTo(this)
    }

    fun clear() {
        submitFields(emptyList(), false, forceRefresh = true)
    }

    fun buildUpdates(): Map<Long, String> {
        return buildMap {
            fields.forEach { field ->
                when (field.fieldType) {
                    "boolean" -> {
                        val view = switchInputs[field.fieldId] ?: return@forEach
                        val hasInitialValue = field.value.isNotBlank()
                        val touched = booleanTouched[field.fieldId] == true
                        put(
                            field.fieldId,
                            if (!hasInitialValue && !touched) "" else if (view.isChecked) "Si" else "No"
                        )
                    }
                    "list" -> {
                        val view = listInputs[field.fieldId] ?: return@forEach
                        put(field.fieldId, view.text?.toString().orEmpty())
                    }
                    else -> {
                        val view = textInputs[field.fieldId] ?: return@forEach
                        put(field.fieldId, view.text?.toString().orEmpty())
                    }
                }
            }
        }
    }

    fun buildChangedUpdates(): Map<Long, String> {
        return buildMap {
            fields.forEach { field ->
                when (field.fieldType) {
                    "boolean" -> {
                        val view = switchInputs[field.fieldId] ?: return@forEach
                        val hasInitialValue = field.value.isNotBlank()
                        val touched = booleanTouched[field.fieldId] == true
                        val currentValue =
                            if (!hasInitialValue && !touched) "" else if (view.isChecked) "Si" else "No"
                        if (currentValue.trim() != field.value.trim()) {
                            put(field.fieldId, currentValue)
                        }
                    }
                    "list" -> {
                        val view = listInputs[field.fieldId] ?: return@forEach
                        val currentValue = view.text?.toString().orEmpty()
                        if (currentValue.trim() != field.value.trim()) {
                            put(field.fieldId, currentValue)
                        }
                    }
                    else -> {
                        val view = textInputs[field.fieldId] ?: return@forEach
                        val currentValue = view.text?.toString().orEmpty()
                        if (currentValue.trim() != field.value.trim()) {
                            put(field.fieldId, currentValue)
                        }
                    }
                }
            }
        }
    }

    inner class AssetFieldViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val cardContainer = itemView.findViewById<LinearLayout>(R.id.assetFieldCardContainer)
        private val labelText = itemView.findViewById<TextView>(R.id.assetFieldLabelText)
        private val metaText = itemView.findViewById<TextView>(R.id.assetFieldMetaText)
        private val readValueText = itemView.findViewById<TextView>(R.id.assetFieldReadValueText)
        private val inputLayout = itemView.findViewById<TextInputLayout>(R.id.assetFieldInputLayout)
        private val inputEditText = itemView.findViewById<TextInputEditText>(R.id.assetFieldInputEditText)
        private val listInputLayout = itemView.findViewById<TextInputLayout>(R.id.assetFieldListInputLayout)
        private val listInputView = itemView.findViewById<AutoCompleteTextView>(R.id.assetFieldListInputView)
        private val booleanSwitch = itemView.findViewById<SwitchCompat>(R.id.assetFieldBooleanSwitch)

        fun bind(field: AssetFieldValue, isEditing: Boolean) {
            labelText.text = labelFormatter(field)
            val compactMode = isEditing && compactEditing
            val verticalPadding = if (compactMode) 10 else 12
            val outerBottomPadding = if (compactMode) 8 else 12
            cardContainer.setBackgroundResource(
                if (compactMode) R.drawable.app_surface_compact_field_background
                else R.drawable.app_surface_card_background
            )
            cardContainer.setPadding(
                verticalPadding.dp(itemView),
                verticalPadding.dp(itemView),
                verticalPadding.dp(itemView),
                verticalPadding.dp(itemView)
            )
            itemView.setPadding(itemView.paddingLeft, itemView.paddingTop, itemView.paddingRight, outerBottomPadding.dp(itemView))
            val metaValue = if (compactMode) buildCompactMeta(field) else ""
            metaText.isVisible = metaValue.isNotEmpty()
            metaText.text = metaValue

            if (!isEditing) {
                cardContainer.isClickable = onReadFieldSelected != null
                cardContainer.isFocusable = onReadFieldSelected != null
                cardContainer.setOnClickListener {
                    onReadFieldSelected?.invoke(field)
                }
                readValueText.isVisible = true
                inputLayout.isVisible = false
                listInputLayout.isVisible = false
                booleanSwitch.isVisible = false
                readValueText.text = field.value.ifBlank { emptyValueLabel }
                return
            }

            cardContainer.setOnClickListener(null)
            cardContainer.isClickable = false
            cardContainer.isFocusable = false

            when (field.fieldType) {
                "boolean" -> {
                    readValueText.isVisible = false
                    inputLayout.isVisible = false
                    listInputLayout.isVisible = false
                    booleanSwitch.isVisible = true
                    booleanSwitch.text = booleanLabel
                    booleanSwitch.setOnCheckedChangeListener(null)
                    booleanSwitch.isChecked = field.value.equals("Si", ignoreCase = true) ||
                        field.value.equals("1", ignoreCase = true) ||
                        field.value.equals("true", ignoreCase = true)
                    booleanTouched[field.fieldId] = false
                    booleanSwitch.setOnCheckedChangeListener { _, _ ->
                        booleanTouched[field.fieldId] = true
                    }
                    switchInputs[field.fieldId] = booleanSwitch
                }

                "list" -> {
                    readValueText.isVisible = false
                    inputLayout.isVisible = false
                    listInputLayout.isVisible = true
                    booleanSwitch.isVisible = false
                    listInputLayout.hint = if (onListFieldEditRequested != null) {
                        itemView.context.getString(R.string.record_create_list_field_hint)
                    } else {
                        itemView.context.getString(R.string.asset_list_input_hint)
                    }
                    listInputView.threshold = 0
                    listInputView.setText(field.value, false)
                    if (onListFieldEditRequested != null) {
                        val launchEditor = {
                            onListFieldEditRequested.invoke(
                                field,
                                listInputView.text?.toString().orEmpty()
                            ) { selectedValue ->
                                listInputView.setText(selectedValue, false)
                                listInputView.setSelection(listInputView.text?.length ?: 0)
                                moveToNextField(listInputView)
                            }
                        }
                        cardContainer.setOnClickListener { launchEditor.invoke() }
                        cardContainer.isClickable = true
                        cardContainer.isFocusable = true
                        listInputView.isFocusable = false
                        listInputView.isFocusableInTouchMode = false
                        listInputView.isClickable = true
                        listInputView.isLongClickable = false
                        listInputView.keyListener = null
                        listInputView.setOnClickListener { launchEditor.invoke() }
                        listInputLayout.setEndIconOnClickListener { launchEditor.invoke() }
                        (listInputView.getTag(LIST_INPUT_WATCHER_TAG_KEY) as? TextWatcher)?.let {
                            listInputView.removeTextChangedListener(it)
                        }
                        listInputView.onItemClickListener = null
                        listInputView.setAdapter(null)
                    } else {
                        cardContainer.setOnClickListener(null)
                        cardContainer.isClickable = false
                        cardContainer.isFocusable = false
                        listInputView.isFocusable = true
                        listInputView.isFocusableInTouchMode = true
                        listInputView.isLongClickable = true
                        listInputView.setOnFocusChangeListener { focusedView, hasFocus ->
                            if (hasFocus) {
                                onFieldFocused(focusedView)
                                loadDefaultListSuggestions(field)
                            }
                        }
                        bindNextFieldNavigation(listInputView)
                        listInputView.setOnClickListener {
                            loadDefaultListSuggestions(field)
                        }
                        listInputLayout.setEndIconOnClickListener {
                            listSelectionLocked[field.fieldId] = false
                            listInputView.requestFocus()
                            loadDefaultListSuggestions(field)
                        }
                        listInputView.onItemClickListener =
                            AdapterView.OnItemClickListener { parent, _, position, _ ->
                            val suggestion = parent.getItemAtPosition(position) as? OptionSuggestion
                            listWatcherMuted[field.fieldId] = true
                            listSelectionLocked[field.fieldId] = true
                            if (suggestion != null) {
                                listInputView.setText(suggestion.selectedValue, false)
                                listInputView.setSelection(listInputView.text?.length ?: 0)
                            }
                            listInputView.post {
                                listInputView.dismissDropDown()
                                listWatcherMuted[field.fieldId] = false
                            }
                        }
                        bindListSuggestions(field)
                        preloadListSuggestions(field)
                    }
                    listInputs[field.fieldId] = listInputView
                }

                else -> {
                    readValueText.isVisible = false
                    inputLayout.isVisible = true
                    listInputLayout.isVisible = false
                    booleanSwitch.isVisible = false
                    inputEditText.setText(field.value)
                    inputEditText.inputType = when (field.fieldType) {
                        "number" -> android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
                        "date" -> android.text.InputType.TYPE_CLASS_DATETIME
                        "textarea" -> android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
                        else -> android.text.InputType.TYPE_CLASS_TEXT
                    }
                    inputEditText.minLines = if (field.fieldType == "textarea") 3 else 1
                    inputEditText.maxLines = if (field.fieldType == "textarea") 4 else 1
                    inputEditText.imeOptions = if (bindingAdapterPosition == itemCount - 1) {
                        EditorInfo.IME_ACTION_DONE
                    } else {
                        EditorInfo.IME_ACTION_NEXT
                    }
                    inputEditText.setOnFocusChangeListener { focusedView, hasFocus ->
                        if (hasFocus) onFieldFocused(focusedView)
                    }
                    if (field.fieldType != "textarea") {
                        bindNextFieldNavigation(inputEditText)
                    } else {
                        inputEditText.setOnEditorActionListener(null)
                        inputEditText.setOnKeyListener(null)
                    }
                    textInputs[field.fieldId] = inputEditText
                }
            }
        }

        private fun buildCompactMeta(field: AssetFieldValue): String {
            val context = itemView.context
            val parts = mutableListOf<String>()
            parts += when (field.fieldType) {
                "textarea" -> context.getString(R.string.schema_field_type_textarea)
                "number" -> context.getString(R.string.schema_field_type_number)
                "date" -> context.getString(R.string.schema_field_type_date)
                "boolean" -> context.getString(R.string.schema_field_type_boolean)
                "list" -> context.getString(R.string.schema_field_type_list)
                else -> context.getString(R.string.schema_field_type_text)
            }
            if (field.fieldType == "list" && onListFieldEditRequested != null) {
                parts += context.getString(R.string.asset_field_meta_list_selector)
            }
            if (field.isRequiredValue) {
                parts += context.getString(R.string.asset_field_meta_required)
            }
            if (field.isUniqueValue) {
                parts += context.getString(R.string.asset_field_meta_unique)
            }
            if (field.isLookupKey) {
                parts += context.getString(R.string.asset_field_meta_lookup)
            }
            return parts.joinToString(" · ")
        }

        private fun Int.dp(view: View): Int {
            return (this * view.resources.displayMetrics.density).toInt()
        }

        private fun bindNextFieldNavigation(targetView: TextView) {
            targetView.setOnEditorActionListener { _, actionId, event ->
                if (targetView is AutoCompleteTextView && targetView.isPopupShowing) {
                    return@setOnEditorActionListener false
                }
                val isImeNextAction = actionId == EditorInfo.IME_ACTION_NEXT ||
                    actionId == EditorInfo.IME_ACTION_DONE
                val isHardwareEnter = actionId == EditorInfo.IME_NULL &&
                    event?.keyCode == KeyEvent.KEYCODE_ENTER &&
                    event.action == KeyEvent.ACTION_DOWN

                if (isImeNextAction || isHardwareEnter) {
                    moveToNextField(targetView)
                    true
                } else {
                    false
                }
            }
            targetView.setOnKeyListener { _, keyCode, event ->
                if (targetView is AutoCompleteTextView && targetView.isPopupShowing) {
                    return@setOnKeyListener false
                }
                if (keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN) {
                    moveToNextField(targetView)
                    true
                } else {
                    false
                }
            }
        }

        private fun moveToNextField(currentView: View) {
            currentView.post {
                val nextView = currentView.focusSearch(View.FOCUS_DOWN)
                if (nextView != null) {
                    nextView.requestFocus()
                    onFieldFocused(nextView)
                } else {
                    currentView.clearFocus()
                }
            }
        }

        private fun bindListSuggestions(field: AssetFieldValue) {
            val sourceCollectionId = field.optionSourceCollectionId
            if (sourceCollectionId == null) {
                listInputView.setAdapter(null)
                return
            }

            val watcher = object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit

                override fun afterTextChanged(editable: Editable?) {
                    if (listWatcherMuted[field.fieldId] == true) {
                        return
                    }
                    listSelectionLocked[field.fieldId] = false
                    val currentQuery = editable?.toString().orEmpty()
                    if (currentQuery.trim().isNotEmpty() && currentQuery.trim().length < LIST_QUERY_MIN_LENGTH) {
                        listInputView.dismissDropDown()
                        return
                    }
                    requestAndRenderSuggestions(field, currentQuery, requireMatch = true)
                }
            }

            (listInputView.getTag(LIST_INPUT_WATCHER_TAG_KEY) as? TextWatcher)?.let {
                listInputView.removeTextChangedListener(it)
            }
            listInputView.addTextChangedListener(watcher)
            listInputView.setTag(LIST_INPUT_WATCHER_TAG_KEY, watcher)
        }

        private fun loadDefaultListSuggestions(field: AssetFieldValue) {
            requestAndRenderSuggestions(field, "", requireMatch = false)
        }

        private fun preloadListSuggestions(field: AssetFieldValue) {
            optionSuggestionsProvider(field, "") { suggestions ->
                val adapter = ArrayAdapter(
                    itemView.context,
                    android.R.layout.simple_dropdown_item_1line,
                    suggestions
                )
                listInputView.setAdapter(adapter)
            }
        }

        private fun requestAndRenderSuggestions(
            field: AssetFieldValue,
            query: String,
            requireMatch: Boolean
        ) {
            optionSuggestionsProvider(field, query) { suggestions ->
                if (listSelectionLocked[field.fieldId] == true) return@optionSuggestionsProvider
                if (requireMatch && listInputView.text?.toString().orEmpty() != query) return@optionSuggestionsProvider
                val adapter = ArrayAdapter(
                    itemView.context,
                    android.R.layout.simple_dropdown_item_1line,
                    suggestions
                )
                listInputView.setAdapter(adapter)
                if (suggestions.isNotEmpty() && listInputView.hasFocus()) {
                    listInputView.post { listInputView.showDropDown() }
                } else if (suggestions.isEmpty()) {
                    listInputView.dismissDropDown()
                }
            }
        }
    }
}
