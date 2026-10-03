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
import java.io.InputStreamReader
import java.nio.charset.Charset
import javax.inject.Inject

class FileSystem @Inject constructor(
    @ApplicationContext
    private val appContext: Context
) {
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
     * Peeks at the leading bytes and delegates the decision to [BackupEncoding].
     *
     * InputStream.mark() returns void, so there is no success flag to check;
     * markSupported() is what says whether reset() will actually rewind.
     */
    private fun detectCharset(fileInputStream: FileInputStream): Charset {
        val mark = ByteArray(3)
        val markSupported = fileInputStream.markSupported()
        fileInputStream.mark(3)
        val read = fileInputStream.read(mark)
        if (markSupported) fileInputStream.reset()

        val bytes = if (read <= 0) ByteArray(0) else mark.copyOf(read)
        return BackupEncoding.detect(bytes)
    }

    @Throws(IOException::class)
    private fun readFileContent(
        fileInputStream: FileInputStream,
        charset: Charset?
    ): String {
        val effective = charset ?: detectCharset(fileInputStream)
        BufferedReader(InputStreamReader(fileInputStream, effective)).use { br ->
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
