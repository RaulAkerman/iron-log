package com.ironlog.app.ui.common

import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FormatTest {
  @Test
  fun formatKg_dropsTrailingZeroOnly() {
    assertEquals("60", formatKg(60.0))
    assertEquals("62.5", formatKg(62.5))
    assertEquals("1.25", formatKg(1.25))
    assertEquals("0", formatKg(0.0))
  }

  @Test
  fun parseKg_acceptsPointAndComma() {
    assertEquals(62.5, parseKg("62.5")!!, 0.0)
    assertEquals(62.5, parseKg("62,5")!!, 0.0)
    assertEquals(60.0, parseKg(" 60 ")!!, 0.0)
    assertEquals(62.0, parseKg("62.")!!, 0.0)
    assertEquals(0.0, parseKg("0")!!, 0.0)
  }

  @Test
  fun parseKg_rejectsEverythingElse() {
    assertNull(parseKg(""))
    assertNull(parseKg(","))
    assertNull(parseKg("abc"))
    assertNull(parseKg("-5"))
    assertNull(parseKg("1.2.3"))
    assertNull(parseKg("99999"))
    assertNull(parseKg("NaN"))
  }

  @Test
  fun parseKg_roundTripsWithFormatKg() {
    for (kg in listOf(0.0, 2.5, 20.0, 57.5, 102.25)) {
      assertEquals(kg, parseKg(formatKg(kg))!!, 0.0)
    }
  }

  @Test
  fun parseReps_onlyPositiveWholeNumbers() {
    assertEquals(8, parseReps("8"))
    assertEquals(12, parseReps(" 12 "))
    assertNull(parseReps("0"))
    assertNull(parseReps("-3"))
    assertNull(parseReps("8.5"))
    assertNull(parseReps(""))
    assertNull(parseReps("1000"))
  }

  @Test
  fun formatRepRange_coversAllShapes() {
    assertNull(formatRepRange(null, null))
    assertEquals("8", formatRepRange(8, null))
    assertEquals("8", formatRepRange(8, 8))
    assertEquals("12", formatRepRange(null, 12))
    assertEquals("8–12", formatRepRange(8, 12))
  }

  @Test
  fun repRangeOf_normalisesInput() {
    assertEquals(null to null, repRangeOf(null, null))
    assertEquals(8 to null, repRangeOf(8, null))
    assertEquals(12 to null, repRangeOf(null, 12))
    assertEquals(8 to null, repRangeOf(8, 8))
    assertEquals(8 to 12, repRangeOf(8, 12))
    assertEquals(8 to 12, repRangeOf(12, 8))
  }

  @Test
  fun formatMinuteOfDay_padsHoursAndMinutes() {
    assertEquals("00:00", formatMinuteOfDay(0))
    assertEquals("08:05", formatMinuteOfDay(8 * 60 + 5))
    assertEquals("21:30", formatMinuteOfDay(21 * 60 + 30))
    assertEquals("23:59", formatMinuteOfDay(24 * 60 - 1))
  }

  @Test
  fun daysAgo_countsCalendarDaysNotHours() {
    val zone = ZoneOffset.UTC
    val lateEvening = LocalDate.of(2026, 11, 9).atTime(23, 30).toInstant(zone).toEpochMilli()
    assertEquals(0, daysAgo(lateEvening, today = LocalDate.of(2026, 11, 9), zone = zone))
    assertEquals(1, daysAgo(lateEvening, today = LocalDate.of(2026, 11, 10), zone = zone))
    assertEquals(5, daysAgo(lateEvening, today = LocalDate.of(2026, 11, 14), zone = zone))
  }

  @Test
  fun durationMinutes_roundsDownAndClamps() {
    assertEquals(0, durationMinutes(1_000, 59_000))
    assertEquals(61, durationMinutes(0, 61 * 60_000L + 59_000))
    assertEquals(0, durationMinutes(10_000, 5_000))
  }
}
