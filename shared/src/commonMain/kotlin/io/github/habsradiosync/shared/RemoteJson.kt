package io.github.habsradiosync.shared

fun RadioState.toJson(): String =
    buildString {
        append('{')
        append("\"stationId\":\"").append(stationId.escapeJson()).append("\",")
        append("\"playing\":").append(playing).append(',')
        append("\"delaySeconds\":").append(delaySeconds).append(',')
        append("\"delayBufferedSeconds\":").append(delayBufferedSeconds).append(',')
        append("\"delayAvailableSeconds\":").append(delayAvailableSeconds).append(',')
        append("\"delayBuffering\":").append(delayBuffering).append(',')
        append("\"audioBytesWritten\":").append(audioBytesWritten).append(',')
        append("\"audioBytesPerSecond\":").append(audioBytesPerSecond).append(',')
        append("\"volumePercent\":").append(volumePercent).append(',')
        append("\"status\":\"").append(status.name).append("\",")
        append("\"error\":")
        if (error == null) append("null") else append('"').append(error.escapeJson()).append('"')
        append('}')
    }

fun radioStateFromJson(json: String): RadioState {
    require(json.trimStart().startsWith("{")) { "Radio state must be a JSON object" }

    val fallback = RadioState()
    return RadioState(
        stationId = json.jsonString("stationId") ?: fallback.stationId,
        playing = json.jsonBoolean("playing") ?: fallback.playing,
        delaySeconds = json.jsonDouble("delaySeconds") ?: fallback.delaySeconds,
        delayBufferedSeconds = json.jsonDouble("delayBufferedSeconds") ?: fallback.delayBufferedSeconds,
        delayAvailableSeconds = json.jsonDouble("delayAvailableSeconds") ?: fallback.delayAvailableSeconds,
        delayBuffering = json.jsonBoolean("delayBuffering") ?: fallback.delayBuffering,
        audioBytesWritten = json.jsonLong("audioBytesWritten") ?: fallback.audioBytesWritten,
        audioBytesPerSecond = json.jsonInt("audioBytesPerSecond") ?: fallback.audioBytesPerSecond,
        volumePercent = json.jsonInt("volumePercent") ?: fallback.volumePercent,
        status = json.jsonString("status")
            ?.let { status -> PlaybackStatus.entries.firstOrNull { it.name == status } }
            ?: fallback.status,
        error = json.jsonNullableString("error"),
    )
}

private fun String.jsonString(name: String): String? =
    Regex("\"${Regex.escape(name)}\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"")
        .find(this)
        ?.groupValues
        ?.get(1)
        ?.unescapeJson()

private fun String.jsonNullableString(name: String): String? {
    if (Regex("\"${Regex.escape(name)}\"\\s*:\\s*null").containsMatchIn(this)) return null
    return jsonString(name)
}

private fun String.jsonDouble(name: String): Double? =
    jsonNumber(name)?.toDoubleOrNull()

private fun String.jsonInt(name: String): Int? =
    jsonNumber(name)?.toIntOrNull()

private fun String.jsonLong(name: String): Long? =
    jsonNumber(name)?.toLongOrNull()

private fun String.jsonBoolean(name: String): Boolean? =
    Regex("\"${Regex.escape(name)}\"\\s*:\\s*(true|false)")
        .find(this)
        ?.groupValues
        ?.get(1)
        ?.toBooleanStrictOrNull()

private fun String.jsonNumber(name: String): String? =
    Regex("\"${Regex.escape(name)}\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?)")
        .find(this)
        ?.groupValues
        ?.get(1)

private fun String.escapeJson(): String =
    buildString(length) {
        for (char in this@escapeJson) {
            when (char) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(char)
            }
        }
    }

private fun String.unescapeJson(): String =
    buildString(length) {
        var index = 0
        while (index < this@unescapeJson.length) {
            val char = this@unescapeJson[index]
            if (char != '\\' || index == this@unescapeJson.lastIndex) {
                append(char)
                index += 1
                continue
            }

            when (val escaped = this@unescapeJson[index + 1]) {
                '\\' -> append('\\')
                '"' -> append('"')
                'n' -> append('\n')
                'r' -> append('\r')
                't' -> append('\t')
                else -> append(escaped)
            }
            index += 2
        }
    }
