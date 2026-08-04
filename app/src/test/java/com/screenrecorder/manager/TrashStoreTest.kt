package com.screenrecorder.manager

import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrashStoreTest {

    private val tempDirs = mutableListOf<File>()

    @After
    fun cleanup() {
        tempDirs.forEach { it.deleteRecursively() }
    }

    private fun storeFile(name: String): File {
        val dir = File(System.getProperty("java.io.tmpdir"), "trash-store-test-$name-${System.nanoTime()}")
        dir.mkdirs()
        tempDirs.add(dir)
        return File(dir, "trash.json")
    }

    private fun store(name: String) = TrashStore(storeFile(name))

    private val now = 1_000_000_000_000L
    private val hour = 3_600_000L

    @Test
    fun addThenList_roundTrip() {
        val s = store("roundtrip")
        s.add("/sessions/uuid1/recording.mp4", "Recording \u2022 Aug 1, 3:04 PM", now)

        assertEquals(1, s.all().size)
        assertEquals("/sessions/uuid1/recording.mp4", s.all()[0].path)
        assertEquals("Recording \u2022 Aug 1, 3:04 PM", s.all()[0].displayName)
        assertEquals(now, s.all()[0].trashedAtMs)
    }

    @Test
    fun persistsAcrossInstances() {
        val file = storeFile("persist")
        TrashStore(file).add("/movies/ScreenRecord_A.mp4", "A", now)

        val reloaded = TrashStore(file)

        assertTrue(reloaded.isTrashed("/movies/ScreenRecord_A.mp4"))
        assertEquals(now, reloaded.all()[0].trashedAtMs)
    }

    @Test
    fun remove_restores() {
        val s = store("remove")
        s.add("/movies/A.mp4", "A", now)

        s.remove("/movies/A.mp4")

        assertFalse(s.isTrashed("/movies/A.mp4"))
        assertTrue(s.all().isEmpty())
    }

    @Test
    fun isTrashed_unknownPath_false() {
        val s = store("unknown")

        assertFalse(s.isTrashed("/movies/Nope.mp4"))
    }

    @Test
    fun sameFileName_inDifferentDirs_trashIndependently() {
        val s = store("same-name")
        s.add("/sessions/uuid1/recording.mp4", "A", now)

        assertTrue(s.isTrashed("/sessions/uuid1/recording.mp4"))
        assertFalse(s.isTrashed("/sessions/uuid2/recording.mp4"))
    }

    @Test
    fun expired_olderThan24h_reported() {
        val s = store("expired")
        val twoDaysAgo = now - 48 * hour
        s.add("/movies/Old.mp4", "Old", twoDaysAgo)
        s.add("/movies/Fresh.mp4", "Fresh", now)

        val expired = s.expired(now)

        assertEquals(listOf("/movies/Old.mp4"), expired.map { it.path })
    }

    @Test
    fun notExpired_justUnder24h() {
        val s = store("young")
        val under24h = now - (24 * hour - 1000L)
        s.add("/movies/Young.mp4", "Young", under24h)

        assertTrue(s.expired(now).isEmpty())
    }

    @Test
    fun purgeExpired_deletesPrivateAndRemovesEntry() {
        val s = store("purge")
        s.add("/movies/Gone.mp4", "Gone", now - 25 * hour)
        s.add("/movies/Stay.mp4", "Stay", now)
        val deleted = mutableListOf<String>()

        s.purgeExpired(now) { deleted.add(it.path) }

        assertEquals(listOf("/movies/Gone.mp4"), deleted)
        assertFalse(s.isTrashed("/movies/Gone.mp4"))
        assertTrue(s.isTrashed("/movies/Stay.mp4"))
    }

    @Test
    fun oldNameKeyedFormat_isIgnored() {
        val file = storeFile("legacy")
        file.writeText("ScreenRecord_20260801_100000.mp4\t$now")

        val s = TrashStore(file)

        assertTrue(s.all().isEmpty())
        assertFalse(s.isTrashed("ScreenRecord_20260801_100000.mp4"))
    }

    @Test
    fun timeLeftLabel_hours() {
        assertEquals("24 hrs left", TrashStore.timeLeftLabel(now, now))
        assertEquals("23 hrs left", TrashStore.timeLeftLabel(now - hour, now))
        assertEquals("1 hr left", TrashStore.timeLeftLabel(now - 23 * hour, now))
    }

    @Test
    fun timeLeftLabel_minutes() {
        assertEquals("59 min left", TrashStore.timeLeftLabel(now - 23 * hour - 60_000L, now))
        assertEquals("1 min left", TrashStore.timeLeftLabel(now - 23 * hour - 59 * 60_000L, now))
    }

    @Test
    fun timeLeftLabel_lessThanMinute() {
        assertEquals("Less than a minute", TrashStore.timeLeftLabel(now - 24 * hour + 30_000L, now))
    }

    @Test
    fun timeLeftLabel_expired() {
        assertEquals("0 min left", TrashStore.timeLeftLabel(now - 25 * hour, now))
    }

    @Test
    fun corruptedFile_loadsAsEmpty() {
        val file = storeFile("corrupt")
        file.writeText("garbage\nnot\ta\tvalid\tline\u0000\u0001\u0002")

        val s = TrashStore(file)

        assertTrue(s.all().isEmpty())
        assertFalse(s.isTrashed("anything.mp4"))
    }

    @Test
    fun missingFile_loadsAsEmpty() {
        val s = TrashStore(File(storeFile("missing").parentFile, "does-not-exist.json"))

        assertTrue(s.all().isEmpty())
    }
}