package org.phenoapps.cotton.interfaces

import org.phenoapps.cotton.models.SampleModel

/**
 * This interface should implement queries
 * to get all samples,
 * and subsamples from a given sample code.
 */
interface SampleController {

    // return all samples
    fun getSamples(): Array<SampleModel>

    // return subsamples of a given sample id
    fun getSubSamples(sid: Long): Array<SampleModel>

    // write weight of a sample
    fun writeWeight(model: SampleModel)

    // prints label for a give sample, returns true/false if fails
    fun printSample(model: SampleModel): Boolean

    // start a barcode scan to save code into model
    fun scanSample(model: SampleModel)

    // delete sample and sub samples from database
    fun deleteSample(model: SampleModel)

    // if a sample was clicked
    fun sampleClicked(model: SampleModel)

    // add a subsample action
    fun addSample(model: SampleModel)

    // start automatic workflow action
    // edit mode overrides the automatic workflow and user inputs data
    // new mode is for creating a new sample (changes back button behaviour)
    fun workflow(model: SampleModel, edit: Boolean, new: Boolean)

    // returns the error thresh preference
    fun getErrorEnabled(): Boolean

    // returns the test mode preference
    fun getTestEnabled(): Boolean

    // view code returns the error thresh preference
    fun getErrorThresh(): Double

    fun getString(id: Int): String
    fun getString(id: Int, diff: String): String
}