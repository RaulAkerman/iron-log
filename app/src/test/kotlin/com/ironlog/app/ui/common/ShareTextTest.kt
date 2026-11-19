package com.ironlog.app.ui.common

import com.ironlog.app.data.db.HistorySet
import com.ironlog.app.data.db.WorkoutEntity
import com.ironlog.app.data.db.WorkoutExerciseItem
import java.time.LocalDateTime
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Test

class ShareTextTest {
  private val zone = ZoneOffset.UTC

  private fun at(day: Int, hour: Int, minute: Int): Long =
    LocalDateTime.of(2026, 11, day, hour, minute).toInstant(zone).toEpochMilli()

  /** A set of the session "Push": 18 November 2026, 18:05 to 19:10. */
  private fun pushSet(
    sessionExerciseId: Long,
    exercise: String,
    weightKg: Double?,
    reps: Int?,
    category: String? = "Chest",
  ) =
    HistorySet(
      sessionId = 2,
      sessionName = "Push",
      startedAt = at(18, 18, 5),
      finishedAt = at(18, 19, 10),
      sessionExerciseId = sessionExerciseId,
      exerciseName = exercise,
      categoryName = category,
      weightKg = weightKg,
      reps = reps,
    )

  /** A set of the session "Pull", two days before "Push": 16 November 2026, 07:30 to 08:15. */
  private fun pullSet(sessionExerciseId: Long, exercise: String, weightKg: Double?, reps: Int?) =
    HistorySet(
      sessionId = 1,
      sessionName = "Pull",
      startedAt = at(16, 7, 30),
      finishedAt = at(16, 8, 15),
      sessionExerciseId = sessionExerciseId,
      exerciseName = exercise,
      categoryName = "Back",
      weightKg = weightKg,
      reps = reps,
    )

  private val push =
    listOf(
      pushSet(20, "Bench Press", 80.0, 8),
      pushSet(20, "Bench Press", 82.5, 6),
      pushSet(21, "Push Up", null, 12, category = null),
      pushSet(21, "Push Up", 0.0, 1, category = null),
    )
  private val pull = listOf(pullSet(10, "Barbell Row", 60.0, 10), pullSet(11, "Pull Up", null, 8))

  private fun planned(name: String, sets: Int, repsMin: Int? = null, repsMax: Int? = null) =
    WorkoutExerciseItem(
      id = 0,
      workoutId = 0,
      exerciseId = 0,
      position = 0,
      targetSets = sets,
      targetRepsMin = repsMin,
      targetRepsMax = repsMax,
      exerciseName = name,
      categoryId = null,
      categoryName = null,
    )

  @Test
  fun historyText_oneWorkout_listsEachExerciseWithItsSets() {
    val expected =
      """
      Push
      ${formatDate(at(18, 18, 5), zone)}, 18:05 · 1 h 05 min
      Bench Press: 80 kg × 8, 82.5 kg × 6
      Push Up: 12 reps, 1 rep
      """
        .trimIndent()

    assertEquals(expected, historyText(push, zone))
  }

  @Test
  fun historyText_severalWorkouts_newestFirstWithAnEmptyLineBetween() {
    val expected =
      """
      Push
      ${formatDate(at(18, 18, 5), zone)}, 18:05 · 1 h 05 min
      Bench Press: 80 kg × 8, 82.5 kg × 6
      Push Up: 12 reps, 1 rep

      Pull
      ${formatDate(at(16, 7, 30), zone)}, 07:30 · 45 min
      Barbell Row: 60 kg × 10
      Pull Up: 8 reps
      """
        .trimIndent()

    assertEquals(expected, historyText(pull + push, zone))
  }

  @Test
  fun historyText_sameExerciseTwiceInAWorkout_staysTwoLines() {
    val sets = listOf(pullSet(10, "Pull Up", null, 8), pullSet(11, "Pull Up", null, 6))

    assertEquals(listOf("Pull Up: 8 reps", "Pull Up: 6 reps"), historyText(sets, zone).lines().drop(2))
  }

  @Test
  fun historyText_setWithoutReps_countsAsZero() {
    val sets = listOf(pullSet(10, "Barbell Row", 60.0, null), pullSet(11, "Pull Up", null, null))

    assertEquals(listOf("Barbell Row: 60 kg × 0", "Pull Up: 0 reps"), historyText(sets, zone).lines().drop(2))
  }

