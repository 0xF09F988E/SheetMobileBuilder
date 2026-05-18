package com.pwa.offline

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView

class RecordPreviewAdapter : RecyclerView.Adapter<RecordPreviewAdapter.RecordViewHolder>() {

    private val items = mutableListOf<RecordPreview>()

    fun submitList(records: List<RecordPreview>) {
        items.clear()
        items.addAll(records)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecordViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_record_preview, parent, false)
        return RecordViewHolder(view)
    }

    override fun onBindViewHolder(holder: RecordViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    class RecordViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val titleText: TextView = itemView.findViewById(R.id.recordTitleText)
        private val bodyText: TextView = itemView.findViewById(R.id.recordBodyText)
        private val updatedAtText: TextView = itemView.findViewById(R.id.recordUpdatedAtText)
        private val copyButton: TextView = itemView.findViewById(R.id.recordCopyButton)

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
            copyButton.setOnClickListener {
                val clipboard = itemView.context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val payload = buildString {
                    append(titleText.text)
                    if (body.isNotBlank()) {
                        appendLine()
                        append(body)
                    }
                }
                clipboard.setPrimaryClip(ClipData.newPlainText(titleText.text, payload))
                Toast.makeText(
                    itemView.context,
                    R.string.browse_copy_done,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
}
