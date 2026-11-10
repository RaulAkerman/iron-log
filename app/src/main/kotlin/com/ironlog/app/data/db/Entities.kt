package com.ironlog.app.data.db

import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey

/** A user-defined group of interchangeable exercises, e.g. "Shoulder press". */
@Entity(tableName = "category", indices = [Index(value = ["name"], unique = true)])
data class CategoryEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val name: String,
)

@Entity(
  tableName = "exercise",
  foreignKeys =
    [
      ForeignKey(
        entity = CategoryEntity::class,
        parentColumns = ["id"],
        childColumns = ["categoryId"],
        onDelete = ForeignKey.SET_NULL,
      )
    ],
  indices = [Index("categoryId")],
)
data class ExerciseEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val name: String,
  val categoryId: Long? = null,
  /** Hidden from lists and pickers, kept only because logged sessions still refer to it. */
  val archived: Boolean = false,
)

/** One workout of the rotating cycle. [position] is its place in the cycle order. */
@Entity(tableName = "workout")
data class WorkoutEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val name: String,
  val position: Int,
)

/** A planned exercise inside a workout, with its target sets and rep range. */
@Entity(
  tableName = "workout_exercise",
  foreignKeys =
    [
      ForeignKey(
        entity = WorkoutEntity::class,
        parentColumns = ["id"],
        childColumns = ["workoutId"],
        onDelete = ForeignKey.CASCADE,
      ),
      ForeignKey(
        entity = ExerciseEntity::class,
        parentColumns = ["id"],
        childColumns = ["exerciseId"],
        onDelete = ForeignKey.CASCADE,
      ),
    ],
  indices = [Index("workoutId"), Index("exerciseId")],
)
data class WorkoutExerciseEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val workoutId: Long,
  val exerciseId: Long,
  val position: Int,
  val targetSets: Int,
  val targetRepsMin: Int? = null,
  val targetRepsMax: Int? = null,
)

/**
 * A performed workout. While [finishedAt] is null the session is in progress; there is at most one
 * such session. [name] is a snapshot so history survives renaming or deleting the workout.
 */
@Entity(
  tableName = "session",
  foreignKeys =
    [
      ForeignKey(
        entity = WorkoutEntity::class,
        parentColumns = ["id"],
        childColumns = ["workoutId"],
        onDelete = ForeignKey.SET_NULL,
      )
    ],
  indices = [Index("workoutId"), Index("finishedAt")],
)
data class SessionEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val workoutId: Long?,
  val name: String,
  /** Epoch milliseconds. */
  val startedAt: Long,
  /** Epoch milliseconds, null while in progress. */
  val finishedAt: Long? = null,
)

@Entity(
  tableName = "session_exercise",
  foreignKeys =
    [
      ForeignKey(
        entity = SessionEntity::class,
        parentColumns = ["id"],
        childColumns = ["sessionId"],
        onDelete = ForeignKey.CASCADE,
      ),
      ForeignKey(
        entity = ExerciseEntity::class,
        parentColumns = ["id"],
        childColumns = ["exerciseId"],
        onDelete = ForeignKey.RESTRICT,
      ),
    ],
  indices = [Index("sessionId"), Index("exerciseId")],
)
data class SessionExerciseEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val sessionId: Long,
  val exerciseId: Long,
  val position: Int,
  /** Rep target copied from the workout plan when the session started. */
  val targetRepsMin: Int? = null,
  val targetRepsMax: Int? = null,
)

/**
 * One set. In a running session, sets are pre-created and only count once [done]; finishing the
 * session deletes the rest. A null or zero [weightKg] means bodyweight.
 */
@Entity(
  tableName = "set_entry",
  foreignKeys =
    [
      ForeignKey(
        entity = SessionExerciseEntity::class,
        parentColumns = ["id"],
        childColumns = ["sessionExerciseId"],
        onDelete = ForeignKey.CASCADE,
      )
    ],
  indices = [Index("sessionExerciseId")],
)
data class SetEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val sessionExerciseId: Long,
  val position: Int,
  val weightKg: Double? = null,
  val reps: Int? = null,
  val done: Boolean = false,
)
