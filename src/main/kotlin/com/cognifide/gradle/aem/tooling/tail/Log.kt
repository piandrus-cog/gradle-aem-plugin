package com.cognifide.gradle.aem.tooling.tail

import com.cognifide.gradle.aem.common.Formats

class Log(
    val text: String,
    val checksum: String,
    val timestamp: String,
    val level: String,
    val source: String,
    messageLines: List<String>
) {

    val message = messageLines.joinToString("\n")

    companion object {
        private const val TIMESTAMP = """(?<timestamp>[0-9]{2}\.[0-9]{2}\.[0-9]{4}\s[0-9]{2}:[0-9]{2}:[0-9]{2}\.[0-9]{3})"""
        private const val LEVEL = """\*(?<level>[A-Z]+)\*"""
        private const val SOURCE = """(?<source>\[.*\])"""
        private const val MESSAGE = """(?<message>.*)"""
        private const val LOG_PATTERN = "$TIMESTAMP\\s$LEVEL\\s$SOURCE\\s$MESSAGE"

        fun create(logLines: List<String>): Log {
            if (logLines.isEmpty() || logLines.first().isBlank()) throw TailException("Passed log entry is empty!")
            val fullLog = logLines.joinToString("\n")
            val checksum = Formats.calculateChecksum(fullLog)
            val result = matchLogLine(logLines.first())
            when (result) {
                null -> throw TailException("Passed text is not a log entry\nPattern:\n$LOG_PATTERN\nText:\n${logLines.first()}")
                else -> {
                    val (timestamp, level, source, message) = result.destructured
                    val followingMessageLines = logLines.slice(1 until logLines.size)
                    return Log(fullLog, checksum, timestamp, level, source, listOf(message) + followingMessageLines)
                }
            }
        }

        fun isFirstLineOfLog(text: String) = matchLogLine(text) != null

        private fun matchLogLine(text: String) = LOG_PATTERN.toRegex().matchEntire(text)
    }
}