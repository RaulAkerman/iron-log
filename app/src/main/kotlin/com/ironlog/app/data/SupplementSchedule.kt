package com.ironlog.app.data

import com.ironlog.app.data.db.SupplementEntity
import com.ironlog.app.data.db.SupplementIntakeEntity
import com.ironlog.app.data.db.SupplementTimeEntity
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** Pure rules of the supplement schedule. No Android or database access. */

const val MINUTES_PER_DAY = 24 * 60

/**
 * The first moment strictly after [now] at which the local time is [minuteOfDay]. A time that does
 * not exist on a day because clocks jump forward is moved forward by the length of that jump.
 */
fun nextOccurrence(minuteOfDay: Int, now: ZonedDateTime): ZonedDateTime {
  val time = LocalTime.of(minuteOfDay / 60, minuteOfDay % 60)
  val today = now.toLocalDate().atTime(time).atZone(now.zone)
  return if (today.isAfter(now)) today else now.toLocalDate().plusDays(1).atTime(time).atZone(now.zone)
}

/** The local date of a moment, as days since 1970-01-01. */
fun epochDayOf(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): Long =
  Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate().toEpochDay()

/** One scheduled dose of a day and whether it has been taken. */
data class Dose(
  val supplementId: Long,
  val supplementName: String,
  val timeId: Long,
  val minuteOfDay: Int,
  val taken: Boolean,
)

/**
 * The doses scheduled for one day, earliest first. [intakesOfDay] must hold only that day's
 * intakes.
 */
fun dosesOfDay(
  supplements: List<SupplementEntity>,
  times: List<SupplementTimeEntity>,
  intakesOfDay: List<SupplementIntakeEntity>,
): List<Dose> {
  val names = supplements.associate { it.id to it.name }
  val taken = intakesOfDay.mapTo(HashSet()) { it.supplementId to it.minuteOfDay }
  return times
    .mapNotNull { time ->
      val name = names[time.supplementId] ?: return@mapNotNull null
      Dose(
        supplementId = time.supplementId,
        supplementName = name,
        timeId = time.id,
        minuteOfDay = time.minuteOfDay,
        taken = (time.supplementId to time.minuteOfDay) in taken,
      )
    }
    .sortedWith(compareBy<Dose> { it.minuteOfDay }.thenBy { it.supplementName.lowercase() })
}

/** The doses taken on one day, for the record shown per supplement. */
data class IntakeDay(val epochDay: Long, val minutesOfDay: List<Int>)

/** Groups intakes by day, newest day first, times ascending within a day. */
fun intakesByDay(intakes: List<SupplementIntakeEntity>): List<IntakeDay> =
  intakes
    .groupBy { it.epochDay }
    .map { (day, ofDay) -> IntakeDay(day, ofDay.map { it.minuteOfDay }.sorted()) }
    .sortedByDescending { it.epochDay }
