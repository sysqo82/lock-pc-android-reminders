package com.locpc.reminders.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReminderAdapterTest {

    @Test
    fun formatDays_returnsNullForNullOrBlank() {
        assertNull(ReminderAdapter.formatDays(null))
        assertNull(ReminderAdapter.formatDays(""))
        assertNull(ReminderAdapter.formatDays("   "))
    }

    @Test
    fun formatDays_returnsEverydayForAllSevenDays() {
        assertEquals("Everyday", ReminderAdapter.formatDays("Mon, Tue, Wed, Thu, Fri, Sat, Sun"))
        assertEquals("Everyday", ReminderAdapter.formatDays("mon,tue,wed,thu,fri,sat,sun"))
        assertEquals("Everyday", ReminderAdapter.formatDays("Monday, Tuesday, Wednesday, Thursday, Friday, Saturday, Sunday"))
        assertEquals("Everyday", ReminderAdapter.formatDays("everyday"))
        assertEquals("Everyday", ReminderAdapter.formatDays("every day"))
    }

    @Test
    fun formatDays_returnsWeekdaysForFiveWeekdays() {
        assertEquals("Weekdays", ReminderAdapter.formatDays("Mon, Tue, Wed, Thu, Fri"))
        assertEquals("Weekdays", ReminderAdapter.formatDays("mon,tue,wed,thu,fri"))
        assertEquals("Weekdays", ReminderAdapter.formatDays("Monday, Tuesday, Wednesday, Thursday, Friday"))
        assertEquals("Weekdays", ReminderAdapter.formatDays("weekdays"))
    }

    @Test
    fun formatDays_returnsWeekendsForWeekendDays() {
        assertEquals("Weekends", ReminderAdapter.formatDays("Sat, Sun"))
        assertEquals("Weekends", ReminderAdapter.formatDays("sat,sun"))
        assertEquals("Weekends", ReminderAdapter.formatDays("Saturday, Sunday"))
        assertEquals("Weekends", ReminderAdapter.formatDays("Sun, Sat"))
        assertEquals("Weekends", ReminderAdapter.formatDays("weekends"))
    }

    @Test
    fun formatDays_returnsCommaSeparatedDaysWhenPatternDoesNotMatch() {
        assertEquals("Mon, Wed, Fri", ReminderAdapter.formatDays("Mon, Wed, Fri"))
        assertEquals("Mon, Tue, Wed, Thu, Fri, Sat", ReminderAdapter.formatDays("mon,tue,wed,thu,fri,sat"))
        assertEquals("Sat", ReminderAdapter.formatDays("sat"))
    }
}
