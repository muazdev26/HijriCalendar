package com.muazdev.hijricalendar.buildlogic

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import java.io.File

/**
 * Generates the Pakistan calendar FIX table's Kotlin source from `pakistan-fixes.json`, so the
 * handwritten `mapOf(...)` in `PakistanHijriCalendar` can be deleted and the JSON is the one source
 * of truth. The output is deterministic: same JSON, same file, byte for byte.
 *
 * Generated file: `<outputDir>/PakistantFixesGenerated.kt` — an `internal val` holding the map and
 * the `verifiedOn` string, both consumed by `PakistanHijriCalendar` and the freshness test.
 */
abstract class GeneratePakistanFixesTask : DefaultTask() {

    @get:InputFile
    abstract val inputFile: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        val source = inputFile.get().asFile
        val document = jsonParser.decodeFromString(FixesDocument.serializer(), source.readText())

        require(document.fixes.isNotEmpty()) {
            "${source.absolutePath} declares no fixes; the Pakistan table would be empty"
        }
        document.fixes.forEach { fix ->
            require(fix.year in 1400..1500) { "fix year ${fix.year} outside the supported range" }
            require(fix.month in 1..12) { "fix month ${fix.month} outside 1..12" }
            if (fix.length != null) {
                require(fix.length == 29 || fix.length == 30) {
                    "fix ${fix.year}-${fix.month} has length ${fix.length}; only 29 and 30 are legal"
                }
            }
            require(document.sources.containsKey(fix.source)) {
                "fix ${fix.year}-${fix.month} names an unknown source \"${fix.source}\""
            }
            val source = document.sources[fix.source]!!
            when (source.kind) {
                "official", "derived" -> {
                    require(source.url.isNotBlank()) {
                        "source \"${fix.source}\" has kind \"${source.kind}\" but empty url; \"official\" and \"derived\" sources must have a URL"
                    }
                }
                "author-verified" -> {
                    require(source.verifiedBy.isNotBlank()) {
                        "source \"${fix.source}\" has kind \"author-verified\" but empty verifiedBy"
                    }
                    require(source.verifiedOn.isNotBlank()) {
                        "source \"${fix.source}\" has kind \"author-verified\" but empty verifiedOn"
                    }
                    require(source.note.isNotBlank()) {
                        "source \"${fix.source}\" has kind \"author-verified\" but empty note"
                    }
                }
                else -> {
                    require(false) {
                        "source \"${fix.source}\" has kind \"${source.kind}\"; must be \"official\", \"derived\", or \"author-verified\""
                    }
                }
            }
            val date = fix.firstGregorian
            require(Regex("""\d{4}-\d{2}-\d{2}""").matches(date)) {
                "fix ${fix.year}-${fix.month} has firstGregorian \"$date\", expected yyyy-MM-dd"
            }
        }
        // The (year, month) pairs must be unique: two fixes for one month would silently overwrite
        // each other in the mapOf expression's linkage.
        val keys = document.fixes.map { it.year to it.month }
        require(keys.distinct().size == keys.size) {
            "duplicate fixes in ${source.absolutePath}: ${keys.groupingBy { it }.eachCount().filterValues { it > 1 }}"
        }

        // Only fixes with a known length can be emitted into the FIX map.
        // A fix with status "pending-length" has a confirmed start date but its length is not yet
        // known, so it is validated above but skipped here.
        val completeFixes = document.fixes.filter { it.length != null }
        require(completeFixes.isNotEmpty()) {
            "${source.absolutePath} has no fixes with a known length; the Pakistan table would be empty"
        }

        val generatedSource = buildString {
            appendLine("// Generated from src/commonMain/resources/pakistan-fixes.json by :generatePakistanFixes.")
            appendLine("// Do not edit: edit the JSON and the build regenerates this file.")
            appendLine("package com.muazdev.hijricalendar.core")
            appendLine()
            appendLine("internal val PAKISTAN_FIXES: Map<Pair<Int, Int>, PakistanHijriCalendar.Fix> = linkedMapOf(")
            completeFixes.sortedBy { it.year * 100 + it.month }.forEach { fix ->
                val (year, month, day) = parseDate(fix.firstGregorian)
                appendLine(
                    "    (${fix.year} to ${fix.month}) to " +
                        "PakistanHijriCalendar.Fix(" +
                        "kotlinx.datetime.LocalDate($year, $month, $day), ${fix.length}),"
                )
            }
            appendLine(")")
            appendLine()
            appendLine("internal const val PAKISTAN_FIXES_VERIFIED_ON: String = \"${document.verifiedOn}\"")
            appendLine()
            appendLine("internal val PAKISTAN_FIXES_SOURCES: Map<String, PakistanFixSource> = mapOf(")
            document.sources.entries.sortedBy { it.key }.forEach { (key, source) ->
                appendLine(
                    "    \"$key\" to PakistanFixSource(" +
                        "\"${source.retrievedOn}\", \"${source.verifiedOn}\"),"
                )
            }
            appendLine(")")
            appendLine()
            appendLine("internal data class PakistanFixSource(val retrievedOn: String, val verifiedOn: String)")
        }

        val destination = outputDir.get().asFile.resolve("PakistanFixesGenerated.kt")
        destination.parentFile.mkdirs()
        destination.writeText(generatedSource)
    }

    private companion object {
        private val jsonParser = Json { ignoreUnknownKeys = true }
    }

    private fun parseDate(value: String): Triple<Int, Int, Int> {
        val parts = value.split('-').map { it.toInt() }
        return Triple(parts[0], parts[1], parts[2])
    }

    @Serializable
    private data class FixesDocument(
        @SerialName("verifiedOn") val verifiedOn: String,
        @SerialName("sources") val sources: Map<String, SourceEntry>,
        @SerialName("fixes") val fixes: List<FixEntry>,
    )

    @Serializable
    private data class SourceEntry(
        @SerialName("url") val url: String = "",
        @SerialName("retrievedOn") val retrievedOn: String = "",
        @SerialName("kind") val kind: String = "",
        @SerialName("note") val note: String = "",
        @SerialName("verifiedBy") val verifiedBy: String = "",
        @SerialName("verifiedOn") val verifiedOn: String = "",
    )

    @Serializable
    private data class FixEntry(
        @SerialName("year") val year: Int,
        @SerialName("month") val month: Int,
        @SerialName("firstGregorian") val firstGregorian: String,
        @SerialName("length") val length: Int? = null,
        @SerialName("source") val source: String,
        @SerialName("lengthDerived") val lengthDerived: Boolean = false,
        @SerialName("status") val status: String? = null,
    )
}
