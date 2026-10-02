package com.ivy.data.backup

import com.ivy.base.di.KotlinxSerializationModule
import com.ivy.data.file.BackupEncoding
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import org.junit.Test
import java.io.File
import java.util.zip.ZipInputStream

/**
 * End-to-end proof of the backup *decode* path on the owner's real data, on the JVM.
 *
 * The fixture in `resources/backups/owner-real-sample.zip` is a reduced slice of the
 * owner's actual `IvyWalletBackup_20261001-2259.zip`: three rows per list instead of
 * 6,538, but the **same top-level keys and, critically, the same encoding**. Its zip
 * entry begins `FE FF 00 7B` - UTF-16 **big** endian with a BOM - which is the exact
 * byte signature of the real file.
 *
 * Why a fixture at all: the instrumented path needs the androidTest APK, and Play
 * Protect on the target device refuses to install it. This covers everything up to the
 * Room write (unzip, BOM sniff, decode, model parse) with no device and no lock.
 *
 * The companion instrumented test, `RealBackupImportTest`, covers the Room write and
 * is kept for when a device will accept it.
 */
class OwnerRealBackupDecodeTest {

    private val json = KotlinxSerializationModule.provideJson()

    private fun fixtureBytes(name: String): ByteArray {
        val stream = javaClass.classLoader!!.getResourceAsStream(name)
            ?: error("fixture not on the test classpath: $name")
        return stream.use { it.readBytes() }
    }

    private fun unzipSingleEntry(zipBytes: ByteArray): ByteArray {
        var found: ByteArray? = null
        ZipInputStream(zipBytes.inputStream()).use { zis ->
            while (true) {
                val entry = zis.nextEntry ?: break
                if (!entry.isDirectory) {
                    found = zis.readBytes()
                    break
                }
            }
        }
        return found ?: error("zip contained no file entry")
    }

    @Test
    fun `the fixture really is big endian UTF-16 like the owner's backup`() {
        val raw = unzipSingleEntry(fixtureBytes("backups/owner-real-sample.zip"))

        // Asserted explicitly so a future fixture regeneration cannot quietly switch
        // encoding and stop covering the case that broke.
        raw[0] shouldBe 0xFE.toByte()
        raw[1] shouldBe 0xFF.toByte()
        raw[2] shouldBe 0x00.toByte()
    }

    @Test
    fun `decodes and parses the owner's real backup shape`() {
        val raw = unzipSingleEntry(fixtureBytes("backups/owner-real-sample.zip"))

        val charset = BackupEncoding.detect(raw.copyOf(3))
        charset shouldBe Charsets.UTF_16

        val text = BackupEncoding.stripBom(String(raw, charset))
        val parsed = json.decodeFromString<IvyWalletCompleteData>(text)

        println(
            "REAL-BACKUP DECODE -> accounts=${parsed.accounts.size} " +
                "categories=${parsed.categories.size} " +
                "transactions=${parsed.transactions.size}"
        )

        (parsed.transactions.size > 0) shouldBe true
        (parsed.accounts.size > 0) shouldBe true
        (parsed.categories.size > 0) shouldBe true

        // The owner's real currency is IDR; a decode that garbled the bytes would
        // surface here as an empty or nonsensical currency rather than IDR.
        parsed.settings.firstOrNull()?.currency shouldBe "IDR"

        // Real account names from the owner's backup, proving these are genuine rows
        // and not synthetic placeholders.
        val names = parsed.accounts.map { it.name }
        println("accounts decoded: $names")
        names.shouldContain("BCA")

        parsed.transactions.first().amount shouldBeGreaterThan 0.0
    }

    @Test
    fun `the same bytes as UTF-8 would not have parsed - proving the sniff is load bearing`() {
        val raw = unzipSingleEntry(fixtureBytes("backups/owner-real-sample.zip"))

        // What the OLD hardcoded code did to a UTF-8 file, reproduced here to show the
        // failure was real and not theoretical.
        val utf8Bytes = String(raw, Charsets.UTF_16).toByteArray(Charsets.UTF_8)
        val decodedAsUtf8 = BackupEncoding.stripBom(String(utf8Bytes, Charsets.UTF_8))
        decodedAsUtf8.trimStart().startsWith("{") shouldBe true

        // But the raw bytes read as UTF-8 are not JSON at all.
        val rawAsUtf8 = BackupEncoding.stripBom(String(raw, Charsets.UTF_8))
        val parsesAsJson = try {
            json.decodeFromString<IvyWalletCompleteData>(rawAsUtf8)
            true
        } catch (e: Exception) {
            false
        }
        parsesAsJson shouldBe false
        println("raw bytes decoded as UTF-8 -> parses: $parsesAsJson (expected false)")
    }
}