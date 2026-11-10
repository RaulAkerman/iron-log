package com.ironlog.app.ui.session

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ironlog.app.R
import com.ironlog.app.data.db.SetEntity
import com.ironlog.app.ui.common.BackButton
import com.ironlog.app.ui.common.ConfirmDialog
import com.ironlog.app.ui.common.EmptyState
import com.ironlog.app.ui.common.appViewModel
import com.ironlog.app.ui.common.formatKg
import com.ironlog.app.ui.common.formatRepRange
import com.ironlog.app.ui.common.formatTime
import com.ironlog.app.ui.common.parseKg
import com.ironlog.app.ui.common.parseReps
import com.ironlog.app.ui.common.setLabel

/** The workout in progress: tick off sets, adjust weight and reps, swap or add exercises. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionScreen(
  onBack: () -> Unit,
  onAddExercises: (sessionId: Long) -> Unit,
  onReplaceExercise: (sessionExerciseId: Long, exerciseId: Long) -> Unit,
  modifier: Modifier = Modifier,
) {
  val viewModel = appViewModel { SessionViewModel(it.sessions) }
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  var showFinish by remember { mutableStateOf(false) }
  var showDiscard by remember { mutableStateOf(false) }
  var menuOpen by remember { mutableStateOf(false) }

  // Finished or discarded: nothing left to show here.
  val session = state.session
  if (!state.loading && session == null) {
    LaunchedEffect(Unit) { onBack() }
    return
  }

  Scaffold(
    modifier = modifier,
    topBar = {
      TopAppBar(
        title = {
          Column {
            Text(session?.name.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (session != null) {
              Text(
                stringResource(R.string.session_started_at, formatTime(session.startedAt)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        },
        navigationIcon = { BackButton(onBack) },
        actions = {
          TextButton(onClick = { showFinish = true }) {
            Text(stringResource(R.string.session_finish))
          }
          Box {
            IconButton(onClick = { menuOpen = true }) {
              Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.more))
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
              DropdownMenuItem(
                text = { Text(stringResource(R.string.session_discard)) },
                onClick = {
                  menuOpen = false
                  showDiscard = true
                },
              )
            }
          }
        },
      )
    },
  ) { innerPadding ->
    if (session == null) return@Scaffold
    LazyColumn(
      modifier = Modifier.fillMaxSize().padding(innerPadding).imePadding(),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      if (state.exercises.isEmpty()) {
        item(key = "empty") {
          EmptyState(
            title = stringResource(R.string.session_empty_title),
            body = stringResource(R.string.session_empty_body),
            modifier = Modifier.fillMaxWidth(),
          )
        }
      }
      items(state.exercises, key = { it.item.id }) { exercise ->
        ExerciseCard(
          exercise = exercise,
          onWeightChange = viewModel::setWeight,
          onRepsChange = viewModel::setReps,
          onDoneChange = viewModel::setDone,
          onAddSet = { viewModel.addSet(exercise.item.id) },
          onRemoveSet = viewModel::removeSet,
          onReplace = { onReplaceExercise(exercise.item.id, exercise.item.exerciseId) },
          onRemove = { viewModel.removeExercise(exercise.item.id) },
        )
      }
      item(key = "add") {
        OutlinedButton(onClick = { onAddExercises(session.id) }, modifier = Modifier.fillMaxWidth()) {
          Icon(Icons.Filled.Add, contentDescription = null)
          Text(stringResource(R.string.session_add_exercise), Modifier.padding(start = 8.dp))
        }
      }
    }
  }

  if (showFinish) {
    val done = state.doneSetCount
    ConfirmDialog(
      title = stringResource(R.string.session_finish_title),
      text =
        if (done == 0) {
          stringResource(R.string.session_finish_nothing)
        } else {
          pluralStringResource(R.plurals.session_finish_text, done, done)
        },
      confirmLabel =
        stringResource(if (done == 0) R.string.session_discard else R.string.session_finish),
      dismissLabel = stringResource(R.string.session_keep_going),
      onConfirm = {
        showFinish = false
        viewModel.finish()
      },
      onDismiss = { showFinish = false },
    )
  }
  if (showDiscard) {
    ConfirmDialog(
      title = stringResource(R.string.session_discard_title),
      text = stringResource(R.string.session_discard_text),
      confirmLabel = stringResource(R.string.session_discard),
      dismissLabel = stringResource(R.string.session_keep_going),
      onConfirm = {
        showDiscard = false
        viewModel.discard()
      },
      onDismiss = { showDiscard = false },
    )
  }
}

@Composable
private fun ExerciseCard(
  exercise: SessionExerciseUi,
  onWeightChange: (setId: Long, weightKg: Double?) -> Unit,
  onRepsChange: (setId: Long, reps: Int?) -> Unit,
  onDoneChange: (setId: Long, done: Boolean) -> Unit,
  onAddSet: () -> Unit,
  onRemoveSet: (setId: Long) -> Unit,
  onReplace: () -> Unit,
  onRemove: () -> Unit,
) {
  var menuOpen by remember { mutableStateOf(false) }
  val repTarget = formatRepRange(exercise.item.targetRepsMin, exercise.item.targetRepsMax)

  Card(Modifier.fillMaxWidth()) {
    Column(Modifier.padding(start = 16.dp, top = 4.dp, end = 4.dp, bottom = 8.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
          exercise.item.exerciseName,
          style = MaterialTheme.typography.titleMedium,
          modifier = Modifier.weight(1f),
        )
        Box {
          IconButton(onClick = { menuOpen = true }) {
            Icon(
              Icons.Filled.MoreVert,
              contentDescription =
                stringResource(R.string.session_exercise_options, exercise.item.exerciseName),
            )
          }
          DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
              text = { Text(stringResource(R.string.session_replace_exercise)) },
              onClick = {
                menuOpen = false
                onReplace()
              },
            )
            DropdownMenuItem(
              text = { Text(stringResource(R.string.session_remove_exercise)) },
              onClick = {
                menuOpen = false
                onRemove()
              },
            )
          }
        }
      }
      Column(Modifier.padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val lastTime =
          exercise.lastTime.map { setLabel(it.weightKg, it.reps) }.joinToString(", ")
        val details =
          listOfNotNull(
            repTarget?.let { stringResource(R.string.session_target_reps, it) },
            if (exercise.lastTime.isEmpty()) {
              stringResource(R.string.session_first_time)
            } else {
              stringResource(R.string.session_last_time, lastTime)
            },
          )
        Text(
          details.joinToString("\n"),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        exercise.sets.forEachIndexed { index, set ->
          key(set.id) {
            SetRow(
              number = index + 1,
              set = set,
              repsPlaceholder = repTarget,
              onWeightChange = { onWeightChange(set.id, it) },
              onRepsChange = { onRepsChange(set.id, it) },
              onDoneChange = { onDoneChange(set.id, it) },
            )
          }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          TextButton(onClick = onAddSet) { Text(stringResource(R.string.session_add_set)) }
          val lastSet = exercise.sets.lastOrNull()
          if (lastSet != null) {
            TextButton(onClick = { onRemoveSet(lastSet.id) }) {
              Text(stringResource(R.string.session_remove_set))
            }
          }
        }
      }
    }
  }
}

@Composable
private fun SetRow(
  number: Int,
  set: SetEntity,
  repsPlaceholder: String?,
  onWeightChange: (Double?) -> Unit,
  onRepsChange: (Int?) -> Unit,
  onDoneChange: (Boolean) -> Unit,
) {
  Row(
    modifier =
      Modifier.fillMaxWidth()
        .background(
          if (set.done) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
          RoundedCornerShape(12.dp),
        )
        .padding(horizontal = 4.dp, vertical = 2.dp),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      "$number",
      style = MaterialTheme.typography.titleMedium,
      textAlign = TextAlign.Center,
      modifier = Modifier.width(24.dp),
    )
    NumberField(
      value = set.weightKg,
      format = ::formatKg,
      parse = ::parseKg,
      allowDecimals = true,
      label = stringResource(R.string.unit_kg),
      placeholder = null,
      description = stringResource(R.string.session_weight_of_set, number),
      onValueChange = onWeightChange,
      modifier = Modifier.weight(1f),
    )
    NumberField(
      value = set.reps,
      format = Int::toString,
      parse = ::parseReps,
      allowDecimals = false,
      label = stringResource(R.string.unit_reps),
      placeholder = repsPlaceholder,
      description = stringResource(R.string.session_reps_of_set, number),
      onValueChange = onRepsChange,
      modifier = Modifier.weight(1f),
    )
    // A set needs reps before it can count.
    FilledIconToggleButton(
      checked = set.done,
      onCheckedChange = onDoneChange,
      enabled = set.done || set.reps != null,
    ) {
      Icon(
        Icons.Filled.Check,
        contentDescription =
          stringResource(
            if (set.done) R.string.session_set_done else R.string.session_set_mark_done,
            number,
          ),
      )
    }
  }
}

/**
 * Numeric text field bound to a stored value. While the field has focus, the typed text is the
 * source of truth, so "62." or an empty field are not overwritten by the value coming back from the
 * database. Without focus it follows the stored value (for example after swapping exercises).
 */
