package org.phenoapps.cotton.fragments

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import dagger.hilt.android.AndroidEntryPoint
import org.phenoapps.cotton.R
import org.phenoapps.cotton.interfaces.MainToolbarManager

@AndroidEntryPoint
class ScaleConfigHelpFragment: Fragment(R.layout.fragment_scale_config_helper) {

    override fun onResume() {
        super.onResume()
        (activity as MainToolbarManager).updateToolbarVisibility()
    }
}