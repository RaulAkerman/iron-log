package com.ironlog.app.ui.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ironlog.app.data.PlannedSet
import com.ironlog.app.data.SessionRepository
import com.ironlog.app.data.db.SessionEntity
import com.ironlog.app.data.db.SessionExerciseItem
import com.ironlog.app.data.db.SetEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One exercise of the running session with its sets and what was done last time. */
data class SessionExerciseUi(
  val item: SessionExerciseItem,
  val sets: List<SetEntity>,
  val lastTime: List<PlannedSet>,
)

data class SessionUiState(
  val loading: Boolean = true,
  /** Null once loaded means there is no session in progress (any more). */
  val session: SessionEntity? = null,
  val exercises: List<SessionExerciseUi> = emptyList(),
) {
  val doneSetCount: Int
    get() = exercises.sumOf { exercise -> exercise.sets.count { it.done } }
}

class SessionViewModel(private val sessions: SessionRepository) : ViewModel() {
  // Finished sessions cannot change while this one is running, so each exercise is looked up once.
  private val lastTimeCache = HashMap<Long, List<PlannedSet>>()

  @OptIn(ExperimentalCoroutinesApi::class)
  val uiState: StateFlow<SessionUiState> =
    sessions.activeSession
      .flatMapLatest { session ->
        if (session == null) {
          flowOf(SessionUiState(loading = false))
        } else {
          combine(sessions.exercises(session.id), sessions.sets(session.id)) { exercises, sets ->
            val setsByExercise = sets.groupBy { it.sessionExerciseId }
            SessionUiState(
              loading = false,
              session = session,
              exercises =
                exercises.map { item ->
                  SessionExerciseUi(
                    item = item,
                    sets = setsByExercise[item.id].orEmpty(),
                    lastTime =
                      lastTimeCache.getOrPut(item.exerciseId) { sessions.lastSets(item.exerciseId) },
                  )
                },
            )
          }
        }
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SessionUiState())

  // Edits are applied strictly in the order they were made, so a slow write can never land after a
  // newer one for the same field.
  private val edits = Channel<suspend () -> Unit>(Channel.UNLIMITED)

  init {
    viewModelScope.launch { for (edit in edits) edit() }
  }

  fun setWeight(setId: Long, weightKg: Double?) {
    edits.trySend { sessions.setWeight(setId, weightKg) }
  }

  fun setReps(setId: Long, reps: Int?) {
    edits.trySend { sessions.setReps(setId, reps) }
  }

  fun setDone(setId: Long, done: Boolean) {
    edits.trySend { sessions.setDone(setId, done) }
  }

  fun addSet(sessionExerciseId: Long) {
    edits.trySend { sessions.addSet(sessionExerciseId) }
  }

  fun removeSet(setId: Long) {
    edits.trySend { sessions.removeSet(setId) }
  }

  fun removeExercise(sessionExerciseId: Long) {
    edits.trySend { sessions.removeExercise(sessionExerciseId) }
  }

  /** Saves the session (or drops it if nothing was done). The screen closes once it is gone. */
  fun finish() {
    val sessionId = uiState.value.session?.id ?: return
    edits.trySend { sessions.finish(sessionId) }
  }

  fun discard() {
    val sessionId = uiState.value.session?.id ?: return
    edits.trySend { sessions.delete(sessionId) }
  }
}
