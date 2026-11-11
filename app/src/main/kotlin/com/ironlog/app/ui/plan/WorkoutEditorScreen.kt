package com.ironlog.app.ui.plan

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ironlog.app.R
import com.ironlog.app.data.PlanRepository
import com.ironlog.app.data.db.WorkoutEntity
import com.ironlog.app.data.db.WorkoutExerciseItem
import com.ironlog.app.ui.common.BackButton
import com.ironlog.app.ui.common.ConfirmDialog
import com.ironlog.app.ui.common.EmptyState
import com.ironlog.app.ui.common.NameDialog
import com.ironlog.app.ui.common.appViewModel
import com.ironlog.app.ui.common.categoryLine
import com.ironlog.app.ui.common.parseReps
import com.ironlog.app.ui.common.repRangeOf
import com.ironlog.app.ui.common.targetLabel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class WorkoutEditorUiState(
  val loading: Boolean = true,
  /** Null once loaded means the workout was deleted. */
  val workout: WorkoutEntity? = null,
  val exercises: List<WorkoutExerciseItem> = emptyList(),
)

class WorkoutEditorViewModel(private val plan: PlanRepository, private val workoutId: Long) :
  ViewModel() {
  val uiState: StateFlow<WorkoutEditorUiState> =
    combine(plan.workout(workoutId), plan.workoutExercises(workoutId)) { workout, exercises ->
        WorkoutEditorUiState(loading = false, workout = workout, exercises = exercises)
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WorkoutEditorUiState())

  suspend fun rename(name: String) = plan.renameWorkout(workoutId, name)

  fun delete() {
    viewModelScope.launch { plan.deleteWorkout(workoutId) }
  }

  fun setTarget(workoutExerciseId: Long, sets: Int, repsMin: Int?, repsMax: Int?) {
    viewModelScope.launch { plan.setTarget(workoutExerciseId, sets, repsMin, repsMax) }
  }

  fun move(workoutExerciseId: Long, delta: Int) {
    viewModelScope.launch { plan.moveExercise(workoutExerciseId, delta) }
  }

  fun remove(workoutExerciseId: Long) {
    viewModelScope.launch { plan.removeExercise(workoutExerciseId) }
  }
}

