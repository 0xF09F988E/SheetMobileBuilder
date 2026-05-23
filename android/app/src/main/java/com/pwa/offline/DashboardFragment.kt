package com.pwa.offline

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import kotlinx.coroutines.launch

class DashboardFragment : Fragment() {

    private val viewModel: DashboardViewModel by viewModels {
        DashboardViewModelFactory(
            DashboardRepository(
                AppDatabaseHelper(requireContext().applicationContext)
            )
        )
    }

    private lateinit var collectionSelector: MaterialAutoCompleteTextView
    private lateinit var fieldSelector: MaterialAutoCompleteTextView
    private lateinit var reviewStatusSelector: MaterialAutoCompleteTextView
    private lateinit var generateButton: Button
    private lateinit var reviewCardsContainer: LinearLayout
    private lateinit var groupedCardsContainer: LinearLayout
    private lateinit var reviewCountText: TextView
    private lateinit var groupedCountText: TextView
    private lateinit var reviewEmptyText: TextView
    private lateinit var groupedEmptyText: TextView
    private lateinit var statusText: TextView

    private var collectionOptions: List<CollectionOption> = emptyList()
    private var fieldOptions: List<FieldDefinition> = emptyList()
    private lateinit var reviewStatusOptions: List<DashboardStatusFilterOption>

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_dashboard, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        collectionSelector = view.findViewById(R.id.dashboardCollectionSelector)
        fieldSelector = view.findViewById(R.id.dashboardFieldSelector)
        reviewStatusSelector = view.findViewById(R.id.dashboardReviewStatusSelector)
        generateButton = view.findViewById(R.id.dashboardGenerateButton)
        reviewCardsContainer = view.findViewById(R.id.dashboardReviewCardsContainer)
        groupedCardsContainer = view.findViewById(R.id.dashboardGroupedCardsContainer)
        reviewCountText = view.findViewById(R.id.dashboardReviewCountText)
        groupedCountText = view.findViewById(R.id.dashboardGroupedCountText)
        reviewEmptyText = view.findViewById(R.id.dashboardReviewEmptyText)
        groupedEmptyText = view.findViewById(R.id.dashboardGroupedEmptyText)
        statusText = view.findViewById(R.id.dashboardStatusText)

        reviewStatusOptions = listOf(
            DashboardStatusFilterOption(null, getString(R.string.dashboard_filter_all)),
            DashboardStatusFilterOption(ReviewStatusCodes.PENDING, getString(R.string.asset_review_status_pending)),
            DashboardStatusFilterOption(ReviewStatusCodes.CONFIRMED, getString(R.string.asset_review_status_confirmed)),
            DashboardStatusFilterOption(ReviewStatusCodes.UPDATED, getString(R.string.asset_review_status_updated))
        )

        configureSelector(collectionSelector)
        configureSelector(fieldSelector)
        configureSelector(reviewStatusSelector)

