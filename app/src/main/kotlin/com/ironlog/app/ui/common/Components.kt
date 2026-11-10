package com.ironlog.app.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ironlog.app.AppContainer
import com.ironlog.app.IronLogApp
import com.ironlog.app.R

/** Creates a screen's ViewModel with access to the app's repositories. */
@Composable
inline fun <reified VM : ViewModel> appViewModel(crossinline create: (AppContainer) -> VM): VM {
  val container = (LocalContext.current.applicationContext as IronLogApp).container
  return viewModel { create(container) }
}

@Composable
fun BackButton(onBack: () -> Unit) {
  IconButton(onClick = onBack) {
    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
  }
}

/** Centered title and explanation for a screen or section that has nothing to show yet. */
@Composable
fun EmptyState(
  title: String,
  body: String,
  modifier: Modifier = Modifier,
  action: @Composable () -> Unit = {},
) {
  Column(
    modifier = modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 24.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
    Text(
      body,
      style = MaterialTheme.typography.bodyLarge,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      textAlign = TextAlign.Center,
    )
    action()
  }
}

/** Yes/no question before something is deleted or discarded. */
@Composable
fun ConfirmDialog(
  title: String,
  text: String,
  confirmLabel: String,
  onConfirm: () -> Unit,
  onDismiss: () -> Unit,
  dismissLabel: String = stringResource(R.string.cancel),
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(title) },
    text = { Text(text) },
    confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel) } },
    dismissButton = { TextButton(onClick = onDismiss) { Text(dismissLabel) } },
  )
}

/**
 * Dialog with a single text field, used to name or rename things. [onConfirm] returns null on
 * success, or the error to show under the field; the dialog only closes on success.
 */
@Composable
fun NameDialog(
  title: String,
  label: String,
  initialName: String,
  confirmLabel: String,
  onConfirm: suspend (String) -> String?,
  onDismiss: () -> Unit,
) {
  var name by remember { mutableStateOf(initialName) }
  var error by remember { mutableStateOf<String?>(null) }
  var submitted by remember { mutableStateOf<String?>(null) }
  val focusRequester = remember { FocusRequester() }

  LaunchedEffect(Unit) { focusRequester.requestFocus() }
  LaunchedEffect(submitted) {
    val value = submitted ?: return@LaunchedEffect
    val failure = onConfirm(value)
    if (failure == null) onDismiss() else error = failure
    submitted = null
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(title) },
    text = {
      OutlinedTextField(
        value = name,
        onValueChange = {
          name = it
          error = null
        },
        label = { Text(label) },
        singleLine = true,
        isError = error != null,
        supportingText = error?.let { message -> { Text(message) } },
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
      )
    },
    confirmButton = {
      TextButton(onClick = { submitted = name }, enabled = name.isNotBlank()) { Text(confirmLabel) }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
  )
}

/** "60 kg × 8", or "8 reps" for a bodyweight set. */
@Composable
fun setLabel(weightKg: Double?, reps: Int?): String {
  val count = reps ?: 0
  return if (weightKg == null || weightKg == 0.0) {
    pluralStringResource(R.plurals.reps, count, count)
  } else {
    stringResource(R.string.set_weight_reps, formatKg(weightKg), count)
  }
}

/** "3 sets" or "3 × 8–12". */
@Composable
fun targetLabel(sets: Int, repsMin: Int?, repsMax: Int?): String {
  val reps = formatRepRange(repsMin, repsMax)
  return if (reps == null) {
    pluralStringResource(R.plurals.sets, sets, sets)
  } else {
    stringResource(R.string.target_sets_reps, sets, reps)
  }
}

/** "45 min" or "1 h 05 min". */
@Composable
fun durationLabel(startMillis: Long, endMillis: Long): String {
  val minutes = durationMinutes(startMillis, endMillis)
  return if (minutes < 60) {
    stringResource(R.string.duration_minutes, minutes)
  } else {
    stringResource(R.string.duration_hours_minutes, minutes / 60, minutes % 60)
  }
}
