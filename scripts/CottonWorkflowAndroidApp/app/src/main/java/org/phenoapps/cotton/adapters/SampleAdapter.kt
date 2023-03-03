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
import org.phenoapps.cotton.adapters.viewholders.LintViewHolder
import org.phenoapps.cotton.adapters.viewholders.ParentViewHolder
import org.phenoapps.cotton.adapters.viewholders.SeedViewHolder
import org.phenoapps.cotton.adapters.viewholders.TestViewHolder
import org.phenoapps.cotton.interfaces.SampleController
import org.phenoapps.cotton.models.SampleModel
import org.phenoapps.cotton.util.WorkflowUtil

/**
 * Reference:
 * https://developer.android.com/guide/topics/ui/layout/recyclerview
 */
class SampleAdapter(private val controller: SampleController):
        ListAdapter<SampleModel, SampleAdapter.ViewHolder>(DiffCallback()) {

    open class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val content: ConstraintLayout = view.findViewById(R.id.list_item_sample_content)
        val codeTextView: TextView = view.findViewById(R.id.list_item_sample_code_header_tv)
        val codeIconIv: ImageView = view.findViewById(R.id.list_item_sample_id_header_iv)
        val weightTextView: TextView = view.findViewById(R.id.list_item_sample_weight_header_tv)
        val subSamplesRecyclerView: RecyclerView = view.findViewById(R.id.list_item_sample_sub_sample_rv)
        val analysisTextView: TextView = view.findViewById(R.id.list_item_sample_analysis_tv)
        val expandImageView: ImageView = view.findViewById(R.id.list_item_sample_expand_iv)
        val timestampTextView: TextView = view.findViewById(R.id.list_item_sample_timestamp_header_tv)
        val weightTimestampTextView: TextView = view.findViewById(R.id.list_item_sample_weight_timestamp_header_tv)
        val weightIcon: ImageView = view.findViewById(R.id.list_item_sample_weight_header_iv)
        val scaleIcon: ImageView = view.findViewById(R.id.list_item_sample_scale_header_iv)
        val scanIcon: ImageView = view.findViewById(R.id.list_item_sample_scan_header_iv)
        val sampleHeaderTv: TextView = view.findViewById(R.id.list_item_sample_tv)
        val scanTimeTv: TextView = view.findViewById(R.id.list_item_sample_timestamp_header_tv)

        open fun bind(controller: SampleController, model: SampleModel) {

        }
    }

    override fun getItemViewType(position: Int): Int {
        return currentList[position].type
    }

    // Create new views (invoked by the layout manager)
    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ViewHolder {
        // Create a new view, which defines the UI of the list item
        val view = LayoutInflater.from(viewGroup.context)
                .inflate(R.layout.list_item_sample, viewGroup, false)

        return when (viewType) {
            WorkflowUtil.Companion.SubSampleType.PARENT.ordinal -> ParentViewHolder(view)
            WorkflowUtil.Companion.SubSampleType.SEED.ordinal -> SeedViewHolder(view)
            WorkflowUtil.Companion.SubSampleType.LINT.ordinal -> LintViewHolder(view)
            else -> TestViewHolder(view)
        }
    }

    // Replace the contents of a view (invoked by the layout manager)
    override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {

        // Get element from your dataset at this position and replace the
        // contents of the view with that element
        with (currentList[position]) {

            when (type) {

                WorkflowUtil.Companion.SubSampleType.PARENT.ordinal -> {
                    (viewHolder as ParentViewHolder).bind(controller, this)
                }
                WorkflowUtil.Companion.SubSampleType.SEED.ordinal -> {
                    (viewHolder as SeedViewHolder).bind(controller, this)
                }
                WorkflowUtil.Companion.SubSampleType.LINT.ordinal -> {
                    (viewHolder as LintViewHolder).bind(controller, this)
                }
                WorkflowUtil.Companion.SubSampleType.TEST.ordinal -> {
                    (viewHolder as TestViewHolder).bind(controller, this)
                }
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