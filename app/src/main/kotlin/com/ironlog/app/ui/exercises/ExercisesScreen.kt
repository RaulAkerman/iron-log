package com.ironlog.app.ui.exercises

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ironlog.app.R
import com.ironlog.app.data.ExerciseRepository
import com.ironlog.app.data.db.CategoryItem
import com.ironlog.app.data.db.ExerciseItem
import com.ironlog.app.ui.common.appViewModel
import com.ironlog.app.ui.common.categoryLine
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
  /** The exercises behind [selectedIds], also those the search or filter currently hides. */
  val selectedExercises: List<ExerciseItem> = emptyList(),
  /** Whether the sections open and close, see [sectionsFold]. */
  val sectionsFold: Boolean = true,
  /** The sections opened in the full list, by main category id (null = uncategorised). */
  val openSections: Set<Long?> = emptySet(),
) {
  /** Whether [section] shows its exercises or only its heading. */
  fun isOpen(section: ExerciseSection): Boolean =
    !sectionsFold || section.categoryId in openSections
}

/**
 * Exercises matching [query] and [filter], grouped by main category with uncategorised ones last.
 * A category filter keeps the exercises that have it as main or as sub category, and puts the
 * group of those that have it as main category first.
 */
fun buildSections(
  exercises: List<ExerciseItem>,
  query: String,
  filter: CategoryFilter,
): List<ExerciseSection> {
  val words = query.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
  val filterId = (filter as? CategoryFilter.Category)?.id
  return exercises
    .asSequence()
    .filter { exercise -> words.all { exercise.name.contains(it, ignoreCase = true) } }
    .filter { exercise ->
      when (filter) {
        CategoryFilter.All -> true
        CategoryFilter.Uncategorised -> exercise.categoryId == null
        is CategoryFilter.Category -> exercise.hasCategory(filter.id)
      }
    }
    .groupBy { it.categoryId }
    .map { (categoryId, group) -> ExerciseSection(categoryId, group.first().categoryName, group) }
    .sortedWith(
      compareBy<ExerciseSection> { filterId == null || it.categoryId != filterId }
        .thenBy { it.title == null }
        .thenBy { it.title?.lowercase() }
    )
}

/**
 * Whether the sections are folded, each opened and closed by tapping its heading. Only the full
 * list folds, starting with every section closed; a search or a category filter shows everything
 * it finds.
 */
fun sectionsFold(query: String, filter: CategoryFilter): Boolean =
  query.isBlank() && filter == CategoryFilter.All

/**
 * For editing several exercises at once: whether all ([ToggleableState.On]), some
 * ([ToggleableState.Indeterminate]) or none ([ToggleableState.Off]) of [exercises] have each
 * category as a sub category. An exercise cannot get its main category as a sub category, so it
 * does not count for that one; a category that is the main one of all of them is left out.
 */
fun subCategoryStates(
  exercises: List<ExerciseItem>,
  categories: List<CategoryItem>,
): Map<Long, ToggleableState> = buildMap {
  for (category in categories) {
    val eligible = exercises.filter { it.categoryId != category.id }
    if (eligible.isEmpty()) continue
    val having = eligible.count { exercise -> exercise.subCategories.any { it.id == category.id } }
    put(
      category.id,
      when (having) {
        eligible.size -> ToggleableState.On
        0 -> ToggleableState.Off
        else -> ToggleableState.Indeterminate
      },
    )
  }
}

/**
 * The state a tap leads to: ticked, then unticked. A category that only some of the exercises had
 * ([initial] is indeterminate) can also go back to that, which leaves those exercises as they are.
 */
fun nextSubCategoryState(initial: ToggleableState?, current: ToggleableState): ToggleableState =
  when (current) {
    ToggleableState.Indeterminate -> ToggleableState.On
    ToggleableState.On -> ToggleableState.Off
    ToggleableState.Off ->
      if (initial == ToggleableState.Indeterminate) ToggleableState.Indeterminate
      else ToggleableState.On
  }

/** Sub categories to give to every edited exercise and to take away from every one of them. */
data class SubCategoryChange(val add: Set<Long>, val remove: Set<Long>)

/**
 * What it takes to get from [initial] to [edited]: a category switched on is added everywhere, one
 * switched off is removed everywhere, and one left as it was stays with the exercises that have it.
 */
