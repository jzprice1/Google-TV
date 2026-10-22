package dev.jzprice.livetv

import java.io.BufferedReader

/** Pure Kotlin M3U/M3U8 playlist parser (no Android dependencies, so it can be unit tested anywhere). */
object M3uParser {
    const val UNCATEGORIZED = "Uncategorized"

    private val ATTR = Regex("([A-Za-z0-9_\\-]+)=\"([^\"]*)\"")

    fun parse(reader: BufferedReader): List<Channel> {
        val out = ArrayList<Channel>()
        var attrs: Map<String, String>? = null
        var name = ""
        var extGroup = ""
        var first = true

        while (true) {
            var line = reader.readLine() ?: break
            if (first) {
                line = line.removePrefix("﻿")
                first = false
            }
            line = line.trim()
            if (line.isEmpty()) continue

            if (line.startsWith("#")) {
                if (line.startsWith("#EXTINF", ignoreCase = true)) {
                    val colon = line.indexOf(':')
                    val body = if (colon >= 0) line.substring(colon + 1) else ""
                    val comma = commaOutsideQuotes(body)
                    val head = if (comma >= 0) body.substring(0, comma) else body
                    name = if (comma >= 0) body.substring(comma + 1).trim() else ""
                    attrs = ATTR.findAll(head).associate {
                        it.groupValues[1].lowercase() to it.groupValues[2].trim()
                    }
                } else if (line.startsWith("#EXTGRP:", ignoreCase = true)) {
                    extGroup = line.substring(8).trim()
                }
                continue
            }

            // Anything that is not a comment should be a stream URL. Skip junk lines.
            if (!line.contains("://")) continue

            val a = attrs ?: emptyMap()
            val finalName = name.ifBlank { a["tvg-name"].orEmpty() }.ifBlank { line }
            val group = a["group-title"].orEmpty().ifBlank { extGroup }.ifBlank { UNCATEGORIZED }
            val tvgId = a["tvg-id"].orEmpty()
            out.add(
                Channel(
                    key = "$tvgId|$finalName",
                    name = finalName,
                    url = line,
                    logo = a["tvg-logo"].orEmpty(),
                    group = group,
                )
            )
            attrs = null
            name = ""
            extGroup = ""
        }
        return out
    }

    /** Index of the first comma that is not inside double quotes (group titles may contain commas). */
    private fun commaOutsideQuotes(s: String): Int {
        var inQuotes = false
        for (i in s.indices) {
            val c = s[i]
            if (c == '"') {
                inQuotes = !inQuotes
            } else if (c == ',' && !inQuotes) {
                return i
            }
        }
        // Unbalanced quotes: fall back to the last comma so the name is still split off.
        return if (inQuotes) s.lastIndexOf(',') else -1
    }
}
