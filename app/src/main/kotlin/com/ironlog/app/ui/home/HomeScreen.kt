package com.ironlog.app.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ironlog.app.R
import com.ironlog.app.data.db.SessionEntity
import com.ironlog.app.data.db.SessionSummary
import com.ironlog.app.data.db.WorkoutSummary
import com.ironlog.app.ui.common.ConfirmDialog
import com.ironlog.app.ui.common.EmptyState
import com.ironlog.app.ui.common.appViewModel
import com.ironlog.app.ui.common.daysAgo
import com.ironlog.app.ui.common.formatTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onOpenSession: () -> Unit, onOpenPlan: () -> Unit, modifier: Modifier = Modifier) {
  val viewModel = appViewModel { HomeViewModel(it.plan, it.sessions) }
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  val emptySessionName = stringResource(R.string.session_empty_name)
  var startInstead by remember { mutableStateOf<WorkoutSummary?>(null) }

  Scaffold(
    modifier = modifier,
    topBar = { CenterAlignedTopAppBar(title = { Text(stringResource(R.string.app_name)) }) },
  ) { innerPadding ->
    val active = state.activeSession
    when {
      state.loading -> Unit
      active == null && state.workouts.isEmpty() ->
        EmptyState(
          title = stringResource(R.string.home_no_cycle_title),
          body = stringResource(R.string.home_no_cycle_body),
          modifier = Modifier.padding(innerPadding),
        ) {
          Button(onClick = onOpenPlan) { Text(stringResource(R.string.home_set_up_cycle)) }
          TextButton(onClick = { viewModel.start(null, emptySessionName, onOpenSession) }) {
            Text(stringResource(R.string.home_start_empty))
          }
        }
      else ->
        LazyColumn(
          modifier = Modifier.fillMaxSize().padding(innerPadding),
          contentPadding = PaddingValues(16.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          if (active != null) {
            item(key = "active") { ActiveSessionCard(active, onContinue = onOpenSession) }
          } else {
            state.nextWorkout?.let { next ->
              item(key = "next") {
                NextWorkoutCard(
                  workout = next,
                  onStart = { viewModel.start(next.id, emptySessionName, onOpenSession) },
                )
              }
            }
          }
          state.lastSession?.let { last -> item(key = "last") { LastSessionLine(last) } }
          if (state.workouts.isNotEmpty()) {
            item(key = "cycleHeader") {
              Text(
                stringResource(R.string.home_cycle_header),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 8.dp),
              )
            }
            items(state.workouts, key = { it.id }) { workout ->
              CycleRow(
                workout = workout,
                isNext = workout.id == state.nextWorkoutId,
                // Another workout can only be started while none is running.
                onClick = if (active == null) ({ startInstead = workout }) else null,
              )
            }
          }
          if (active == null) {
            item(key = "empty") {
              TextButton(onClick = { viewModel.start(null, emptySessionName, onOpenSession) }) {
                Text(stringResource(R.string.home_start_empty))
              }
            }
          }
        }
    }
  }

  startInstead?.let { workout ->
    ConfirmDialog(
      title = stringResource(R.string.home_start_other_title, workout.name),
      text = stringResource(R.string.home_start_other_text),
      confirmLabel = stringResource(R.string.start),
      onConfirm = {
        startInstead = null
        viewModel.start(workout.id, emptySessionName, onOpenSession)
      },
      onDismiss = { startInstead = null },
    )
  }
}

@Composable
private fun ActiveSessionCard(session: SessionEntity, onContinue: () -> Unit) {
  Card(
    colors =
      CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
      Text(
        stringResource(R.string.home_in_progress),
        style = MaterialTheme.typography.labelLarge,
      )
      Text(session.name, style = MaterialTheme.typography.headlineSmall)
      Text(
        stringResource(R.string.session_started_at, formatTime(session.startedAt)),
        style = MaterialTheme.typography.bodyMedium,
      )
      Button(onClick = onContinue, modifier = Modifier.padding(top = 8.dp)) {
        Text(stringResource(R.string.home_continue))
      }
    }
  }
}

@Composable
private fun NextWorkoutCard(workout: WorkoutSummary, onStart: () -> Unit) {
  Card(
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
      Text(stringResource(R.string.home_next_up), style = MaterialTheme.typography.labelLarge)
      Text(workout.name, style = MaterialTheme.typography.headlineSmall)
      Text(
        workout.exerciseNames ?: stringResource(R.string.workout_no_exercises),
        style = MaterialTheme.typography.bodyMedium,
      )
      Button(onClick = onStart, modifier = Modifier.padding(top = 8.dp)) {
        Text(stringResource(R.string.home_start_workout))
      }
    }
  }
}

@Composable
private fun LastSessionLine(session: SessionSummary) {
  val days = daysAgo(session.startedAt)
  val whenText =
    when (days) {
      0 -> stringResource(R.string.today)
      1 -> stringResource(R.string.yesterday)
      else -> pluralStringResource(R.plurals.days_ago, days, days)
    }
  Text(
    stringResource(R.string.home_last_workout, session.name, whenText),
    style = MaterialTheme.typography.bodyMedium,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
  )
}

@Composable
private fun CycleRow(workout: WorkoutSummary, isNext: Boolean, onClick: (() -> Unit)?) {
  ListItem(
    headlineContent = { Text(workout.name) },
    supportingContent = {
      Text(
        workout.exerciseNames ?: stringResource(R.string.workout_no_exercises),
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
      )
    },
    leadingContent = {
      Text("${workout.position + 1}", style = MaterialTheme.typography.titleMedium)
    },
    trailingContent = {
      if (isNext) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(
            stringResource(R.string.next_badge),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
          )
        }
      }
    },
    modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
  )
}
