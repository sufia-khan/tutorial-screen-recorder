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
        s.add("ScreenRecord_20260801_100000.mp4", now)

        assertEquals(1, s.all().size)
        assertEquals("ScreenRecord_20260801_100000.mp4", s.all()[0].fileName)
        assertEquals(now, s.all()[0].trashedAtMs)
    }

    @Test
    fun persistsAcrossInstances() {
        val file = storeFile("persist")
        TrashStore(file).add("A.mp4", now)

        val reloaded = TrashStore(file)

        assertTrue(reloaded.isTrashed("A.mp4"))
        assertEquals(now, reloaded.all()[0].trashedAtMs)
    }

    @Test
    fun remove_restores() {
        val s = store("remove")
        s.add("A.mp4", now)

        s.remove("A.mp4")

        assertFalse(s.isTrashed("A.mp4"))
        assertTrue(s.all().isEmpty())
    }

    @Test
    fun isTrashed_unknownFile_false() {
        val s = store("unknown")

        assertFalse(s.isTrashed("Nope.mp4"))
    }

    @Test
    fun expired_olderThan24h_reported() {
        val s = store("expired")
        val twoDaysAgo = now - 48 * hour
        s.add("Old.mp4", twoDaysAgo)
        s.add("Fresh.mp4", now)

        val expired = s.expired(now)

        assertEquals(listOf("Old.mp4"), expired.map { it.fileName })
    }

    @Test
    fun notExpired_justUnder24h() {
        val s = store("young")
        val under24h = now - (24 * hour - 1000L)
        s.add("Young.mp4", under24h)

        assertTrue(s.expired(now).isEmpty())
    }

    @Test
    fun purgeExpired_deletesPrivateAndRemovesEntry() {
        val s = store("purge")
        s.add("Gone.mp4", now - 25 * hour)
        s.add("Stay.mp4", now)
        val deleted = mutableListOf<String>()

        s.purgeExpired(now) { deleted.add(it.fileName) }

        assertEquals(listOf("Gone.mp4"), deleted)
        assertFalse(s.isTrashed("Gone.mp4"))
        assertTrue(s.isTrashed("Stay.mp4"))
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
