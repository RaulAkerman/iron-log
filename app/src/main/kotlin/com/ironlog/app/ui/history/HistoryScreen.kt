package com.ironlog.app.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ironlog.app.R
import com.ironlog.app.data.SessionRepository
import com.ironlog.app.data.db.SessionSummary
import com.ironlog.app.ui.common.EmptyState
import com.ironlog.app.ui.common.appViewModel
import com.ironlog.app.ui.common.durationLabel
import com.ironlog.app.ui.common.formatDate
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class HistoryViewModel(sessions: SessionRepository) : ViewModel() {
  /** Finished sessions, newest first; null until loaded. */
  val sessions: StateFlow<List<SessionSummary>?> =
    sessions.finishedSessions
      .map<List<SessionSummary>, List<SessionSummary>?> { it }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

/** All finished workouts, newest first. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(onOpenSession: (Long) -> Unit, modifier: Modifier = Modifier) {
  val viewModel = appViewModel { HistoryViewModel(it.sessions) }
  val sessions by viewModel.sessions.collectAsStateWithLifecycle()

  Scaffold(
    modifier = modifier,
    topBar = { CenterAlignedTopAppBar(title = { Text(stringResource(R.string.history_title)) }) },
  ) { innerPadding ->
    val loaded = sessions
    when {
      loaded == null -> Unit
      loaded.isEmpty() ->
        EmptyState(
          title = stringResource(R.string.history_empty_title),
          body = stringResource(R.string.history_empty_body),
          modifier = Modifier.padding(innerPadding),
        )
      else ->
        LazyColumn(Modifier.fillMaxSize().padding(innerPadding)) {
          items(loaded, key = { it.id }) { session ->
            ListItem(
              headlineContent = { Text(session.name) },
              overlineContent = { Text(formatDate(session.startedAt)) },
              supportingContent = {
                Text(
                  listOf(
                      pluralStringResource(
                        R.plurals.exercises,
                        session.exerciseCount,
                        session.exerciseCount,
                      ),
                      pluralStringResource(R.plurals.sets, session.setCount, session.setCount),
                      durationLabel(session.startedAt, session.finishedAt ?: session.startedAt),
                    )
                    .joinToString(" · ")
                )
              },
              modifier = Modifier.clickable { onOpenSession(session.id) },
            )
            HorizontalDivider()
          }
        }
    }
  }
}
