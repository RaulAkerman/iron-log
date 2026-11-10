package com.ironlog.app.ui.exercises

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ironlog.app.R
import com.ironlog.app.data.ExerciseRepository
import com.ironlog.app.data.db.CategoryItem
import com.ironlog.app.data.db.ExerciseItem
import com.ironlog.app.ui.common.appViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Which part of the exercise list is shown. */
sealed interface CategoryFilter {
  data object All : CategoryFilter

  data object Uncategorised : CategoryFilter

  data class Category(val id: Long) : CategoryFilter
}

/** A run of exercises under one heading; [title] is null for the uncategorised group. */
data class ExerciseSection(val categoryId: Long?, val title: String?, val exercises: List<ExerciseItem>)

data class ExercisesUiState(
  val loading: Boolean = true,
  val categories: List<CategoryItem> = emptyList(),
  val uncategorisedCount: Int = 0,
  val query: String = "",
  val filter: CategoryFilter = CategoryFilter.All,
  val sections: List<ExerciseSection> = emptyList(),
  val selectedIds: Set<Long> = emptySet(),
)

/** Exercises matching [query] and [filter], grouped by category with uncategorised ones last. */
fun buildSections(
  exercises: List<ExerciseItem>,
  query: String,
  filter: CategoryFilter,
): List<ExerciseSection> {
  val words = query.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
  return exercises
    .asSequence()
    .filter { exercise -> words.all { exercise.name.contains(it, ignoreCase = true) } }
    .filter { exercise ->
      when (filter) {
        CategoryFilter.All -> true
        CategoryFilter.Uncategorised -> exercise.categoryId == null
        is CategoryFilter.Category -> exercise.categoryId == filter.id
      }
    }
    .groupBy { it.categoryId }
    .map { (categoryId, group) -> ExerciseSection(categoryId, group.first().categoryName, group) }
    .sortedWith(compareBy<ExerciseSection> { it.title == null }.thenBy { it.title?.lowercase() })
}

class ExercisesViewModel(private val repository: ExerciseRepository) : ViewModel() {
  private val query = MutableStateFlow("")
  private val filter = MutableStateFlow<CategoryFilter>(CategoryFilter.All)
  private val selectedIds = MutableStateFlow(emptySet<Long>())

  val uiState: StateFlow<ExercisesUiState> =
    combine(repository.exercises, repository.categories, query, filter, selectedIds) {
        exercises,
        categories,
        query,
        filter,
        selected ->
        // A filter on a category that was deleted meanwhile falls back to everything.
        val validFilter =
          if (filter is CategoryFilter.Category && categories.none { it.id == filter.id }) {
            CategoryFilter.All
          } else {
            filter
          }
        ExercisesUiState(
          loading = false,
          categories = categories,
          uncategorisedCount = exercises.count { it.categoryId == null },
          query = query,
          filter = validFilter,
          sections = buildSections(exercises, query, validFilter),
          selectedIds = selected intersect exercises.mapTo(HashSet()) { it.id },
        )
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExercisesUiState())

  fun setQuery(text: String) {
    query.value = text
  }

  fun setFilter(newFilter: CategoryFilter) {
    filter.value = newFilter
  }

  fun toggleSelected(exerciseId: Long) {
    selectedIds.update { if (exerciseId in it) it - exerciseId else it + exerciseId }
  }

  fun clearSelection() {
    selectedIds.value = emptySet()
  }

  /** Moves the selected exercises into a category (null = none) and ends the selection. */
  fun categoriseSelected(categoryId: Long?) {
    val ids = uiState.value.selectedIds
    clearSelection()
    viewModelScope.launch { repository.setCategory(ids, categoryId) }
  }

  /** Creates a category named [name] and moves the selected exercises into it. */
  fun categoriseSelectedIntoNew(name: String) {
    val ids = uiState.value.selectedIds
    clearSelection()
    viewModelScope.launch {
      val categoryId = repository.createCategory(name) ?: return@launch
      repository.setCategory(ids, categoryId)
    }
  }

  suspend fun createExercise(name: String, categoryId: Long?): Boolean =
    repository.saveExercise(id = null, name = name, categoryId = categoryId)
}

