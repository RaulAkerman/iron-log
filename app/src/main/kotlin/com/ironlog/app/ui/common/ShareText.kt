package com.ironlog.app.ui.common

import com.ironlog.app.data.db.HistorySet
import com.ironlog.app.data.db.WorkoutEntity
import com.ironlog.app.data.db.WorkoutExerciseItem
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * What is shared out of the app: logged workouts and the cycle as plain text, and the history as a
 * spreadsheet. The wording is English and fixed, like the app's own.
 */

const val HISTORY_TEXT_FILE = "ironlog-history.txt"
const val HISTORY_CSV_FILE = "ironlog-history.csv"

/**
 * Finished workouts as text, the newest first, with an empty line between them:
 * ```
 * Push
 * Wed 18 Nov 2026, 18:05 · 1 h 05 min
 * Bench Press: 80 kg × 8, 80 kg × 7
 * Pull Up: 10 reps, 8 reps
 * ```
 *
 * [sets] come as the database returns them: oldest workout first, each in the order it was done.
 */
fun historyText(sets: List<HistorySet>, zone: ZoneId = ZoneId.systemDefault()): String =
  sets.groupBy { it.sessionId }.values.reversed().joinToString("\n\n") { sessionText(it, zone) }

private fun sessionText(sets: List<HistorySet>, zone: ZoneId): String {
  val session = sets.first()
  val date = formatDate(session.startedAt, zone)
  val time = formatTime(session.startedAt, zone)
  val duration = durationText(durationMinutes(session.startedAt, session.finishedAt))
  return buildString {
    append(session.sessionName)
    append("\n$date, $time · $duration")
    for (exercise in sets.groupBy { it.sessionExerciseId }.values) {
      append("\n${exercise.first().exerciseName}: ")
      append(exercise.joinToString { setText(it.weightKg, it.reps) })
    }
  }
}

/**
 * The history as a spreadsheet: one row per set, the oldest workout first. Fields are separated by
 * commas and weights have a decimal point. A bodyweight set has no weight.
 */
fun historyCsv(sets: List<HistorySet>, zone: ZoneId = ZoneId.systemDefault()): String =
  buildString {
    // A byte order mark, without which Excel does not read the file as UTF-8.
    append('\uFEFF')
    appendCsvRow(
      "Date",
      "Time",
      "Workout",
      "Duration (min)",
      "Exercise",
      "Category",
      "Set",
      "Weight (kg)",
      "Reps",
    )
    for (exercise in sets.groupBy { it.sessionExerciseId }.values) {
      exercise.forEachIndexed { index, set ->
        val start = Instant.ofEpochMilli(set.startedAt).atZone(zone)
        appendCsvRow(
          start.toLocalDate().toString(),
          start.toLocalTime().truncatedTo(ChronoUnit.MINUTES).toString(),
          set.sessionName,
          durationMinutes(set.startedAt, set.finishedAt).toString(),
          set.exerciseName,
          set.categoryName.orEmpty(),
          (index + 1).toString(),
          set.weightKg?.takeIf { it != 0.0 }?.let(::formatKg).orEmpty(),
          set.reps?.toString().orEmpty(),
        )
      }
    }
  }

/** Adds one line; a field with a comma, a quote or a line break in it is put in quotes. */
private fun StringBuilder.appendCsvRow(vararg fields: String) {
  fields.joinTo(this, separator = ",", postfix = "\r\n") { field ->
    if (field.any { it in ",\"\r\n" }) "\"${field.replace("\"", "\"\"")}\"" else field
  }
}

/**
 * A planned workout as text, its name and then each exercise with its target:
 * ```
 * Push
 * Bench Press: 3 × 8–12
 * Plank: 3 sets
 * ```
 */
fun workoutText(name: String, exercises: List<WorkoutExerciseItem>): String = buildString {
  append(name)
  if (exercises.isEmpty()) append("\nNo exercises yet")
  for (exercise in exercises) {
    val target = targetText(exercise.targetSets, exercise.targetRepsMin, exercise.targetRepsMax)
    append("\n${exercise.exerciseName}: $target")
  }
}

/** The cycle as text: its workouts in order and numbered, with an empty line between them. */
fun cycleText(workouts: List<Pair<WorkoutEntity, List<WorkoutExerciseItem>>>): String =
  workouts.withIndex().joinToString("\n\n") { (index, workout) ->
    "${index + 1}. ${workoutText(workout.first.name, workout.second)}"
  }

/** "60 kg × 8", or "8 reps" for a bodyweight set. */
private fun setText(weightKg: Double?, reps: Int?): String {
  val count = reps ?: 0
  return when {
    weightKg != null && weightKg != 0.0 -> "${formatKg(weightKg)} kg × $count"
    count == 1 -> "1 rep"
    else -> "$count reps"
  }
}

/** "3 sets" or "3 × 8–12". */
private fun targetText(sets: Int, repsMin: Int?, repsMax: Int?): String {
  val reps = formatRepRange(repsMin, repsMax)
  return when {
    reps != null -> "$sets × $reps"
    sets == 1 -> "1 set"
    else -> "$sets sets"
  }
}

/** "45 min" or "1 h 05 min". */
private fun durationText(minutes: Int): String =
  if (minutes < 60) "$minutes min"
  else String.format(Locale.ROOT, "%d h %02d min", minutes / 60, minutes % 60)
