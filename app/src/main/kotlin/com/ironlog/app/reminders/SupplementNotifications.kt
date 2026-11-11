package com.ironlog.app.reminders

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.ironlog.app.MainActivity
import com.ironlog.app.R
import com.ironlog.app.data.db.SupplementEntity
import com.ironlog.app.data.db.SupplementTimeEntity
import com.ironlog.app.data.epochDayOf
import com.ironlog.app.ui.common.formatMinuteOfDay

/** The notification channel for supplement reminders and the reminders posted on it. */
class SupplementNotifications(private val context: Context) {
  private val manager = NotificationManagerCompat.from(context)

  /**
   * Creates the channel if it does not exist yet. It has no sound and no vibration; default
   * importance keeps reminders in the main part of the shade and on the lock screen without a
   * pop-up banner. The system ignores changes to an existing channel, so different behaviour needs
   * a new [CHANNEL_ID].
   */
  fun ensureChannel() {
    val channel =
      NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_DEFAULT)
        .setName(context.getString(R.string.supplement_channel_name))
        .setDescription(context.getString(R.string.supplement_channel_description))
        .setSound(null, null)
        .setVibrationEnabled(false)
        .build()
    manager.createNotificationChannel(channel)
  }

  /** False when the user has turned the app's notifications or the reminder channel off. */
  fun canNotify(): Boolean {
    val channel = manager.getNotificationChannelCompat(CHANNEL_ID)
    return hasPermission() &&
      manager.areNotificationsEnabled() &&
      channel?.importance != NotificationManagerCompat.IMPORTANCE_NONE
  }

  /** Shows the reminder for one dose. [scheduledAt] is the moment the dose was due. */
  fun show(supplement: SupplementEntity, time: SupplementTimeEntity, scheduledAt: Long) {
    // Checked inline rather than through hasPermission() so that lint can follow it.
    if (
      Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
          PackageManager.PERMISSION_GRANTED
    ) {
      return
    }
    ensureChannel()
    val notification =
      NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_pill)
        .setContentTitle(supplement.name)
        .setContentText(
          context.getString(
            R.string.supplement_notification_text,
            formatMinuteOfDay(time.minuteOfDay),
          )
        )
        .setCategory(NotificationCompat.CATEGORY_REMINDER)
        .setWhen(scheduledAt)
        .setShowWhen(true)
        .setAutoCancel(true)
        .setContentIntent(openSupplementsIntent())
        .addAction(
          0,
          context.getString(R.string.supplement_taken_action),
          takenIntent(supplement.id, time, epochDayOf(scheduledAt)),
        )
        .build()
    manager.notify(TAG, time.id.toInt(), notification)
  }

  fun cancel(timeId: Long) = manager.cancel(TAG, timeId.toInt())

  private fun hasPermission(): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
      ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED

  private fun openSupplementsIntent(): PendingIntent =
    PendingIntent.getActivity(
      context,
      0,
      Intent(context, MainActivity::class.java)
        .setAction(MainActivity.ACTION_OPEN_SUPPLEMENTS)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

  private fun takenIntent(
    supplementId: Long,
    time: SupplementTimeEntity,
    epochDay: Long,
  ): PendingIntent =
    PendingIntent.getBroadcast(
      context,
      time.id.toInt(),
      Intent(context, ReminderActionReceiver::class.java)
        .setAction(ReminderActionReceiver.ACTION_TAKEN)
        .setData("ironlog://supplement-taken/${time.id}".toUri())
        .putExtra(ReminderActionReceiver.EXTRA_SUPPLEMENT_ID, supplementId)
        .putExtra(ReminderActionReceiver.EXTRA_TIME_ID, time.id)
        .putExtra(ReminderActionReceiver.EXTRA_EPOCH_DAY, epochDay)
        .putExtra(ReminderActionReceiver.EXTRA_MINUTE_OF_DAY, time.minuteOfDay),
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

  companion object {
    const val CHANNEL_ID = "supplement_reminders"
    private const val TAG = "supplement"
  }
}
