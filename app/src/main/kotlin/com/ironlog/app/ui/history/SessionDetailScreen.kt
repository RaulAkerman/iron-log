package com.ironlog.app.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ironlog.app.R
import com.ironlog.app.data.SessionRepository
import com.ironlog.app.data.db.SessionEntity
import com.ironlog.app.data.db.SessionExerciseItem
import com.ironlog.app.data.db.SetEntity
import com.ironlog.app.ui.common.BackButton
import com.ironlog.app.ui.common.ConfirmDialog
import com.ironlog.app.ui.common.appViewModel
import com.ironlog.app.ui.common.durationLabel
import com.ironlog.app.ui.common.formatDate
import com.ironlog.app.ui.common.formatTime
import com.ironlog.app.ui.common.historyText
import com.ironlog.app.ui.common.setLabel
import com.ironlog.app.ui.common.shareText
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SessionDetailUiState(
  val loading: Boolean = true,
  /** Null once loaded means the session was deleted. */
  val session: SessionEntity? = null,
  val exercises: List<Pair<SessionExerciseItem, List<SetEntity>>> = emptyList(),
)

class SessionDetailViewModel(private val sessions: SessionRepository, private val sessionId: Long) :
  ViewModel() {
  val uiState: StateFlow<SessionDetailUiState> =
    combine(sessions.session(sessionId), sessions.exercises(sessionId), sessions.sets(sessionId)) {
        session,
        exercises,
        sets ->
        val setsByExercise = sets.groupBy { it.sessionExerciseId }
        SessionDetailUiState(
          loading = false,
          session = session,
          exercises = exercises.map { it to setsByExercise[it.id].orEmpty() },
        )
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SessionDetailUiState())

  /** This workout as readable text. */
  suspend fun text(): String = historyText(sessions.historySets(sessionId))

  fun delete() {
    viewModelScope.launch { sessions.delete(sessionId) }
  }
}

/** A finished workout: when it was and every set that was logged. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionDetailScreen(sessionId: Long, onBack: () -> Unit, modifier: Modifier = Modifier) {
  val viewModel = appViewModel { SessionDetailViewModel(it.sessions, sessionId) }
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  var showDelete by remember { mutableStateOf(false) }

  val session = state.session
  if (!state.loading && session == null) {
    LaunchedEffect(Unit) { onBack() }
    return
  }

  Scaffold(
    modifier = modifier,
    topBar = {
      TopAppBar(
        title = { Text(session?.name.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = { BackButton(onBack) },
        actions = {
          IconButton(onClick = { scope.launch { shareText(context, viewModel.text()) } }) {
            Icon(Icons.Filled.Share, contentDescription = stringResource(R.string.session_share))
          }
          IconButton(onClick = { showDelete = true }) {
            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.session_delete))
          }
        },
      )
    },
  ) { innerPadding ->
    if (session == null) return@Scaffold
    LazyColumn(
      modifier = Modifier.fillMaxSize().padding(innerPadding),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      item(key = "when") {
        Text(
          stringResource(
            R.string.session_detail_when,
            formatDate(session.startedAt),
            formatTime(session.startedAt),
            durationLabel(session.startedAt, session.finishedAt ?: session.startedAt),
          ),
          style = MaterialTheme.typography.bodyLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      items(state.exercises, key = { (exercise, _) -> exercise.id }) { (exercise, sets) ->
        Card(Modifier.fillMaxWidth()) {
          Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(exercise.exerciseName, style = MaterialTheme.typography.titleMedium)
            sets.forEachIndexed { index, set ->
              Row {
                Text(
                  "${index + 1}",
                  style = MaterialTheme.typography.bodyLarge,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  modifier = Modifier.width(32.dp),
                )
                Text(setLabel(set.weightKg, set.reps), style = MaterialTheme.typography.bodyLarge)
              }
            }
          }
        }
      }
    }
  }

  if (showDelete && session != null) {
    ConfirmDialog(
      title = stringResource(R.string.session_delete_title),
      text = stringResource(R.string.session_delete_text),
      confirmLabel = stringResource(R.string.delete),
      onConfirm = {
        showDelete = false
        viewModel.delete()
      },
      onDismiss = { showDelete = false },
    )
  }
}
