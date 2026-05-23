package com.pwa.offline

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class RecordPreviewAdapter(
    private val onShareRecord: (RecordPreview) -> Unit,
    private val onDeleteRecord: (RecordPreview) -> Unit
) : RecyclerView.Adapter<RecordPreviewAdapter.RecordViewHolder>() {

    companion object {
        private const val ACTION_SHARE = 1
        private const val ACTION_DELETE = 2
    }

    private val items = mutableListOf<RecordPreview>()

    fun submitList(records: List<RecordPreview>) {
        items.clear()
        items.addAll(records)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecordViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_record_preview, parent, false)
        return RecordViewHolder(view, onShareRecord, onDeleteRecord)
    }

    override fun onBindViewHolder(holder: RecordViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    class RecordViewHolder(
        itemView: View,
        private val onShareRecord: (RecordPreview) -> Unit,
        private val onDeleteRecord: (RecordPreview) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        private val titleText: TextView = itemView.findViewById(R.id.recordTitleText)
        private val bodyText: TextView = itemView.findViewById(R.id.recordBodyText)
        private val updatedAtText: TextView = itemView.findViewById(R.id.recordUpdatedAtText)
        private val actionButton: TextView = itemView.findViewById(R.id.recordActionButton)

        fun bind(record: RecordPreview) {
            titleText.text = record.title.ifBlank {
                itemView.context.getString(R.string.browse_record_title_fallback)
            }
            updatedAtText.text = itemView.context.getString(
                R.string.browse_record_updated_at,
                TimestampFormatters.sqliteUtcToDeviceMx(record.updatedAt)
            )
            val body = buildString {
                if (record.values.isEmpty()) {
                    append(itemView.context.getString(R.string.browse_record_empty))
                } else {
                    record.values.entries.forEachIndexed { index, entry ->
                        append(entry.key)
                        append(": ")
                        append(entry.value.ifBlank { "-" })
                        if (index < record.values.size - 1) {
                            appendLine()
                        }
                    }
                }
            }
            bodyText.text = body
            actionButton.setOnClickListener { anchor ->
                PopupMenu(anchor.context, anchor).apply {
                    menu.add(0, ACTION_SHARE, 0, R.string.browse_action_share)
                    menu.add(0, ACTION_DELETE, 1, R.string.browse_action_delete)
                    setOnMenuItemClickListener { menuItem ->
                        when (menuItem.itemId) {
                            ACTION_SHARE -> {
                                onShareRecord(record)
                                true
                            }
                            ACTION_DELETE -> {
                                onDeleteRecord(record)
                                true
                            }
                            else -> false
                        }
                    }
                }.show()
            }
        }
    }
}
