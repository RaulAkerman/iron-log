package com.ironlog.app.data.db

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {
  // Sessions

  @Query("SELECT * FROM session WHERE finishedAt IS NULL ORDER BY startedAt DESC LIMIT 1")
  fun observeActiveSession(): Flow<SessionEntity?>

  @Query("SELECT * FROM session WHERE finishedAt IS NULL ORDER BY startedAt DESC LIMIT 1")
  suspend fun getActiveSession(): SessionEntity?

  @Query("SELECT * FROM session WHERE id = :id") fun observeSession(id: Long): Flow<SessionEntity?>

  @Insert suspend fun insertSession(session: SessionEntity): Long

  @Query("UPDATE session SET finishedAt = :finishedAt WHERE id = :id")
  suspend fun setFinished(id: Long, finishedAt: Long)

  @Query("DELETE FROM session WHERE id = :id") suspend fun deleteSession(id: Long)

  @Query(
    """
    SELECT s.id, s.name, s.startedAt, s.finishedAt,
      COUNT(DISTINCT se.id) AS exerciseCount, COUNT(st.id) AS setCount
    FROM session s
      LEFT JOIN session_exercise se ON se.sessionId = s.id
      LEFT JOIN set_entry st ON st.sessionExerciseId = se.id
    WHERE s.finishedAt IS NOT NULL
    GROUP BY s.id
    ORDER BY s.startedAt DESC
    """
  )
  fun observeFinishedSessions(): Flow<List<SessionSummary>>

  /** The workout of the cycle that was completed most recently, if it still exists. */
  @Query(
    """
    SELECT workoutId FROM session
    WHERE finishedAt IS NOT NULL AND workoutId IS NOT NULL
    ORDER BY finishedAt DESC LIMIT 1
    """
  )
  fun observeLastCompletedWorkoutId(): Flow<Long?>

  // Exercises of a session

  @Query(
    """
    SELECT se.id, se.sessionId, se.exerciseId, se.position, se.targetRepsMin, se.targetRepsMax,
      e.name AS exerciseName, e.categoryId
    FROM session_exercise se JOIN exercise e ON e.id = se.exerciseId
    WHERE se.sessionId = :sessionId
    ORDER BY se.position
    """
  )
  fun observeSessionExercises(sessionId: Long): Flow<List<SessionExerciseItem>>

  @Query("SELECT * FROM session_exercise WHERE id = :id")
  suspend fun getSessionExercise(id: Long): SessionExerciseEntity?

  @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM session_exercise WHERE sessionId = :sessionId")
  suspend fun nextExercisePosition(sessionId: Long): Int

  @Insert suspend fun insertSessionExercise(sessionExercise: SessionExerciseEntity): Long

  @Query(
    """
    UPDATE session_exercise
    SET exerciseId = :exerciseId, targetRepsMin = NULL, targetRepsMax = NULL
    WHERE id = :id
    """
  )
  suspend fun replaceExercise(id: Long, exerciseId: Long)

  @Query("DELETE FROM session_exercise WHERE id = :id") suspend fun deleteSessionExercise(id: Long)

  @Query(
    """
    DELETE FROM session_exercise
    WHERE sessionId = :sessionId
      AND id NOT IN (SELECT DISTINCT sessionExerciseId FROM set_entry)
    """
  )
  suspend fun deleteExercisesWithoutSets(sessionId: Long)

  // Sets

  @Query(
    """
    SELECT st.* FROM set_entry st JOIN session_exercise se ON se.id = st.sessionExerciseId
    WHERE se.sessionId = :sessionId
    ORDER BY se.position, st.position
    """
  )
  fun observeSets(sessionId: Long): Flow<List<SetEntity>>

  @Query("SELECT * FROM set_entry WHERE sessionExerciseId = :sessionExerciseId ORDER BY position")
  suspend fun getSets(sessionExerciseId: Long): List<SetEntity>

  @Insert suspend fun insertSets(sets: List<SetEntity>)

  @Query("UPDATE set_entry SET weightKg = :weightKg WHERE id = :id")
  suspend fun setWeight(id: Long, weightKg: Double?)

  @Query("UPDATE set_entry SET reps = :reps WHERE id = :id")
  suspend fun setReps(id: Long, reps: Int?)

  @Query("UPDATE set_entry SET weightKg = :weightKg, reps = :reps WHERE id = :id")
  suspend fun setValues(id: Long, weightKg: Double?, reps: Int?)

  @Query("UPDATE set_entry SET done = :done WHERE id = :id")
  suspend fun setDone(id: Long, done: Boolean)

  @Query("DELETE FROM set_entry WHERE id = :id") suspend fun deleteSet(id: Long)

  @Query(
    """
    DELETE FROM set_entry
    WHERE done = 0
      AND sessionExerciseId IN (SELECT id FROM session_exercise WHERE sessionId = :sessionId)
    """
  )
  suspend fun deleteUnfinishedSets(sessionId: Long)

  @Query(
    """
    SELECT COUNT(*) FROM set_entry st JOIN session_exercise se ON se.id = st.sessionExerciseId
    WHERE se.sessionId = :sessionId AND st.done = 1
    """
  )
  suspend fun countDoneSets(sessionId: Long): Int

  // History of one exercise

  /** Sets from the most recent finished session that contained [exerciseId]. */
  @Query(
    """
    SELECT st.* FROM set_entry st
    WHERE st.sessionExerciseId = (
      SELECT se.id FROM session_exercise se JOIN session s ON s.id = se.sessionId
      WHERE se.exerciseId = :exerciseId AND s.finishedAt IS NOT NULL
      ORDER BY s.startedAt DESC, se.position DESC LIMIT 1)
    ORDER BY st.position
    """
  )
  suspend fun getLastSets(exerciseId: Long): List<SetEntity>

  @Query(
    """
    SELECT s.id AS sessionId, s.startedAt, st.weightKg, st.reps
    FROM set_entry st
      JOIN session_exercise se ON se.id = st.sessionExerciseId
      JOIN session s ON s.id = se.sessionId
    WHERE se.exerciseId = :exerciseId AND s.finishedAt IS NOT NULL
    ORDER BY s.startedAt DESC, se.position, st.position
    """
  )
  fun observeExerciseHistory(exerciseId: Long): Flow<List<LoggedSet>>
}
