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

        sampleHeaderTv.text = controller.getString(R.string.list_item_sample_parent_title)

        subSamplesRecyclerView.shrink()

        if (model.code != null) {

            codeTextView.text = model.code

        } else {

            codeIconIv.visibility = View.GONE
            codeTextView.visibility = View.GONE
            scanIcon.visibility = View.GONE
            scanTimeTv.visibility = View.GONE

        }

        if (model.scanTime != null) {

            timestampTextView.text = model.scanTime?.toDateString()

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

        //hide/show recycler content with chevron button that switches drawable states
        expandImageView.setOnClickListener {
            expandImageView.setImageResource(when (expandImageView.tag) {
                "down" -> {
                    subSamplesRecyclerView.wrap()
                    expandImageView.tag = "up"
                    R.drawable.chevron_up
                }
                else -> {
                    subSamplesRecyclerView.shrink()
                    expandImageView.tag = "down"
                    R.drawable.chevron_down
                }
            })
        }

        //only show expand button if it is a parent
        if (model.parent != null) {
            expandImageView.visibility = View.GONE
        }

        if (model.weight != null) {

            scaleIcon.visibility = View.VISIBLE
            weightIcon.visibility = View.VISIBLE
            weightTextView.text = controller
                .getString(R.string.grams_unit,"${model.weight}")
            weightTextView.wrap()
            weightIcon.wrap()
            weightTimestampTextView.wrap()

        } else {

            scaleIcon.visibility = View.GONE
            weightIcon.visibility = View.GONE
            weightTextView.shrink()
            weightIcon.shrink()
            weightTimestampTextView.shrink()
        }

        analysisTextView.shrink()

        //check that this id has been assigned
        model.sid?.let { id ->

            val errorThresh = controller.getErrorThresh()
            val subsamples = controller.getSubSamples(id)
            val adapter = SampleAdapter(controller)
            adapter.submitList(subsamples.sortedBy { it.sid }.toList())
            adapter.notifyItemRangeChanged(0, subsamples.size)

            subSamplesRecyclerView.adapter = adapter

            if (subsamples.size == WorkflowUtil.NumSubSamples) {

                val w = model.weight
                val subWeight1 = subsamples[0].weight //seed weight
                val subWeight2 = subsamples[1].weight //lint weight

                try {

                    if (w != null && subWeight1 != null && subWeight2 != null) {

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
                    }

                } catch (e: java.lang.Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
}