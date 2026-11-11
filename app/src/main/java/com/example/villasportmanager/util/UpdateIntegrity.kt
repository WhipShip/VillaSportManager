package com.example.villasportmanager.util

import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

/** A downloaded or generated file does not match what the server said it should be. */
class IntegrityException(message: String) : Exception(message)

/**
 * Pure-JVM helpers for the update pipeline (unit-tested in UpdateIntegrityTest).
 *
 * Contract: every file that ends up handed to the package installer went through
 * `verifyFile` (when the server published a hash/size) and `seal` (so a half-written file can
 * never carry the final name).
 */
object UpdateIntegrity {

    private val HEX64 = Regex("^[0-9a-f]{64}$")

    fun sha256Hex(file: File): String = file.inputStream().use { sha256Hex(it) }

    fun sha256Hex(input: InputStream): String {
        val md = MessageDigest.getInstance("SHA-256")
        val buf = ByteArray(64 * 1024)
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            md.update(buf, 0, n)
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * Normalises a hash as published in the DB: trims, drops an optional `sha256:` prefix, lower-cases.
     * Returns null when nothing was published (null/blank). A published value that is not 64 hex
     * characters is an operator error and fails loudly rather than silently disabling the check.
     */
    fun normalize(expected: String?): String? {
        val cleaned = expected?.trim()?.removePrefix("sha256:")?.lowercase() ?: return null
        if (cleaned.isEmpty()) return null
        if (!HEX64.matches(cleaned)) throw IntegrityException("Published hash is not a 64-character SHA-256: '$expected'")
        return cleaned
    }

    /**
     * Verifies [file] against what the server published. Either expectation may be absent (null),
     * in which case that part of the check is skipped.
     */
    fun verifyFile(file: File, expectedSha256: String?, expectedSize: Long?, what: String) {
        if (!file.isFile) throw IntegrityException("$what is missing")
        if (expectedSize != null && expectedSize > 0 && file.length() != expectedSize) {
            throw IntegrityException("$what has ${file.length()} bytes, expected $expectedSize")
        }
        val expected = normalize(expectedSha256) ?: return
        val actual = sha256Hex(file)
        if (actual != expected) throw IntegrityException("$what hash mismatch: expected $expected, got $actual")
    }

    /** Atomically moves a fully written [part] file over [target]. */
    fun seal(part: File, target: File) {
        Files.move(part.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    }
}
