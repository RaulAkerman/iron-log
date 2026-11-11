package com.ironlog.app.ui.exercises

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ironlog.app.R
import com.ironlog.app.data.ExerciseRepository
import com.ironlog.app.data.SessionRepository
import com.ironlog.app.data.db.CategoryItem
import com.ironlog.app.data.db.ExerciseItem
import com.ironlog.app.data.db.LoggedSet
import com.ironlog.app.ui.common.BackButton
import com.ironlog.app.ui.common.ConfirmDialog
import com.ironlog.app.ui.common.appViewModel
import com.ironlog.app.ui.common.formatDate
import com.ironlog.app.ui.common.setLabel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The sets of one exercise logged in one session. */
data class SessionSets(val sessionId: Long, val startedAt: Long, val sets: List<LoggedSet>)

/** Groups sets by session, keeping the order in which sessions first appear. */
fun groupBySession(sets: List<LoggedSet>): List<SessionSets> =
  sets
    .groupBy { it.sessionId }
    .map { (sessionId, group) -> SessionSets(sessionId, group.first().startedAt, group) }

/** The heaviest set; among equally heavy sets the one with the most reps. */
fun bestSet(sets: List<LoggedSet>): LoggedSet? =
  sets.maxWithOrNull(compareBy<LoggedSet>({ it.weightKg ?: 0.0 }, { it.reps ?: 0 }))

data class ExerciseDetailUiState(
  val loading: Boolean = true,
  /** Null once loaded means the exercise was deleted. */
  val exercise: ExerciseItem? = null,
  val categories: List<CategoryItem> = emptyList(),
  /** Other exercises with the same main category. */
  val similar: List<ExerciseItem> = emptyList(),
  val history: List<SessionSets> = emptyList(),
  val best: LoggedSet? = null,
)

class ExerciseDetailViewModel(
  private val exercises: ExerciseRepository,
  sessions: SessionRepository,
  private val exerciseId: Long,
) : ViewModel() {
  val uiState: StateFlow<ExerciseDetailUiState> =
    combine(
        exercises.exercise(exerciseId),
        exercises.exercises,
        exercises.categories,
        sessions.exerciseHistory(exerciseId),
      ) { exercise, all, categories, history ->
        // An archived exercise counts as deleted here.
        val visible = exercise?.takeIf { shown -> all.any { it.id == shown.id } }
        ExerciseDetailUiState(
          loading = false,
          exercise = visible,
          categories = categories,
          similar =
            if (visible?.categoryId == null) {
              emptyList()
            } else {
              all.filter { it.categoryId == visible.categoryId && it.id != visible.id }
            },
          history = groupBySession(history),
          best = bestSet(history),
        )
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExerciseDetailUiState())

  suspend fun save(name: String, categoryId: Long?, subCategoryIds: Set<Long>): Boolean =
    exercises.saveExercise(exerciseId, name, categoryId, subCategoryIds)

  fun delete() {
    viewModelScope.launch { exercises.deleteExercise(exerciseId) }
  }
}

/** One exercise: its categories, best set and everything logged for it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseDetailScreen(exerciseId: Long, onBack: () -> Unit, modifier: Modifier = Modifier) {
  val viewModel = appViewModel { ExerciseDetailViewModel(it.exercises, it.sessions, exerciseId) }
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  var showEdit by remember { mutableStateOf(false) }
  var showDelete by remember { mutableStateOf(false) }

  val exercise = state.exercise
  if (!state.loading && exercise == null) {
    LaunchedEffect(Unit) { onBack() }
    return
  }

  Scaffold(
    modifier = modifier,
    topBar = {
      TopAppBar(
        title = { Text(exercise?.name.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = { BackButton(onBack) },
        actions = {
          IconButton(onClick = { showEdit = true }) {
            Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.exercise_edit))
          }
          IconButton(onClick = { showDelete = true }) {
            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.exercise_delete))
          }
        },
      )
    },
  ) { innerPadding ->
    if (exercise == null) return@Scaffold
    LazyColumn(
      modifier = Modifier.fillMaxSize().padding(innerPadding),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      item(key = "category") {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
          Text(
            if (exercise.categoryName == null) {
              stringResource(R.string.category_uncategorised)
            } else {
              stringResource(R.string.category_main_value, exercise.categoryName)
            },
            style = MaterialTheme.typography.titleMedium,
          )
          if (exercise.subCategories.isNotEmpty()) {
            Text(
              stringResource(
                R.string.category_subs_value,
                exercise.subCategories.joinToString(", ") { it.name },
              ),
              style = MaterialTheme.typography.bodyLarge,
            )
          }
          if (state.similar.isNotEmpty()) {
            Text(
              stringResource(
                R.string.exercise_similar,
                state.similar.joinToString(", ") { it.name },
              ),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              // A muscle group can hold dozens of exercises; the history below matters more.
              maxLines = 3,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }
      }
      state.best?.let { best ->
        item(key = "best") {
          Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
              Text(
                stringResource(R.string.exercise_best_set),
                style = MaterialTheme.typography.labelLarge,
              )
              Text(
                setLabel(best.weightKg, best.reps),
                style = MaterialTheme.typography.headlineSmall,
              )
              Text(
                stringResource(
                  R.string.exercise_best_detail,
                  formatDate(best.startedAt),
                  pluralStringResource(
                    R.plurals.sessions,
                    state.history.size,
                    state.history.size,
                  ),
                ),
                style = MaterialTheme.typography.bodyMedium,
              )
            }
          }
        }
      }
      item(key = "historyHeader") {
        Text(stringResource(R.string.history_title), style = MaterialTheme.typography.titleMedium)
      }
      if (state.history.isEmpty()) {
        item(key = "noHistory") {
          Text(
            stringResource(R.string.exercise_no_history),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
      items(state.history, key = { it.sessionId }) { session ->
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
          Text(formatDate(session.startedAt), style = MaterialTheme.typography.labelLarge)
          Text(
            session.sets.map { setLabel(it.weightKg, it.reps) }.joinToString("  ·  "),
            style = MaterialTheme.typography.bodyLarge,
          )
        }
      }
    }
  }

  if (showEdit && exercise != null) {
    ExerciseEditDialog(
      title = stringResource(R.string.exercise_edit),
      initialName = exercise.name,
      initialCategoryId = exercise.categoryId,
      initialSubCategoryIds = exercise.subCategories.mapTo(HashSet()) { it.id },
      categories = state.categories,
      onSave = viewModel::save,
      onDismiss = { showEdit = false },
    )
  }
  if (showDelete && exercise != null) {
    ConfirmDialog(
      title = stringResource(R.string.exercise_delete_title, exercise.name),
      text = stringResource(R.string.exercise_delete_text),
      confirmLabel = stringResource(R.string.delete),
      onConfirm = {
        showDelete = false
        viewModel.delete()
      },
      onDismiss = { showDelete = false },
    )
  }
}
