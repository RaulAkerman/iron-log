package com.ironlog.app.ui.common

import androidx.annotation.StringRes
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import com.ironlog.app.R
import com.ironlog.app.data.WorkoutMuscles
import com.ironlog.app.ui.theme.mainCategory
import com.ironlog.app.ui.theme.subCategory

/**
 * An exercise's categories on one line: the main one in green, then the sub categories in orange.
 * The main one also comes first and is a little bolder, so the two can be told apart without
 * seeing the colours. Empty when the exercise has no categories.
 */
@Composable
fun categoryLine(mainName: String?, subNames: List<String>): AnnotatedString =
  categoryLine(listOfNotNull(mainName), subNames)

/** Like the line for one exercise, for several main categories: the muscles of a workout. */
@Composable
fun categoryLine(mainNames: List<String>, subNames: List<String>): AnnotatedString {
  val colors = MaterialTheme.colorScheme
  val main = SpanStyle(color = colors.mainCategory, fontWeight = FontWeight.Medium)
  val sub = SpanStyle(color = colors.subCategory)
  return buildAnnotatedString {
    for ((index, name) in (mainNames + subNames).withIndex()) {
      if (index > 0) append(" · ")
      withStyle(if (index < mainNames.size) main else sub) { append(name) }
    }
  }
}

/**
 * A workout's preview: the muscles it trains, or how many exercises it has when none of them has a
 * category.
 */
@Composable
fun workoutMusclesLine(muscles: WorkoutMuscles?, exerciseCount: Int): AnnotatedString =
  when {
    exerciseCount == 0 -> AnnotatedString(stringResource(R.string.workout_no_exercises))
    muscles == null || (muscles.main.isEmpty() && muscles.other.isEmpty()) ->
      AnnotatedString(pluralStringResource(R.plurals.exercises, exerciseCount, exerciseCount))
    else -> categoryLine(muscles.main, muscles.other)
  }

/** The string [id] filled in with [value], which is shown in [color]: "Main category: Chest". */
@Composable
fun stringResourceWithColoredValue(@StringRes id: Int, value: String, color: Color): AnnotatedString {
  val template = stringResource(id, PLACEHOLDER)
  val at = template.indexOf(PLACEHOLDER)
  if (at < 0) return AnnotatedString(stringResource(id, value))
  return buildAnnotatedString {
    append(template, 0, at)
    withStyle(SpanStyle(color = color)) { append(value) }
    append(template, at + PLACEHOLDER.length, template.length)
  }
}

private const val PLACEHOLDER = "\u0000"
