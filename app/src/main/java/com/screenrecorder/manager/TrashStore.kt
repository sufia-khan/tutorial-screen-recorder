package com.screenrecorder.manager

import java.io.File

data class TrashedRecording(
    val path: String,
    val displayName: String,
    val trashedAtMs: Long
)

class TrashStore(private val storeFile: File) {

    private data class Entry(val displayName: String, val trashedAtMs: Long)

    private val entries = mutableMapOf<String, Entry>()

    init {
        load()
    }

    fun add(path: String, displayName: String, trashedAtMs: Long) {
        entries[path] = Entry(displayName, trashedAtMs)
        save()
    }

    fun all(): List<TrashedRecording> =
        entries.map { (path, entry) -> TrashedRecording(path, entry.displayName, entry.trashedAtMs) }
            .sortedByDescending { it.trashedAtMs }

    fun isTrashed(path: String): Boolean = entries.containsKey(path)

    fun remove(path: String) {
        if (entries.remove(path) != null) save()
    }

    fun expired(nowMs: Long): List<TrashedRecording> =
        entries.mapNotNull { (path, entry) ->
            if (nowMs - entry.trashedAtMs >= EXPIRY_MS) {
                TrashedRecording(path, entry.displayName, entry.trashedAtMs)
            } else {
                null
            }
        }

    fun purgeExpired(nowMs: Long, deletePrivate: (TrashedRecording) -> Unit) {
        expired(nowMs).forEach { recording ->
            deletePrivate(recording)
            remove(recording.path)
        }
    }

    private fun load() {
        entries.clear()
        if (!storeFile.exists()) return
        try {
            storeFile.readLines().forEach { line ->
                val parts = line.split('\t')
                if (parts.size == 3) {
                    val path = parts[0]
                    val displayName = parts[1]
                    val at = parts[2].toLongOrNull()
                    if (path.isNotBlank() && displayName.isNotBlank() && at != null) {
                        entries[path] = Entry(displayName, at)
                    }
                }
            }
        } catch (e: Exception) {
            entries.clear()
        }
    }

    private fun save() {
        try {
            storeFile.parentFile?.mkdirs()
            storeFile.writeText(entries.entries.joinToString("\n") { (path, entry) ->
                "$path\t${entry.displayName}\t${entry.trashedAtMs}"
            })
        } catch (e: Exception) {
            // never crash on a failed save; trash stays in memory for this session
        }
    }

    companion object {
        const val EXPIRY_MS = 24L * 60L * 60L * 1000L

        fun timeLeftLabel(trashedAtMs: Long, nowMs: Long): String {
            val remainingMs = EXPIRY_MS - (nowMs - trashedAtMs)
            if (remainingMs <= 0) return "0 min left"
            val hours = remainingMs / 3_600_000L
            if (hours >= 1) return if (hours == 1L) "1 hr left" else "$hours hrs left"
            val minutes = remainingMs / 60_000L
            if (minutes < 1) return "Less than a minute"
            return if (minutes == 1L) "1 min left" else "$minutes min left"
        }
    }
}
