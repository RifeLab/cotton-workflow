package org.phenoapps.cotton.adapters.viewholders

import android.graphics.Color
import android.view.View
import org.phenoapps.cotton.R
import org.phenoapps.cotton.adapters.SampleAdapter
import org.phenoapps.cotton.interfaces.SampleController
import org.phenoapps.cotton.models.SampleModel
import org.phenoapps.cotton.util.DateUtil.Companion.toDateString
import org.phenoapps.cotton.util.LayoutUtil.Companion.shrink
import org.phenoapps.cotton.util.LayoutUtil.Companion.wrap
import org.phenoapps.cotton.util.WorkflowUtil
import kotlin.math.abs

internal open class ParentViewHolder(view: View): SampleAdapter.ViewHolder(view) {

    override fun bind(controller: SampleController, model: SampleModel) {

        sampleHeaderTv.visibility = View.GONE

        if (model.code != null) {

            codeTextView.text = model.code

        } else {

            codeIconIv.visibility = View.GONE
            codeTextView.visibility = View.GONE
        }

        if (model.scaleTime != null) {

            weightTimestampTextView.text = model.scaleTime?.toDateString()

            weightTimestampTextView.wrap()
            weightIcon.wrap()

        } else {

            weightTimestampTextView.shrink()
            weightIcon.shrink()
        }

        content.setOnClickListener {
            controller.sampleClicked(model)
        }

        //only show expand button if it is a parent
        if (model.parent != null) {
            expandImageView.visibility = View.GONE
        }

        if (model.weight != null) {

            weightTextView.text = "${model.weight}"

            scaleIcon.visibility = View.VISIBLE

            weightTextView.wrap()

            weightTimestampTextView.wrap()

        } else {

            scaleIcon.visibility = View.GONE

            weightTimestampTextView.shrink()

        }

        analysisTextView.shrink()

        //check that this id has been assigned
        model.sid?.let { id ->

            val testEnabled = controller.getTestEnabled()
            val errorEnabled = controller.getErrorEnabled()
            val errorThresh = controller.getErrorThresh()
            val subsamples = controller.getSubSamples(id)

            //val adapter = SampleAdapter(controller)
            //adapter.submitList(subsamples.sortedBy { it.sid }.toList())
            //adapter.notifyItemRangeChanged(0, subsamples.size)

            //subSamplesRecyclerView.adapter = adapter

            if (subsamples.size == WorkflowUtil.NumSubSamples) {

                val w = model.weight
                val subWeight1 = subsamples.first { it.type == WorkflowUtil.Companion.SubSampleType.SEED.ordinal }.weight //seed weight
                val subWeight2 = subsamples.first { it.type == WorkflowUtil.Companion.SubSampleType.LINT.ordinal }.weight //lint weight
                val testCode = subsamples.first { it.type == WorkflowUtil.Companion.SubSampleType.TEST.ordinal }.code //test barcode

                if (subWeight1 != null) seedWeightTv.text = subWeight1.toString()
                if (subWeight2 != null) lintWeightTv.text = subWeight2.toString()

                analysisIcon.visibility = View.GONE
                analysisTextView.visibility = View.GONE

                //show test icon if test is enabled
                if (testCode != null && testCode.isNotBlank()) {

                    testIcon.visibility = View.VISIBLE
                    testText.visibility = View.VISIBLE
                    testText.text = testCode

                } else {

                    testIcon.visibility = View.GONE
                    testText.visibility = View.GONE

                }

                if (errorEnabled) {

                    try {

                        if (w != null && subWeight1 != null && subWeight2 != null) {

                            analysisIcon.visibility = View.VISIBLE
                            analysisTextView.visibility = View.VISIBLE

                            val doubleError = abs(w - (subWeight1 + subWeight2))
                            var error = doubleError.toString()

                            val length = error.length

                            if (length > 5) {

                                //TODO delete trailing zeroes
                                error = error.substring(0..5)
                            }

                            analysisTextView.wrap()
                            analysisTextView.text = controller.getString(R.string.list_item_sample_weight_diff, error)

                            analysisTextView.setTextColor(
                                if (doubleError > abs(errorThresh)) Color.RED
                                else Color.GREEN)

                            analysisIcon.setImageResource(
                                if (doubleError > abs(errorThresh)) R.drawable.alert_circle
                                else R.drawable.check_circle_outline)
                        }

                    } catch (e: java.lang.Exception) {

                        e.printStackTrace()

                    }
                }
            }

            // update note ui when the database value exists
            if (!model.note.isNullOrBlank()) {

                noteText.text = model.note
                noteText.visibility = View.VISIBLE
                noteIcon.visibility = View.VISIBLE

            } else {

                noteText.visibility = View.GONE
                noteIcon.visibility = View.GONE

            }
        }
    }
}