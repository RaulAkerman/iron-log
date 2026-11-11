package com.ironlog.app.data.db

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface PlanDao {
  @Query(
    """
    SELECT w.id, w.name, w.position,
      (SELECT COUNT(*) FROM workout_exercise we WHERE we.workoutId = w.id) AS exerciseCount,
      (SELECT group_concat(n, ', ') FROM
        (SELECT e.name AS n FROM workout_exercise we JOIN exercise e ON e.id = we.exerciseId
         WHERE we.workoutId = w.id ORDER BY we.position)) AS exerciseNames
    FROM workout w
    ORDER BY w.position
    """
  )
  fun observeWorkouts(): Flow<List<WorkoutSummary>>

  @Query("SELECT * FROM workout WHERE id = :id") fun observeWorkout(id: Long): Flow<WorkoutEntity?>

  @Query("SELECT * FROM workout WHERE id = :id") suspend fun getWorkout(id: Long): WorkoutEntity?

  @Query("SELECT * FROM workout ORDER BY position") suspend fun getWorkouts(): List<WorkoutEntity>

  @Insert suspend fun insertWorkout(workout: WorkoutEntity): Long

  @Query("UPDATE workout SET name = :name WHERE id = :id")
  suspend fun renameWorkout(id: Long, name: String)

  @Query("UPDATE workout SET position = :position WHERE id = :id")
  suspend fun setWorkoutPosition(id: Long, position: Int)

  @Query("DELETE FROM workout WHERE id = :id") suspend fun deleteWorkout(id: Long)

  /** The sub categories of each exercise come in no particular order. */
  @Transaction
  @Query(
    """
    SELECT we.id, we.workoutId, we.exerciseId, we.position, we.targetSets, we.targetRepsMin,
      we.targetRepsMax, e.name AS exerciseName, e.categoryId, c.name AS categoryName
    FROM workout_exercise we
      JOIN exercise e ON e.id = we.exerciseId
      LEFT JOIN category c ON c.id = e.categoryId
    WHERE we.workoutId = :workoutId
    ORDER BY we.position
    """
  )
  fun observeWorkoutExercises(workoutId: Long): Flow<List<WorkoutExerciseItem>>

  /** The exercises of every workout, each workout's in order, like [observeWorkoutExercises]. */
  @Transaction
  @Query(
    """
    SELECT we.id, we.workoutId, we.exerciseId, we.position, we.targetSets, we.targetRepsMin,
      we.targetRepsMax, e.name AS exerciseName, e.categoryId, c.name AS categoryName
    FROM workout_exercise we
      JOIN exercise e ON e.id = we.exerciseId
      LEFT JOIN category c ON c.id = e.categoryId
    ORDER BY we.workoutId, we.position
    """
  )
  fun observeAllWorkoutExercises(): Flow<List<WorkoutExerciseItem>>

  @Query("SELECT * FROM workout_exercise WHERE workoutId = :workoutId ORDER BY position")
  suspend fun getWorkoutExercises(workoutId: Long): List<WorkoutExerciseEntity>

  @Query("SELECT * FROM workout_exercise WHERE id = :id")
  suspend fun getWorkoutExercise(id: Long): WorkoutExerciseEntity?

  @Insert suspend fun insertWorkoutExercise(workoutExercise: WorkoutExerciseEntity): Long

  @Query(
    """
    UPDATE workout_exercise
    SET targetSets = :targetSets, targetRepsMin = :targetRepsMin, targetRepsMax = :targetRepsMax
    WHERE id = :id
    """
  )
  suspend fun setTarget(id: Long, targetSets: Int, targetRepsMin: Int?, targetRepsMax: Int?)

  @Query("UPDATE workout_exercise SET position = :position WHERE id = :id")
  suspend fun setWorkoutExercisePosition(id: Long, position: Int)

  @Query("DELETE FROM workout_exercise WHERE id = :id") suspend fun deleteWorkoutExercise(id: Long)
}
