package com.ironlog.app.ui.supplements

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ironlog.app.R
import com.ironlog.app.data.IntakeDay
import com.ironlog.app.data.SupplementRepository
import com.ironlog.app.data.SupplementSaveResult
import com.ironlog.app.data.intakesByDay
import com.ironlog.app.reminders.SupplementReminders
import com.ironlog.app.ui.common.BackButton
import com.ironlog.app.ui.common.ConfirmDialog
import com.ironlog.app.ui.common.appViewModel
import com.ironlog.app.ui.common.formatEpochDay
import com.ironlog.app.ui.common.formatMinuteOfDay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SupplementEditUiState(
  val loading: Boolean = true,
  val name: String = "",
  /** The daily times being edited, earliest first. Not saved until [SupplementEditViewModel.save]. */
  val minutesOfDay: List<Int> = emptyList(),
  val nameTaken: Boolean = false,
  /** Days on which doses were taken, newest first. Empty for a new supplement. */
  val record: List<IntakeDay> = emptyList(),
) {
  val canSave: Boolean
    get() = !loading && name.isNotBlank() && minutesOfDay.isNotEmpty()
}

class SupplementEditViewModel(
  private val repository: SupplementRepository,
  private val reminders: SupplementReminders,
  private val supplementId: Long?,
) : ViewModel() {
  private data class Draft(
    val loading: Boolean,
    val name: String = "",
    val minutesOfDay: Set<Int> = emptySet(),
    val nameTaken: Boolean = false,
  )

  private val draft = MutableStateFlow(Draft(loading = supplementId != null))

  init {
    if (supplementId != null) {
      viewModelScope.launch {
        val supplement = repository.supplement(supplementId)
        val times = repository.timesOf(supplementId)
        draft.value =
          Draft(
            loading = false,
            name = supplement?.name.orEmpty(),
            minutesOfDay = times.mapTo(HashSet()) { it.minuteOfDay },
          )
      }
    }
  }

  val uiState: StateFlow<SupplementEditUiState> =
    combine(
        draft,
        if (supplementId == null) flowOf(emptyList()) else repository.recentIntakes(supplementId),
      ) { draft, intakes ->
        SupplementEditUiState(
          loading = draft.loading,
          name = draft.name,
          minutesOfDay = draft.minutesOfDay.sorted(),
          nameTaken = draft.nameTaken,
          record = intakesByDay(intakes),
        )
      }
      .stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        SupplementEditUiState(loading = supplementId != null),
      )

  fun setName(name: String) {
    draft.update { it.copy(name = name, nameTaken = false) }
  }

  fun addTime(minuteOfDay: Int) {
    draft.update { it.copy(minutesOfDay = it.minutesOfDay + minuteOfDay) }
  }

  fun removeTime(minuteOfDay: Int) {
    draft.update { it.copy(minutesOfDay = it.minutesOfDay - minuteOfDay) }
  }

  /** Saves the draft and returns true, or flags a duplicate name and returns false. */
  suspend fun save(): Boolean {
    val current = draft.value
    return when (reminders.save(supplementId, current.name, current.minutesOfDay)) {
      is SupplementSaveResult.Saved -> true
      SupplementSaveResult.NameTaken -> {
        draft.update { it.copy(nameTaken = true) }
        false
      }
      SupplementSaveResult.Invalid -> false
    }
  }

  suspend fun delete() {
    if (supplementId != null) reminders.delete(supplementId)
  }
}

