package com.ironlog.app

import android.app.Application
import android.content.Context
import com.ironlog.app.data.ExerciseRepository
import com.ironlog.app.data.PlanRepository
import com.ironlog.app.data.SessionRepository
import com.ironlog.app.data.db.IronLogDatabase

class IronLogApp : Application() {
  /** Created on first use so that app start does not wait for the database. */
  val container: AppContainer by lazy { AppContainer(this) }
}

/** The app's long-lived objects, shared by all screens. */
class AppContainer(context: Context) {
  private val database = IronLogDatabase.create(context)

  val exercises = ExerciseRepository(database)
  val plan = PlanRepository(database)
  val sessions = SessionRepository(database)
}
