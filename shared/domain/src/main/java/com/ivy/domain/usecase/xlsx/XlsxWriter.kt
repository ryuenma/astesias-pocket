package com.ivy.domain.usecase.xlsx

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Minimal .xlsx writer.
 *
 * An xlsx file is a ZIP of XML parts. Apache POI is the usual tool and is far
 * too heavy for Android - it drags in a whole desktop classpath for what is
 * ultimately a handful of strings. This writes the parts directly.
 *
 * Uses inline strings (`t="inlineStr"`) rather than a shared-strings table.
 * That is simpler and perfectly valid; the shared table only pays off when the
 * same string repeats thousands of times, which transaction descriptions do not.
 *
 * No formulas, no styling beyond a bold header, no multiple sheets. The owner
 * asked for a spreadsheet they can open in Excel or Sheets, and this produces
 * exactly that without pretending to be a spreadsheet library.
 */
object XlsxWriter {

    private const val CONTENT_TYPES =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
<Default Extension="xml" ContentType="application/xml"/>
<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
<Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
</Types>"""

    private const val ROOT_RELS =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>"""

    private const val WORKBOOK =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
<sheets><sheet name="Transactions" sheetId="1" r:id="rId1"/></sheets>
</workbook>"""

    private const val WORKBOOK_RELS =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
<Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
</Relationships>"""

    /** One bold cell style and one plain; s="1" marks the header row. */
    private const val STYLES =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
<fonts count="2"><font/><font><b/></font></fonts>
<cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
<cellXfs count="2"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/><xf numFmtId="0" fontId="1" fillId="0" borderId="0" xfId="0" applyFont="1"/></cellXfs>
</styleSheet>"""

    /**
     * @param rows first row is treated as the header and rendered bold.
     * @param sheetName must not contain [\/:*?[] or exceed 31 characters;
     *   Excel silently refuses the file otherwise, so it is clamped here rather
     *   than producing a corrupt download.
     */
    fun write(rows: List<List<String?>>, sheetName: String = "Transactions"): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.put("[Content_Types].xml", CONTENT_TYPES)
            zip.put("_rels/.rels", ROOT_RELS)
            zip.put("xl/workbook.xml", WORKBOOK.replace("Transactions", sheetName.safeSheetName()))
            zip.put("xl/_rels/workbook.xml.rels", WORKBOOK_RELS)
            zip.put("xl/styles.xml", STYLES)
            zip.put("xl/worksheets/sheet1.xml", sheet(rows))
        }
        return out.toByteArray()
    }

    private fun String.safeSheetName(): String {
        val cleaned = replace(Regex("[\\\\/:*?\\[\\]]"), " ").trim().take(31)
        return cleaned.ifBlank { "Sheet1" }
    }

    private fun ZipOutputStream.put(name: String, content: String) {
        putNextEntry(ZipEntry(name))
        write(content.toByteArray(Charsets.UTF_8))
        closeEntry()
    }

    private fun sheet(rows: List<List<String?>>): String {
        val sb = StringBuilder(rows.size * 64)
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sb.append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>""")
        rows.forEachIndexed { rowIndex, row ->
            sb.append("""<row r="${rowIndex + 1}">""")
            row.forEachIndexed { colIndex, cell ->
                sb.append(cell(rowIndex, colIndex, cell))
            }
            sb.append("</row>")
        }
        sb.append("</sheetData></worksheet>")
        return sb.toString()
    }

    private fun cell(rowIndex: Int, colIndex: Int, value: String?): String {
        val ref = columnName(colIndex) + (rowIndex + 1)
        val style = if (rowIndex == 0) """ s="1"""" else ""
        val text = value.orEmpty()
        if (text.isEmpty()) return """<c r="$ref"$style/>"""
        // Excel's 32767-character cell limit; exceeding it corrupts the file
        // rather than truncating, so cut it here where it is at least visible.
        val safe = if (text.length > 32767) text.take(32767) else text
        // No formula guard is needed. t="inlineStr" means the content is literal
        // text in the XML and Excel never parses it as a formula. The usual
        // leading-apostrophe trick would put a visible ' in front of every such
        // value, which is worse than the problem it solves.
        return """<c r="$ref"$style t="inlineStr"><is><t xml:space="preserve">${safe.escapeXml()}</t></is></c>"""
    }

    /** 0 -> A, 25 -> Z, 26 -> AA. Excel caps a sheet at 16384 columns. */
    private fun columnName(index: Int): String {
        var i = index
        val sb = StringBuilder()
        while (i >= 0) {
            sb.insert(0, ('A' + (i % 26)))
            i = (i / 26) - 1
        }
        return sb.toString()
    }

    private fun String.escapeXml(): String = buildString(length + 16) {
        for (c in this@escapeXml) {
            when {
                c == '&' -> append("&amp;")
                c == '<' -> append("&lt;")
                c == '>' -> append("&gt;")
                c == '"' -> append("&quot;")
                c == '\'' -> append("&apos;")
                // XML 1.0 forbids most control characters outright. Stripping
                // them is better than emitting a file Excel refuses to open.
                c.code < 0x20 && c != '\t' && c != '\n' && c != '\r' -> Unit
                else -> append(c)
            }
        }
    }
}