package com.ironlog.app.data.db

import android.content.Context
import androidx.room3.AutoMigration
import androidx.room3.Database
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers

@Database(
  entities =
    [
      CategoryEntity::class,
      ExerciseEntity::class,
      WorkoutEntity::class,
      WorkoutExerciseEntity::class,
      SessionEntity::class,
      SessionExerciseEntity::class,
      SetEntity::class,
      SupplementEntity::class,
      SupplementTimeEntity::class,
      SupplementIntakeEntity::class,
    ],
  version = 2,
  // Version 2 only adds the supplement tables, which Room can derive from the exported schemas.
  autoMigrations = [AutoMigration(from = 1, to = 2)],
)
abstract class IronLogDatabase : RoomDatabase() {
  abstract fun exerciseDao(): ExerciseDao

  abstract fun planDao(): PlanDao

  abstract fun sessionDao(): SessionDao

  abstract fun supplementDao(): SupplementDao

  companion object {
    fun create(context: Context): IronLogDatabase =
      Room.databaseBuilder<IronLogDatabase>(context.applicationContext, "ironlog.db")
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .addCallback(SeedCallback)
        .build()
  }
}

/** Fills the exercise list once, when the database file is created. */
private object SeedCallback : RoomDatabase.Callback() {
  override suspend fun onCreate(connection: SQLiteConnection) {
    connection.prepare("INSERT INTO exercise (name, categoryId, archived) VALUES (?, NULL, 0)").use {
      statement ->
      for (name in SeedExercises) {
        statement.bindText(1, name)
        statement.step()
        statement.reset()
      }
    }
  }
}
