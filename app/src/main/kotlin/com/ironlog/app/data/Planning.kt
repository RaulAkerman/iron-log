package com.ironlog.app.data

/** Pure rules of the rotating cycle and of pre-filling a new session. No Android or database types. */

/**
 * The workout that comes after [lastCompletedWorkoutId] in the cycle, wrapping around at the end.
 * Starts from the first workout when nothing was completed yet or the last one no longer exists.
 */
fun nextWorkoutId(orderedWorkoutIds: List<Long>, lastCompletedWorkoutId: Long?): Long? {
  if (orderedWorkoutIds.isEmpty()) return null
  val lastIndex = orderedWorkoutIds.indexOf(lastCompletedWorkoutId)
  return orderedWorkoutIds[(lastIndex + 1) % orderedWorkoutIds.size]
}

/** Weight and reps a new, not yet performed set starts with. */
data class PlannedSet(val weightKg: Double?, val reps: Int?)

/**
 * Values for [count] new sets, taken from what was done last time. Set n copies set n of the last
 * session; sets beyond that copy its final set. Empty values when the exercise is new.
 */
fun planSets(count: Int, lastTime: List<PlannedSet>): List<PlannedSet> =
  List(count) { index -> lastTime.getOrNull(index) ?: lastTime.lastOrNull() ?: PlannedSet(null, null) }

/** Returns [items] with the element at [index] moved by [delta] places, clamped to the list bounds. */
fun <T> moved(items: List<T>, index: Int, delta: Int): List<T> {
  if (index !in items.indices) return items
  val target = (index + delta).coerceIn(0, items.lastIndex)
  if (target == index) return items
  return items.toMutableList().apply { add(target, removeAt(index)) }
}

/** A muscle group, which is what a category stands for. */
data class Muscle(val id: Long, val name: String)

/**
 * An exercise of a workout as far as the muscles it trains go: its main category ([main], null
 * when it has none), its sub categories and the number of sets planned for it.
 */
data class PlannedExercise(val sets: Int, val main: Muscle?, val subs: List<Muscle>)

/**
 * The muscles a workout trains, shown instead of its exercises where they would not fit. [main]
 * are the main categories of its exercises; [other] the sub categories that are not also one of
 * those.
 */
data class WorkoutMuscles(val main: List<String> = emptyList(), val other: List<String> = emptyList())

/**
 * What the workout made of [exercises] (in workout order) trains. Each list starts with the muscle
 * that gets the most planned sets; on a tie, the one that comes first in the workout.
 */
fun workoutMuscles(exercises: List<PlannedExercise>): WorkoutMuscles {
  val mainIds = exercises.mapNotNullTo(HashSet()) { it.main?.id }
  return WorkoutMuscles(
    main = mostSetsFirst(exercises.mapNotNull { exercise -> exercise.main?.let { it to exercise.sets } }),
    other =
      mostSetsFirst(
        exercises.flatMap { exercise ->
          exercise.subs.filter { it.id !in mainIds }.map { it to exercise.sets }
        }
      ),
  )
}

/** The names of the muscles in [setsPerMuscle], most sets in total first, ties in order of appearance. */
private fun mostSetsFirst(setsPerMuscle: List<Pair<Muscle, Int>>): List<String> =
  setsPerMuscle
    .groupBy({ it.first }, { it.second })
    .entries
    .sortedByDescending { (_, sets) -> sets.sum() }
    .map { (muscle, _) -> muscle.name }
