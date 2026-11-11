package com.ironlog.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ironlog.app.data.PlanRepository
import com.ironlog.app.data.SessionRepository
import com.ironlog.app.data.WorkoutMuscles
import com.ironlog.app.data.db.SessionEntity
import com.ironlog.app.data.db.SessionSummary
import com.ironlog.app.data.db.WorkoutSummary
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiState(
  val loading: Boolean = true,
  val activeSession: SessionEntity? = null,
  /** The cycle in order. */
  val workouts: List<WorkoutSummary> = emptyList(),
  val nextWorkoutId: Long? = null,
  val lastSession: SessionSummary? = null,
  /** What each workout trains, by workout id. */
  val muscles: Map<Long, WorkoutMuscles> = emptyMap(),
) {
  val nextWorkout: WorkoutSummary?
    get() = workouts.firstOrNull { it.id == nextWorkoutId }
}

class HomeViewModel(plan: PlanRepository, private val sessions: SessionRepository) : ViewModel() {
  val uiState: StateFlow<HomeUiState> =
    combine(
        sessions.activeSession,
        plan.workouts,
        plan.nextWorkoutId,
        sessions.finishedSessions,
        plan.workoutMuscles,
      ) { active, workouts, nextWorkoutId, finished, muscles ->
        HomeUiState(
          loading = false,
          activeSession = active,
          workouts = workouts,
          nextWorkoutId = nextWorkoutId,
          lastSession = finished.firstOrNull(),
          muscles = muscles,
        )
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

  /** Starts a session for [workoutId], or an empty one when null, then calls [onStarted]. */
  fun start(workoutId: Long?, emptySessionName: String, onStarted: () -> Unit) {
    viewModelScope.launch {
      sessions.start(workoutId, emptySessionName)
      onStarted()
    }
  }
}
