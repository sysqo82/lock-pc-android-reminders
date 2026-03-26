package com.locpc.reminders.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.locpc.reminders.R
import com.locpc.reminders.data.Reminder
import java.util.Locale

class ReminderAdapter(private val reminders: List<Reminder>) :
    RecyclerView.Adapter<ReminderAdapter.ReminderViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReminderViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_reminder, parent, false)
        return ReminderViewHolder(view)
    }

    override fun onBindViewHolder(holder: ReminderViewHolder, position: Int) {
        val reminder = reminders[position]
        holder.bind(reminder)
    }

    override fun getItemCount(): Int = reminders.size

    inner class ReminderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val titleTextView: TextView = itemView.findViewById(R.id.reminderTitle)
        private val descriptionTextView: TextView = itemView.findViewById(R.id.reminderDescription)
        private val timeTextView: TextView = itemView.findViewById(R.id.reminderTime)

        fun bind(reminder: Reminder) {
            // API returns title/description reversed — description holds the display name
            titleTextView.text = reminder.description ?: reminder.title

            // Build a clean schedule line e.g. "Mon, Wed, Fri  |  14:30"
            val dayPart = formatDays(reminder.getDaysString())
            val timePart = reminder.time
            timeTextView.text = when {
                dayPart != null && timePart != null -> "$dayPart  |  $timePart"
                dayPart != null -> dayPart
                timePart != null -> timePart
                else -> ""
            }
        }

        /** Capitalises and joins comma-separated day tokens, e.g. "mon,wed" → "Mon, Wed" */
        private fun formatDays(raw: String?): String? {
            if (raw.isNullOrBlank()) return null
            return raw.split(",")
                .map { it.trim().replaceFirstChar { c -> c.uppercase(Locale.getDefault()) } }
                .joinToString(", ")
        }
    }
}