/** One workout of the cycle: its name, exercises, their order and targets. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutEditorScreen(
  workoutId: Long,
  onBack: () -> Unit,
  onAddExercises: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val viewModel = appViewModel { WorkoutEditorViewModel(it.plan, workoutId) }
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  var showRename by remember { mutableStateOf(false) }
  var showDelete by remember { mutableStateOf(false) }
  var editingTarget by remember { mutableStateOf<WorkoutExerciseItem?>(null) }

  val workout = state.workout
  if (!state.loading && workout == null) {
    LaunchedEffect(Unit) { onBack() }
    return
  }

  Scaffold(
    modifier = modifier,
    topBar = {
      TopAppBar(
        title = { Text(workout?.name.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = { BackButton(onBack) },
        actions = {
          IconButton(onClick = { showRename = true }) {
            Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.rename))
          }
          IconButton(onClick = { showDelete = true }) {
            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.workout_delete))
          }
        },
      )
    },
    floatingActionButton = {
      ExtendedFloatingActionButton(
        onClick = onAddExercises,
        // The button exposes only the icon's description to accessibility services, not its text.
        icon = { Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.add_exercises)) },
        text = { Text(stringResource(R.string.add_exercises)) },
      )
    },
  ) { innerPadding ->
    when {
      state.loading -> Unit
      state.exercises.isEmpty() ->
        EmptyState(
          title = stringResource(R.string.workout_empty_title),
          body = stringResource(R.string.workout_empty_body),
          modifier = Modifier.padding(innerPadding),
        )
      else ->
        LazyColumn(
          modifier = Modifier.fillMaxSize().padding(innerPadding),
          contentPadding = PaddingValues(bottom = 96.dp),
        ) {
          itemsIndexed(state.exercises, key = { _, exercise -> exercise.id }) { index, exercise ->
            PlannedExerciseRow(
              exercise = exercise,
              canMoveUp = index > 0,
              canMoveDown = index < state.exercises.lastIndex,
              onClick = { editingTarget = exercise },
              onMove = { delta -> viewModel.move(exercise.id, delta) },
              onRemove = { viewModel.remove(exercise.id) },
            )
            HorizontalDivider()
          }
        }
    }
  }

  if (showRename && workout != null) {
    NameDialog(
      title = stringResource(R.string.workout_rename),
      label = stringResource(R.string.name_label),
      initialName = workout.name,
      confirmLabel = stringResource(R.string.save),
      onConfirm = { name ->
        viewModel.rename(name)
        null
      },
      onDismiss = { showRename = false },
    )
  }
  if (showDelete && workout != null) {
    ConfirmDialog(
      title = stringResource(R.string.workout_delete_title, workout.name),
      text = stringResource(R.string.workout_delete_text),
      confirmLabel = stringResource(R.string.delete),
      onConfirm = {
        showDelete = false
        viewModel.delete()
      },
      onDismiss = { showDelete = false },
    )
  }
  editingTarget?.let { exercise ->
    TargetDialog(
      exercise = exercise,
      onSave = { sets, repsMin, repsMax ->
        viewModel.setTarget(exercise.id, sets, repsMin, repsMax)
        editingTarget = null
      },
      onDismiss = { editingTarget = null },
    )
  }
}

@Composable
private fun PlannedExerciseRow(
  exercise: WorkoutExerciseItem,
  canMoveUp: Boolean,
  canMoveDown: Boolean,
  onClick: () -> Unit,
  onMove: (Int) -> Unit,
  onRemove: () -> Unit,
) {
  val target = targetLabel(exercise.targetSets, exercise.targetRepsMin, exercise.targetRepsMax)
  ListItem(
    headlineContent = { Text(exercise.exerciseName) },
    supportingContent = {
      Column {
        Text(target)
        val categories = categoryLine(exercise.categoryName, exercise.subCategories.map { it.name })
        if (categories.isNotEmpty()) Text(categories)
      }
    },
    trailingContent = {
      Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onMove(-1) }, enabled = canMoveUp) {
          Icon(
            Icons.Filled.KeyboardArrowUp,
            contentDescription = stringResource(R.string.move_earlier, exercise.exerciseName),
          )
        }
        IconButton(onClick = { onMove(1) }, enabled = canMoveDown) {
          Icon(
            Icons.Filled.KeyboardArrowDown,
            contentDescription = stringResource(R.string.move_later, exercise.exerciseName),
          )
        }
        IconButton(onClick = onRemove) {
          Icon(
            Icons.Filled.Close,
            contentDescription = stringResource(R.string.remove_named, exercise.exerciseName),
          )
        }
      }
    },
    modifier = Modifier.clickable(onClick = onClick),
  )
}

/** Edits the planned number of sets and the rep target of one exercise. */
@Composable
private fun TargetDialog(
  exercise: WorkoutExerciseItem,
  onSave: (sets: Int, repsMin: Int?, repsMax: Int?) -> Unit,
  onDismiss: () -> Unit,
) {
  var sets by remember { mutableStateOf(exercise.targetSets.toString()) }
  var repsFrom by remember { mutableStateOf(exercise.targetRepsMin?.toString().orEmpty()) }
  var repsTo by remember { mutableStateOf(exercise.targetRepsMax?.toString().orEmpty()) }
  val setCount = sets.toIntOrNull()?.takeIf { it in 1..PlanRepository.MAX_SET_COUNT }
  val digitsOnly = { text: String -> text.filter(Char::isDigit).take(3) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(exercise.exerciseName) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
          value = sets,
          onValueChange = { sets = digitsOnly(it) },
          label = { Text(stringResource(R.string.target_sets_label)) },
          singleLine = true,
          isError = setCount == null,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        Row(
          horizontalArrangement = Arrangement.spacedBy(12.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          OutlinedTextField(
            value = repsFrom,
            onValueChange = { repsFrom = digitsOnly(it) },
            label = { Text(stringResource(R.string.target_reps_from_label)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f),
          )
          OutlinedTextField(
            value = repsTo,
            onValueChange = { repsTo = digitsOnly(it) },
            label = { Text(stringResource(R.string.target_reps_to_label)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f),
          )
        }
        Text(stringResource(R.string.target_reps_hint))
      }
    },
    confirmButton = {
      TextButton(
        enabled = setCount != null,
        onClick = {
          val (repsMin, repsMax) = repRangeOf(parseReps(repsFrom), parseReps(repsTo))
          onSave(setCount ?: exercise.targetSets, repsMin, repsMax)
        },
      ) {
        Text(stringResource(R.string.save))
      }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
  )
}