/** The exercise list: search, filter by category, and bulk-assign categories. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExercisesScreen(
  onOpenExercise: (Long) -> Unit,
  onOpenCategories: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val viewModel = appViewModel { ExercisesViewModel(it.exercises) }
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  val selecting = state.selectedIds.isNotEmpty()
  var showAddDialog by remember { mutableStateOf(false) }
  var showCategoryPicker by remember { mutableStateOf(false) }

  BackHandler(enabled = selecting) { viewModel.clearSelection() }

  Scaffold(
    modifier = modifier,
    topBar = {
      if (selecting) {
        TopAppBar(
          title = { Text(stringResource(R.string.selected_count, state.selectedIds.size)) },
          navigationIcon = {
            IconButton(onClick = viewModel::clearSelection) {
              Icon(
                Icons.Filled.Close,
                contentDescription = stringResource(R.string.selection_clear),
              )
            }
          },
          actions = {
            TextButton(onClick = { showCategoryPicker = true }) {
              Text(stringResource(R.string.category_set))
            }
          },
        )
      } else {
        TopAppBar(
          title = { Text(stringResource(R.string.exercises_title)) },
          actions = {
            TextButton(onClick = onOpenCategories) {
              Text(stringResource(R.string.categories_title))
            }
          },
        )
      }
    },
    floatingActionButton = {
      if (!selecting) {
        FloatingActionButton(onClick = { showAddDialog = true }) {
          Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.exercise_add))
        }
      }
    },
  ) { innerPadding ->
    // imePadding keeps the end of the list reachable while the keyboard is open for searching.
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
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
      )
      CategoryFilterRow(state, onSelect = viewModel::setFilter)
      if (!state.loading && state.sections.isEmpty()) {
        Text(
          stringResource(R.string.exercises_none_found),
          style = MaterialTheme.typography.bodyLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(16.dp),
        )
      }
      LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
        for (section in state.sections) {
          stickyHeader(key = "header-${section.categoryId}") {
            SectionHeader(
              section.title ?: stringResource(R.string.category_uncategorised),
              section.exercises.size,
            )
          }
          items(section.exercises, key = { it.id }) { exercise ->
            ExerciseRow(
              exercise = exercise,
              selecting = selecting,
              selected = exercise.id in state.selectedIds,
              onClick = {
                if (selecting) viewModel.toggleSelected(exercise.id) else onOpenExercise(exercise.id)
              },
              onLongClick = { viewModel.toggleSelected(exercise.id) },
            )
          }
        }
      }
    }
  }

  if (showAddDialog) {
    ExerciseEditDialog(
      title = stringResource(R.string.exercise_add),
      initialName = "",
      initialCategoryId = (state.filter as? CategoryFilter.Category)?.id,
      categories = state.categories,
      onSave = viewModel::createExercise,
      onDismiss = { showAddDialog = false },
    )
  }
  if (showCategoryPicker) {
    CategoryPickerDialog(
      categories = state.categories,
      onPick = { categoryId ->
        showCategoryPicker = false
        viewModel.categoriseSelected(categoryId)
      },
      onCreate = { name ->
        showCategoryPicker = false
        viewModel.categoriseSelectedIntoNew(name)
      },
      onDismiss = { showCategoryPicker = false },
    )
  }
}

@Composable
private fun CategoryFilterRow(state: ExercisesUiState, onSelect: (CategoryFilter) -> Unit) {
  LazyRow(
    contentPadding = PaddingValues(horizontal = 16.dp),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    item(key = "all") {
      FilterChip(
        selected = state.filter == CategoryFilter.All,
        onClick = { onSelect(CategoryFilter.All) },
        label = { Text(stringResource(R.string.filter_all)) },
      )
    }
    item(key = "none") {
      FilterChip(
        selected = state.filter == CategoryFilter.Uncategorised,
        onClick = { onSelect(CategoryFilter.Uncategorised) },
        label = {
          Text(stringResource(R.string.filter_uncategorised, state.uncategorisedCount))
        },
      )
    }
    items(state.categories, key = { it.id }) { category ->
      FilterChip(
        selected = state.filter == CategoryFilter.Category(category.id),
        onClick = { onSelect(CategoryFilter.Category(category.id)) },
        label = {
          Text(stringResource(R.string.filter_category, category.name, category.exerciseCount))
        },
      )
    }
  }
}

@Composable
internal fun SectionHeader(title: String, count: Int) {
  Text(
    stringResource(R.string.section_header, title, count),
    style = MaterialTheme.typography.titleSmall,
    color = MaterialTheme.colorScheme.primary,
    // Opaque, so rows scrolling underneath a pinned header do not show through.
    modifier =
      Modifier.fillMaxWidth()
        .background(MaterialTheme.colorScheme.surface)
        .padding(horizontal = 16.dp, vertical = 8.dp),
  )
}

@Composable
private fun ExerciseRow(
  exercise: ExerciseItem,
  selecting: Boolean,
  selected: Boolean,
  onClick: () -> Unit,
  onLongClick: () -> Unit,
) {
  ListItem(
    headlineContent = { Text(exercise.name) },
    leadingContent =
      if (selecting) ({ Checkbox(checked = selected, onCheckedChange = null) }) else null,
    modifier = Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick),
  )
}
