package com.ironlog.app

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

// Top-level destinations, shown in the bottom bar.

@Serializable data object Home : NavKey

@Serializable data object Plan : NavKey

@Serializable data object Exercises : NavKey

@Serializable data object History : NavKey

@Serializable data object Supplements : NavKey

// Screens opened on top of them.

@Serializable data class WorkoutEditor(val workoutId: Long) : NavKey

@Serializable data object Categories : NavKey

@Serializable data class ExerciseDetail(val exerciseId: Long) : NavKey

/** The workout session in progress. */
@Serializable data object ActiveSession : NavKey

@Serializable data class SessionDetail(val sessionId: Long) : NavKey

/** Adds a supplement when [supplementId] is null, otherwise edits that one. */
@Serializable data class SupplementEdit(val supplementId: Long? = null) : NavKey

/**
 * Exercise chooser. Exactly one target is set: add to a planned workout, add to the running
 * session, or replace one exercise of the running session.
 */
@Serializable
data class ExercisePicker(
  val workoutId: Long? = null,
  val sessionId: Long? = null,
  val replaceSessionExerciseId: Long? = null,
  /** When replacing: the exercise being replaced, so its category can be offered first. */
  val replacedExerciseId: Long? = null,
) : NavKey
