package com.jeremiah.phoneusage.data

import android.util.Log
import java.io.File
import java.io.OutputStream
import java.time.LocalDate
import org.json.JSONObject

/**
 * On-device storage. Two streams, both kept in the app's private internal directory
 * (`/data/data/<pkg>/files`) which no other app can read:
 *
 *  * `events/<yyyy-MM>.jsonl` — one raw usage event per line, appended as it is collected.
 *  * `daily/<yyyy-MM>.json`   — per-day, per-app foreground milliseconds taken from the
 *    platform's own daily statistics. These survive long collection gaps, because Android
 *    keeps daily buckets far longer than it keeps the raw event stream.
 *
 * Nothing here ever expires on its own. Files are only removed when the user asks.
 */
class EventStore(filesDir: File) {

    private val eventsDir = File(filesDir, "events").apply { mkdirs() }
    private val dailyDir = File(filesDir, "daily").apply { mkdirs() }

    // ---------------------------------------------------------------- events

    fun appendEvents(events: List<LoggedEvent>) {
        if (events.isEmpty()) return
        events.groupBy { MonthKeys.monthOf(it.timestamp) }.forEach { (month, monthEvents) ->
            val lines = monthEvents.sortedBy { it.timestamp }
                .joinToString(separator = "") { encode(it) + "\n" }
            runCatching { File(eventsDir, "$month.jsonl").appendText(lines, Charsets.UTF_8) }
                .onFailure { Log.w(TAG, "Could not append events for $month", it) }
        }
    }

    fun readEvents(months: Collection<String>): List<LoggedEvent> {
        val out = ArrayList<LoggedEvent>()
        months.sorted().forEach { month ->
            val file = File(eventsDir, "$month.jsonl")
            if (!file.exists()) return@forEach
            runCatching {
                file.bufferedReader(Charsets.UTF_8).useLines { lines ->
                    lines.forEach { line -> decode(line)?.let(out::add) }
                }
            }.onFailure { Log.w(TAG, "Unreadable event file $month", it) }
        }
        out.sortBy { it.timestamp }
        return out
    }

    fun eventCount(month: String): Int {
        val file = File(eventsDir, "$month.jsonl")
        if (!file.exists()) return 0
        return runCatching {
            file.bufferedReader(Charsets.UTF_8).useLines { lines -> lines.count { it.isNotBlank() } }
        }.getOrDefault(0)
    }

    private fun encode(event: LoggedEvent): String =
        JSONObject().apply {
            put("t", event.timestamp)
            put("p", event.packageName)
            put("e", event.type)
            event.className?.let { put("c", it) }
        }.toString()

    private fun decode(line: String): LoggedEvent? {
        if (line.isBlank()) return null
        return runCatching {
            val json = JSONObject(line)
            LoggedEvent(
                timestamp = json.getLong("t"),
                packageName = json.optString("p"),
                type = json.getInt("e"),
                className = if (json.has("c")) json.optString("c") else null,
            )
        }.getOrNull()
    }

    // ----------------------------------------------------------- daily totals

    /**
     * Merges freshly queried per-day totals into storage, keeping the larger of the two
     * values for each app. Merging upwards means a day that the platform has already
     * forgotten (and now reports as zero) never erases numbers already recorded.
     */
    fun mergeDailyTotals(perDay: Map<LocalDate, Map<String, Long>>) {
        if (perDay.isEmpty()) return
        perDay.entries.groupBy { MonthKeys.monthOf(it.key) }.forEach { (month, entries) ->
            val file = File(dailyDir, "$month.json")
            val existing = readJson(file)
            entries.forEach { (day, totals) ->
                val dayKey = day.toString()
                val dayJson = existing.optJSONObject(dayKey) ?: JSONObject()
                totals.forEach { (pkg, ms) ->
                    if (ms > dayJson.optLong(pkg, 0L)) dayJson.put(pkg, ms)
                }
                existing.put(dayKey, dayJson)
            }
            runCatching { file.writeText(existing.toString(), Charsets.UTF_8) }
                .onFailure { Log.w(TAG, "Could not write daily totals for $month", it) }
        }
    }

    fun readDailyTotals(months: Collection<String>): Map<LocalDate, Map<String, Long>> {
        val out = HashMap<LocalDate, Map<String, Long>>()
        months.forEach { month ->
            val json = readJson(File(dailyDir, "$month.json"))
            json.keys().forEach { dayKey ->
                val day = MonthKeys.parseDay(dayKey) ?: return@forEach
                val dayJson = json.optJSONObject(dayKey) ?: return@forEach
                val totals = HashMap<String, Long>(dayJson.length())
                dayJson.keys().forEach { pkg -> totals[pkg] = dayJson.optLong(pkg, 0L) }
                out[day] = totals
            }
        }
        return out
    }

    private fun readJson(file: File): JSONObject {
        if (!file.exists()) return JSONObject()
        return runCatching { JSONObject(file.readText(Charsets.UTF_8)) }.getOrElse { JSONObject() }
    }

    // ------------------------------------------------------------- management

    /** Months that hold data, newest first. */
    fun months(): List<String> {
        val keys = LinkedHashSet<String>()
        listOf(eventsDir, dailyDir).forEach { dir ->
            dir.listFiles()?.forEach { file ->
                val key = file.name.substringBeforeLast('.')
                if (MonthKeys.parseMonth(key) != null) keys.add(key)
            }
        }
        return keys.sortedDescending()
    }

    fun storageBytes(): Long =
        listOf(eventsDir, dailyDir).sumOf { dir -> dir.listFiles()?.sumOf { it.length() } ?: 0L }

    fun deleteMonth(month: String): Boolean {
        val events = File(eventsDir, "$month.jsonl").let { !it.exists() || it.delete() }
        val daily = File(dailyDir, "$month.json").let { !it.exists() || it.delete() }
        return events && daily
    }

    /** Deletes every month strictly older than [month]. */
    fun deleteBefore(month: String): Int =
        months().filter { it < month }.count { deleteMonth(it) }

    fun deleteAll(): Int = months().count { deleteMonth(it) }

    // ----------------------------------------------------------------- export

    /** Streams a plain-JSON copy of the requested months to [out] (used by "Export"). */
    fun export(out: OutputStream, months: List<String>) {
        out.bufferedWriter(Charsets.UTF_8).use { writer ->
            writer.write("{\"format\":\"phone-usage-tracker/1\",")
            writer.write("\"exportedAt\":${System.currentTimeMillis()},\"months\":{")
            months.forEachIndexed { index, month ->
                if (index > 0) writer.write(",")
                writer.write("\"$month\":{\"daily\":")
                writer.write(readJson(File(dailyDir, "$month.json")).toString())
                writer.write(",\"events\":[")
                val file = File(eventsDir, "$month.jsonl")
                if (file.exists()) {
                    var first = true
                    file.bufferedReader(Charsets.UTF_8).useLines { lines ->
                        lines.forEach { line ->
                            if (line.isNotBlank()) {
                                if (!first) writer.write(",")
                                writer.write(line)
                                first = false
                            }
                        }
                    }
                }
                writer.write("]}")
            }
            writer.write("}}")
        }
    }

    private companion object {
        const val TAG = "EventStore"
    }
}
