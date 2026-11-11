package com.ironlog.app.reminders

import com.ironlog.app.data.SupplementRepository
import com.ironlog.app.data.SupplementSaveResult
import com.ironlog.app.data.epochDayOf

/**
 * Keeps the system alarms and shown notifications in step with the supplement data. Every change
 * that affects reminders goes through here rather than straight to the repository.
 */
class SupplementReminders(
  private val repository: SupplementRepository,
  private val scheduler: ReminderScheduler,
  private val notifications: SupplementNotifications,
) {
  /** Saves a supplement and updates the alarms for the times that were added or removed. */
  suspend fun save(id: Long?, name: String, minutesOfDay: Set<Int>): SupplementSaveResult {
    val result = repository.save(id, name, minutesOfDay)
    if (result is SupplementSaveResult.Saved) {
      result.removedTimeIds.forEach(::forget)
      result.addedTimes.forEach(scheduler::schedule)
    }
    return result
  }

  suspend fun delete(supplementId: Long) {
    repository.delete(supplementId).forEach(::forget)
  }

  /**
   * Ticks a dose off, or un-ticks it. Taking it removes its reminder if one is showing. [timeId] is
   * the schedule entry the dose belongs to, when it still exists.
   */
  suspend fun setTaken(
    supplementId: Long,
    timeId: Long?,
    epochDay: Long,
    minuteOfDay: Int,
    taken: Boolean,
  ) {
    repository.setTaken(supplementId, epochDay, minuteOfDay, taken)
    if (taken && timeId != null) notifications.cancel(timeId)
  }

  /**
   * Handles the alarm of one time going off: shows the reminder unless the dose is already taken,
   * then sets the alarm for the next day. [scheduledAt] is when the alarm was due, which can be
   * well before now if the phone was idle.
   */
  suspend fun onReminderDue(timeId: Long, scheduledAt: Long) {
    // The time or its supplement may have been deleted since the alarm was set.
    val time = repository.time(timeId) ?: return
    val supplement = repository.supplement(time.supplementId) ?: return
    if (!repository.isTaken(supplement.id, epochDayOf(scheduledAt), time.minuteOfDay)) {
      notifications.show(supplement, time, scheduledAt)
    }
    scheduler.schedule(time)
  }

  /** Sets every alarm again. Needed after a reboot, an app update and clock or time zone changes. */
  suspend fun rescheduleAll() {
    repository.allTimes().forEach(scheduler::schedule)
  }

  /**
   * Sets the alarms that are missing, for example after the app was force-stopped. Alarms that are
   * still pending are left alone, so one that is overdue but not yet delivered is not skipped.
   */
  suspend fun ensureScheduled() {
    repository.allTimes().filterNot { scheduler.isScheduled(it.id) }.forEach(scheduler::schedule)
  }

  private fun forget(timeId: Long) {
    scheduler.cancel(timeId)
    notifications.cancel(timeId)
  }
}
