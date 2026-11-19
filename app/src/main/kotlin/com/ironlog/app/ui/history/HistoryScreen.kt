package com.ironlog.app.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ironlog.app.R
import com.ironlog.app.data.SessionRepository
import com.ironlog.app.data.db.SessionSummary
import com.ironlog.app.ui.common.EmptyState
import com.ironlog.app.ui.common.HISTORY_CSV_FILE
import com.ironlog.app.ui.common.HISTORY_TEXT_FILE
import com.ironlog.app.ui.common.appViewModel
import com.ironlog.app.ui.common.durationLabel
import com.ironlog.app.ui.common.formatDate
import com.ironlog.app.ui.common.historyCsv
import com.ironlog.app.ui.common.historyText
import com.ironlog.app.ui.common.shareAsFile
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HistoryViewModel(private val repository: SessionRepository) : ViewModel() {
  /** Finished sessions, newest first; null until loaded. */
  val sessions: StateFlow<List<SessionSummary>?> =
    repository.finishedSessions
      .map<List<SessionSummary>, List<SessionSummary>?> { it }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

  /** Every finished workout as readable text. */
  suspend fun text(): String = historyText(repository.historySets())

  /** Every logged set as a spreadsheet. */
  suspend fun csv(): String = historyCsv(repository.historySets())
}

/** All finished workouts, newest first. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(onOpenSession: (Long) -> Unit, modifier: Modifier = Modifier) {
  val viewModel = appViewModel { HistoryViewModel(it.sessions) }
  val sessions by viewModel.sessions.collectAsStateWithLifecycle()
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  var shareMenuOpen by remember { mutableStateOf(false) }

  Scaffold(
    modifier = modifier,
    topBar = {
      CenterAlignedTopAppBar(
        title = { Text(stringResource(R.string.history_title)) },
        actions = {
          if (sessions.isNullOrEmpty()) return@CenterAlignedTopAppBar
          // The whole history goes out as a file: as a message it would soon be too long.
          Box {
            IconButton(onClick = { shareMenuOpen = true }) {
              Icon(Icons.Filled.Share, contentDescription = stringResource(R.string.history_share))
            }
            DropdownMenu(expanded = shareMenuOpen, onDismissRequest = { shareMenuOpen = false }) {
              DropdownMenuItem(
                text = { Text(stringResource(R.string.history_share_text)) },
                onClick = {
                  shareMenuOpen = false
                  scope.launch {
                    shareAsFile(context, HISTORY_TEXT_FILE, "text/plain", viewModel.text())
                  }
                },
              )
              DropdownMenuItem(
                text = { Text(stringResource(R.string.history_share_csv)) },
                onClick = {
                  shareMenuOpen = false
                  scope.launch { shareAsFile(context, HISTORY_CSV_FILE, "text/csv", viewModel.csv()) }
                },
              )
            }
          }
        },
      )
    },
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