  @Test
  fun historyText_nothingLogged_isEmpty() {
    assertEquals("", historyText(emptyList(), zone))
  }

  @Test
  fun historyCsv_oneRowPerSet_oldestFirst_setsNumberedPerExercise() {
    val expected =
      listOf(
          "\uFEFFDate,Time,Workout,Duration (min),Exercise,Category,Set,Weight (kg),Reps",
          "2026-11-16,07:30,Pull,45,Barbell Row,Back,1,60,10",
          "2026-11-16,07:30,Pull,45,Pull Up,Back,1,,8",
          "2026-11-18,18:05,Push,65,Bench Press,Chest,1,80,8",
          "2026-11-18,18:05,Push,65,Bench Press,Chest,2,82.5,6",
          "2026-11-18,18:05,Push,65,Push Up,,1,,12",
          "2026-11-18,18:05,Push,65,Push Up,,2,,1",
        )
        .joinToString("") { "$it\r\n" }

    assertEquals(expected, historyCsv(pull + push, zone))
  }

  @Test
  fun historyCsv_usesTheGivenTimeZone() {
    val tallinn = java.time.ZoneId.of("Europe/Tallinn")
    val lateEvening = listOf(pullSet(10, "Pull Up", null, 8).copy(startedAt = at(16, 23, 30)))

    assertEquals("2026-11-17,01:30", historyCsv(lateEvening, tallinn).lines()[1].take(16))
  }

  @Test
  fun historyCsv_quotesFieldsThatWouldBreakARow() {
    val sets =
      listOf(
        pullSet(10, "Row, one arm", 20.0, 10).copy(sessionName = "Pull \"heavy\""),
        pullSet(11, "Curl\n(strict)", 12.5, 10),
      )

    val rows = historyCsv(sets, zone).split("\r\n")

    assertEquals("2026-11-16,07:30,\"Pull \"\"heavy\"\"\",45,\"Row, one arm\",Back,1,20,10", rows[1])
    assertEquals("2026-11-16,07:30,Pull,45,\"Curl\n(strict)\",Back,1,12.5,10", rows[2])
  }

  @Test
  fun historyCsv_setWithoutReps_leavesThemEmpty() {
    val row = historyCsv(listOf(pullSet(10, "Barbell Row", 60.0, null)), zone).split("\r\n")[1]

    assertEquals("2026-11-16,07:30,Pull,45,Barbell Row,Back,1,60,", row)
  }

  @Test
  fun historyCsv_nothingLogged_isOnlyTheHeader() {
    assertEquals(1, historyCsv(emptyList(), zone).trimEnd().lines().size)
  }

  @Test
  fun workoutText_listsExercisesWithTheirTargets() {
    val exercises =
      listOf(
        planned("Bench Press", sets = 3, repsMin = 8, repsMax = 12),
        planned("Overhead Press", sets = 4, repsMin = 6),
        planned("Plank", sets = 3),
        planned("Farmer's Walk", sets = 1),
      )
    val expected =
      """
      Push
      Bench Press: 3 × 8–12
      Overhead Press: 4 × 6
      Plank: 3 sets
      Farmer's Walk: 1 set
      """
        .trimIndent()

    assertEquals(expected, workoutText("Push", exercises))
  }

  @Test
  fun workoutText_withoutExercises_saysSo() {
    assertEquals("Rest day\nNo exercises yet", workoutText("Rest day", emptyList()))
  }

  @Test
  fun cycleText_numbersTheWorkoutsInOrder() {
    val cycle =
      listOf(
        WorkoutEntity(id = 7, name = "Push", position = 0) to
          listOf(planned("Bench Press", sets = 3, repsMin = 8, repsMax = 12)),
        WorkoutEntity(id = 3, name = "Pull", position = 1) to
          listOf(planned("Barbell Row", sets = 3), planned("Pull Up", sets = 2, repsMin = 8)),
      )
    val expected =
      """
      1. Push
      Bench Press: 3 × 8–12

      2. Pull
      Barbell Row: 3 sets
      Pull Up: 2 × 8
      """
        .trimIndent()

    assertEquals(expected, cycleText(cycle))
  }
}
