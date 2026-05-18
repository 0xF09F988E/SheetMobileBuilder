package com.pwa.offline

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HomeFragment : Fragment() {

    private lateinit var databaseHelper: AppDatabaseHelper

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val totalTablesText = view.findViewById<TextView>(R.id.totalTablesValueText)
        val totalRecordsText = view.findViewById<TextView>(R.id.totalRecordsValueText)
        val dashboardCardsContainer = view.findViewById<LinearLayout>(R.id.dashboardCardsContainer)
        val dashboardStatusText = view.findViewById<TextView>(R.id.homeStatusText)

        databaseHelper = AppDatabaseHelper(requireContext())

        dashboardStatusText.text = getString(R.string.home_status_loading)
        viewLifecycleOwner.lifecycleScope.launch {
            val summary = withContext(Dispatchers.IO) {
                databaseHelper.fetchDashboardSummary()
            }
            totalTablesText.text = summary.totalTables.toString()
            totalRecordsText.text = summary.totalRecords.toString()
            renderDashboardCards(dashboardCardsContainer, summary.tableCards)
            dashboardStatusText.text = getString(R.string.home_status_ready, summary.tableCards.size)
        }
    }

    override fun onDestroy() {
        databaseHelper.close()
        super.onDestroy()
    }

    private fun renderDashboardCards(
        container: LinearLayout,
        cards: List<TableDashboardCard>
    ) {
        container.removeAllViews()

        if (cards.isEmpty()) {
            val emptyText = TextView(requireContext()).apply {
                text = getString(R.string.home_dashboard_empty)
                setTextColor(resources.getColor(R.color.muted_ink, null))
                textSize = 14f
            }
            container.addView(emptyText)
            return
        }

        cards.forEach { card ->
            val cardView = layoutInflater.inflate(R.layout.item_dashboard_table_card, container, false)
            cardView.findViewById<TextView>(R.id.dashboardTableNameText).text = card.displayName
            cardView.findViewById<TextView>(R.id.dashboardTableSlugText).text = card.slug
            cardView.findViewById<TextView>(R.id.dashboardTableCountText).text =
                getString(R.string.home_dashboard_card_count, card.recordCount)
            container.addView(cardView)
        }
    }
}
