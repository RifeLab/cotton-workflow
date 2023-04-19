package org.phenoapps.cotton.database.repositories

import org.phenoapps.cotton.database.dao.SampleDao
import org.phenoapps.cotton.database.entities.SampleEntity
import org.phenoapps.cotton.models.SampleModel
import javax.inject.Inject

class SampleRepository @Inject constructor(
    private val dao: SampleDao
) {

    fun getAll() = dao.getAll()

    suspend fun getSampleWithCode(code: String) = dao.getSampleWithCode(code)

    suspend fun insert(model: SampleModel) = dao.insert(SampleEntity(model))

    suspend fun updateSample(model: SampleModel) = dao.update(SampleEntity(model))

    suspend fun deleteSample(model: SampleModel) = dao.delete(SampleEntity(model))

    suspend fun deleteAll() = dao.deleteAll()

}