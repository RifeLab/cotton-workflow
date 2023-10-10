package org.phenoapps.cotton.adapters.viewholders

import android.view.View
import org.phenoapps.cotton.R
import org.phenoapps.cotton.interfaces.SampleController
import org.phenoapps.cotton.models.SampleModel
import org.phenoapps.cotton.util.LayoutUtil.Companion.shrink

internal class SeedViewHolder(view: View): ParentViewHolder(view) {

    override fun bind(controller: SampleController, model: SampleModel) {
        super.bind(controller, model)

        sampleHeaderTv.visibility = View.VISIBLE

        //make code invisible, seed code is same as parent
        codeIconIv.visibility = View.GONE
        codeTextView.visibility = View.GONE

        //set seed header
        sampleHeaderTv.text = controller.getString(R.string.list_item_seed_title,
            if (model.weight == null) controller.getString(R.string.list_item_sample_uncollected) else String())

        if (model.weight == null) {

            weightTimestampTextView.shrink()
            weightIcon.shrink()
            weightTextView.shrink()

        }

    }
}