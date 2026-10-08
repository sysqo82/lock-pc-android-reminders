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

    companion object {
        private val ALL_DAYS = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
        private val WEEKDAYS = setOf("Mon", "Tue", "Wed", "Thu", "Fri")
        private val WEEKENDS = setOf("Sat", "Sun")

        private val DAY_MAP = mapOf(
            "mon" to "Mon", "monday" to "Mon",
            "tue" to "Tue", "tues" to "Tue", "tuesday" to "Tue",
            "wed" to "Wed", "wednesday" to "Wed",
            "thu" to "Thu", "thur" to "Thu", "thurs" to "Thu", "thursday" to "Thu",
            "fri" to "Fri", "friday" to "Fri",
            "sat" to "Sat", "saturday" to "Sat",
            "sun" to "Sun", "sunday" to "Sun"
        )

        /** Formats day string. Returns "Everyday", "Weekdays", "Weekends", or comma-separated days. */
        fun formatDays(raw: String?): String? {
            if (raw.isNullOrBlank()) return null
            val trimmed = raw.trim()
            val lower = trimmed.lowercase(Locale.getDefault())
            if (lower == "everyday" || lower == "every day" || lower == "daily") return "Everyday"
            if (lower == "weekdays") return "Weekdays"
            if (lower == "weekends") return "Weekends"

            val tokens = trimmed.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            if (tokens.isEmpty()) return null

            val parsedDays = mutableListOf<String>()
            val canonicalSet = mutableSetOf<String>()

            for (token in tokens) {
                val canon = DAY_MAP[token.lowercase(Locale.getDefault())]
                if (canon != null) {
                    if (canonicalSet.add(canon)) {
                        parsedDays.add(canon)
                    }
                } else {
                    val formatted = token.replaceFirstChar { c -> c.uppercase(Locale.getDefault()) }
                    if (canonicalSet.add(formatted)) {
                        parsedDays.add(formatted)
                    }
                }
            }

            if (canonicalSet.size == 7 && canonicalSet.containsAll(ALL_DAYS)) {
                return "Everyday"
            }
            if (canonicalSet == WEEKDAYS) {
                return "Weekdays"
            }
            if (canonicalSet == WEEKENDS) {
                return "Weekends"
            }

            val ordered = ALL_DAYS.filter { canonicalSet.contains(it) } +
                    parsedDays.filter { !ALL_DAYS.contains(it) }

            return ordered.joinToString(", ")
        }
    }

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
    }
}
