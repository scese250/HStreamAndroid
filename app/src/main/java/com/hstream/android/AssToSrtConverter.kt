package com.hstream.android

object AssToSrtConverter {

    fun convert(assContent: String): String {
        val lines = assContent.lines()
        val eventsStart = lines.indexOfFirst { it.trim().equals("[Events]", ignoreCase = true) }
        if (eventsStart == -1) return ""

        var formatLine = ""
        for (i in eventsStart + 1 until lines.size) {
            val l = lines[i].trim()
            if (l.startsWith("Format:", ignoreCase = true)) { formatLine = l; break }
            if (l.startsWith("[")) break
        }
        if (formatLine.isEmpty()) return ""

        val formatParts = formatLine.removePrefix("Format:").split(",").map { it.trim() }
        val idxStart = formatParts.indexOfFirst { it.equals("Start", ignoreCase = true) }
        val idxEnd   = formatParts.indexOfFirst { it.equals("End", ignoreCase = true) }
        val idxText  = formatParts.indexOfFirst { it.equals("Text", ignoreCase = true) }
        if (idxStart == -1 || idxEnd == -1 || idxText == -1) return ""

        val sb = StringBuilder()
        var counter = 1

        for (i in eventsStart + 1 until lines.size) {
            val line = lines[i].trim()
            if (line.startsWith("[")) break
            if (!line.startsWith("Dialogue:", ignoreCase = true)) continue

            val content = line.removePrefix("Dialogue:").trimStart()
            val parts = content.split(",", limit = formatParts.size)
            if (parts.size < formatParts.size) continue

            val startStr = parts[idxStart].trim()
            val endStr   = parts[idxEnd].trim()
            val text     = parts.drop(idxText).joinToString(",")

            val startMs = assTimestampToMs(startStr)
            val endMs   = assTimestampToMs(endStr)
            if (startMs < 0 || endMs < 0 || endMs <= startMs) continue

            val cleanText = text
                .replace(Regex("\\{[^}]*\\}"), "")
                .replace("\\N", "\n")
                .replace("\\n", "\n")
                .replace("\\h", " ")
                .trim()

            if (cleanText.isEmpty()) continue

            sb.append(counter++).append("\n")
            sb.append(msToSrtTimestamp(startMs)).append(" --> ").append(msToSrtTimestamp(endMs)).append("\n")
            sb.append(cleanText).append("\n\n")
        }

        return sb.toString()
    }

    private fun assTimestampToMs(ts: String): Long {
        return try {
            val parts = ts.split(":", ".")
            if (parts.size < 4) return -1
            val h  = parts[0].toLong()
            val m  = parts[1].toLong()
            val s  = parts[2].toLong()
            val cs = parts[3].toLong()
            h * 3600_000L + m * 60_000L + s * 1000L + cs * 10L
        } catch (e: Exception) { -1L }
    }

    private fun msToSrtTimestamp(ms: Long): String {
        val totalSec = ms / 1000
        val millis   = ms % 1000
        val sec      = totalSec % 60
        val min      = (totalSec / 60) % 60
        val hour     = totalSec / 3600
        return "%02d:%02d:%02d,%03d".format(hour, min, sec, millis)
    }
}
