package com.ivy.data.file

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the backup-import encoding fix.
 *
 * Ivy Wallet hardcoded `Charsets.UTF_16` when reading backups. That worked only
 * because Ivy also wrote UTF-16, so the reader and writer agreed and the limitation
 * never surfaced. Anything else - a UTF-8 backup from another tool, or a hand-edited
 * file - decoded to mojibake and then failed *silently*, reporting "0 imported" with
 * no reason given.
 *
 * These tests pin the byte patterns that actually occur, including the one from the
 * owner's real backup: UTF-16 **big** endian, BOM FE FF. That case is the reason
 * [BackupEncoding.detect] returns `Charsets.UTF_16` rather than `UTF_16BE` - the
 * explicit variant leaves U+FEFF as the first character and the JSON parse fails.
 */
class BackupEncodingTest {

    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

    @Test
    fun `detects UTF-16 big endian from the owner's real backup`() {
        // The real IvyWalletBackup_20261001-2259.zip entry starts FE FF 00 7B.
        val realBackupStart = bytes(0xFE, 0xFF, 0x00)

        assertEquals(Charsets.UTF_16, BackupEncoding.detect(realBackupStart))
    }

    @Test
    fun `detects UTF-16 little endian`() {
        assertEquals(Charsets.UTF_16, BackupEncoding.detect(bytes(0xFF, 0xFE, 0x7B)))
    }

    @Test
    fun `detects UTF-8 from a BOM`() {
        assertEquals(Charsets.UTF_8, BackupEncoding.detect(bytes(0xEF, 0xBB, 0xBF)))
    }

    @Test
    fun `defaults to UTF-8 with no BOM`() {
        assertEquals(Charsets.UTF_8, BackupEncoding.detect(bytes(0x7B, 0x22)))
    }

    @Test
    fun `defaults to UTF-8 for a file too short to carry a BOM`() {
        assertEquals(Charsets.UTF_8, BackupEncoding.detect(ByteArray(0)))
        assertEquals(Charsets.UTF_8, BackupEncoding.detect(bytes(0x7B)))
    }

    @Test
    fun `UTF-16 big endian JSON parses - the regression that would have broken import`() {
        val json = """{"accounts":[],"transactions":[]}"""
        val asUtf16Be = json.toByteArray(Charsets.UTF_16BE)
        val withBom = bytes(0xFE, 0xFF) + asUtf16Be

        val charset = BackupEncoding.detect(withBom.copyOf(3))

        // UTF_16BE on its own keeps the BOM as character zero and fails to parse.
        val wrongWay = String(withBom, Charsets.UTF_16BE)
        assertTrue(
            "decoding as UTF_16BE should leave the BOM in place",
            wrongWay.startsWith("\uFEFF")
        )

        // The chosen charset must actually work once the text is cleaned up.
        val text = BackupEncoding.stripBom(withBom.toString(charset))
        val parsed = Json.parseToJsonElement(text).jsonObject

        assertTrue(parsed.containsKey("transactions"))
    }

    @Test
    fun `stripBom removes a leading zero width no break space`() {
        assertEquals("{}", BackupEncoding.stripBom("\uFEFF{}"))
        assertEquals("{}", BackupEncoding.stripBom("{}"))
    }

    @Test
    fun `stripBom leaves a BOM-like character that is not at the start alone`() {
        assertEquals("a\uFEFFb", BackupEncoding.stripBom("a\uFEFFb"))
    }
}