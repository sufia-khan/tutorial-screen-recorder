package com.screenrecorder.zoom

import android.content.Context
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class ZoomSegmentStore(
    private val file: File,
    private val legacyFile: File? = null
) {

    val sidecarFile: File get() = file

    fun load(): List<ZoomSegment> {
        read(file)?.let { return it }
        val legacy = legacyFile
        if (legacy != null && legacy != file && legacy.exists()) {
            read(legacy)?.let { return it }
        }
        return emptyList()
    }

    fun save(segments: List<ZoomSegment>) {
        val array = JSONArray()
        segments.forEach { array.put(segmentToJson(it)) }
        val root = JSONObject()
        root.put(KEY_VERSION, VERSION)
        root.put(KEY_SEGMENTS, array)
        runCatching { writeAtomically(root.toString()) }
    }

    private fun read(file: File): List<ZoomSegment>? {
        if (!file.exists()) return null
        return try {
            val root = JSONObject(file.readText())
            val array = root.optJSONArray(KEY_SEGMENTS) ?: return emptyList()
            buildList {
                for (i in 0 until array.length()) {
                    array.optJSONObject(i)?.let { parseSegment(it) }?.let { add(it) }
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun parseSegment(obj: JSONObject): ZoomSegment? = try {
        ZoomSegment(
            id = obj.getString(KEY_ID),
            startMs = obj.getLong(KEY_START_MS),
            endMs = obj.getLong(KEY_END_MS),
            scale = obj.getDouble(KEY_SCALE).toFloat(),
            centerX = obj.getDouble(KEY_CENTER_X).toFloat(),
            centerY = obj.getDouble(KEY_CENTER_Y).toFloat()
        )
    } catch (e: JSONException) {
        null
    }

    private fun segmentToJson(segment: ZoomSegment): JSONObject = JSONObject()
        .put(KEY_ID, segment.id)
        .put(KEY_START_MS, segment.startMs)
        .put(KEY_END_MS, segment.endMs)
        .put(KEY_SCALE, segment.scale.toDouble())
        .put(KEY_CENTER_X, segment.centerX.toDouble())
        .put(KEY_CENTER_Y, segment.centerY.toDouble())

    private fun writeAtomically(content: String) {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile ?: File("."), file.name + ".tmp")
        tmp.writeText(content)
        Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }

    companion object {
        private const val VERSION = 1
        private const val KEY_VERSION = "version"
        private const val KEY_SEGMENTS = "segments"
        private const val KEY_ID = "id"
        private const val KEY_START_MS = "startMs"
        private const val KEY_END_MS = "endMs"
        private const val KEY_SCALE = "scale"
        private const val KEY_CENTER_X = "centerX"
        private const val KEY_CENTER_Y = "centerY"

        fun sidecarFileFor(videoFile: File): File {
            val name = videoFile.name.substringBeforeLast('.', videoFile.name)
            val parent = videoFile.parentFile
            return if (parent != null) File(parent, "$name.zoom.json") else File("$name.zoom.json")
        }

        fun forVideo(videoFile: File): ZoomSegmentStore =
            ZoomSegmentStore(sidecarFileFor(videoFile))

        fun forVideo(context: Context, videoFile: File): ZoomSegmentStore {
            val dir = File(context.filesDir, "zoom_sidecars")
            val name = videoFile.name.substringBeforeLast('.', videoFile.name)
            return ZoomSegmentStore(
                file = File(dir, "$name.zoom.json"),
                legacyFile = sidecarFileFor(videoFile)
            )
        }
    }
}
