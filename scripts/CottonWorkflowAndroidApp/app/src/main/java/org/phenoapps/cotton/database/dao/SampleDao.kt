package org.phenoapps.cotton.database.dao

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Update
import org.phenoapps.cotton.database.entities.SampleEntity
import org.phenoapps.cotton.models.SampleModel

@Dao
interface SampleDao {

    @Query("SELECT * FROM samples ORDER BY scan_time DESC")
    fun getAll(): LiveData<List<SampleEntity>>

    @Query("SELECT * FROM samples WHERE code = :code LIMIT 1")
    suspend fun getSampleWithCode(code: String): SampleEntity?

    suspend fun insert(model: SampleModel): Long = insert(model.sid, model.code, model.weight, model.scanTime, model.scaleTime, model.parent, model.person)

    @Update
    suspend fun update(model: SampleEntity)

    @Delete
    suspend fun delete(model: SampleEntity)

    @Query("INSERT INTO samples(sid, code, weight, scan_time, scale_time, parent, person) VALUES(:sid, :code, :weight, :scanTime, :scaleTime, :parent, :person)")
    suspend fun insert(sid: Long?, code: String?, weight: Double? = null, scanTime: Long, scaleTime: Long? = null, parent: Long? = null, person: String? = null): Long

    @Query("UPDATE samples SET weight = :weight WHERE sid = :id")
    suspend fun writeWeight(id: Long, weight: String)
}