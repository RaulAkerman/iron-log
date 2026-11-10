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
