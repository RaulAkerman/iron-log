package com.ironlog.app.data

import androidx.room3.withWriteTransaction
import com.ironlog.app.data.db.IronLogDatabase
import com.ironlog.app.data.db.SupplementEntity
import com.ironlog.app.data.db.SupplementIntakeEntity
import com.ironlog.app.data.db.SupplementTimeEntity
import kotlinx.coroutines.flow.Flow

/** Outcome of saving a supplement. */
sealed interface SupplementSaveResult {
  /** Saved; lists what changed in the schedule so the matching alarms can be updated. */
  data class Saved(
    val supplementId: Long,
    val addedTimes: List<SupplementTimeEntity>,
    val removedTimeIds: List<Long>,
  ) : SupplementSaveResult

  /** Another supplement already has this name. */
  data object NameTaken : SupplementSaveResult

  /** Blank name, no times, or a time outside the day. */
  data object Invalid : SupplementSaveResult
}

/** Supplements, their daily times and the record of doses taken. Data only; no alarms. */
class SupplementRepository(
  private val db: IronLogDatabase,
  private val now: () -> Long = System::currentTimeMillis,
) {
  private val dao = db.supplementDao()

  val supplements: Flow<List<SupplementEntity>> = dao.observeSupplements()
  val times: Flow<List<SupplementTimeEntity>> = dao.observeTimes()

  /** Doses taken on the given day. */
  fun intakes(epochDay: Long): Flow<List<SupplementIntakeEntity>> = dao.observeIntakes(epochDay)

  /** The latest doses taken of one supplement, newest first. */
  fun recentIntakes(supplementId: Long): Flow<List<SupplementIntakeEntity>> =
    dao.observeRecentIntakes(supplementId, RECENT_INTAKE_LIMIT)

  suspend fun supplement(id: Long): SupplementEntity? = dao.getSupplement(id)

  suspend fun time(id: Long): SupplementTimeEntity? = dao.getTime(id)

  suspend fun timesOf(supplementId: Long): List<SupplementTimeEntity> = dao.getTimes(supplementId)

  suspend fun allTimes(): List<SupplementTimeEntity> = dao.getAllTimes()

  suspend fun isTaken(supplementId: Long, epochDay: Long, minuteOfDay: Int): Boolean =
    dao.countIntakes(supplementId, epochDay, minuteOfDay) > 0

  /**
   * Creates the supplement when [id] is null, otherwise renames it, and makes its daily times equal
   * to [minutesOfDay].
   */
  suspend fun save(id: Long?, name: String, minutesOfDay: Set<Int>): SupplementSaveResult {
    val cleanName = name.trim()
    val validTimes = minutesOfDay.isNotEmpty() && minutesOfDay.all { it in 0 until MINUTES_PER_DAY }
    if (cleanName.isEmpty() || !validTimes) return SupplementSaveResult.Invalid
    return db.withWriteTransaction {
      val sameName = dao.findByName(cleanName)
      if (sameName != null && sameName.id != id) {
        return@withWriteTransaction SupplementSaveResult.NameTaken
      }
      val supplementId =
        if (id == null) {
          dao.insertSupplement(SupplementEntity(name = cleanName))
        } else {
          dao.renameSupplement(id, cleanName)
          id
        }
      val existing = dao.getTimes(supplementId)
      val removed = existing.filter { it.minuteOfDay !in minutesOfDay }
      removed.forEach { dao.deleteTime(it.id) }
      val added =
        (minutesOfDay - existing.mapTo(HashSet()) { it.minuteOfDay }).sorted().map { minute ->
          val time = SupplementTimeEntity(supplementId = supplementId, minuteOfDay = minute)
          time.copy(id = dao.insertTime(time))
        }
      SupplementSaveResult.Saved(supplementId, added, removed.map { it.id })
    }
  }

  /** Deletes the supplement with its times and record; returns the ids of the deleted times. */
  suspend fun delete(id: Long): List<Long> =
    db.withWriteTransaction {
      val timeIds = dao.getTimes(id).map { it.id }
      dao.deleteSupplement(id)
      timeIds
    }

  /** Records the dose as taken now, or removes that record again. */
  suspend fun setTaken(supplementId: Long, epochDay: Long, minuteOfDay: Int, taken: Boolean) {
    if (taken) {
      dao.insertIntake(
        SupplementIntakeEntity(
          supplementId = supplementId,
          epochDay = epochDay,
          minuteOfDay = minuteOfDay,
          takenAt = now(),
        )
      )
    } else {
      dao.deleteIntake(supplementId, epochDay, minuteOfDay)
    }
  }

  private companion object {
    const val RECENT_INTAKE_LIMIT = 100
  }
}
