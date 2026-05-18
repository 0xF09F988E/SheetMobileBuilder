package com.pwa.offline

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ModuleAdapter(
    private val modules: List<ModuleDefinition>,
    private val onSelected: (ModuleDefinition) -> Unit
) : RecyclerView.Adapter<ModuleAdapter.ModuleViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ModuleViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_module, parent, false)
        return ModuleViewHolder(view, onSelected)
    }

    override fun onBindViewHolder(holder: ModuleViewHolder, position: Int) {
        holder.bind(modules[position])
    }

    override fun getItemCount(): Int = modules.size

    class ModuleViewHolder(
        itemView: View,
        private val onSelected: (ModuleDefinition) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {

        private val titleText: TextView = itemView.findViewById(R.id.moduleTitleText)
        private val summaryText: TextView = itemView.findViewById(R.id.moduleSummaryText)
        private val iconView: ImageView = itemView.findViewById(R.id.moduleIconView)

        fun bind(module: ModuleDefinition) {
            titleText.text = module.title
            summaryText.text = module.summary
            iconView.setImageResource(module.iconRes)
            itemView.setOnClickListener { onSelected(module) }
        }
    }
}
