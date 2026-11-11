package com.ironlog.app.ui.supplements

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ironlog.app.IronLogApp
import com.ironlog.app.R
import com.ironlog.app.data.Dose
import com.ironlog.app.data.SupplementRepository
import com.ironlog.app.data.db.SupplementEntity
import com.ironlog.app.data.dosesOfDay
import com.ironlog.app.reminders.SupplementReminders
import com.ironlog.app.ui.common.EmptyState
import com.ironlog.app.ui.common.appViewModel
import com.ironlog.app.ui.common.formatMinuteOfDay
import java.time.Duration
import java.time.ZonedDateTime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A supplement with its daily times, earliest first. */
data class SupplementRow(val supplement: SupplementEntity, val minutesOfDay: List<Int>)

data class SupplementsUiState(
  val loading: Boolean = true,
  /** The day [today] refers to, as days since 1970-01-01. */
  val epochDay: Long = 0,
  val today: List<Dose> = emptyList(),
  val supplements: List<SupplementRow> = emptyList(),
)

/** Today's date as an epoch day, emitted again each time midnight passes. */
fun currentEpochDay(now: () -> ZonedDateTime = ZonedDateTime::now): Flow<Long> =
  flow {
      while (true) {
        val moment = now()
        emit(moment.toLocalDate().toEpochDay())
        val midnight = moment.toLocalDate().plusDays(1).atStartOfDay(moment.zone)
        delay(Duration.between(moment, midnight).toMillis() + 1_000)
      }
    }
    .distinctUntilChanged()

class SupplementsViewModel(
  repository: SupplementRepository,
  private val reminders: SupplementReminders,
) : ViewModel() {
  @OptIn(ExperimentalCoroutinesApi::class)
  val uiState: StateFlow<SupplementsUiState> =
    currentEpochDay()
      .flatMapLatest { day ->
        combine(repository.supplements, repository.times, repository.intakes(day)) {
          supplements,
          times,
          intakes ->
          val timesBySupplement = times.groupBy { it.supplementId }
          SupplementsUiState(
            loading = false,
            epochDay = day,
            today = dosesOfDay(supplements, times, intakes),
            supplements =
              supplements.map { supplement ->
                SupplementRow(
                  supplement,
                  timesBySupplement[supplement.id].orEmpty().map { it.minuteOfDay },
                )
              },
          )
        }
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SupplementsUiState())

  /** Ticks one of today's doses off, or un-ticks it. */
  fun setTaken(dose: Dose, taken: Boolean) {
    val day = uiState.value.epochDay
    viewModelScope.launch {
      reminders.setTaken(dose.supplementId, dose.timeId, day, dose.minuteOfDay, taken)
    }
  }
}

/** Today's doses to tick off, and the list of supplements with their reminder times. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupplementsScreen(
  onAddSupplement: () -> Unit,
  onEditSupplement: (Long) -> Unit,
  modifier: Modifier = Modifier,
) {
  val viewModel = appViewModel { SupplementsViewModel(it.supplements, it.reminders) }
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  val canNotify = rememberCanNotify(askWhen = state.supplements.isNotEmpty())

  Scaffold(
    modifier = modifier,
    topBar = {
      CenterAlignedTopAppBar(title = { Text(stringResource(R.string.supplements_title)) })
    },
    floatingActionButton = {
      FloatingActionButton(onClick = onAddSupplement) {
        Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.supplement_add))
      }
    },
  ) { innerPadding ->
    when {
      state.loading -> Unit
      state.supplements.isEmpty() ->
        EmptyState(
          title = stringResource(R.string.supplements_empty_title),
          body = stringResource(R.string.supplements_empty_body),
          modifier = Modifier.padding(innerPadding),
        )
      else ->
        LazyColumn(
          modifier = Modifier.fillMaxSize().padding(innerPadding),
          // Bottom space keeps the last row clear of the floating button.
          contentPadding = PaddingValues(bottom = 96.dp),
        ) {
          if (!canNotify) {
            item(key = "notificationsOff") { NotificationsOffCard() }
          }
          item(key = "todayHeader") { SectionTitle(stringResource(R.string.supplements_today)) }
          items(state.today, key = { "dose-${it.timeId}" }) { dose ->
            DoseRow(dose, onTakenChange = { viewModel.setTaken(dose, it) })
          }
          item(key = "allHeader") { SectionTitle(stringResource(R.string.supplements_all)) }
          items(state.supplements, key = { "supplement-${it.supplement.id}" }) { row ->
            ListItem(
              headlineContent = { Text(row.supplement.name) },
              supportingContent = {
                Text(row.minutesOfDay.joinToString(" · ") { formatMinuteOfDay(it) })
              },
              modifier = Modifier.clickable { onEditSupplement(row.supplement.id) },
            )
          }
        }
    }
  }
}

/**
 * Whether reminders can currently be shown. Re-checked whenever the screen comes back to the front,
 * since the user can change it in the system settings. On Android 13 and later the system
 * permission dialog is opened once, the first time [askWhen] is true.
 */
@Composable
private fun rememberCanNotify(askWhen: Boolean): Boolean {
  val notifications =
    (LocalContext.current.applicationContext as IronLogApp).container.notifications
  var canNotify by remember { mutableStateOf(notifications.canNotify()) }
  var asked by rememberSaveable { mutableStateOf(false) }
  val permissionRequest =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
      canNotify = notifications.canNotify()
    }

  LifecycleResumeEffect(notifications) {
    canNotify = notifications.canNotify()
    onPauseOrDispose {}
  }
  LaunchedEffect(askWhen, canNotify) {
    if (askWhen && !canNotify && !asked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      asked = true
      permissionRequest.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
  }
  return canNotify
}

@Composable
private fun NotificationsOffCard() {
  val context = LocalContext.current
  Card(
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
  ) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Text(
        stringResource(R.string.supplements_notifications_off_title),
        style = MaterialTheme.typography.titleMedium,
      )
      Text(
        stringResource(R.string.supplements_notifications_off_body),
        style = MaterialTheme.typography.bodyMedium,
      )
      Button(
        onClick = {
          context.startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
              .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
          )
        }
      ) {
        Text(stringResource(R.string.supplements_notifications_open_settings))
      }
    }
  }
}

@Composable
private fun SectionTitle(title: String) {
  Text(
    title,
    style = MaterialTheme.typography.titleSmall,
    color = MaterialTheme.colorScheme.primary,
    modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 4.dp),
  )
}

@Composable
private fun DoseRow(dose: Dose, onTakenChange: (Boolean) -> Unit) {
  ListItem(
    headlineContent = { Text(dose.supplementName) },
    supportingContent = { Text(formatMinuteOfDay(dose.minuteOfDay)) },
    leadingContent = { Checkbox(checked = dose.taken, onCheckedChange = null) },
    // The whole row is one checkbox, so it is a single target for touch and screen readers.
    modifier =
      Modifier.toggleable(value = dose.taken, role = Role.Checkbox, onValueChange = onTakenChange),
  )
}
