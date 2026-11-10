package com.ironlog.app.data.db

/** Query result rows that join or aggregate several tables. */

data class ExerciseItem(
  val id: Long,
  val name: String,
  val categoryId: Long?,
  val categoryName: String?,
)

data class CategoryItem(
  val id: Long,
  val name: String,
  val exerciseCount: Int,
)

data class WorkoutSummary(
  val id: Long,
  val name: String,
  val position: Int,
  val exerciseCount: Int,
  /** Comma-separated exercise names in workout order, null when the workout is empty. */
  val exerciseNames: String?,
)

data class WorkoutExerciseItem(
  val id: Long,
  val workoutId: Long,
  val exerciseId: Long,
  val position: Int,
  val targetSets: Int,
  val targetRepsMin: Int?,
  val targetRepsMax: Int?,
  val exerciseName: String,
  val categoryName: String?,
)

data class SessionExerciseItem(
  val id: Long,
  val sessionId: Long,
  val exerciseId: Long,
  val position: Int,
  val targetRepsMin: Int?,
  val targetRepsMax: Int?,
  val exerciseName: String,
  val categoryId: Long?,
)

data class SessionSummary(
  val id: Long,
  val name: String,
  val startedAt: Long,
  val finishedAt: Long?,
  val exerciseCount: Int,
  val setCount: Int,
)

/** One completed set of an exercise together with the session it was logged in. */
data class LoggedSet(
  val sessionId: Long,
  val startedAt: Long,
  val weightKg: Double?,
  val reps: Int?,
)
