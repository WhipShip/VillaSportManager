package com.example.villasportmanager

import com.example.villasportmanager.util.IntegrityException
import com.example.villasportmanager.util.UpdateIntegrity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class UpdateIntegrityTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val abcSha = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"

    @Test
    fun sha256OfKnownContent() {
        val f = tmp.newFile().apply { writeText("abc") }
        assertEquals(abcSha, UpdateIntegrity.sha256Hex(f))
    }

    @Test
    fun normalizeAcceptsPrefixCaseAndWhitespace() {
        assertEquals(abcSha, UpdateIntegrity.normalize("  sha256:${abcSha.uppercase()} \n"))
        assertNull(UpdateIntegrity.normalize(null))
        assertNull(UpdateIntegrity.normalize("   "))
    }

    @Test
    fun malformedPublishedHashFailsLoudly() {
        assertThrows(IntegrityException::class.java) { UpdateIntegrity.normalize("deadbeef") }
    }

    @Test
    fun verifyPassesWhenHashAndSizeMatch() {
        val f = tmp.newFile().apply { writeText("abc") }
        UpdateIntegrity.verifyFile(f, abcSha, 3, "test")
        UpdateIntegrity.verifyFile(f, null, null, "test") // nothing published: nothing to check
    }

    @Test
    fun verifyFailsOnWrongHash() {
        val f = tmp.newFile().apply { writeText("abd") }
        val e = assertThrows(IntegrityException::class.java) { UpdateIntegrity.verifyFile(f, abcSha, null, "Downloaded file") }
        assertTrue(e.message!!.contains("hash mismatch"))
    }

    @Test
    fun verifyFailsOnTruncation() {
        val f = tmp.newFile().apply { writeText("ab") }
        val e = assertThrows(IntegrityException::class.java) { UpdateIntegrity.verifyFile(f, null, 3, "Downloaded file") }
        assertTrue(e.message!!.contains("2 bytes, expected 3"))
    }

    @Test
    fun verifyFailsOnMissingFile() {
        assertThrows(IntegrityException::class.java) { UpdateIntegrity.verifyFile(tmp.root.resolve("nope"), null, null, "x") }
    }

    @Test
    fun sealReplacesTargetAndRemovesPart() {
        val part = tmp.newFile("update.apk.part").apply { writeText("new") }
        val target = tmp.newFile("update.apk").apply { writeText("old-and-maybe-truncated") }
        UpdateIntegrity.seal(part, target)
        assertFalse(part.exists())
        assertEquals("new", target.readText())
    }
}
