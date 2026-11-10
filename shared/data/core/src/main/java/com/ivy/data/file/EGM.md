# com.ivy.data.file — content-provider file I/O

## Overview

Reading and writing user-visible files through `ContentResolver` URIs (SAF), with BOM-aware decoding
and `Either`-returning failures. `BackupEncoding` holds the encoding decision as a pure function so it
can be unit-tested on the JVM.

## Interface

- `class FileSystem @Inject constructor(@ApplicationContext Context)`
  - `fun writeToFile(uri: Uri, content: String): Either<Failure, Unit>` — writes **UTF-16**.
  - `fun writeToFile(uri: Uri, content: ByteArray): Either<Failure, Unit>` — writes raw bytes.
  - `fun read(uri: Uri, charset: Charset? = null): Either<Failure, String>` — BOM-sniffing read.
  - `sealed class/interface Failure` with `FileNotFound(Throwable)` and `IO(Exception)`.
- `object BackupEncoding` — `fun detect(mark: ByteArray): Charset`, `fun stripBom(text: String): String`.

## Invariants

- **The String overload writes UTF-16; it must never be used for binary.** A ZIP container passed
  through a charset encoder is corrupted, and the failure surfaces as "the file will not open in Excel"
  rather than as an error here. `byte[]` output (XLSX) goes through the `ByteArray` overload.
- UTF-16 BOMs map to `Charsets.UTF_16`, deliberately **not** `UTF_16BE`/`UTF_16LE`: Java's `UTF_16`
  consumes the BOM and auto-detects endianness, whereas the explicit variants leave `U+FEFF` as the
  first character and the JSON parser then rejects the document.
- No BOM defaults to **UTF-8**, which accepts backups from other tools and hand-edited files. The old
  hardcoded `UTF_16` reader turned those into silent mojibake.
- `stripBom` must run after decoding a UTF-8 BOM, which becomes a real `U+FEFF`; without it an
  otherwise-valid JSON document is unparseable.
- Pass `charset` explicitly for CSV import: CSV files carry no BOM, so detection would assume UTF-8 and
  a legacy UTF-16 CSV would decode wrong.
- All failures are `Either.Left`; nothing throws out of `FileSystem`.

## Side Effects

- Opens content-provider file descriptors for write (`"w"`) and read, creating/truncating the target.
- Throws internally on `FileNotFoundException`/`IOException` and converts them to `Failure` values.

## Verification

- Run: `./gradlew :shared:data:core:testDebugUnitTest`