fun subCategoryChange(
  initial: Map<Long, ToggleableState>,
  edited: Map<Long, ToggleableState>,
): SubCategoryChange {
  val changed = edited.filter { (categoryId, state) -> initial[categoryId] != state }
  return SubCategoryChange(
    add = changed.filterValues { it == ToggleableState.On }.keys,
    remove = changed.filterValues { it == ToggleableState.Off }.keys,
  )
}

class ExercisesViewModel(private val repository: ExerciseRepository) : ViewModel() {
  private val query = MutableStateFlow("")
  private val filter = MutableStateFlow<CategoryFilter>(CategoryFilter.All)
  private val selectedIds = MutableStateFlow(emptySet<Long>())
  private val openSections = MutableStateFlow(emptySet<Long?>())

  val uiState: StateFlow<ExercisesUiState> =
    combine(repository.exercises, repository.categories, query, filter, selectedIds) {
        exercises,
        categories,
        query,
        filter,
        selected ->
        val uncategorisedCount = exercises.count { it.categoryId == null }
        // A filter that has lost its subject falls back to everything: the category was deleted
        // meanwhile, or the last uncategorised exercise got a category.
        val validFilter =
          when {
            filter is CategoryFilter.Category && categories.none { it.id == filter.id } ->
              CategoryFilter.All
            filter == CategoryFilter.Uncategorised && uncategorisedCount == 0 -> CategoryFilter.All
            else -> filter
          }
        ExercisesUiState(
          loading = false,
          categories = categories,
          uncategorisedCount = uncategorisedCount,
          query = query,
          filter = validFilter,
          sections = buildSections(exercises, query, validFilter),
          selectedIds = selected intersect exercises.mapTo(HashSet()) { it.id },
          selectedExercises = exercises.filter { it.id in selected },
          sectionsFold = sectionsFold(query, validFilter),
        )
      }
      .combine(openSections) { state, open -> state.copy(openSections = open) }
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

  /** Opens or closes the section of main category [categoryId] (null = uncategorised). */
  fun toggleSection(categoryId: Long?) {
    openSections.update { if (categoryId in it) it - categoryId else it + categoryId }
  }

  /** Opens the section that exercises were just put into, so they do not vanish from view. */
  private fun openSection(categoryId: Long?) {
    openSections.update { it + categoryId }
  }

  /** Gives the selected exercises a main category (null = none) and ends the selection. */
  fun categoriseSelected(categoryId: Long?) {
    val ids = uiState.value.selectedIds
    clearSelection()
    openSection(categoryId)
    viewModelScope.launch { repository.setCategory(ids, categoryId) }
  }

  /** Creates a category named [name] and makes it the main category of the selected exercises. */
  fun categoriseSelectedIntoNew(name: String) {
    val ids = uiState.value.selectedIds
    clearSelection()
    viewModelScope.launch {
      val categoryId = repository.createCategory(name) ?: return@launch
      openSection(categoryId)
      repository.setCategory(ids, categoryId)
    }
  }

  /** Adds and removes sub categories on all selected exercises and ends the selection. */
  fun changeSubCategoriesOfSelected(change: SubCategoryChange) {
    val ids = uiState.value.selectedIds
    clearSelection()
    viewModelScope.launch { repository.changeSubCategories(ids, change.add, change.remove) }
  }

  suspend fun createCategory(name: String): Long? = repository.createCategory(name)

  suspend fun createExercise(name: String, categoryId: Long?, subCategoryIds: Set<Long>): Boolean =
    repository.saveExercise(
      id = null,
      name = name,
      categoryId = categoryId,
      subCategoryIds = subCategoryIds,
    )
      .also { saved -> if (saved) openSection(categoryId) }
}

