package com.example.streetvoicetv.domain.model

import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/** `2026-11-03T18:18:42+0800` (the API's `publish_at`), also with a `+08:00` / `Z` offset. */
private val PUBLISH_AT_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss[.SSSSSS][.SSS]XX")

/** Release dates are shown in StreetVoice's own time zone (and independent of the device). */
private val DISPLAY_ZONE: ZoneId = ZoneId.of("Asia/Taipei")
private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy/M/d")

/** Epoch millis of a `publish_at` value, or null when absent / unparsable. */
fun parsePublishAt(value: String?): Long? {
    if (value.isNullOrBlank()) return null
    return try {
        OffsetDateTime.parse(value, PUBLISH_AT_FORMAT).toInstant().toEpochMilli()
    } catch (_: DateTimeParseException) {
        try {
            OffsetDateTime.parse(value).toInstant().toEpochMilli()
        } catch (_: DateTimeParseException) {
            null
        }
    }
}

private const val MINUTE = 60_000L
private const val HOUR = 60 * MINUTE
private const val DAY = 24 * HOUR

/**
 * 「剛剛」/「5 分鐘前」/「3 小時前」/「3 天前」 for the last 30 days, else the date (2026/6/11).
 * A date in the future (clock skew) reads 「剛剛」.
 */
fun formatReleaseAge(publishedAtMillis: Long, nowMillis: Long): String {
    val age = nowMillis - publishedAtMillis
    return when {
        age < MINUTE -> "剛剛"
        age < HOUR -> "${age / MINUTE} 分鐘前"
        age < DAY -> "${age / HOUR} 小時前"
        age < 30 * DAY -> "${age / DAY} 天前"
        else -> DATE_FORMAT.format(Instant.ofEpochMilli(publishedAtMillis).atZone(DISPLAY_ZONE))
    }
}
