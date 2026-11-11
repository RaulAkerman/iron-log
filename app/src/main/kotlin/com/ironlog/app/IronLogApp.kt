package com.ironlog.app

import android.app.Application
import android.content.Context
import com.ironlog.app.data.ExerciseRepository
import com.ironlog.app.data.PlanRepository
import com.ironlog.app.data.SessionRepository
import com.ironlog.app.data.SupplementRepository
import com.ironlog.app.data.db.IronLogDatabase
import com.ironlog.app.reminders.ReminderScheduler
import com.ironlog.app.reminders.SupplementNotifications
import com.ironlog.app.reminders.SupplementReminders
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class IronLogApp : Application() {
  /** Created on first use so that app start does not wait for the database. */
  val container: AppContainer by lazy { AppContainer(this) }

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

  override fun onCreate() {
    super.onCreate()
    // Runs for every process start, including one caused by an alarm or a reboot, so reminders
    // recover on their own if the system dropped them.
    scope.launch {
      container.notifications.ensureChannel()
      container.reminders.ensureScheduled()
    }
  }
}

/** The app's long-lived objects, shared by all screens and broadcast receivers. */
class AppContainer(context: Context) {
  private val database = IronLogDatabase.create(context)

  val exercises = ExerciseRepository(database)
  val plan = PlanRepository(database)
  val sessions = SessionRepository(database)
  val supplements = SupplementRepository(database)

  val notifications = SupplementNotifications(context.applicationContext)
  val reminders =
    SupplementReminders(supplements, ReminderScheduler(context.applicationContext), notifications)
}
