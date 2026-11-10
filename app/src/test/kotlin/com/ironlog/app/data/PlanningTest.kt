package com.ironlog.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlanningTest {
  @Test
  fun nextWorkout_emptyCycle_isNull() {
    assertNull(nextWorkoutId(emptyList(), lastCompletedWorkoutId = null))
    assertNull(nextWorkoutId(emptyList(), lastCompletedWorkoutId = 7))
  }

  @Test
  fun nextWorkout_nothingCompletedYet_isFirst() {
    assertEquals(10L, nextWorkoutId(listOf(10, 20, 30), lastCompletedWorkoutId = null))
  }

  @Test
  fun nextWorkout_followsCycleOrder() {
    assertEquals(20L, nextWorkoutId(listOf(10, 20, 30), lastCompletedWorkoutId = 10))
    assertEquals(30L, nextWorkoutId(listOf(10, 20, 30), lastCompletedWorkoutId = 20))
  }

  @Test
  fun nextWorkout_wrapsAroundAfterLast() {
    assertEquals(10L, nextWorkoutId(listOf(10, 20, 30), lastCompletedWorkoutId = 30))
  }

  @Test
  fun nextWorkout_lastCompletedNoLongerInCycle_startsOver() {
    assertEquals(10L, nextWorkoutId(listOf(10, 20, 30), lastCompletedWorkoutId = 99))
  }

  @Test
  fun nextWorkout_singleWorkout_repeats() {
    assertEquals(10L, nextWorkoutId(listOf(10), lastCompletedWorkoutId = 10))
  }

  @Test
  fun planSets_newExercise_isBlank() {
    assertEquals(List(3) { PlannedSet(null, null) }, planSets(3, lastTime = emptyList()))
  }

  @Test
  fun planSets_copiesLastTimeSetBySet() {
    val lastTime = listOf(PlannedSet(60.0, 8), PlannedSet(60.0, 7), PlannedSet(57.5, 6))
    assertEquals(lastTime, planSets(3, lastTime))
  }

  @Test
  fun planSets_extraSetsRepeatTheFinalSet() {
    val lastTime = listOf(PlannedSet(60.0, 8), PlannedSet(57.5, 6))
    assertEquals(
      listOf(PlannedSet(60.0, 8), PlannedSet(57.5, 6), PlannedSet(57.5, 6), PlannedSet(57.5, 6)),
      planSets(4, lastTime),
    )
  }

  @Test
  fun planSets_fewerSetsThanLastTime_takesTheFirstOnes() {
    val lastTime = listOf(PlannedSet(60.0, 8), PlannedSet(60.0, 7), PlannedSet(57.5, 6))
    assertEquals(lastTime.take(2), planSets(2, lastTime))
  }

  @Test
  fun moved_shiftsElementAndClampsAtTheEnds() {
    val items = listOf("a", "b", "c")
    assertEquals(listOf("b", "a", "c"), moved(items, index = 0, delta = 1))
    assertEquals(listOf("a", "c", "b"), moved(items, index = 2, delta = -1))
    assertEquals(items, moved(items, index = 0, delta = -1))
    assertEquals(items, moved(items, index = 2, delta = 1))
    assertEquals(items, moved(items, index = 5, delta = 1))
  }
}
