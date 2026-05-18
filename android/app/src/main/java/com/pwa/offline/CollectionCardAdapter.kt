package com.pwa.offline

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class CollectionCardAdapter(
    private val onDelete: (CollectionCard) -> Unit,
    private val onSetMaster: (CollectionCard) -> Unit,
    private val descriptionProvider: (CollectionCard) -> String,
    private val issueProvider: (CollectionCard) -> String?,
    private val masterActionProvider: (CollectionCard) -> CollectionActionUi
) : RecyclerView.Adapter<CollectionCardAdapter.CollectionCardViewHolder>() {

    private val items = mutableListOf<CollectionCard>()

    fun submitList(cards: List<CollectionCard>) {
        items.clear()
        items.addAll(cards)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CollectionCardViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_schema_collection_card, parent, false)
        return CollectionCardViewHolder(view)
    }

    override fun onBindViewHolder(holder: CollectionCardViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class CollectionCardViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val nameText: TextView = itemView.findViewById(R.id.collectionCardNameText)
        private val descriptionText: TextView = itemView.findViewById(R.id.collectionCardDescriptionText)
        private val metaText: TextView = itemView.findViewById(R.id.collectionCardMetaText)
        private val healthText: TextView = itemView.findViewById(R.id.collectionCardHealthText)
        private val kindBadgeText: TextView = itemView.findViewById(R.id.collectionCardKindBadgeText)
        private val masterActionText: TextView = itemView.findViewById(R.id.collectionCardMasterActionText)
        private val deleteButton: ImageButton = itemView.findViewById(R.id.collectionDeleteButton)

        fun bind(card: CollectionCard) {
            nameText.text = card.displayName
            descriptionText.text = descriptionProvider(card)
            metaText.text = itemView.context.getString(R.string.schema_table_meta, card.fieldCount)

            val issueText = issueProvider(card)
            if (issueText.isNullOrBlank()) {
                healthText.visibility = View.GONE
            } else {
                healthText.visibility = View.VISIBLE
                healthText.text = issueText
            }

            when {
                card.isMaster -> {
                    kindBadgeText.visibility = View.VISIBLE
                    kindBadgeText.text = itemView.context.getString(R.string.schema_master_badge)
                }
                card.isOptions -> {
                    kindBadgeText.visibility = View.VISIBLE
                    kindBadgeText.text = itemView.context.getString(R.string.schema_options_badge)
                }
                else -> kindBadgeText.visibility = View.GONE
            }

            val actionUi = masterActionProvider(card)
            masterActionText.text = actionUi.label
            masterActionText.alpha = if (actionUi.enabled) 1f else 0.7f
            masterActionText.setOnClickListener(
                if (actionUi.enabled) {
                    View.OnClickListener { onSetMaster(card) }
                } else {
                    null
                }
            )

            deleteButton.setOnClickListener { onDelete(card) }
        }
    }
}

data class CollectionActionUi(
    val label: String,
    val enabled: Boolean
)