/** The exercise list: search, filter by category, and assign categories to several at once. */
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
  val listState = rememberLazyListState()
  val scope = rememberCoroutineScope()

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
      LazyColumn(
        Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(bottom = 96.dp),
      ) {
        var itemCount = 0
        for (section in state.sections) {
          val headerIndex = itemCount
          val open = state.isOpen(section)
          stickyHeader(key = "header-${section.categoryId}") {
            SectionHeader(
              section.title ?: stringResource(R.string.category_uncategorised),
              section.exercises.size,
              open = open.takeIf { state.sectionsFold },
              onToggle = {
                // Closing the section the list is scrolled into would leave the view somewhere
                // in the sections below; keep its heading at the top instead.
                if (open && listState.firstVisibleItemIndex > headerIndex) {
                  scope.launch { listState.scrollToItem(headerIndex) }
                }
                viewModel.toggleSection(section.categoryId)
              },
            )
          }
          itemCount += 1
          if (!open) continue
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
          itemCount += section.exercises.size
        }
      }
    }
  }

  if (showAddDialog) {
    ExerciseEditDialog(
      title = stringResource(R.string.exercise_add),
      initialName = "",
      initialCategoryId = (state.filter as? CategoryFilter.Category)?.id,
      initialSubCategoryIds = emptySet(),
      categories = state.categories,
      onSave = viewModel::createExercise,
      onDismiss = { showAddDialog = false },
    )
  }
  if (showCategoryPicker) {
    CategoryPickerDialog(
      categories = state.categories,
      exercises = state.selectedExercises,
      onPickMain = { categoryId ->
        showCategoryPicker = false
        viewModel.categoriseSelected(categoryId)
      },
      onCreateMain = { name ->
        showCategoryPicker = false
        viewModel.categoriseSelectedIntoNew(name)
      },
      onChangeSubCategories = { change ->
        showCategoryPicker = false
        viewModel.changeSubCategoriesOfSelected(change)
      },
      onCreateCategory = viewModel::createCategory,
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
    if (state.uncategorisedCount > 0) {
      item(key = "none") {
        FilterChip(
          selected = state.filter == CategoryFilter.Uncategorised,
          onClick = { onSelect(CategoryFilter.Uncategorised) },
          label = {
            Text(stringResource(R.string.filter_uncategorised, state.uncategorisedCount))
          },
        )
      }
    }
    items(state.categories, key = { it.id }) { category ->
      FilterChip(
        selected = state.filter == CategoryFilter.Category(category.id),
        onClick = { onSelect(CategoryFilter.Category(category.id)) },
        label = {
          // Counts what the filter shows: the category as main and as sub category.
          Text(
            stringResource(
              R.string.filter_category,
              category.name,
              category.exerciseCount + category.subExerciseCount,
            )
          )
        },
      )
    }
  }
}

/**
 * The heading of a section of exercises. With [open] set, tapping it opens or closes the section
 * and an arrow shows which it is.
 */
@Composable
internal fun SectionHeader(
  title: String,
  count: Int,
  open: Boolean? = null,
  onToggle: () -> Unit = {},
) {
  val text = stringResource(R.string.section_header, title, count)
  val style = MaterialTheme.typography.titleSmall
  val color = MaterialTheme.colorScheme.primary
  // Opaque, so rows scrolling underneath a pinned header do not show through.
  val background = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)
  if (open == null) {
    Text(
      text,
      style = style,
      color = color,
      modifier = background.padding(horizontal = 16.dp, vertical = 8.dp),
    )
    return
  }
  val stateText = stringResource(if (open) R.string.section_expanded else R.string.section_collapsed)
  val arrowRotation by animateFloatAsState(if (open) 180f else 0f, label = "section arrow")
  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier =
      background
        .clickable(role = Role.Button, onClick = onToggle)
        .semantics { stateDescription = stateText }
        .heightIn(min = 48.dp)
        .padding(horizontal = 16.dp),
  ) {
    Text(text, style = style, color = color, modifier = Modifier.weight(1f))
    Icon(
      Icons.Filled.KeyboardArrowDown,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.rotate(arrowRotation),
    )
  }
}

/** The line under an exercise's name with its categories, or null when it has none. */
internal fun categoriesContent(exercise: ExerciseItem): (@Composable () -> Unit)? =
  if (exercise.categoryName == null && exercise.subCategories.isEmpty()) {
    null
  } else {
    { Text(categoryLine(exercise.categoryName, exercise.subCategories.map { it.name })) }
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
    supportingContent = categoriesContent(exercise),
    leadingContent =
      if (selecting) ({ Checkbox(checked = selected, onCheckedChange = null) }) else null,
    modifier = Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick),
  )
}
