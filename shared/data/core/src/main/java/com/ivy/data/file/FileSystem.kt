package com.ivy.data.file

import android.content.Context
import android.net.Uri
import arrow.core.Either
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.BufferedReader
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.PushbackInputStream
import java.nio.charset.Charset
import javax.inject.Inject

class FileSystem @Inject constructor(
    @ApplicationContext
    private val appContext: Context
) {
    private companion object {
        /** Longest BOM we sniff: UTF-16 is 2 bytes, UTF-8 is 3. */
        const val MARK_LENGTH = 3
    }

    fun writeToFile(uri: Uri, content: String): Either<Failure, Unit> = try {
        val contentResolver = appContext.contentResolver

        contentResolver.openFileDescriptor(uri, "w")?.use {
            FileOutputStream(it.fileDescriptor).use { fOut ->
                val writer = fOut.writer(charset = Charsets.UTF_16)
                writer.write(content)
                writer.close()
            }
        }
        Either.Right(Unit)
    } catch (e: FileNotFoundException) {
        Either.Left(Failure.FileNotFound(e))
    } catch (e: Exception) {
        Either.Left(Failure.IO(e))
    }

    /**
     * Writes raw bytes, for binary exports such as .xlsx.
     *
     * The String overload writes UTF-16 and is not usable for binary: a ZIP
     * container passed through a charset encoder is corrupted, and it would fail
     * as "the file will not open in Excel" rather than as an error here.
     */
    fun writeToFile(uri: Uri, content: ByteArray): Either<Failure, Unit> = try {
        val contentResolver = appContext.contentResolver

        contentResolver.openFileDescriptor(uri, "w")?.use {
            FileOutputStream(it.fileDescriptor).use { fOut ->
                fOut.write(content)
                fOut.flush()
            }
        }
        Either.Right(Unit)
    } catch (e: FileNotFoundException) {
        Either.Left(Failure.FileNotFound(e))
    } catch (e: Exception) {
        Either.Left(Failure.IO(e))
    }

    /**
     * Reads [uri] as text, detecting the encoding from a byte-order mark when one
     * is present.
     *
     * Ivy Wallet always wrote its backups as UTF-16, so its reader hardcoded
     * `Charsets.UTF_16` and round-tripped its own format correctly. That hid the
     * real constraint: any UTF-8 backup — from another tool, a hand-edited file, or
     * a future writer — decoded to mojibake and then failed *silently*. Sniffing
     * the BOM accepts both encodings, and defaults to UTF-8 (the Android norm) when
     * there is no mark to go on.
     *
     * Pass [charset] to force an encoding and skip detection (the CSV importer
     * needs this, because CSV files carry no BOM).
     */
    fun read(
        uri: Uri,
        charset: Charset? = null
    ): Either<Failure, String> {
        return try {
            val contentResolver = appContext.contentResolver
            var fileContent: String? = null

            contentResolver.openFileDescriptor(uri, "r")?.use {
                FileInputStream(it.fileDescriptor).use { fileInputStream ->
                    fileContent = readFileContent(
                        fileInputStream = fileInputStream,
                        charset = charset
                    )
                }
            }

            Either.Right(fileContent!!)
        } catch (e: FileNotFoundException) {
            Either.Left(Failure.FileNotFound(e))
        } catch (e: Exception) {
            Either.Left(Failure.IO(e))
        }
    }

    /**
     * True when a database file named [dbFileName] exists in the app's private
     * database directory.
     *
     * Room places databases in `context.getDatabasePath(name)`, so existence is
     * checked in exactly that location rather than the files dir - otherwise this
     * would report false for a profile that genuinely has data, and the setup flow
     * would wrongly offer to overwrite it.
     *
     * [dbFileName] is a bare file name, never a path: callers pass
     * [com.ivy.data.profile.ProfileId.dbFileName], which is validated to contain only
     * `a-z`, `0-9` and `_`.
     */
    fun databaseExists(dbFileName: String): Boolean =
        appContext.getDatabasePath(dbFileName).exists()

    /**
     * Detects the encoding from the leading bytes and returns a stream that still
     * contains them, so the sniffed bytes are never dropped.
     *
     * The obvious implementation - mark(3), read(mark), reset() - silently loses
     * those 3 bytes, because [FileInputStream.markSupported] is false: mark() is a
     * no-op and reset() throws, so the code must not rely on rewinding. A
     * [java.io.PushbackInputStream] is used instead, which can always push bytes
     * back regardless of the underlying stream's mark support.
     *
     * This was not theoretical. It broke every real backup: a UTF-16BE file starts
     * `FE FF 00 7B`, so consuming 3 bytes removed the BOM *and* the first content
     * byte, left the stream at an odd length, and Charsets.UTF_16 - with no BOM to
     * go on - fell back to big endian over misaligned data. The owner's 6,538-row
     * backup decoded to mojibake and the import failed. See RealBackupImportTest.
     */
    private fun detectCharsetAndRewind(fileInputStream: InputStream): Pair<Charset, InputStream> {
        val mark = ByteArray(MARK_LENGTH)
        val read = fileInputStream.read(mark)
        val bytes = if (read <= 0) ByteArray(0) else mark.copyOf(read)
        val rewound = if (read > 0) {
            PushbackInputStream(fileInputStream, MARK_LENGTH).apply { unread(mark, 0, read) }
        } else {
            fileInputStream
        }
        return BackupEncoding.detect(bytes) to rewound
    }

    @Throws(IOException::class)
    private fun readFileContent(
        fileInputStream: FileInputStream,
        charset: Charset?
    ): String {
        val (effective, stream) = if (charset != null) {
            charset to fileInputStream
        } else {
            detectCharsetAndRewind(fileInputStream)
        }

        BufferedReader(InputStreamReader(stream, effective)).use { br ->
            val sb = StringBuilder()
            var line: String?
            while (br.readLine().also { line = it } != null) {
                sb.append(line)
                sb.append('\n')
            }
            return BackupEncoding.stripBom(sb.toString())
        }
    }

    sealed interface Failure {
        val e: Throwable

        data class FileNotFound(override val e: Throwable) : Failure
        data class IO(override val e: Throwable) : Failure
    }
}