/** Adds or edits a supplement: its name and the times of day it is taken. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupplementEditScreen(supplementId: Long?, onDone: () -> Unit, modifier: Modifier = Modifier) {
  val viewModel = appViewModel {
    SupplementEditViewModel(it.supplements, it.reminders, supplementId)
  }
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  val scope = rememberCoroutineScope()
  var showTimePicker by remember { mutableStateOf(false) }
  var showDelete by remember { mutableStateOf(false) }

  Scaffold(
    modifier = modifier,
    topBar = {
      TopAppBar(
        title = {
          Text(
            stringResource(
              if (supplementId == null) R.string.supplement_add else R.string.supplement_edit
            )
          )
        },
        navigationIcon = { BackButton(onDone) },
        actions = {
          if (supplementId != null) {
            IconButton(onClick = { showDelete = true }) {
              Icon(
                Icons.Filled.Delete,
                contentDescription = stringResource(R.string.supplement_delete),
              )
            }
          }
          TextButton(
            onClick = { scope.launch { if (viewModel.save()) onDone() } },
            enabled = state.canSave,
          ) {
            Text(stringResource(R.string.save))
          }
        },
      )
    },
  ) { innerPadding ->
    if (state.loading) return@Scaffold
    Column(
      modifier =
        Modifier.fillMaxSize()
          .padding(innerPadding)
          .imePadding()
          .verticalScroll(rememberScrollState())
          .padding(vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      OutlinedTextField(
        value = state.name,
        onValueChange = viewModel::setName,
        label = { Text(stringResource(R.string.name_label)) },
        singleLine = true,
        isError = state.nameTaken,
        supportingText =
          if (state.nameTaken) ({ Text(stringResource(R.string.supplement_name_taken)) }) else null,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
      )

      SectionLabel(stringResource(R.string.supplement_times))
      if (state.minutesOfDay.isEmpty()) {
        Hint(stringResource(R.string.supplement_times_empty))
      }
      for (minuteOfDay in state.minutesOfDay) {
        val time = formatMinuteOfDay(minuteOfDay)
        ListItem(
          headlineContent = { Text(time) },
          trailingContent = {
            IconButton(onClick = { viewModel.removeTime(minuteOfDay) }) {
              Icon(
                Icons.Filled.Close,
                contentDescription = stringResource(R.string.remove_named, time),
              )
            }
          },
        )
      }
      OutlinedButton(
        onClick = { showTimePicker = true },
        modifier = Modifier.padding(horizontal = 16.dp),
      ) {
        Icon(Icons.Filled.Add, contentDescription = null)
        Text(stringResource(R.string.supplement_add_time), Modifier.padding(start = 8.dp))
      }
      Hint(stringResource(R.string.supplement_times_hint))

      if (supplementId != null) {
        SectionLabel(stringResource(R.string.supplement_record))
        if (state.record.isEmpty()) {
          Hint(stringResource(R.string.supplement_record_empty))
        }
        for (day in state.record) {
          ListItem(
            headlineContent = { Text(formatEpochDay(day.epochDay)) },
            supportingContent = {
              Text(day.minutesOfDay.joinToString(" · ") { formatMinuteOfDay(it) })
            },
          )
        }
      }
    }
  }

  if (showTimePicker) {
    TimePickerDialog(
      onConfirm = { minuteOfDay ->
        showTimePicker = false
        viewModel.addTime(minuteOfDay)
      },
      onDismiss = { showTimePicker = false },
    )
  }
  if (showDelete) {
    ConfirmDialog(
      title = stringResource(R.string.supplement_delete_title, state.name),
      text = stringResource(R.string.supplement_delete_text),
      confirmLabel = stringResource(R.string.delete),
      onConfirm = {
        showDelete = false
        scope.launch {
          viewModel.delete()
          onDone()
        }
      },
      onDismiss = { showDelete = false },
    )
  }
}

@Composable
private fun SectionLabel(text: String) {
  Text(
    text,
    style = MaterialTheme.typography.titleSmall,
    color = MaterialTheme.colorScheme.primary,
    modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp),
  )
}

@Composable
private fun Hint(text: String) {
  Text(
    text,
    style = MaterialTheme.typography.bodyMedium,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier.padding(horizontal = 16.dp),
  )
}

/** Picks a time of day; [onConfirm] receives it as minutes after midnight. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(onConfirm: (minuteOfDay: Int) -> Unit, onDismiss: () -> Unit) {
  val pickerState =
    rememberTimePickerState(
      initialHour = 8,
      initialMinute = 0,
      is24Hour = DateFormat.is24HourFormat(LocalContext.current),
    )
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(R.string.supplement_add_time)) },
    text = { TimePicker(state = pickerState) },
    confirmButton = {
      TextButton(onClick = { onConfirm(pickerState.hour * 60 + pickerState.minute) }) {
        Text(stringResource(R.string.add))
      }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
  )
}
