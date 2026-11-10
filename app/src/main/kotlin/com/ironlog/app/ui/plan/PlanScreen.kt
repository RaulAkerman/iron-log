package com.ironlog.app.ui.plan

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ironlog.app.R
import com.ironlog.app.data.PlanRepository
import com.ironlog.app.data.db.WorkoutSummary
import com.ironlog.app.ui.common.EmptyState
import com.ironlog.app.ui.common.NameDialog
import com.ironlog.app.ui.common.appViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PlanUiState(
  val loading: Boolean = true,
  val workouts: List<WorkoutSummary> = emptyList(),
  val nextWorkoutId: Long? = null,
)

class PlanViewModel(private val plan: PlanRepository) : ViewModel() {
  val uiState: StateFlow<PlanUiState> =
    combine(plan.workouts, plan.nextWorkoutId) { workouts, next ->
        PlanUiState(loading = false, workouts = workouts, nextWorkoutId = next)
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlanUiState())

  suspend fun createWorkout(name: String): Long = plan.createWorkout(name)

  fun move(workoutId: Long, delta: Int) {
    viewModelScope.launch { plan.moveWorkout(workoutId, delta) }
  }
}

/** The rotating cycle: its workouts in order. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanScreen(onOpenWorkout: (Long) -> Unit, modifier: Modifier = Modifier) {
  val viewModel = appViewModel { PlanViewModel(it.plan) }
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  var showAddDialog by remember { mutableStateOf(false) }

  Scaffold(
    modifier = modifier,
    topBar = { CenterAlignedTopAppBar(title = { Text(stringResource(R.string.plan_title)) }) },
    floatingActionButton = {
      ExtendedFloatingActionButton(
        onClick = { showAddDialog = true },
        // The button exposes only the icon's description to accessibility services, not its text.
        icon = {
          Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.plan_add_workout))
        },
        text = { Text(stringResource(R.string.plan_add_workout)) },
      )
    },
  ) { innerPadding ->
    when {
      state.loading -> Unit
      state.workouts.isEmpty() ->
        EmptyState(
          title = stringResource(R.string.plan_empty_title),
          body = stringResource(R.string.plan_empty_body),
          modifier = Modifier.padding(innerPadding),
        )
      else ->
        LazyColumn(
          modifier = Modifier.fillMaxSize().padding(innerPadding),
          // Bottom space keeps the last card clear of the floating button.
          contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 96.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          item(key = "hint") {
            Text(
              stringResource(R.string.plan_hint),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(bottom = 4.dp),
            )
          }
          itemsIndexed(state.workouts, key = { _, workout -> workout.id }) { index, workout ->
            WorkoutCard(
              workout = workout,
              isNext = workout.id == state.nextWorkoutId,
              canMoveUp = index > 0,
              canMoveDown = index < state.workouts.lastIndex,
              onClick = { onOpenWorkout(workout.id) },
              onMove = { delta -> viewModel.move(workout.id, delta) },
            )
          }
        }
    }
  }

  if (showAddDialog) {
    // Suggests "Workout A", "Workout B", ... for the first 26 workouts.
    val letter = ('A' + state.workouts.size).takeIf { it <= 'Z' }
    NameDialog(
      title = stringResource(R.string.plan_add_workout),
      label = stringResource(R.string.name_label),
      initialName = letter?.let { stringResource(R.string.plan_default_name, it.toString()) }.orEmpty(),
      confirmLabel = stringResource(R.string.add),
      onConfirm = { name ->
        onOpenWorkout(viewModel.createWorkout(name))
        null
      },
      onDismiss = { showAddDialog = false },
    )
  }
}

@Composable
private fun WorkoutCard(
  workout: WorkoutSummary,
  isNext: Boolean,
  canMoveUp: Boolean,
  canMoveDown: Boolean,
  onClick: () -> Unit,
  onMove: (Int) -> Unit,
) {
  Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
    Row(
      modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        "${workout.position + 1}",
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(end = 16.dp),
      )
      Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            workout.name,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
          )
          if (isNext) {
            Text(
              stringResource(R.string.next_badge),
              style = MaterialTheme.typography.labelLarge,
              color = MaterialTheme.colorScheme.primary,
            )
          }
        }
        Text(
          workout.exerciseNames ?: stringResource(R.string.workout_no_exercises),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
      }
      Column {
        IconButton(onClick = { onMove(-1) }, enabled = canMoveUp) {
          Icon(
            Icons.Filled.KeyboardArrowUp,
            contentDescription = stringResource(R.string.move_earlier, workout.name),
          )
        }
        IconButton(onClick = { onMove(1) }, enabled = canMoveDown) {
          Icon(
            Icons.Filled.KeyboardArrowDown,
            contentDescription = stringResource(R.string.move_later, workout.name),
          )
        }
      }
    }
  }
}
