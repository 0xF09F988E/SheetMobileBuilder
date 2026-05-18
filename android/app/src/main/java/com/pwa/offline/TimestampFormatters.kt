package com.pwa.offline

import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

object TimestampFormatters {
    private val sqliteUtcFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.US)

    private val mxDisplayFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss", Locale.forLanguageTag("es-MX"))

    fun sqliteUtcToDeviceMx(rawValue: String): String {
        if (rawValue.isBlank()) return rawValue
        return try {
            val utcDateTime = LocalDateTime.parse(rawValue, sqliteUtcFormatter)
            utcDateTime
                .atOffset(ZoneOffset.UTC)
                .atZoneSameInstant(ZoneId.systemDefault())
                .format(mxDisplayFormatter)
        } catch (_: Throwable) {
            rawValue
        }
    }
}
