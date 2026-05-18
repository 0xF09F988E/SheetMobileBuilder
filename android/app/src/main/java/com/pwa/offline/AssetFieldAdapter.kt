package com.pwa.offline

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import android.text.Editable
import android.text.TextWatcher

class AssetFieldAdapter(
    private val emptyValueLabel: String,
    private val booleanLabel: String,
    private val labelFormatter: (AssetFieldValue) -> String,
    private val onFieldFocused: (View) -> Unit,
    private val optionSuggestionsProvider: (AssetFieldValue, String, (List<OptionSuggestion>) -> Unit) -> Unit
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

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AssetFieldViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_asset_field, parent, false)
        return AssetFieldViewHolder(view)
    }

    override fun onBindViewHolder(holder: AssetFieldViewHolder, position: Int) {
        val field = fields[position]
        holder.bind(field, isEditing)
    }

    override fun getItemCount(): Int = fields.size

    fun submitFields(fields: List<AssetFieldValue>, isEditing: Boolean) {
        textInputs.clear()
        listInputs.clear()
        switchInputs.clear()
        booleanTouched.clear()
        listSelectionInProgress.clear()
        this.fields = fields
        this.isEditing = isEditing
        notifyDataSetChanged()
    }

    fun clear() {
        submitFields(emptyList(), false)
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

    inner class AssetFieldViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val labelText = itemView.findViewById<TextView>(R.id.assetFieldLabelText)
        private val readValueText = itemView.findViewById<TextView>(R.id.assetFieldReadValueText)
        private val inputLayout = itemView.findViewById<TextInputLayout>(R.id.assetFieldInputLayout)
        private val inputEditText = itemView.findViewById<TextInputEditText>(R.id.assetFieldInputEditText)
        private val listInputLayout = itemView.findViewById<TextInputLayout>(R.id.assetFieldListInputLayout)
        private val listInputView = itemView.findViewById<AutoCompleteTextView>(R.id.assetFieldListInputView)
        private val booleanSwitch = itemView.findViewById<SwitchCompat>(R.id.assetFieldBooleanSwitch)

        fun bind(field: AssetFieldValue, isEditing: Boolean) {
            labelText.text = labelFormatter(field)

            if (!isEditing) {
                readValueText.isVisible = true
                inputLayout.isVisible = false
                listInputLayout.isVisible = false
                booleanSwitch.isVisible = false
                readValueText.text = field.value.ifBlank { emptyValueLabel }
                return
            }

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
                    listInputView.threshold = 0
                    listInputView.setText(field.value, false)
                    listInputView.setOnFocusChangeListener { focusedView, hasFocus ->
                        if (hasFocus) {
                            onFieldFocused(focusedView)
                            loadDefaultListSuggestions(field)
                        }
                    }
                    listInputView.setOnClickListener {
                        loadDefaultListSuggestions(field)
                    }
                    listInputLayout.setEndIconOnClickListener {
                        listInputView.requestFocus()
                        loadDefaultListSuggestions(field)
                    }
                    listInputView.setOnItemClickListener { _, _, _, _ ->
                        listSelectionInProgress[field.fieldId] = true
                        listInputView.dismissDropDown()
                    }
                    bindListSuggestions(field)
                    preloadListSuggestions(field)
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
                    inputEditText.setOnFocusChangeListener { focusedView, hasFocus ->
                        if (hasFocus) onFieldFocused(focusedView)
                    }
                    textInputs[field.fieldId] = inputEditText
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
                    if (listSelectionInProgress[field.fieldId] == true) {
                        listSelectionInProgress[field.fieldId] = false
                        return
                    }
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
                val labels = suggestions.map { it.displayLabel }
                val adapter = ArrayAdapter(
                    itemView.context,
                    android.R.layout.simple_dropdown_item_1line,
                    labels
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
                if (requireMatch && listInputView.text?.toString().orEmpty() != query) return@optionSuggestionsProvider
                val labels = suggestions.map { it.displayLabel }
                val adapter = ArrayAdapter(
                    itemView.context,
                    android.R.layout.simple_dropdown_item_1line,
                    labels
                )
                listInputView.setAdapter(adapter)
                if (labels.isNotEmpty() && listInputView.hasFocus()) {
                    listInputView.post { listInputView.showDropDown() }
                } else if (labels.isEmpty()) {
                    listInputView.dismissDropDown()
                }
            }
        }
    }
}
