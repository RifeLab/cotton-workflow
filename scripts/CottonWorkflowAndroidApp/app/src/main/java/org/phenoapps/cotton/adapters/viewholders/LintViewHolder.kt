package org.phenoapps.cotton.adapters.viewholders

import android.view.View
import org.phenoapps.cotton.R
import org.phenoapps.cotton.interfaces.SampleController
import org.phenoapps.cotton.models.SampleModel

internal class LintViewHolder(view: View): ParentViewHolder(view) {

    override fun bind(controller: SampleController, model: SampleModel) {
        super.bind(controller, model)

        sampleHeaderTv.visibility = View.VISIBLE

        //lint does not have a barcode and is never scanned
        codeTextView.visibility = View.GONE
        scanIcon.visibility = View.GONE
        scanTimeTv.visibility = View.GONE

        codeIconIv.visibility = View.GONE

        //set lint header
        sampleHeaderTv.text = controller.getString(R.string.list_item_lint_title,
            if (model.weight == null) controller.getString(R.string.list_item_sample_uncollected) else String())

        expandImageView.visibility = View.GONE
    }
}