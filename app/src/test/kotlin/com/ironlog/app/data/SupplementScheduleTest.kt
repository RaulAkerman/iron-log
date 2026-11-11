package com.ironlog.app.data

import com.ironlog.app.data.db.SupplementEntity
import com.ironlog.app.data.db.SupplementIntakeEntity
import com.ironlog.app.data.db.SupplementTimeEntity
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SupplementScheduleTest {
  private val tallinn = ZoneId.of("Europe/Tallinn")

  private fun at(date: String, time: String, zone: ZoneId = tallinn): ZonedDateTime =
    LocalDateTime.parse("${date}T$time").atZone(zone)

  private fun minuteOfDay(hour: Int, minute: Int) = hour * 60 + minute

  @Test
  fun nextOccurrence_laterToday() {
    assertEquals(
      at("2026-11-11", "08:00"),
      nextOccurrence(minuteOfDay(8, 0), at("2026-11-11", "07:59:30")),
    )
  }

  @Test
  fun nextOccurrence_alreadyPassedToday_isTomorrow() {
    assertEquals(
      at("2026-11-12", "08:00"),
      nextOccurrence(minuteOfDay(8, 0), at("2026-11-11", "08:00:01")),
    )
  }

  @Test
  fun nextOccurrence_exactlyNow_isTomorrow() {
    // An alarm that fires exactly on time must schedule the next day, not itself again.
    assertEquals(
      at("2026-11-12", "08:00"),
      nextOccurrence(minuteOfDay(8, 0), at("2026-11-11", "08:00")),
    )
  }

  @Test
  fun nextOccurrence_rollsOverMonthAndYear() {
    assertEquals(
      at("2027-01-01", "00:00"),
      nextOccurrence(minuteOfDay(0, 0), at("2026-12-31", "23:59")),
    )
  }

  @Test
  fun nextOccurrence_lastMinuteOfDay() {
    assertEquals(
      at("2026-11-11", "23:59"),
      nextOccurrence(MINUTES_PER_DAY - 1, at("2026-11-11", "12:00")),
    )
  }

  @Test
  fun nextOccurrence_keepsLocalTimeAcrossClockChange() {
    // Clocks in Tallinn go back on 2026-10-25: the day is 25 hours long, 08:00 stays 08:00.
    val next = nextOccurrence(minuteOfDay(8, 0), at("2026-10-24", "09:00"))

    assertEquals(at("2026-10-25", "08:00"), next)
    assertEquals(ZoneOffset.ofHours(2), next.offset)
  }

  @Test
  fun nextOccurrence_timeSkippedBySpringForward_movesToAfterTheJump() {
    // On 2027-03-28 clocks in Tallinn jump from 03:00 to 04:00, so 03:30 does not exist.
    val next = nextOccurrence(minuteOfDay(3, 30), at("2027-03-27", "12:00"))

    assertEquals(at("2027-03-28", "04:30"), next)
    assertTrue(next.isAfter(at("2027-03-28", "02:59")))
  }

  @Test
  fun epochDayOf_usesTheLocalDate() {
    // 23:30 UTC on the 10th is already the 11th in Tallinn (UTC+2 in winter).
    val moment = at("2026-11-10", "23:30", ZoneOffset.UTC).toInstant().toEpochMilli()

    assertEquals(LocalDate.of(2026, 11, 10).toEpochDay(), epochDayOf(moment, ZoneOffset.UTC))
    assertEquals(LocalDate.of(2026, 11, 11).toEpochDay(), epochDayOf(moment, tallinn))
  }

  @Test
  fun dosesOfDay_sortedByTimeThenName_withTakenFlags() {
    val creatine = SupplementEntity(id = 1, name = "Creatine")
    val magnesium = SupplementEntity(id = 2, name = "magnesium")
    val times =
      listOf(
        SupplementTimeEntity(id = 10, supplementId = 2, minuteOfDay = minuteOfDay(21, 30)),
        SupplementTimeEntity(id = 11, supplementId = 1, minuteOfDay = minuteOfDay(8, 0)),
        SupplementTimeEntity(id = 12, supplementId = 2, minuteOfDay = minuteOfDay(8, 0)),
      )
    val intakes =
      listOf(SupplementIntakeEntity(1, supplementId = 2, epochDay = 5, minuteOfDay = 480, takenAt = 0))

    val doses = dosesOfDay(listOf(creatine, magnesium), times, intakes)

    assertEquals(
      listOf(
        Dose(1, "Creatine", timeId = 11, minuteOfDay = 480, taken = false),
        Dose(2, "magnesium", timeId = 12, minuteOfDay = 480, taken = true),
        Dose(2, "magnesium", timeId = 10, minuteOfDay = 1290, taken = false),
      ),
      doses,
    )
  }

  @Test
  fun dosesOfDay_intakeOfAnotherTimeDoesNotCount() {
    val creatine = SupplementEntity(id = 1, name = "Creatine")
    val times = listOf(SupplementTimeEntity(id = 11, supplementId = 1, minuteOfDay = 480))
    val intakes =
      listOf(SupplementIntakeEntity(1, supplementId = 1, epochDay = 5, minuteOfDay = 540, takenAt = 0))

    assertEquals(false, dosesOfDay(listOf(creatine), times, intakes).single().taken)
  }

  @Test
  fun dosesOfDay_ignoresTimesWithoutSupplement() {
    val times = listOf(SupplementTimeEntity(id = 11, supplementId = 99, minuteOfDay = 480))

    assertEquals(emptyList<Dose>(), dosesOfDay(emptyList(), times, emptyList()))
  }

  @Test
  fun intakesByDay_newestDayFirst_timesAscending() {
    val intakes =
      listOf(
        SupplementIntakeEntity(1, supplementId = 1, epochDay = 7, minuteOfDay = 1290, takenAt = 0),
        SupplementIntakeEntity(2, supplementId = 1, epochDay = 9, minuteOfDay = 480, takenAt = 0),
        SupplementIntakeEntity(3, supplementId = 1, epochDay = 7, minuteOfDay = 480, takenAt = 0),
      )

    assertEquals(
      listOf(IntakeDay(9, listOf(480)), IntakeDay(7, listOf(480, 1290))),
      intakesByDay(intakes),
    )
  }
}
