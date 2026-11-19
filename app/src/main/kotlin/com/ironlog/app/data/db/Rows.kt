package com.ironlog.app.data.db

import androidx.room3.Junction
import androidx.room3.Relation

/** Query result rows that join or aggregate several tables. */

data class ExerciseItem(
  val id: Long,
  val name: String,
  /** The main category. */
  val categoryId: Long?,
  val categoryName: String?,
  @Relation(
    entity = CategoryEntity::class,
    parentColumns = ["id"],
    entityColumns = ["id"],
    associateBy =
      Junction(
        ExerciseSubCategoryEntity::class,
        parentColumns = ["exerciseId"],
        entityColumns = ["categoryId"],
      ),
  )
  val subCategories: List<CategoryEntity> = emptyList(),
) {
  /** True when [categoryId] is the main category or one of the sub categories. */
  fun hasCategory(categoryId: Long): Boolean =
    this.categoryId == categoryId || subCategories.any { it.id == categoryId }
}

data class CategoryItem(
  val id: Long,
  val name: String,
  /** Exercises that have it as their main category. */
  val exerciseCount: Int,
  /** Exercises that have it as a sub category. */
  val subExerciseCount: Int = 0,
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
  /** The exercise's main category. */
  val categoryId: Long?,
  val categoryName: String?,
  @Relation(
    entity = CategoryEntity::class,
    parentColumns = ["exerciseId"],
    entityColumns = ["id"],
    associateBy =
      Junction(
        ExerciseSubCategoryEntity::class,
        parentColumns = ["exerciseId"],
        entityColumns = ["categoryId"],
      ),
  )
  val subCategories: List<CategoryEntity> = emptyList(),
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

/**
 * One completed set of a finished session, with what is needed to show it on its own: the session
 * and the exercise it belongs to. History is shared from a list of these.
 */
data class HistorySet(
  val sessionId: Long,
  val sessionName: String,
  val startedAt: Long,
  val finishedAt: Long,
  /** The same for all sets of one exercise of the session. */
  val sessionExerciseId: Long,
  val exerciseName: String,
  /** The exercise's main category. */
  val categoryName: String?,
  val weightKg: Double?,
  val reps: Int?,
)
