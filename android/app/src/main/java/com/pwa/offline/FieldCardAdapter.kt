package com.pwa.offline

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class FieldCardAdapter(
    private val onEdit: (FieldCard) -> Unit,
    private val onDelete: (FieldCard) -> Unit,
    private val fieldTypeLabelProvider: (String) -> String,
    private val optionRoleLabelProvider: (String) -> String
) : RecyclerView.Adapter<FieldCardAdapter.FieldCardViewHolder>() {

    private val items = mutableListOf<FieldCard>()

    fun submitList(cards: List<FieldCard>) {
        items.clear()
        items.addAll(cards)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FieldCardViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_schema_field_card, parent, false)
        return FieldCardViewHolder(view)
    }

    override fun onBindViewHolder(holder: FieldCardViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class FieldCardViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val nameText: TextView = itemView.findViewById(R.id.fieldCardNameText)
        private val metaText: TextView = itemView.findViewById(R.id.fieldCardMetaText)
        private val typeBadgeText: TextView = itemView.findViewById(R.id.fieldCardTypeBadgeText)
        private val lookupBadgeText: TextView = itemView.findViewById(R.id.fieldCardLookupBadgeText)
        private val uniqueBadgeText: TextView = itemView.findViewById(R.id.fieldCardUniqueBadgeText)
        private val requiredBadgeText: TextView = itemView.findViewById(R.id.fieldCardRequiredBadgeText)
        private val optionSourceBadgeText: TextView = itemView.findViewById(R.id.fieldCardOptionSourceBadgeText)
        private val optionRoleBadgeText: TextView = itemView.findViewById(R.id.fieldCardOptionRoleBadgeText)
        private val editButton: ImageButton = itemView.findViewById(R.id.fieldEditButton)
        private val deleteButton: ImageButton = itemView.findViewById(R.id.fieldDeleteButton)

        fun bind(card: FieldCard) {
            nameText.text = card.displayName
            metaText.visibility = View.GONE
            typeBadgeText.text = itemView.context.getString(
                R.string.schema_field_type_badge,
                fieldTypeLabelProvider(card.fieldType)
            )

            lookupBadgeText.visibility = if (card.isLookupKey || card.isFlexibleSearch) View.VISIBLE else View.GONE
            uniqueBadgeText.visibility = if (card.isUniqueValue) View.VISIBLE else View.GONE
            requiredBadgeText.visibility = if (card.isRequiredValue) View.VISIBLE else View.GONE
            optionSourceBadgeText.visibility =
                if (card.fieldType == SchemaFieldType.LIST.storageValue && card.optionSourceCollectionName.isNotBlank()) View.VISIBLE else View.GONE
            optionRoleBadgeText.visibility =
                if (card.optionDisplayRole != SchemaOptionDisplayRole.NONE.storageValue) View.VISIBLE else View.GONE

            if (card.isLookupKey) {
                lookupBadgeText.text = itemView.context.getString(R.string.schema_lookup_badge)
            } else if (card.isFlexibleSearch) {
                lookupBadgeText.text = itemView.context.getString(R.string.schema_flexible_badge)
            }
            if (card.isUniqueValue) {
                uniqueBadgeText.text = itemView.context.getString(R.string.schema_unique_badge)
            }
            if (card.isRequiredValue) {
                requiredBadgeText.text = itemView.context.getString(R.string.schema_required_badge)
            }
            if (optionSourceBadgeText.visibility == View.VISIBLE) {
                optionSourceBadgeText.text = itemView.context.getString(
                    R.string.schema_list_source_badge,
                    card.optionSourceCollectionName
                )
            }
            if (optionRoleBadgeText.visibility == View.VISIBLE) {
                optionRoleBadgeText.text = optionRoleLabelProvider(card.optionDisplayRole)
            }

            itemView.setOnClickListener { onEdit(card) }
            editButton.setOnClickListener { onEdit(card) }
            deleteButton.setOnClickListener { onDelete(card) }
        }
    }
}
