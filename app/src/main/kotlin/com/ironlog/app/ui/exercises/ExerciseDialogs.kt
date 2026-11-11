package com.ironlog.app.ui.exercises

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.triStateToggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.ironlog.app.R
import com.ironlog.app.data.db.CategoryItem
import com.ironlog.app.data.db.ExerciseItem
import kotlinx.coroutines.launch

/**
 * Creates or edits an exercise: its name, main category and sub categories. [onSave] returns false
 * when the name is already taken, which keeps the dialog open with an error.
 */
@Composable
fun ExerciseEditDialog(
  title: String,
  initialName: String,
  initialCategoryId: Long?,
  initialSubCategoryIds: Set<Long>,
  categories: List<CategoryItem>,
  onSave: suspend (name: String, categoryId: Long?, subCategoryIds: Set<Long>) -> Boolean,
  onDismiss: () -> Unit,
) {
  var name by remember { mutableStateOf(initialName) }
  var categoryId by remember { mutableStateOf(initialCategoryId) }
  var subCategoryIds by remember { mutableStateOf(initialSubCategoryIds) }
  var nameTaken by remember { mutableStateOf(false) }
  var saving by remember { mutableStateOf(false) }
  var menuOpen by remember { mutableStateOf(false) }

  // The main category cannot be a sub category as well.
  fun pickMain(id: Long?) {
    categoryId = id
    if (id != null) subCategoryIds -= id
    menuOpen = false
  }

  LaunchedEffect(saving) {
    if (!saving) return@LaunchedEffect
    if (onSave(name, categoryId, subCategoryIds)) onDismiss() else nameTaken = true
    saving = false
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(title) },
    text = {
      Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        OutlinedTextField(
          value = name,
          onValueChange = {
            name = it
            nameTaken = false
          },
          label = { Text(stringResource(R.string.name_label)) },
          singleLine = true,
          isError = nameTaken,
          supportingText =
            if (nameTaken) ({ Text(stringResource(R.string.exercise_name_taken)) }) else null,
          keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
          modifier = Modifier.fillMaxWidth(),
        )
        Box {
          OutlinedButton(onClick = { menuOpen = true }, modifier = Modifier.fillMaxWidth()) {
            val categoryName = categories.firstOrNull { it.id == categoryId }?.name
            Text(
              if (categoryName == null) {
                stringResource(R.string.category_main_none)
              } else {
                stringResource(R.string.category_main_value, categoryName)
              }
            )
          }
          DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
              text = { Text(stringResource(R.string.category_main_none)) },
              onClick = { pickMain(null) },
            )
            for (category in categories) {
              DropdownMenuItem(
                text = { Text(category.name) },
                onClick = { pickMain(category.id) },
              )
            }
          }
        }
        val subChoices = categories.filter { it.id != categoryId }
        if (subChoices.isNotEmpty()) {
          Column {
            Text(
              stringResource(R.string.category_subs),
              style = MaterialTheme.typography.labelLarge,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              for (category in subChoices) {
                val selected = category.id in subCategoryIds
                FilterChip(
                  selected = selected,
                  onClick = {
                    subCategoryIds =
                      if (selected) subCategoryIds - category.id else subCategoryIds + category.id
                  },
                  label = { Text(category.name) },
                  leadingIcon =
                    if (selected) {
                      {
                        Icon(
                          Icons.Filled.Check,
                          contentDescription = null,
                          modifier = Modifier.size(FilterChipDefaults.IconSize),
                        )
                      }
                    } else {
                      null
                    },
                )
              }
            }
          }
        }
      }
    },
    confirmButton = {
      TextButton(onClick = { saving = true }, enabled = name.isNotBlank() && !saving) {
        Text(stringResource(R.string.save))
      }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
  )
}

/**
 * Sets the categories of the selected [exercises], on one of two pages. "Main category" works by
 * tapping one, or naming a new one: [onPickMain] receives null for "no main category". "Sub
 * categories" shows for each category whether all, some or none of the exercises have it; ticking
 * and saving reports through [onChangeSubCategories] what to add to all of them and what to remove
 * from all of them. [onCreateCategory] returns the id of the category with that name, creating it
 * if needed.
 */
