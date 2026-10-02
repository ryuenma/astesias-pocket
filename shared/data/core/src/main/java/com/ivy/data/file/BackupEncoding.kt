package com.ivy.data.file

import java.nio.charset.Charset

/**
 * Works out which encoding a backup file is written in, from its leading bytes.
 *
 * Ivy Wallet always wrote backups as UTF-16, so its reader hardcoded
 * `Charsets.UTF_16` and round-tripped its own format perfectly — which is exactly
 * why the limitation went unnoticed. A UTF-8 backup decoded to mojibake and then
 * failed silently (see [com.ivy.data.backup.ImportResult.errorMessage]).
 *
 * Kept as a pure function over bytes rather than a private method on [FileSystem]
 * so it can be unit-tested on the JVM: [FileSystem] needs a `Context`, so a test
 * there would require an instrumented device for what is really a byte comparison.
 */
object BackupEncoding {

    /**
     * @param mark the first up-to-3 bytes of the file; may be shorter than 3.
     * @return the charset to decode with.
     */
    fun detect(mark: ByteArray): Charset {
        if (mark.size < 2) return Charsets.UTF_8

        return when {
            // FE FF (UTF-16BE) or FF FE (UTF-16LE) -> Charsets.UTF_16, deliberately
            // NOT UTF_16BE/UTF_16LE. Java's UTF_16 consumes the BOM and auto-detects
            // endianness, whereas the explicit variants leave U+FEFF as the first
            // character and the JSON parser then rejects the document.
            //
            // Verified against a real backup that is UTF-16 *big* endian (FE FF):
            // decoding it as UTF_16BE keeps the BOM as character zero and the parse
            // fails outright.
            isUtf16BigEndian(mark) || isUtf16LittleEndian(mark) -> Charsets.UTF_16

            // UTF-8: EF BB BF. Nothing to consume here, but the decoder turns the
            // BOM into a real U+FEFF, so [stripBom] removes it afterwards.
            isUtf8Bom(mark) -> Charsets.UTF_8

            // No BOM: UTF-8 is the sane default, and is what Android itself writes.
            else -> Charsets.UTF_8
        }
    }

    /**
     * Drops a leading U+FEFF left behind by the decoder.
     *
     * A UTF-8 BOM becomes a real zero-width no-break space rather than being
     * consumed, and its presence makes an otherwise-valid JSON document unparseable.
     */
    fun stripBom(text: String): String =
        if (text.startsWith('\uFEFF')) text.substring(1) else text

    private fun isUtf16BigEndian(mark: ByteArray): Boolean =
        mark[0] == 0xFE.toByte() && mark[1] == 0xFF.toByte()

    private fun isUtf16LittleEndian(mark: ByteArray): Boolean =
        mark[0] == 0xFF.toByte() && mark[1] == 0xFE.toByte()

    private fun isUtf8Bom(mark: ByteArray): Boolean =
        mark.size >= 3 &&
            mark[0] == 0xEF.toByte() &&
            mark[1] == 0xBB.toByte() &&
            mark[2] == 0xBF.toByte()
}