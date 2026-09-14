package dev.compatvideo.reminder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OriginalReminderScheduleTest {
    @Test
    fun `reminder becomes due only after thirty days`() {
        val createdAt = 1_000L
        val event = "$createdAt|event"

        assertEquals(
            emptySet<String>(),
            OriginalReminderSchedule.dueEvents(
                setOf(event),
                createdAt + OriginalReminderSchedule.REMINDER_DELAY_MS - 1L,
            ),
        )
        assertEquals(
            setOf(event),
            OriginalReminderSchedule.dueEvents(
                setOf(event),
                createdAt + OriginalReminderSchedule.REMINDER_DELAY_MS,
            ),
        )
    }

    @Test
    fun `next delay uses the oldest valid event`() {
        val now = 10_000L
        val events = setOf(
            "3000|newer",
            "1000|older",
            "not-a-timestamp|ignored",
        )

        assertEquals(
            1_000L + OriginalReminderSchedule.REMINDER_DELAY_MS - now,
            OriginalReminderSchedule.nextDelay(events, now),
        )
    }

    @Test
    fun `invalid or empty events schedule nothing`() {
        assertNull(OriginalReminderSchedule.nextDelay(emptySet(), 0L))
        assertNull(OriginalReminderSchedule.nextDelay(setOf("invalid"), 0L))
    }
}
