package com.ironlog.app.ui.exercises

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ironlog.app.ExercisePicker
import com.ironlog.app.R
import com.ironlog.app.data.ExerciseRepository
import com.ironlog.app.data.PlanRepository
import com.ironlog.app.data.SessionRepository
import com.ironlog.app.data.db.ExerciseItem
import com.ironlog.app.ui.common.BackButton
import com.ironlog.app.ui.common.appViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ExercisePickerUiState(
  val loading: Boolean = true,
  val query: String = "",
  val sections: List<ExerciseSection> = emptyList(),
  /** In the order they were ticked, which is the order they get added in. */
  val selectedIds: List<Long> = emptyList(),
  val folding: SectionFolding = SectionFolding(),
)

/**
 * The sections open in the picker: as the user left them ([opened]), or before the first tap on a
 * heading, the main category of the exercise being replaced, which holds its stand-ins.
 */
fun pickerOpenSections(opened: Set<Long?>?, replaced: ExerciseItem?): Set<Long?> =
  opened ?: setOfNotNull(replaced?.categoryId)

class ExercisePickerViewModel(
  exercises: ExerciseRepository,
  private val plan: PlanRepository,
  private val sessions: SessionRepository,
  private val target: ExercisePicker,
) : ViewModel() {
  private val query = MutableStateFlow("")
  private val selectedIds = MutableStateFlow(emptyList<Long>())
  /** Null until a heading is first tapped, see [pickerOpenSections]. */
  private val openSections = MutableStateFlow<Set<Long?>?>(null)

  /** True when picking a single replacement instead of ticking several exercises to add. */
  val replacing: Boolean = target.replaceSessionExerciseId != null

  val uiState: StateFlow<ExercisePickerUiState> =
    combine(exercises.exercises, query, selectedIds, openSections) { all, query, selected, opened ->
        val replaced = all.firstOrNull { it.id == target.replacedExerciseId }
        val sections =
          buildSections(all.filter { it.id != replaced?.id }, query, CategoryFilter.All)
            // The category of the exercise being replaced comes first: those are its stand-ins.
            .sortedByDescending { replaced?.categoryId != null && it.categoryId == replaced.categoryId }
        ExercisePickerUiState(
          loading = false,
          query = query,
          sections = sections,
          selectedIds = selected,
          folding =
            SectionFolding(
              folded = sectionsFold(query, CategoryFilter.All),
              open = pickerOpenSections(opened, replaced),
            ),
        )
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExercisePickerUiState())

  fun setQuery(text: String) {
    query.value = text
  }

  fun toggleSelected(exerciseId: Long) {
    selectedIds.update { if (exerciseId in it) it - exerciseId else it + exerciseId }
  }

  /** Opens or closes the section of main category [categoryId] (null = uncategorised). */
  fun toggleSection(categoryId: Long?) {
    openSections.update { (it ?: uiState.value.folding.open).toggle(categoryId) }
  }

  /** Adds the ticked exercises to the workout or running session, then calls [onDone]. */
  fun addSelected(onDone: () -> Unit) {
    val ids = selectedIds.value
    viewModelScope.launch {
      when {
        target.workoutId != null -> plan.addExercises(target.workoutId, ids)
        target.sessionId != null -> sessions.addExercises(target.sessionId, ids)
      }
      onDone()
    }
  }

  /** Swaps the session's exercise for [exerciseId], then calls [onDone]. */
  fun replaceWith(exerciseId: Long, onDone: () -> Unit) {
    val sessionExerciseId = target.replaceSessionExerciseId ?: return
    viewModelScope.launch {
      sessions.replaceExercise(sessionExerciseId, exerciseId)
      onDone()
    }
  }
}

/** Chooses exercises for a workout or the running session; see [ExercisePicker]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExercisePickerScreen(target: ExercisePicker, onDone: () -> Unit, modifier: Modifier = Modifier) {
  val viewModel = appViewModel {
    ExercisePickerViewModel(it.exercises, it.plan, it.sessions, target)
  }
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  val listState = rememberLazyListState()
  val scope = rememberCoroutineScope()

  Scaffold(
    modifier = modifier,
    topBar = {
      TopAppBar(
        title = {
          Text(
            stringResource(
              if (viewModel.replacing) R.string.picker_replace_title else R.string.add_exercises
            )
          )
        },
        navigationIcon = { BackButton(onDone) },
        actions = {
          if (!viewModel.replacing) {
            TextButton(
              onClick = { viewModel.addSelected(onDone) },
              enabled = state.selectedIds.isNotEmpty(),
            ) {
              Text(stringResource(R.string.picker_add_count, state.selectedIds.size))
            }
          }
        },
      )
    },
  ) { innerPadding ->
    Column(Modifier.fillMaxSize().padding(innerPadding).imePadding()) {
      OutlinedTextField(
        value = state.query,
        onValueChange = viewModel::setQuery,
        placeholder = { Text(stringResource(R.string.exercise_search)) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
          if (state.query.isNotEmpty()) {
            IconButton(onClick = { viewModel.setQuery("") }) {
              Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.search_clear))
            }
          }
        },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
      )
      if (!state.loading && state.sections.isEmpty()) {
        Text(
          stringResource(R.string.exercises_none_found),
          style = MaterialTheme.typography.bodyLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(16.dp),
        )
      }
      LazyColumn(Modifier.fillMaxSize(), state = listState) {
        exerciseSections(
          state.sections,
          state.folding,
          listState,
          scope,
          onToggle = viewModel::toggleSection,
        ) { exercise ->
          ListItem(
            headlineContent = { Text(exercise.name) },
            supportingContent = categoriesContent(exercise),
            leadingContent =
              if (viewModel.replacing) {
                null
              } else {
                { Checkbox(checked = exercise.id in state.selectedIds, onCheckedChange = null) }
              },
            modifier =
              Modifier.clickable {
                if (viewModel.replacing) {
                  viewModel.replaceWith(exercise.id, onDone)
                } else {
                  viewModel.toggleSelected(exercise.id)
                }
              },
          )
        }
      }
    }
  }
}
