package com.ironlog.app.ui.common

import androidx.annotation.StringRes
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import com.ironlog.app.ui.theme.mainCategory
import com.ironlog.app.ui.theme.subCategory

/**
 * An exercise's categories on one line: the main one in green, then the sub categories in orange.
 * The main one also comes first and is a little bolder, so the two can be told apart without
 * seeing the colours. Empty when the exercise has no categories.
 */
@Composable
fun categoryLine(mainName: String?, subNames: List<String>): AnnotatedString {
  val colors = MaterialTheme.colorScheme
  return buildAnnotatedString {
    if (mainName != null) {
      withStyle(SpanStyle(color = colors.mainCategory, fontWeight = FontWeight.Medium)) {
        append(mainName)
      }
    }
    subNames.forEachIndexed { index, name ->
      if (index > 0 || mainName != null) append(" · ")
      withStyle(SpanStyle(color = colors.subCategory)) { append(name) }
    }
  }
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
