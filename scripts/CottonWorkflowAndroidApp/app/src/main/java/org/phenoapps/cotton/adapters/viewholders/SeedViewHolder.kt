package org.phenoapps.cotton.adapters.viewholders

import android.view.View
import org.phenoapps.cotton.R
import org.phenoapps.cotton.interfaces.SampleController
import org.phenoapps.cotton.models.SampleModel
import org.phenoapps.cotton.util.LayoutUtil.Companion.shrink

internal class SeedViewHolder(view: View): ParentViewHolder(view) {

    override fun bind(controller: SampleController, model: SampleModel) {
        super.bind(controller, model)

        //make code invisible, seed code is same as parent
        codeIconIv.visibility = View.GONE
        codeTextView.visibility = View.GONE

        //same for scan ui
        scanIcon.visibility = View.GONE
        scanTimeTv.visibility = View.GONE

        //set seed header
        sampleHeaderTv.text = controller.getString(R.string.list_item_seed_title,
            if (model.weight == null) controller.getString(R.string.list_item_sample_uncollected) else String())

        subSamplesRecyclerView.shrink()
        expandImageView.shrink()

        if (model.weight == null) {

            weightTimestampTextView.shrink()
            weightIcon.shrink()
            weightTextView.shrink()

        }

    }
}