package com.ironlog.app.data

import androidx.room3.withWriteTransaction
import com.ironlog.app.data.db.HistorySet
import com.ironlog.app.data.db.IronLogDatabase
import com.ironlog.app.data.db.LoggedSet
import com.ironlog.app.data.db.SessionEntity
import com.ironlog.app.data.db.SessionExerciseEntity
import com.ironlog.app.data.db.SessionExerciseItem
import com.ironlog.app.data.db.SessionSummary
import com.ironlog.app.data.db.SetEntity
import kotlinx.coroutines.flow.Flow

/** Running and finished workout sessions. Every change is written straight to the database. */
class SessionRepository(
  private val db: IronLogDatabase,
  private val now: () -> Long = System::currentTimeMillis,
) {
  private val dao = db.sessionDao()
  private val planDao = db.planDao()

  /** The session in progress, null when there is none. */
  val activeSession: Flow<SessionEntity?> = dao.observeActiveSession()

  /** Finished sessions, newest first. */
  val finishedSessions: Flow<List<SessionSummary>> = dao.observeFinishedSessions()

  fun session(id: Long): Flow<SessionEntity?> = dao.observeSession(id)

  fun exercises(sessionId: Long): Flow<List<SessionExerciseItem>> =
    dao.observeSessionExercises(sessionId)

  fun sets(sessionId: Long): Flow<List<SetEntity>> = dao.observeSets(sessionId)

  /** All completed sets of an exercise across finished sessions, newest session first. */
  fun exerciseHistory(exerciseId: Long): Flow<List<LoggedSet>> =
    dao.observeExerciseHistory(exerciseId)

  /**
   * Every set of the finished sessions, or of [sessionId] alone: oldest session first, and within
   * a session in the order the sets were done.
   */
  suspend fun historySets(sessionId: Long? = null): List<HistorySet> =
    dao.getHistorySets(sessionId)

  /** What was done the last time this exercise was part of a finished session. */
  suspend fun lastSets(exerciseId: Long): List<PlannedSet> =
    dao.getLastSets(exerciseId).map { PlannedSet(it.weightKg, it.reps) }

  /**
   * Starts a session from a workout of the cycle, or an empty one named [emptyName] when
   * [workoutId] is null. If a session is already running, that one is returned instead.
   */
  suspend fun start(workoutId: Long?, emptyName: String): Long =
    db.withWriteTransaction {
      dao.getActiveSession()?.let {
        return@withWriteTransaction it.id
      }
      val workout = workoutId?.let { planDao.getWorkout(it) }
      val sessionId =
        dao.insertSession(
          SessionEntity(
            workoutId = workout?.id,
            name = workout?.name ?: emptyName,
            startedAt = now(),
          )
        )
      if (workout != null) {
        planDao.getWorkoutExercises(workout.id).forEachIndexed { index, planned ->
          insertExercise(
            sessionId = sessionId,
            exerciseId = planned.exerciseId,
            position = index,
            setCount = planned.targetSets,
            repsMin = planned.targetRepsMin,
            repsMax = planned.targetRepsMax,
          )
        }
      }
      sessionId
    }

  /** Appends exercises that are not part of the plan to a running session. */
  suspend fun addExercises(sessionId: Long, exerciseIds: List<Long>) {
    db.withWriteTransaction {
      var position = dao.nextExercisePosition(sessionId)
      for (exerciseId in exerciseIds) {
        insertExercise(sessionId, exerciseId, position++, setCount = null)
      }
    }
  }

  /**
   * Swaps the exercise of a session entry, e.g. when the planned machine is taken. Sets already
   * done stay as logged; the remaining ones are pre-filled from the new exercise's last session.
   */
  suspend fun replaceExercise(sessionExerciseId: Long, exerciseId: Long) {
    db.withWriteTransaction {
      dao.replaceExercise(sessionExerciseId, exerciseId)
      val sets = dao.getSets(sessionExerciseId)
      val planned = planSets(sets.size, lastSets(exerciseId))
      sets.forEachIndexed { index, set ->
        if (!set.done) dao.setValues(set.id, planned[index].weightKg, planned[index].reps)
      }
    }
  }

  suspend fun removeExercise(sessionExerciseId: Long) = dao.deleteSessionExercise(sessionExerciseId)

  /** Adds a set that starts with the values of the set before it. */
  suspend fun addSet(sessionExerciseId: Long) {
    db.withWriteTransaction {
      val previous = dao.getSets(sessionExerciseId).lastOrNull()
      dao.insertSets(
        listOf(
          SetEntity(
            sessionExerciseId = sessionExerciseId,
            position = (previous?.position ?: -1) + 1,
            weightKg = previous?.weightKg,
            reps = previous?.reps,
          )
        )
      )
    }
  }

  suspend fun removeSet(setId: Long) = dao.deleteSet(setId)

  suspend fun setWeight(setId: Long, weightKg: Double?) = dao.setWeight(setId, weightKg)

  suspend fun setReps(setId: Long, reps: Int?) = dao.setReps(setId, reps)

  suspend fun setDone(setId: Long, done: Boolean) = dao.setDone(setId, done)

  /**
   * Finishes the session, keeping only the sets marked done. A session without any done set is
   * deleted instead; returns whether a session was saved.
   */
  suspend fun finish(sessionId: Long): Boolean =
    db.withWriteTransaction {
      if (dao.countDoneSets(sessionId) == 0) {
        dao.deleteSession(sessionId)
        false
      } else {
        dao.deleteUnfinishedSets(sessionId)
        dao.deleteExercisesWithoutSets(sessionId)
        dao.setFinished(sessionId, now())
        true
      }
    }

  /** Deletes a session, running or finished, with everything logged in it. */
  suspend fun delete(sessionId: Long) = dao.deleteSession(sessionId)

  private suspend fun insertExercise(
    sessionId: Long,
    exerciseId: Long,
    position: Int,
    setCount: Int?,
    repsMin: Int? = null,
    repsMax: Int? = null,
  ) {
    val lastTime = lastSets(exerciseId)
    val sessionExerciseId =
      dao.insertSessionExercise(
        SessionExerciseEntity(
          sessionId = sessionId,
          exerciseId = exerciseId,
          position = position,
          targetRepsMin = repsMin,
          targetRepsMax = repsMax,
        )
      )
    val count = setCount ?: lastTime.size.takeIf { it > 0 } ?: PlanRepository.DEFAULT_SET_COUNT
    dao.insertSets(
      planSets(count, lastTime).mapIndexed { index, planned ->
        SetEntity(
          sessionExerciseId = sessionExerciseId,
          position = index,
          weightKg = planned.weightKg,
          reps = planned.reps,
        )
      }
    )
  }
}
