package com.muazdev.hijricalendar.widget.glance

import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Month-length overrides persist to one DataStore Preferences file shared by the app and its
 * widgets, so an alarm-woken widget in a fresh process sees the same table a settings screen wrote.
 * The point of the repository (rather than the process-global companion table it replaces) is
 * durability, so the load-bearing test is that **a fresh repository instance sees what a previous
 * instance wrote** — with no shared in-memory state at all.
 *
 * Creating two DataStores on one file in one process is an error by design, so "fresh" is
 * simulated honestly: each repository gets its own [CoroutineScope], and the writer's scope is
 * cancelled before the reader starts — the on-process-death case the store exists for.
 */
class HijriMonthLengthsRepositoryTest {

    private lateinit var file: File
    private val scopes = mutableListOf<CoroutineScope>()

    private fun newRepository(): HijriMonthLengthsRepository {
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        scopes += scope
        return HijriMonthLengthsRepository(
            PreferenceDataStoreFactory.create(
                corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
                scope = scope,
                produceFile = { file },
            ),
        )
    }

    /** Writes [block], then closes the writer — the "fresh process" step. */
    private suspend fun writtenAndClosed(block: suspend (HijriMonthLengthsRepository) -> Unit) {
        val writerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        scopes += writerScope
        block(
            HijriMonthLengthsRepository(
                PreferenceDataStoreFactory.create(
                    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
                    scope = writerScope,
                    produceFile = { file },
                ),
            ),
        )
        writerScope.cancel()
    }

    @Before
    fun createFile() {
        file = File.createTempFile("overrides", ".preferences_pb").apply { deleteOnExit() }
    }

    @After
    fun deleteFile() {
        scopes.forEach { it.cancel() }
        file.delete()
    }

    @Test
    fun aFreshReaderSeesWhatAPreviousWriterWrote() = runTest {
        writtenAndClosed { writer ->
            writer.setMonthLength(1448, 3, 30)
            writer.setMonthLength(1448, 4, 29)
        }

        val reader = newRepository()
        assertEquals(30, reader.load()[1448 to 3], "overrides must survive a process restart")
        assertEquals(29, reader.load()[1448 to 4])
        assertNull(reader.load()[1448 to 5])
    }

    @Test
    fun aFreshReaderSeesClearsFromAnotherWriter() = runTest {
        writtenAndClosed { writer ->
            writer.setMonthLength(1448, 3, 30)
            writer.setMonthLength(1448, 4, 29)
            writer.clearMonthLength(1448, 3)
            writer.clearAll()
        }

        val reader = newRepository()
        assertTrue(reader.load().isEmpty())
    }

    @Test
    fun aFreshReaderSeesTheEmptyTableWhenNothingWasEverWritten() = runTest {
        val reader = newRepository()
        assertTrue(reader.load().isEmpty())
        assertNull(reader.load()[1448 to 3])
    }

    @Test
    fun loadTableProducesAnIndependentInstanceWithTheStoredOverrides() = runTest {
        writtenAndClosed { writer -> writer.setMonthLength(1448, 3, 30) }

        val reader = newRepository()
        val table = reader.loadTable()
        assertEquals(30, table.monthLength(1448, 3))
        // The built table is independent of the store: writes to it must not leak back.
        table.setMonthLength(1448, 4, 29)
        assertEquals(mapOf((1448 to 3) to 30), reader.load())
        assertEquals(29, table.monthLength(1448, 4))
    }

    @Test
    fun replaceAllRewritesTheStoreAtomically() = runTest {
        writtenAndClosed { writer ->
            writer.setMonthLength(1448, 3, 30)
            writer.replaceAll(mapOf((1447 to 10) to 29))
        }

        val reader = newRepository()
        assertEquals(mapOf((1447 to 10) to 29), reader.load())
    }
}
