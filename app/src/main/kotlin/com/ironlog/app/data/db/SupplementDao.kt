package com.ironlog.app.data.db

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SupplementDao {
  @Query("SELECT * FROM supplement ORDER BY name COLLATE NOCASE")
  fun observeSupplements(): Flow<List<SupplementEntity>>

  @Query("SELECT * FROM supplement WHERE id = :id") suspend fun getSupplement(id: Long): SupplementEntity?

  @Query("SELECT * FROM supplement WHERE name = :name COLLATE NOCASE LIMIT 1")
  suspend fun findByName(name: String): SupplementEntity?

  @Insert suspend fun insertSupplement(supplement: SupplementEntity): Long

  @Query("UPDATE supplement SET name = :name WHERE id = :id")
  suspend fun renameSupplement(id: Long, name: String)

  @Query("DELETE FROM supplement WHERE id = :id") suspend fun deleteSupplement(id: Long)

  @Query("SELECT * FROM supplement_time ORDER BY minuteOfDay")
  fun observeTimes(): Flow<List<SupplementTimeEntity>>

  @Query("SELECT * FROM supplement_time") suspend fun getAllTimes(): List<SupplementTimeEntity>

  @Query("SELECT * FROM supplement_time WHERE supplementId = :supplementId ORDER BY minuteOfDay")
  suspend fun getTimes(supplementId: Long): List<SupplementTimeEntity>

  @Query("SELECT * FROM supplement_time WHERE id = :id")
  suspend fun getTime(id: Long): SupplementTimeEntity?

  @Insert suspend fun insertTime(time: SupplementTimeEntity): Long

  @Query("DELETE FROM supplement_time WHERE id = :id") suspend fun deleteTime(id: Long)

  @Query("SELECT * FROM supplement_intake WHERE epochDay = :epochDay")
  fun observeIntakes(epochDay: Long): Flow<List<SupplementIntakeEntity>>

  /** The most recent doses taken of one supplement, newest first. */
  @Query(
    """
    SELECT * FROM supplement_intake
    WHERE supplementId = :supplementId
    ORDER BY epochDay DESC, minuteOfDay
    LIMIT :limit
    """
  )
  fun observeRecentIntakes(supplementId: Long, limit: Int): Flow<List<SupplementIntakeEntity>>

  @Query(
    """
    SELECT COUNT(*) FROM supplement_intake
    WHERE supplementId = :supplementId AND epochDay = :epochDay AND minuteOfDay = :minuteOfDay
    """
  )
  suspend fun countIntakes(supplementId: Long, epochDay: Long, minuteOfDay: Int): Int

  /** Does nothing if the dose is already recorded. */
  @Insert(onConflict = OnConflictStrategy.IGNORE)
  suspend fun insertIntake(intake: SupplementIntakeEntity): Long

  @Query(
    """
    DELETE FROM supplement_intake
    WHERE supplementId = :supplementId AND epochDay = :epochDay AND minuteOfDay = :minuteOfDay
    """
  )
  suspend fun deleteIntake(supplementId: Long, epochDay: Long, minuteOfDay: Int)
}
