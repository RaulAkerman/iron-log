package com.ironlog.app.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ironlog.app.IronLogApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Receives the alarm of a supplement time and shows its reminder. */
class ReminderReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    if (intent.action != ACTION_REMIND) return
    val timeId = intent.getLongExtra(EXTRA_TIME_ID, -1)
    val scheduledAt = intent.getLongExtra(EXTRA_SCHEDULED_AT, System.currentTimeMillis())
    runAsync { context.reminders.onReminderDue(timeId, scheduledAt) }
  }

  companion object {
    const val ACTION_REMIND = "com.ironlog.app.action.SUPPLEMENT_REMINDER"
    const val EXTRA_TIME_ID = "timeId"
    const val EXTRA_SCHEDULED_AT = "scheduledAt"
  }
}

/** Handles the "Taken" button of a reminder. */
class ReminderActionReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    if (intent.action != ACTION_TAKEN) return
    val supplementId = intent.getLongExtra(EXTRA_SUPPLEMENT_ID, -1)
    val timeId = intent.getLongExtra(EXTRA_TIME_ID, -1)
    val epochDay = intent.getLongExtra(EXTRA_EPOCH_DAY, -1)
    val minuteOfDay = intent.getIntExtra(EXTRA_MINUTE_OF_DAY, -1)
    if (supplementId < 0 || epochDay < 0 || minuteOfDay < 0) return
    runAsync {
      context.reminders.setTaken(supplementId, timeId, epochDay, minuteOfDay, taken = true)
    }
  }

  companion object {
    const val ACTION_TAKEN = "com.ironlog.app.action.SUPPLEMENT_TAKEN"
    const val EXTRA_SUPPLEMENT_ID = "supplementId"
    const val EXTRA_TIME_ID = "timeId"
    const val EXTRA_EPOCH_DAY = "epochDay"
    const val EXTRA_MINUTE_OF_DAY = "minuteOfDay"
  }
}

/**
 * Sets all alarms again when the system has dropped them (reboot, app update) or when they point
 * at the wrong moment (clock or time zone changed).
 */
class RescheduleReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    when (intent.action) {
      Intent.ACTION_BOOT_COMPLETED,
      Intent.ACTION_MY_PACKAGE_REPLACED,
      Intent.ACTION_TIME_CHANGED,
      Intent.ACTION_TIMEZONE_CHANGED -> runAsync { context.reminders.rescheduleAll() }
    }
  }
}

private val Context.reminders: SupplementReminders
  get() = (applicationContext as IronLogApp).container.reminders

/** Runs [block] off the main thread while keeping the broadcast, and so the process, alive. */
private fun BroadcastReceiver.runAsync(block: suspend () -> Unit) {
  val pendingResult = goAsync()
  CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
    try {
      block()
    } finally {
      pendingResult.finish()
    }
  }
}
