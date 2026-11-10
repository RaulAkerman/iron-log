package com.ironlog.app.ui.exercises

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ironlog.app.R
import com.ironlog.app.data.ExerciseRepository
import com.ironlog.app.data.db.CategoryItem
import com.ironlog.app.ui.common.BackButton
import com.ironlog.app.ui.common.ConfirmDialog
import com.ironlog.app.ui.common.EmptyState
import com.ironlog.app.ui.common.NameDialog
import com.ironlog.app.ui.common.appViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CategoriesViewModel(private val repository: ExerciseRepository) : ViewModel() {
  /** Null until loaded. */
  val categories: StateFlow<List<CategoryItem>?> =
    repository.categories
      .map<List<CategoryItem>, List<CategoryItem>?> { it }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

  suspend fun create(name: String) {
    repository.createCategory(name)
  }

  suspend fun rename(id: Long, name: String): Boolean = repository.renameCategory(id, name)

  fun delete(id: Long) {
    viewModelScope.launch { repository.deleteCategory(id) }
  }
}

/** Create, rename and delete the user's exercise categories. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
  val viewModel = appViewModel { CategoriesViewModel(it.exercises) }
  val categories by viewModel.categories.collectAsStateWithLifecycle()
  var showAdd by remember { mutableStateOf(false) }
  var renaming by remember { mutableStateOf<CategoryItem?>(null) }
  var deleting by remember { mutableStateOf<CategoryItem?>(null) }
  val nameTaken = stringResource(R.string.category_name_taken)

  Scaffold(
    modifier = modifier,
    topBar = {
      TopAppBar(
        title = { Text(stringResource(R.string.categories_title)) },
        navigationIcon = { BackButton(onBack) },
      )
    },
    floatingActionButton = {
      FloatingActionButton(onClick = { showAdd = true }) {
        Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.category_add))
      }
    },
  ) { innerPadding ->
    val loaded = categories
    when {
      loaded == null -> Unit
      loaded.isEmpty() ->
        EmptyState(
          title = stringResource(R.string.categories_empty_title),
          body = stringResource(R.string.categories_empty_body),
          modifier = Modifier.padding(innerPadding),
        )
      else ->
        LazyColumn(
          modifier = Modifier.fillMaxSize().padding(innerPadding),
          contentPadding = PaddingValues(bottom = 96.dp),
        ) {
          items(loaded, key = { it.id }) { category ->
            ListItem(
              headlineContent = { Text(category.name) },
              supportingContent = {
                Text(
                  pluralStringResource(
                    R.plurals.exercises,
                    category.exerciseCount,
                    category.exerciseCount,
                  )
                )
              },
              trailingContent = {
                Row {
                  IconButton(onClick = { renaming = category }) {
                    Icon(
                      Icons.Filled.Edit,
                      contentDescription = stringResource(R.string.rename_named, category.name),
                    )
                  }
                  IconButton(onClick = { deleting = category }) {
                    Icon(
                      Icons.Filled.Delete,
                      contentDescription = stringResource(R.string.delete_named, category.name),
                    )
                  }
                }
              },
            )
            HorizontalDivider()
          }
        }
    }
  }

  if (showAdd) {
    NameDialog(
      title = stringResource(R.string.category_add),
      label = stringResource(R.string.name_label),
      initialName = "",
      confirmLabel = stringResource(R.string.add),
      onConfirm = { name ->
        viewModel.create(name)
        null
      },
      onDismiss = { showAdd = false },
    )
  }
  renaming?.let { category ->
    NameDialog(
      title = stringResource(R.string.category_rename),
      label = stringResource(R.string.name_label),
      initialName = category.name,
      confirmLabel = stringResource(R.string.save),
      onConfirm = { name -> if (viewModel.rename(category.id, name)) null else nameTaken },
      onDismiss = { renaming = null },
    )
  }
  deleting?.let { category ->
    ConfirmDialog(
      title = stringResource(R.string.category_delete_title, category.name),
      text = stringResource(R.string.category_delete_text),
      confirmLabel = stringResource(R.string.delete),
      onConfirm = {
        deleting = null
        viewModel.delete(category.id)
      },
      onDismiss = { deleting = null },
    )
  }
}
