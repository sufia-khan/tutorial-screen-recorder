package com.screenrecorder.manager

import java.io.File

data class TrashedRecording(
    val fileName: String,
    val trashedAtMs: Long
)

class TrashStore(private val storeFile: File) {

    private val entries = mutableMapOf<String, Long>()

    init {
        load()
    }

    fun add(fileName: String, trashedAtMs: Long) {
        entries[fileName] = trashedAtMs
        save()
    }

    fun all(): List<TrashedRecording> =
        entries.map { (name, at) -> TrashedRecording(name, at) }
            .sortedByDescending { it.trashedAtMs }

    fun isTrashed(fileName: String): Boolean = entries.containsKey(fileName)

    fun remove(fileName: String) {
        if (entries.remove(fileName) != null) save()
    }

    fun expired(nowMs: Long): List<TrashedRecording> =
        entries.mapNotNull { (name, at) ->
            if (nowMs - at >= EXPIRY_MS) TrashedRecording(name, at) else null
        }

    fun purgeExpired(nowMs: Long, deletePrivate: (TrashedRecording) -> Unit) {
        expired(nowMs).forEach { recording ->
            deletePrivate(recording)
            remove(recording.fileName)
        }
    }

    private fun load() {
        entries.clear()
        if (!storeFile.exists()) return
        try {
            storeFile.readLines().forEach { line ->
                val parts = line.split('\t')
                if (parts.size == 2) {
                    val name = parts[0]
                    val at = parts[1].toLongOrNull()
                    if (name.isNotBlank() && at != null) {
                        entries[name] = at
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
            storeFile.writeText(entries.entries.joinToString("\n") { (name, at) -> "$name\t$at" })
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
