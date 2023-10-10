package org.phenoapps.cotton.adapters.viewholders

import android.view.View
import org.phenoapps.cotton.R
import org.phenoapps.cotton.interfaces.SampleController
import org.phenoapps.cotton.models.SampleModel

internal class TestViewHolder(view: View): ParentViewHolder(view) {

    override fun bind(controller: SampleController, model: SampleModel) {
        super.bind(controller, model)

        sampleHeaderTv.visibility = View.VISIBLE

        //set test header
        sampleHeaderTv.text = controller.getString(R.string.list_item_test_title,
            if (model.weight == null) controller.getString(R.string.list_item_sample_uncollected)
            else if (model.code == null) controller.getString(R.string.list_item_sample_unscanned)
            else String())

        expandImageView.visibility = View.GONE

    }
}