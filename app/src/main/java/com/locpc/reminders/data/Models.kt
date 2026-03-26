package com.locpc.reminders.data

import com.google.gson.annotations.SerializedName
import java.io.Serializable

data class Reminder(
    @SerializedName("id")
    val id: String,
    @SerializedName("title")
    val title: String,
    @SerializedName("description")
    val description: String? = null,
    @SerializedName("remindTime")
    val remindTime: Long? = null,
    @SerializedName("remind_time")
    val remindTimeAlt: Long? = null,
    @SerializedName("time")
    val time: String? = null,
    @SerializedName("days")
    val days: List<String>? = null,
    @SerializedName("day")
    val day: String? = null,
    @SerializedName("status")
    val status: String = "pending", // pending, completed, dismissed
    @SerializedName("createdAt")
    val createdAt: Long? = null,
    @SerializedName("created_at")
    val createdAtAlt: Long? = null,
    @SerializedName("updatedAt")
    val updatedAt: Long? = null,
    @SerializedName("updated_at")
    val updatedAtAlt: Long? = null
) : Serializable {
    fun getRemindTime(): Long = remindTime ?: remindTimeAlt ?: 0L
    fun getCreatedAt(): Long = createdAt ?: createdAtAlt ?: 0L
    fun getUpdatedAt(): Long = updatedAt ?: updatedAtAlt ?: 0L

    /** Returns days as a comma-separated string for display/parsing, e.g. "Sat,Sun".
     *  Prefers the `days` array (server format) over the legacy `day` string field. */
    fun getDaysString(): String? =
        days?.filter { it.isNotBlank() }?.joinToString(",")?.ifBlank { null } ?: day?.ifBlank { null }
}

// Deprecated: API returns List<Reminder> directly
data class ReminderResponse(
    @SerializedName("success")
    val success: Boolean? = null,
    @SerializedName("data")
    val data: List<Reminder>? = null,
    @SerializedName("message")
    val message: String? = null
) {
    // Helper to get data from direct array response
    fun getReminders(): List<Reminder> {
        return data ?: emptyList()
    }
}

data class LocationUpdate(
    @SerializedName("latitude")
    val latitude: Double,
    @SerializedName("longitude")
    val longitude: Double,
    @SerializedName("accuracy")
    val accuracy: Float,
    @SerializedName("timestamp")
    val timestamp: Long = System.currentTimeMillis(),
    @SerializedName("deviceId")
    val deviceId: String
)

data class LocationResponse(
    @SerializedName("success")
    val success: Boolean,
    @SerializedName("message")
    val message: String? = null
)
