package com.ironlog.app.ui.exercises

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.ironlog.app.R
import com.ironlog.app.data.db.CategoryItem

/**
 * Creates or edits an exercise: its name and category. [onSave] returns false when the name is
 * already taken, which keeps the dialog open with an error.
 */
@Composable
fun ExerciseEditDialog(
  title: String,
  initialName: String,
  initialCategoryId: Long?,
  categories: List<CategoryItem>,
  onSave: suspend (name: String, categoryId: Long?) -> Boolean,
  onDismiss: () -> Unit,
) {
  var name by remember { mutableStateOf(initialName) }
  var categoryId by remember { mutableStateOf(initialCategoryId) }
  var nameTaken by remember { mutableStateOf(false) }
  var saving by remember { mutableStateOf(false) }
  var menuOpen by remember { mutableStateOf(false) }

  LaunchedEffect(saving) {
    if (!saving) return@LaunchedEffect
    if (onSave(name, categoryId)) onDismiss() else nameTaken = true
    saving = false
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(title) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                stringResource(R.string.category_none)
              } else {
                stringResource(R.string.category_value, categoryName)
              }
            )
          }
          DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
              text = { Text(stringResource(R.string.category_none)) },
              onClick = {
                categoryId = null
                menuOpen = false
              },
            )
            for (category in categories) {
              DropdownMenuItem(
                text = { Text(category.name) },
                onClick = {
                  categoryId = category.id
                  menuOpen = false
                },
              )
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
 * Lets the user pick the category for one or more exercises, or create a new one on the spot.
 * [onPick] receives null for "no category".
 */
@Composable
fun CategoryPickerDialog(
  categories: List<CategoryItem>,
  onPick: (categoryId: Long?) -> Unit,
  onCreate: (name: String) -> Unit,
  onDismiss: () -> Unit,
) {
  var newName by remember { mutableStateOf("") }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(R.string.category_pick_title)) },
    text = {
      Column {
        Column(Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState())) {
          CategoryChoice(stringResource(R.string.category_none), onClick = { onPick(null) })
          for (category in categories) {
            CategoryChoice(category.name, onClick = { onPick(category.id) })
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
          TextButton(onClick = { onCreate(newName) }, enabled = newName.isNotBlank()) {
            Text(stringResource(R.string.add))
          }
        }
      }
    },
    confirmButton = {},
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
