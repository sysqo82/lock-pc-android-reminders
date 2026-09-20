package com.locpc.reminders.data

import com.locpc.reminders.api.GsonProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReminderTest {

    @Test
    fun getRemindTime_prefersPrimaryFieldOverAlt() {
        val reminder = Reminder(
            id = "1",
            title = "Test",
            remindTime = 1000L,
            remindTimeAlt = 2000L
        )
        assertEquals(1000L, reminder.getRemindTime())
    }

    @Test
    fun getRemindTime_usesAltWhenPrimaryIsNull() {
        val reminder = Reminder(
            id = "1",
            title = "Test",
            remindTime = null,
            remindTimeAlt = 2000L
        )
        assertEquals(2000L, reminder.getRemindTime())
    }

    @Test
    fun getRemindTime_defaultsToZeroWhenBothNull() {
        val reminder = Reminder(id = "1", title = "Test")
        assertEquals(0L, reminder.getRemindTime())
    }

    @Test
    fun getCreatedAt_prefersPrimaryAndFallsBack() {
        val primary = Reminder(id = "1", title = "T", createdAt = 500L, createdAtAlt = 600L)
        val alt = Reminder(id = "1", title = "T", createdAt = null, createdAtAlt = 600L)
        val none = Reminder(id = "1", title = "T")

        assertEquals(500L, primary.getCreatedAt())
        assertEquals(600L, alt.getCreatedAt())
        assertEquals(0L, none.getCreatedAt())
    }

    @Test
    fun getUpdatedAt_prefersPrimaryAndFallsBack() {
        val primary = Reminder(id = "1", title = "T", updatedAt = 700L, updatedAtAlt = 800L)
        val alt = Reminder(id = "1", title = "T", updatedAt = null, updatedAtAlt = 800L)
        val none = Reminder(id = "1", title = "T")

        assertEquals(700L, primary.getUpdatedAt())
        assertEquals(800L, alt.getUpdatedAt())
        assertEquals(0L, none.getUpdatedAt())
    }

    @Test
    fun getDaysString_formatsDaysArrayCorrectly() {
        val reminder = Reminder(
            id = "1",
            title = "Test",
            days = listOf("Mon", "Wed", "Fri"),
            day = "Sun"
        )
        assertEquals("Mon,Wed,Fri", reminder.getDaysString())
    }

    @Test
    fun getDaysString_filtersBlankElementsInDaysArray() {
        val reminder = Reminder(
            id = "1",
            title = "Test",
            days = listOf("Mon", " ", "")
        )
        assertEquals("Mon", reminder.getDaysString())
    }

    @Test
    fun getDaysString_fallsBackToDayFieldWhenDaysArrayIsEmptyOrBlank() {
        val reminder = Reminder(
            id = "1",
            title = "Test",
            days = listOf(" "),
            day = "Tue"
        )
        assertEquals("Tue", reminder.getDaysString())
    }

    @Test
    fun getDaysString_returnsNullWhenBothDaysAndDayAreBlank() {
        val reminder = Reminder(
            id = "1",
            title = "Test",
            days = listOf(" "),
            day = ""
        )
        assertNull(reminder.getDaysString())
    }

    @Test
    fun reminderResponse_getReminders_returnsDataOrEmptyList() {
        val responseWithData = ReminderResponse(success = true, data = listOf(Reminder("1", "T")))
        val responseNull = ReminderResponse(success = false, data = null)

        assertEquals(1, responseWithData.getReminders().size)
        assertEquals(0, responseNull.getReminders().size)
    }

    @Test
    fun jsonDeserialization_parsesSnakeCaseFields() {
        val json = """
            {
                "id": "123",
                "title": "Meeting",
                "description": "Sync with team",
                "remind_time": 1700000000000,
                "created_at": 1690000000000,
                "updated_at": 1695000000000,
                "days": ["Mon", "Thu"],
                "status": "pending"
            }
        """.trimIndent()

        val reminder = GsonProvider.lenientGson.fromJson(json, Reminder::class.java)

        assertEquals("123", reminder.id)
        assertEquals("Meeting", reminder.title)
        assertEquals("Sync with team", reminder.description)
        assertEquals(1700000000000L, reminder.getRemindTime())
        assertEquals(1690000000000L, reminder.getCreatedAt())
        assertEquals(1695000000000L, reminder.getUpdatedAt())
        assertEquals("Mon,Thu", reminder.getDaysString())
        assertEquals("pending", reminder.status)
    }
}
