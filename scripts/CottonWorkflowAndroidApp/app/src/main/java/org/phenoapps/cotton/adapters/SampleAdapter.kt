package org.phenoapps.cotton.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.phenoapps.cotton.R
import org.phenoapps.cotton.interfaces.SampleController
import org.phenoapps.cotton.models.SampleModel
import org.phenoapps.cotton.util.BarcodeUtil
import org.phenoapps.cotton.util.LayoutUtil.Companion.shrink
import org.phenoapps.cotton.util.LayoutUtil.Companion.wrap
import java.text.SimpleDateFormat
import kotlin.math.abs

/**
 * Reference:
 * https://developer.android.com/guide/topics/ui/layout/recyclerview
 */
class SampleAdapter(private val controller: SampleController):
        ListAdapter<SampleModel, SampleAdapter.ViewHolder>(DiffCallback()) {

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val content: ConstraintLayout = view.findViewById(R.id.list_item_sample_content)
        val codeTextView: TextView = view.findViewById(R.id.list_item_sample_code_header_tv)
        val weightTextView: TextView = view.findViewById(R.id.list_item_sample_weight_header_tv)
        val codePreviewImageView: ImageView = view.findViewById(R.id.list_item_sample_code_iv)
        val subSamplesRecyclerView: RecyclerView = view.findViewById(R.id.list_item_sample_sub_sample_rv)
        val analysisTextView: TextView = view.findViewById(R.id.list_item_sample_analysis_tv)
        val expandImageView: ImageView = view.findViewById(R.id.list_item_sample_expand_iv)
        val timestampTextView: TextView = view.findViewById(R.id.list_item_sample_timestamp_header_tv)
        val weightTimestampTextView: TextView = view.findViewById(R.id.list_item_sample_weight_timestamp_header_tv)
    }

    // Create new views (invoked by the layout manager)
    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ViewHolder {
        // Create a new view, which defines the UI of the list item
        val view = LayoutInflater.from(viewGroup.context)
                .inflate(R.layout.list_item_sample, viewGroup, false)

        return ViewHolder(view)
    }

    // Replace the contents of a view (invoked by the layout manager)
    override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {

        // Get element from your dataset at this position and replace the
        // contents of the view with that element
        with (currentList[position]) {

            this.code?.let { c ->

                viewHolder.subSamplesRecyclerView.shrink()

                viewHolder.codeTextView.text = c

                val formatter = SimpleDateFormat.getDateTimeInstance()

                viewHolder.timestampTextView.text = formatter.format(scanTime)

                if (scaleTime != null) {

                    viewHolder.weightTimestampTextView.text = formatter.format(scaleTime)

                    viewHolder.weightTimestampTextView.wrap()

                } else {

                    viewHolder.weightTimestampTextView.shrink()
                }

                viewHolder.content.setOnClickListener {
                    controller.sampleClicked(this)
                }

                //hide/show recycler content with chevron button that switches drawable states
                viewHolder.expandImageView.setOnClickListener {
                    viewHolder.expandImageView.setImageResource(when (viewHolder.expandImageView.tag) {
                        "down" -> {
                            viewHolder.subSamplesRecyclerView.wrap()
                            viewHolder.expandImageView.tag = "up"
                            R.drawable.chevron_up
                        }
                        else -> {
                            viewHolder.subSamplesRecyclerView.shrink()
                            viewHolder.expandImageView.tag = "down"
                            R.drawable.chevron_down
                        }
                    })
                }

                //only show expand button if it is a parent
                if (parent != null) {
                    viewHolder.expandImageView.visibility = View.GONE
                }

                if (this.weight != null) {

                    viewHolder.weightTextView.text = this.weight
                    viewHolder.weightTextView.wrap()

                } else {

                    viewHolder.weightTextView.shrink()
                }

                BarcodeUtil.encodeBarcode(c, { bmp ->

                    viewHolder.codePreviewImageView.setImageBitmap(bmp)

                })

                //check that this id has been assigned
                this.sid?.let { id ->

                    val subsamples = controller.getSubSamples(id)
                    val adapter = SampleAdapter(controller)
                    adapter.submitList(subsamples.toList())
                    adapter.notifyItemRangeChanged(0, subsamples.size)

                    viewHolder.subSamplesRecyclerView.adapter = adapter

                    if (subsamples.size == 2) {

                        //TODO generify weight this expects g or no unit
                        val totalWeight = weight?.replace("g", "")
                        val subWeight1 = subsamples[0].weight?.replace("g", "")
                        val subWeight2 = subsamples[1].weight?.replace("g", "")

                        try {

                            val x = totalWeight?.toDouble()
                            val y = subWeight1?.toDouble()
                            val z = subWeight2?.toDouble()

                            if (x != null && y != null && z != null) {

                                var error = abs(x - (y + z)).toString()

                                val length = error.length

                                if (length > 5) {

                                    error = error.substring(0..5)
                                }

                                viewHolder.analysisTextView.text = controller.getString(R.string.list_item_sample_weight_diff, error)
                            }

                        } catch (e: java.lang.NumberFormatException) {
                            e.printStackTrace()
                        }
                    }
                }

//                if (parent != null) {
//
//                    viewHolder.content.shrink()
//
//                } else {
//
//                    viewHolder.content.wrap()
//
//                }
            }
        }
    }

    // Return the size of your dataset (invoked by the layout manager)
    override fun getItemCount() = currentList.size

    class DiffCallback : DiffUtil.ItemCallback<SampleModel>() {

        override fun areItemsTheSame(oldItem: SampleModel, newItem: SampleModel): Boolean {
            return oldItem == newItem
        }

        override fun areContentsTheSame(oldItem: SampleModel, newItem: SampleModel): Boolean {
            return oldItem == newItem
        }
    }
}