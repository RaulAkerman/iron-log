package com.ironlog.app.ui.exercises

import com.ironlog.app.data.db.ExerciseItem
import com.ironlog.app.data.db.LoggedSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExerciseListLogicTest {
  private val machinePress = ExerciseItem(1, "Shoulder Press (Machine)", 10, "Shoulder press")
  private val dumbbellPress = ExerciseItem(2, "Overhead Press (Dumbbell)", 10, "Shoulder press")
  private val benchPress = ExerciseItem(3, "Bench Press (Barbell)", 20, "Bench")
  private val plank = ExerciseItem(4, "Plank", null, null)
  private val squat = ExerciseItem(5, "Squat (Barbell)", null, null)
  private val all = listOf(benchPress, dumbbellPress, plank, machinePress, squat)

  @Test
  fun sections_groupByCategory_alphabeticalWithUncategorisedLast() {
    val sections = buildSections(all, query = "", filter = CategoryFilter.All)

    assertEquals(listOf("Bench", "Shoulder press", null), sections.map { it.title })
    assertEquals(listOf(dumbbellPress, machinePress), sections[1].exercises)
    assertEquals(listOf(plank, squat), sections[2].exercises)
  }

  @Test
  fun sections_uncategorisedFilter_keepsOnlyThose() {
    val sections = buildSections(all, query = "", filter = CategoryFilter.Uncategorised)

    assertEquals(listOf(listOf(plank, squat)), sections.map { it.exercises })
  }

  @Test
  fun sections_categoryFilter_keepsOnlyThatCategory() {
    val sections = buildSections(all, query = "", filter = CategoryFilter.Category(10))

    assertEquals(listOf(listOf(dumbbellPress, machinePress)), sections.map { it.exercises })
  }

  @Test
  fun sections_query_matchesEveryWordIgnoringCaseAndOrder() {
    val names = { query: String ->
      buildSections(all, query, CategoryFilter.All).flatMap { it.exercises }.map { it.name }
    }

    assertEquals(
      listOf("Bench Press (Barbell)", "Overhead Press (Dumbbell)", "Shoulder Press (Machine)"),
      names("press"),
    )
    assertEquals(listOf("Shoulder Press (Machine)"), names("machine PRESS"))
    assertEquals(listOf("Bench Press (Barbell)", "Squat (Barbell)"), names("  barbell "))
    assertEquals(emptyList<String>(), names("press kettlebell"))
  }

  @Test
  fun sections_queryAndFilterCombine() {
    val sections = buildSections(all, query = "press", filter = CategoryFilter.Category(20))

    assertEquals(listOf(benchPress), sections.flatMap { it.exercises })
  }

  @Test
  fun groupBySession_keepsNewestFirstOrderAndSetOrder() {
    val sets =
      listOf(
        LoggedSet(sessionId = 9, startedAt = 300, weightKg = 62.5, reps = 8),
        LoggedSet(sessionId = 9, startedAt = 300, weightKg = 62.5, reps = 7),
        LoggedSet(sessionId = 4, startedAt = 100, weightKg = 60.0, reps = 8),
      )

    val grouped = groupBySession(sets)

    assertEquals(listOf(9L, 4L), grouped.map { it.sessionId })
    assertEquals(listOf(8, 7), grouped[0].sets.map { it.reps })
    assertEquals(100L, grouped[1].startedAt)
  }

  @Test
  fun bestSet_isHeaviest_thenMostReps() {
    val heavy = LoggedSet(sessionId = 1, startedAt = 100, weightKg = 62.5, reps = 6)
    val heavyMoreReps = LoggedSet(sessionId = 2, startedAt = 200, weightKg = 62.5, reps = 8)
    val lightManyReps = LoggedSet(sessionId = 3, startedAt = 300, weightKg = 50.0, reps = 15)

    assertEquals(heavyMoreReps, bestSet(listOf(heavy, lightManyReps, heavyMoreReps)))
  }

  @Test
  fun bestSet_bodyweightOnly_isMostReps() {
    val ten = LoggedSet(sessionId = 1, startedAt = 100, weightKg = null, reps = 10)
    val twelve = LoggedSet(sessionId = 2, startedAt = 200, weightKg = null, reps = 12)

    assertEquals(twelve, bestSet(listOf(ten, twelve)))
    assertNull(bestSet(emptyList()))
  }
}
