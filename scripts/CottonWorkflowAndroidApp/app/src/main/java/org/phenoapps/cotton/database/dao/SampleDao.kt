package org.phenoapps.cotton.database.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import org.phenoapps.cotton.database.entities.SampleEntity

@Dao
interface SampleDao {

    @Query("SELECT * FROM samples ORDER BY scan_time DESC")
    fun getAll(): LiveData<List<SampleEntity>>

    @Query("SELECT * FROM samples WHERE code = :code LIMIT 1")
    suspend fun getSampleWithCode(code: String): SampleEntity?

    @Insert
    suspend fun insert(model: SampleEntity): Long

    @Update
    suspend fun update(model: SampleEntity)

    @Delete
    suspend fun delete(model: SampleEntity)

    @Query("UPDATE samples SET weight = :weight WHERE sid = :id")
    suspend fun writeWeight(id: Long, weight: String)
}