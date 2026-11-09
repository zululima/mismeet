package app.mismeet.android.store

import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShareDurationTest {
    private val helsinki = ZoneId.of("Europe/Helsinki")

    private fun millis(text: String) = ZonedDateTime.parse(text).toInstant().toEpochMilli()

    @Test
    fun anHourIs3600SecondsLater() {
        val now = millis("2026-11-09T23:30+02:00[Europe/Helsinki]")
        assertEquals(now + 3_600_000, ShareDuration.HOUR.endsAt(now, helsinki))
    }

    @Test
    fun theEndOfTheDayIsTheNextLocalMidnight() {
        val end = ShareDuration.END_OF_DAY.endsAt(millis("2026-11-09T14:05+02:00[Europe/Helsinki]"), helsinki)!!
        assertEquals(millis("2026-11-10T00:00+02:00[Europe/Helsinki]"), end)
        assertTrue(ShareDuration.isStartOfDay(end, helsinki))
    }

    @Test
    fun theEndOfADayThatChangesTheClockIsStillMidnight() {
        val end = ShareDuration.END_OF_DAY.endsAt(millis("2026-03-29T12:00+03:00[Europe/Helsinki]"), helsinki)!!
        assertEquals(millis("2026-03-30T00:00+03:00[Europe/Helsinki]"), end)
    }

    @Test
    fun anIndefiniteShareHasNoEnd() {
        assertNull(ShareDuration.INDEFINITELY.endsAt(0, helsinki))
    }

    @Test
    fun onlyMidnightCountsAsTheStartOfADay() {
        assertFalse(ShareDuration.isStartOfDay(millis("2026-11-10T00:30+02:00[Europe/Helsinki]"), helsinki))
    }

    @Test
    fun aShareEndsAtItsEndTime() {
        val contact = Contact("id", "Aino", emptyList(), shareEndsAt = 1_000, addedAt = 0)
        assertTrue(contact.sharesAt(999))
        assertFalse(contact.sharesAt(1_000))
        assertFalse(contact.copy(share = false, shareEndsAt = null).sharesAt(0))
        assertTrue(contact.copy(shareEndsAt = null).sharesAt(Long.MAX_VALUE))
    }
}
