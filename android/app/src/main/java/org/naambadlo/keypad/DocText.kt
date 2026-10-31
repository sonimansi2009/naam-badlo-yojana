package org.naambadlo.keypad

import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

/** Reads text out of the files people are likely to have: .txt, .md, .srt, .csv and Word .docx. */
object DocText {
    const val MAX_BYTES = 5 * 1024 * 1024

    sealed class Result {
        data class Ok(val text: String) : Result()
        data class Error(val message: String) : Result()
    }

    fun read(name: String, mime: String?, bytes: ByteArray): Result {
        val lower = name.lowercase()
        return when {
            bytes.size > MAX_BYTES -> Result.Error("That file is over 5 MB. Try a smaller part of it.")
            lower.endsWith(".docx") || mime == "application/vnd.openxmlformats-officedocument.wordprocessingml.document" ->
                docx(bytes)?.let { Result.Ok(it) } ?: Result.Error("Couldn't read that Word file. Save it as .docx or .txt and try again.")
            lower.endsWith(".pdf") || mime == "application/pdf" ->
                Result.Error("PDFs aren't supported yet. Copy the text from the PDF and paste it here.")
            lower.endsWith(".doc") ->
                Result.Error("Old .doc files aren't supported. Save it as .docx or .txt and try again.")
            else -> Result.Ok(plain(bytes))
        }
    }

    private fun plain(bytes: ByteArray): String {
        var s = String(bytes, Charsets.UTF_8)
        if (s.startsWith("﻿")) s = s.substring(1)
        return s.replace("\r\n", "\n")
    }

    /** Text of word/document.xml, keeping paragraphs, line breaks and tabs. */
    fun docx(bytes: ByteArray): String? = runCatching {
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var e = zip.nextEntry
            while (e != null) {
                if (e.name == "word/document.xml") {
                    val xml = zip.readBytes().toString(Charsets.UTF_8)
                    return@use xmlToText(xml)
                }
                e = zip.nextEntry
            }
            null
        }
    }.getOrNull()

    fun xmlToText(xml: String): String {
        val withBreaks = xml
            .replace("\r", "").replace("\n", "")          // the file's own line breaks mean nothing
            .replace(Regex("<w:tabs>.*?</w:tabs>"), "")    // tab-stop settings, not real tabs
            .replace(Regex("</w:p>"), "\n")
            .replace(Regex("<w:br[^>]*/>"), "\n")
            .replace(Regex("<w:tab[^>]*/>"), "\t")
        val sb = StringBuilder()
        // keep only the text inside <w:t ...>...</w:t>, plus the breaks we just added
        val re = Regex("<w:t(?:\\s[^>]*)?>([^<]*)</w:t>|(\n)|(\t)")
        for (m in re.findAll(withBreaks)) {
            when {
                m.groups[1] != null -> sb.append(unescape(m.groupValues[1]))
                m.groups[2] != null -> sb.append('\n')
                m.groups[3] != null -> sb.append('\t')
            }
        }
        return sb.toString().trim() + "\n"
    }

    private fun unescape(s: String) = s
        .replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
        .replace("&apos;", "'").replace("&amp;", "&")
}