@Composable
fun CategoryPickerDialog(
  categories: List<CategoryItem>,
  exercises: List<ExerciseItem>,
  onPickMain: (categoryId: Long?) -> Unit,
  onCreateMain: (name: String) -> Unit,
  onChangeSubCategories: (SubCategoryChange) -> Unit,
  onCreateCategory: suspend (name: String) -> Long?,
  onDismiss: () -> Unit,
) {
  var onSubPage by remember { mutableStateOf(false) }
  var newName by remember { mutableStateOf("") }
  val initialStates = remember(exercises, categories) { subCategoryStates(exercises, categories) }
  // What the user ticked or unticked on the sub category page, on top of [initialStates].
  var edits by remember { mutableStateOf(emptyMap<Long, ToggleableState>()) }
  val scope = rememberCoroutineScope()

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(R.string.category_set)) },
    text = {
      Column {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
          SegmentedButton(
            selected = !onSubPage,
            onClick = { onSubPage = false },
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            // No tick: the two labels only just fit side by side.
            icon = {},
          ) {
            Text(stringResource(R.string.category_main))
          }
          SegmentedButton(
            selected = onSubPage,
            onClick = { onSubPage = true },
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            icon = {},
          ) {
            Text(stringResource(R.string.category_subs))
          }
        }
        // Five and a half rows: the cut-off one shows that the list scrolls.
        Column(
          Modifier.padding(top = 8.dp).heightIn(max = 264.dp).verticalScroll(rememberScrollState())
        ) {
          if (onSubPage) {
            for (category in categories) {
              // No state: it is the main category of every selected exercise.
              val state = edits[category.id] ?: initialStates[category.id]
              SubCategoryChoice(
                label = category.name,
                state = state ?: ToggleableState.Off,
                enabled = state != null,
                onClick = {
                  if (state != null) {
                    val next = nextSubCategoryState(initialStates[category.id], state)
                    edits = edits + (category.id to next)
                  }
                },
              )
            }
          } else {
            CategoryChoice(
              stringResource(R.string.category_main_none),
              onClick = { onPickMain(null) },
            )
            for (category in categories) {
              CategoryChoice(category.name, onClick = { onPickMain(category.id) })
            }
          }
        }
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        Row(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          OutlinedTextField(
            value = newName,
            onValueChange = { newName = it },
            label = { Text(stringResource(R.string.category_new_label)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.weight(1f),
          )
          TextButton(
            onClick = {
              if (onSubPage) {
                // The new category joins the list, already ticked.
                val name = newName
                newName = ""
                scope.launch {
                  val id = onCreateCategory(name) ?: return@launch
                  edits = edits + (id to ToggleableState.On)
                }
              } else {
                onCreateMain(newName)
              }
            },
            enabled = newName.isNotBlank(),
          ) {
            Text(stringResource(R.string.add))
          }
        }
      }
    },
    confirmButton = {
      if (onSubPage) {
        TextButton(
          onClick = { onChangeSubCategories(subCategoryChange(initialStates, initialStates + edits)) }
        ) {
          Text(stringResource(R.string.save))
        }
      }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
  )
}

@Composable
private fun CategoryChoice(label: String, onClick: () -> Unit) {
  Text(
    label,
    style = MaterialTheme.typography.bodyLarge,
    modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
  )
}

/** A category with a tick box; the whole row is the target for touch and screen readers. */
@Composable
private fun SubCategoryChoice(
  label: String,
  state: ToggleableState,
  enabled: Boolean,
  onClick: () -> Unit,
) {
  Row(
    horizontalArrangement = Arrangement.spacedBy(12.dp),
    verticalAlignment = Alignment.CenterVertically,
    modifier =
      Modifier.fillMaxWidth()
        .triStateToggleable(state = state, enabled = enabled, role = Role.Checkbox, onClick = onClick)
        .padding(vertical = 12.dp),
  ) {
    TriStateCheckbox(state = state, onClick = null, enabled = enabled)
    Text(
      label,
      style = MaterialTheme.typography.bodyLarge,
      color =
        if (enabled) MaterialTheme.colorScheme.onSurface
        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
    )
  }
}
