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
 *
 * Each source's own date (`retrievedOn` for derived sources, `verifiedOn` for author-verified)
 * is checked individually. The newest of those dates must be within [MAX_SOURCE_AGE_MONTHS].
 */
class PakistanCalendarFreshnessTest {

    private companion object {
        const val MAX_FIX_AGE_MONTHS = 12
        const val MAX_SOURCE_AGE_MONTHS = 12
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

    @Test
    fun theNewestSourceIsNotStale() {
        // Each source has its own date: retrievedOn for derived/official, verifiedOn for author-verified.
        // Find the newest date across all sources and check it is within the allowed age.
        val sources = PAKISTAN_FIXES_SOURCES
        val today = Clock.System.now().toLocalDateTime(TimeZone.UTC).date

        var newestDate: LocalDate? = null
        var oldestSourceKey: String? = null
        var oldestDate: LocalDate? = null

        for ((key, source) in sources) {
            val dateStr = source.retrievedOn.ifBlank { source.verifiedOn }
            if (dateStr.isBlank()) {
                assertTrue(
                    false,
                    "source \"$key\" has neither retrievedOn nor verifiedOn; every source must have a date",
                )
            }
            val date = LocalDate.parse(dateStr)
            if (newestDate == null || date > newestDate) {
                newestDate = date
            }
            if (oldestDate == null || date < oldestDate) {
                oldestDate = date
                oldestSourceKey = key
            }
        }

        assertTrue(newestDate != null, "no sources found in PAKISTAN_FIXES_SOURCES")
        val newest = newestDate!!
        val ageMonths = (today.year - newest.year) * 12 + (today.month.ordinal - newest.month.ordinal)
        assertTrue(
            ageMonths <= MAX_SOURCE_AGE_MONTHS,
            "the newest Pakistan fix source date is $newest (source: ${oldestSourceKey ?: "unknown"}, " +
                "oldest: $oldestDate), more than $MAX_SOURCE_AGE_MONTHS months ago; " +
                "check for new announcements and update pakistan-fixes.json with current data.",
        )
    }
}
