package com.ironlog.app.data.db

import android.content.Context
import androidx.room3.AutoMigration
import androidx.room3.Database
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.room3.migration.AutoMigrationSpec
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.ironlog.app.data.StoredExercise
import com.ironlog.app.data.planPreload
import kotlinx.coroutines.Dispatchers

@Database(
  entities =
    [
      CategoryEntity::class,
      ExerciseEntity::class,
      ExerciseSubCategoryEntity::class,
      WorkoutEntity::class,
      WorkoutExerciseEntity::class,
      SessionEntity::class,
      SessionExerciseEntity::class,
      SetEntity::class,
      SupplementEntity::class,
      SupplementTimeEntity::class,
      SupplementIntakeEntity::class,
    ],
  version = 3,
  autoMigrations =
    [
      // Version 2 only adds the supplement tables, which Room can derive from the exported schemas.
      AutoMigration(from = 1, to = 2),
      // Version 3 adds the sub category table the same way; its spec then merges the longer,
      // categorised pre-loaded list into the existing exercises.
      AutoMigration(from = 2, to = 3, spec = PreloadedListVersion3::class),
    ],
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

/** Fills the exercise list and its categories once, when the database file is created. */
private object SeedCallback : RoomDatabase.Callback() {
  override suspend fun onCreate(connection: SQLiteConnection) {
    applyPreloadedList(connection, storedVersion = 0)
  }
}

/** Runs after Room has created the tables that are new in version 3. */
internal class PreloadedListVersion3 : AutoMigrationSpec {
  override suspend fun onPostMigrate(connection: SQLiteConnection) {
    applyPreloadedList(connection, storedVersion = 2)
  }
}

/**
 * Brings [SeedExercises] into a database that was created by [storedVersion] (0 for a new one).
 * What exactly is added is decided by [planPreload]; categories are looked up by name, ignoring
 * case, and created when missing.
 */
private fun applyPreloadedList(connection: SQLiteConnection, storedVersion: Int) {
  val stored = buildList {
    connection
      .prepare(
        """
        SELECT e.id, e.name, c.name, e.archived
        FROM exercise e LEFT JOIN category c ON c.id = e.categoryId
        """
      )
      .use { row ->
        while (row.step()) {
          add(
            StoredExercise(
              id = row.getLong(0),
              name = row.getText(1),
              category = if (row.isNull(2)) null else row.getText(2),
              archived = row.getLong(3) != 0L,
            )
          )
        }
      }
  }
  val plan = planPreload(SeedExercises, stored, storedVersion)

  val categoryIds = HashMap<String, Long>()
  fun categoryId(name: String): Long =
    categoryIds.getOrPut(name.lowercase()) {
      connection.queryLong("SELECT id FROM category WHERE name = ? COLLATE NOCASE LIMIT 1", name)
        ?: connection.insert("INSERT INTO category (name) VALUES (?)", name)
    }
  fun addSubCategories(exerciseId: Long, names: List<String>) {
    for (name in names) {
      connection.execute(
        "INSERT OR IGNORE INTO exercise_sub_category (exerciseId, categoryId) VALUES (?, ?)",
        exerciseId,
        categoryId(name),
      )
    }
  }

  for (exercise in plan.newExercises) {
    val id =
      connection.insert(
        "INSERT INTO exercise (name, categoryId, archived) VALUES (?, ?, 0)",
        exercise.name,
        categoryId(exercise.category),
      )
    addSubCategories(id, exercise.subCategories)
  }
  for ((exerciseId, category) in plan.mainCategories) {
    // The condition keeps a main category the user chose, whatever the plan says.
    connection.execute(
      "UPDATE exercise SET categoryId = ? WHERE id = ? AND categoryId IS NULL",
      categoryId(category),
      exerciseId,
    )
  }
  for ((exerciseId, names) in plan.subCategories) addSubCategories(exerciseId, names)
}

/** Runs one statement; [args] are bound in order and may be Long, String or null. */
private fun SQLiteConnection.execute(sql: String, vararg args: Any?) {
  prepare(sql).use { statement ->
    statement.bindAll(args)
    statement.step()
  }
}

/** Runs an INSERT and returns the id of the new row. */
private fun SQLiteConnection.insert(sql: String, vararg args: Any?): Long {
  execute(sql, *args)
  return checkNotNull(queryLong("SELECT last_insert_rowid()"))
}

/** The first column of the first row, or null when there is no row or the value is NULL. */
private fun SQLiteConnection.queryLong(sql: String, vararg args: Any?): Long? =
  prepare(sql).use { statement ->
    statement.bindAll(args)
    if (statement.step() && !statement.isNull(0)) statement.getLong(0) else null
  }

private fun SQLiteStatement.bindAll(args: Array<out Any?>) {
  args.forEachIndexed { index, arg ->
    when (arg) {
      null -> bindNull(index + 1)
      is Long -> bindLong(index + 1, arg)
      is String -> bindText(index + 1, arg)
      else -> error("Cannot bind $arg")
    }
  }
}
