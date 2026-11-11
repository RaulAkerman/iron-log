package com.ironlog.app.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import com.ironlog.app.data.db.SupplementTimeEntity
import com.ironlog.app.data.nextOccurrence
import java.time.ZonedDateTime

/**
 * Registers one alarm per supplement time with the system. Each alarm fires once, at the next
 * occurrence of its time of day; [ReminderReceiver] sets the following one when it goes off.
 *
 * The alarms are inexact on purpose: they may be delivered up to [WINDOW_MILLIS] late and are held
 * back while the phone is in Doze. That needs no special permission, and the reminders are silent
 * anyway, so they are only seen once the phone is picked up.
 */
class ReminderScheduler(
  private val context: Context,
  private val now: () -> ZonedDateTime = ZonedDateTime::now,
) {
  private val alarmManager = context.getSystemService(AlarmManager::class.java)

  /** Sets (or moves) the alarm for the next occurrence of this time. */
  fun schedule(time: SupplementTimeEntity) {
    val triggerAt = nextOccurrence(time.minuteOfDay, now()).toInstant().toEpochMilli()
    val operation = checkNotNull(pendingIntent(time.id, triggerAt, PendingIntent.FLAG_UPDATE_CURRENT))
    alarmManager.setWindow(AlarmManager.RTC_WAKEUP, triggerAt, WINDOW_MILLIS, operation)
  }

  fun cancel(timeId: Long) {
    val operation = pendingIntent(timeId, 0, PendingIntent.FLAG_NO_CREATE) ?: return
    alarmManager.cancel(operation)
    operation.cancel()
  }

  /** Whether an alarm for this time is registered and has not gone off yet. */
  fun isScheduled(timeId: Long): Boolean =
    pendingIntent(timeId, 0, PendingIntent.FLAG_NO_CREATE) != null

  private fun pendingIntent(timeId: Long, triggerAt: Long, flag: Int): PendingIntent? {
    val intent =
      Intent(context, ReminderReceiver::class.java)
        .setAction(ReminderReceiver.ACTION_REMIND)
        // The data makes the intents of different times distinct; extras alone do not.
        .setData("ironlog://supplement-time/$timeId".toUri())
        .putExtra(ReminderReceiver.EXTRA_TIME_ID, timeId)
        .putExtra(ReminderReceiver.EXTRA_SCHEDULED_AT, triggerAt)
    return PendingIntent.getBroadcast(
      context,
      timeId.toInt(),
      intent,
      flag or PendingIntent.FLAG_IMMUTABLE,
    )
  }

  companion object {
    /** Shortest delivery window the system honours for inexact alarms. */
    const val WINDOW_MILLIS = 10 * 60 * 1000L
  }
}
