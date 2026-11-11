package com.ironlog.app.data

import androidx.room3.withWriteTransaction
import com.ironlog.app.data.db.IronLogDatabase
import com.ironlog.app.data.db.WorkoutEntity
import com.ironlog.app.data.db.WorkoutExerciseEntity
import com.ironlog.app.data.db.WorkoutExerciseItem
import com.ironlog.app.data.db.WorkoutSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/** The rotating cycle: its workouts, their order and the exercises planned in each. */
class PlanRepository(private val db: IronLogDatabase) {
  private val dao = db.planDao()

  val workouts: Flow<List<WorkoutSummary>> = dao.observeWorkouts()

  /** Id of the workout that is next in the cycle, null while the cycle is empty. */
  val nextWorkoutId: Flow<Long?> =
    combine(workouts, db.sessionDao().observeLastCompletedWorkoutId()) { workouts, lastCompleted ->
      nextWorkoutId(workouts.map { it.id }, lastCompleted)
    }

  fun workout(id: Long): Flow<WorkoutEntity?> = dao.observeWorkout(id)

  /** What each workout trains, by workout id; a workout without exercises is left out. */
  val workoutMuscles: Flow<Map<Long, WorkoutMuscles>> =
    dao.observeAllWorkoutExercises().map { all ->
      all
        .groupBy { it.workoutId }
        .mapValues { (_, exercises) -> workoutMuscles(exercises.map { it.toPlannedExercise() }) }
    }

  fun workoutExercises(workoutId: Long): Flow<List<WorkoutExerciseItem>> =
    dao.observeWorkoutExercises(workoutId).map { list ->
      list.map { it.copy(subCategories = it.subCategories.sortedByName()) }
    }

  /** Appends a workout to the end of the cycle and returns its id. */
  suspend fun createWorkout(name: String): Long =
    db.withWriteTransaction {
      dao.insertWorkout(WorkoutEntity(name = name.trim(), position = dao.getWorkouts().size))
    }

  suspend fun renameWorkout(id: Long, name: String) {
    if (name.isNotBlank()) dao.renameWorkout(id, name.trim())
  }

  suspend fun deleteWorkout(id: Long) {
    db.withWriteTransaction {
      dao.deleteWorkout(id)
      renumberWorkouts(dao.getWorkouts())
    }
  }

  /** Moves a workout earlier (negative [delta]) or later in the cycle. */
  suspend fun moveWorkout(id: Long, delta: Int) {
    db.withWriteTransaction {
      val workouts = dao.getWorkouts()
      renumberWorkouts(moved(workouts, workouts.indexOfFirst { it.id == id }, delta))
    }
  }

  suspend fun addExercises(workoutId: Long, exerciseIds: List<Long>) {
    db.withWriteTransaction {
      var position = dao.getWorkoutExercises(workoutId).size
      for (exerciseId in exerciseIds) {
        dao.insertWorkoutExercise(
          WorkoutExerciseEntity(
            workoutId = workoutId,
            exerciseId = exerciseId,
            position = position++,
            targetSets = DEFAULT_SET_COUNT,
          )
        )
      }
    }
  }

  suspend fun setTarget(workoutExerciseId: Long, sets: Int, repsMin: Int?, repsMax: Int?) {
    dao.setTarget(workoutExerciseId, sets.coerceIn(1, MAX_SET_COUNT), repsMin, repsMax)
  }

  suspend fun removeExercise(workoutExerciseId: Long) {
    db.withWriteTransaction {
      val removed = dao.getWorkoutExercise(workoutExerciseId) ?: return@withWriteTransaction
      dao.deleteWorkoutExercise(workoutExerciseId)
      renumberExercises(dao.getWorkoutExercises(removed.workoutId))
    }
  }

  /** Moves an exercise earlier (negative [delta]) or later within its workout. */
  suspend fun moveExercise(workoutExerciseId: Long, delta: Int) {
    db.withWriteTransaction {
      val entry = dao.getWorkoutExercise(workoutExerciseId) ?: return@withWriteTransaction
      val exercises = dao.getWorkoutExercises(entry.workoutId)
      renumberExercises(moved(exercises, exercises.indexOfFirst { it.id == entry.id }, delta))
    }
  }

  private suspend fun renumberWorkouts(ordered: List<WorkoutEntity>) {
    ordered.forEachIndexed { index, workout ->
      if (workout.position != index) dao.setWorkoutPosition(workout.id, index)
    }
  }

  private suspend fun renumberExercises(ordered: List<WorkoutExerciseEntity>) {
    ordered.forEachIndexed { index, exercise ->
      if (exercise.position != index) dao.setWorkoutExercisePosition(exercise.id, index)
    }
  }

  companion object {
    const val DEFAULT_SET_COUNT = 3
    const val MAX_SET_COUNT = 20
  }
}

private fun WorkoutExerciseItem.toPlannedExercise() =
  PlannedExercise(
    sets = targetSets,
    main = categoryId?.let { Muscle(it, categoryName.orEmpty()) },
    subs = subCategories.map { Muscle(it.id, it.name) },
  )
