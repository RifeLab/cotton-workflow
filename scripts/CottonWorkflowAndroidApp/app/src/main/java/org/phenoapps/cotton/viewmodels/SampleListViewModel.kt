package org.phenoapps.cotton.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import org.phenoapps.cotton.database.repositories.SampleRepository
import org.phenoapps.cotton.models.SampleModel
import javax.inject.Inject

@HiltViewModel
class SampleListViewModel @Inject constructor(
    private val samplesRepo: SampleRepository
): ViewModel() {

    fun getSamples() = samplesRepo.getAll()

    suspend fun insertSample(model: SampleModel) = samplesRepo.insert(model)

    suspend fun getSampleWithCode(code: String) = samplesRepo.getSampleWithCode(code)

    fun updateSample(model: SampleModel) {
        viewModelScope.launch {
            samplesRepo.updateSample(model)
        }
    }

    fun deleteSample(model: SampleModel) {
        viewModelScope.launch {
            samplesRepo.deleteSample(model)
        }
    }
}