        collectionSelector.setOnItemClickListener { _, _, position, _ ->
            viewModel.selectCollection(collectionOptions.getOrNull(position))
        }
        fieldSelector.setOnItemClickListener { _, _, position, _ ->
            viewModel.selectField(fieldOptions.getOrNull(position))
        }
        reviewStatusSelector.setOnItemClickListener { _, _, position, _ ->
            viewModel.selectReviewStatus(reviewStatusOptions.getOrNull(position)?.code)
        }
        generateButton.setOnClickListener {
            viewModel.generate()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect(::renderState)
            }
        }

        viewModel.loadInitial()
    }

    private fun renderState(state: DashboardUiState) {
        collectionOptions = state.collections
        fieldOptions = state.fields

        collectionSelector.setAdapter(
            ArrayAdapter(
                requireContext(),
                android.R.layout.simple_dropdown_item_1line,
                collectionOptions.map { it.displayName }
            )
        )
        collectionSelector.setText(state.selectedCollection?.displayName.orEmpty(), false)

        fieldSelector.setAdapter(
            ArrayAdapter(
                requireContext(),
                android.R.layout.simple_dropdown_item_1line,
                fieldOptions.map { it.displayName }
            )
        )
        fieldSelector.setText(state.selectedField?.displayName.orEmpty(), false)

        reviewStatusSelector.setAdapter(
            ArrayAdapter(
                requireContext(),
                android.R.layout.simple_dropdown_item_1line,
                reviewStatusOptions.map { it.label }
            )
        )
        reviewStatusSelector.setText(
            reviewStatusOptions.firstOrNull { it.code == state.selectedReviewStatus }?.label.orEmpty(),
            false
        )

        val reviewCards = if (state.hasGenerated) {
            listOf(
                DashboardCardUi(getString(R.string.dashboard_total_card), state.reviewSummary.totalCount.toString()),
                DashboardCardUi(getString(R.string.asset_review_status_pending), state.reviewSummary.pendingCount.toString()),
                DashboardCardUi(getString(R.string.asset_review_status_confirmed), state.reviewSummary.confirmedCount.toString()),
                DashboardCardUi(getString(R.string.asset_review_status_updated), state.reviewSummary.updatedCount.toString())
            )
        } else {
            emptyList()
        }
        val groupedCards = if (state.hasGenerated) {
            state.groupedCards.map {
                DashboardCardUi(
                    label = it.valueLabel,
                    value = it.recordCount.toString(),
                    subtitle = getString(R.string.dashboard_group_card_subtitle)
                )
            }
        } else {
            emptyList()
        }

        renderSummaryCards(reviewCardsContainer, reviewCards)
        reviewCardsContainer.visibility = if (reviewCards.isEmpty()) View.GONE else View.VISIBLE
        reviewEmptyText.visibility = if (reviewCards.isEmpty()) View.VISIBLE else View.GONE
        reviewCountText.text = if (state.hasGenerated) {
            getString(R.string.dashboard_review_count_value, reviewCards.size)
        } else {
            getString(R.string.dashboard_waiting_generate)
        }
        reviewEmptyText.text = if (state.hasGenerated) {
            getString(R.string.dashboard_empty)
        } else {
            getString(R.string.dashboard_waiting_generate)
        }

        renderSummaryCards(groupedCardsContainer, groupedCards)
        groupedCardsContainer.visibility = if (groupedCards.isEmpty()) View.GONE else View.VISIBLE
        groupedEmptyText.visibility = if (groupedCards.isEmpty()) View.VISIBLE else View.GONE
        groupedCountText.text = if (state.hasGenerated) {
            getString(R.string.dashboard_group_count_value, groupedCards.size)
        } else {
            getString(R.string.dashboard_waiting_generate)
        }
        groupedEmptyText.text = if (state.hasGenerated) {
            getString(R.string.dashboard_group_empty)
        } else {
            getString(R.string.dashboard_waiting_generate)
        }

        generateButton.isEnabled = state.canGenerate

        statusText.text = when {
            state.errorMessage != null -> state.errorMessage
            state.isLoading -> getString(R.string.dashboard_status_loading)
            state.collections.isEmpty() -> getString(R.string.dashboard_status_no_tables)
            state.selectedCollection == null -> getString(R.string.dashboard_status_select_table)
            state.selectedField == null -> getString(R.string.dashboard_status_no_fields)
            !state.hasGenerated -> getString(
                R.string.dashboard_status_waiting_generate,
                state.selectedCollection.displayName,
                state.selectedField.displayName
            )
            else -> getString(
                R.string.dashboard_status_ready,
                state.selectedCollection.displayName,
                state.selectedField.displayName
            )
        }
    }

    private fun renderSummaryCards(
        container: LinearLayout,
        cards: List<DashboardCardUi>
    ) {
        container.removeAllViews()
        cards.chunked(2).forEach { rowCards ->
            val row = LinearLayout(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                orientation = LinearLayout.HORIZONTAL
            }

            rowCards.forEach { card ->
                val cardView = layoutInflater.inflate(R.layout.item_dashboard_metric_card, row, false)
                cardView.layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                ).apply {
                    marginStart = 6.dp
                    marginEnd = 6.dp
                    topMargin = 6.dp
                    bottomMargin = 6.dp
                }
                cardView.findViewById<TextView>(R.id.dashboardCardLabelText).text = card.label
                cardView.findViewById<TextView>(R.id.dashboardCardValueText).text = card.value
                val subtitleText = cardView.findViewById<TextView>(R.id.dashboardCardSubtitleText)
                if (card.subtitle.isBlank()) {
                    subtitleText.visibility = View.GONE
                } else {
                    subtitleText.text = card.subtitle
                    subtitleText.visibility = View.VISIBLE
                }
                row.addView(cardView)
            }

            if (rowCards.size == 1) {
                row.addView(View(requireContext()).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        0,
                        0,
                        1f
                    )
                })
            }

            container.addView(row)
        }
    }

    private val Int.dp: Int
        get() = (this * resources.displayMetrics.density).toInt()

    private fun configureSelector(selector: MaterialAutoCompleteTextView) {
        selector.threshold = 0
        selector.setOnClickListener {
            selector.showDropDown()
        }
        selector.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                selector.showDropDown()
            }
        }
    }
}

data class DashboardStatusFilterOption(
    val code: String?,
    val label: String
)

data class DashboardCardUi(
    val label: String,
    val value: String,
    val subtitle: String = ""
)
