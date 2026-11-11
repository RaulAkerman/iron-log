package com.ironlog.app.ui.common

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/** Text formatting and parsing for weights, reps, dates and durations. */

const val MAX_WEIGHT_KG = 2000.0
const val MAX_REPS = 999

/** "60" for whole kilograms, "62.5" otherwise. */
fun formatKg(kg: Double): String = if (kg % 1.0 == 0.0) kg.toLong().toString() else kg.toString()

/** Parses a weight typed with either a decimal point or a decimal comma; null if not a weight. */
fun parseKg(text: String): Double? =
  text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() && it in 0.0..MAX_WEIGHT_KG }

/** Parses a positive whole number of reps; null otherwise. */
fun parseReps(text: String): Int? = text.trim().toIntOrNull()?.takeIf { it in 1..MAX_REPS }

/** "8", "8–12" or null when there is no rep target. */
fun formatRepRange(min: Int?, max: Int?): String? =
  when {
    min == null && max == null -> null
    min == null -> max.toString()
    max == null || max == min -> min.toString()
    else -> "$min–$max"
  }

/**
 * Normalises two optional rep inputs into a (min, max) target: a single value becomes the min, and
 * reversed values are swapped. Max is null unless it is a real range.
 */
fun repRangeOf(first: Int?, second: Int?): Pair<Int?, Int?> {
  val values = listOfNotNull(first, second)
  val min = values.minOrNull()
  val max = values.maxOrNull()
  return min to max.takeIf { it != min }
}

/** "08:05" for a time of day given as minutes after midnight. */
fun formatMinuteOfDay(minuteOfDay: Int): String =
  String.format(Locale.ROOT, "%02d:%02d", minuteOfDay / 60, minuteOfDay % 60)

private val dateFormat = DateTimeFormatter.ofPattern("EEE d MMM yyyy")
private val timeFormat = DateTimeFormatter.ofPattern("HH:mm")

fun formatDate(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
  dateFormat.format(Instant.ofEpochMilli(epochMillis).atZone(zone))

fun formatTime(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
  timeFormat.format(Instant.ofEpochMilli(epochMillis).atZone(zone))

/** Formats a date given as days since 1970-01-01. */
fun formatEpochDay(epochDay: Long): String = dateFormat.format(LocalDate.ofEpochDay(epochDay))

/** Whole calendar days between a moment and today; 0 means today. */
fun daysAgo(
  epochMillis: Long,
  today: LocalDate = LocalDate.now(),
  zone: ZoneId = ZoneId.systemDefault(),
): Int =
  ChronoUnit.DAYS.between(Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate(), today)
    .toInt()

/** Minutes between two moments, rounded down and never negative. */
fun durationMinutes(startMillis: Long, endMillis: Long): Int =
  ((endMillis - startMillis).coerceAtLeast(0) / 60_000).toInt()