@Composable
private fun <T : Any> NumberField(
  value: T?,
  format: (T) -> String,
  parse: (String) -> T?,
  allowDecimals: Boolean,
  label: String,
  placeholder: String?,
  description: String,
  onValueChange: (T?) -> Unit,
  modifier: Modifier = Modifier,
) {
  var text by remember { mutableStateOf(value?.let(format).orEmpty()) }
  var focused by remember { mutableStateOf(false) }
  LaunchedEffect(value, focused) {
    if (!focused && parse(text) != value) text = value?.let(format).orEmpty()
  }

  OutlinedTextField(
    value = text,
    onValueChange = { input ->
      val cleaned =
        input.filter { it.isDigit() || (allowDecimals && (it == '.' || it == ',')) }.take(7)
      text = cleaned
      onValueChange(parse(cleaned))
    },
    label = { Text(label) },
    placeholder = placeholder?.let { hint -> { Text(hint) } },
    singleLine = true,
    textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center),
    keyboardOptions =
      KeyboardOptions(
        keyboardType = if (allowDecimals) KeyboardType.Decimal else KeyboardType.Number,
        imeAction = ImeAction.Next,
      ),
    modifier =
      modifier
        .onFocusChanged { focused = it.isFocused }
        .semantics { contentDescription = description },
  )
}
