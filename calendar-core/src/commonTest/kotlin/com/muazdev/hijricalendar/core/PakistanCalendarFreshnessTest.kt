package com.muazdev.hijricalendar.core

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Clock

/**
 * The Pakistan fix table is only as good as the last announcement behind it. Beyond the newest
 * verified fix, Pakistan mode silently answers with Umm al-Qura as a proxy — accurate for most
 * months, off by up to a day for the ones the committee has not ruled on yet. This test is the
 * guard: it fails when the table has not been re-verified for [MAX_FIX_AGE_MONTHS], so the proxy is
 * never extended indefinitely by silence.
 *
 * The `verifiedOn` value is not the date of the sighting; it is the date the table was last
 * reviewed against the Central Ruet-e-Hilal Committee's announced decisions.
 */
class PakistanCalendarFreshnessTest {

    private companion object {
        const val MAX_FIX_AGE_MONTHS = 12
    }

    @Test
    fun theVerifiedTableIsNotStale() {
        val verified = LocalDate.parse(PAKISTAN_FIXES_VERIFIED_ON)
        val today = Clock.System.now().toLocalDateTime(TimeZone.UTC).date
        val ageMonths = (today.year - verified.year) * 12 + (today.month.ordinal - verified.month.ordinal)
        assertTrue(
            ageMonths <= MAX_FIX_AGE_MONTHS,
            "the Pakistan fix table was last verified ${PAKISTAN_FIXES_VERIFIED_ON}, " +
                "more than $MAX_FIX_AGE_MONTHS months ago; beyond the verified window Pakistan mode " +
                "silently uses Umm al-Qura as a proxy. Re-check the Central Ruet-e-Hilal Committee " +
                "decisions and update calendar-core/src/commonMain/resources/pakistan-fixes.json " +
                "(and `verifiedOn`) rather than letting the proxy drift on.",
        )
    }
}
