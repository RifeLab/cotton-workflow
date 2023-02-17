package org.phenoapps.cotton.fragments

import android.app.Activity
import android.net.Uri
import org.phenoapps.cotton.activities.DefineStorageActivity
import org.phenoapps.fragments.storage.PhenoLibStorageDefinerFragment

class StorageDefinerFragment: PhenoLibStorageDefinerFragment() {

    //default root folder name if user choose an incorrect root on older devices
    override val defaultAppName: String = "cotton"

    //if this file exists the migrator will be skipped
    override val migrateChecker: String = ".cotton"

    override fun onTreeDefined(treeUri: Uri) {
        (activity as DefineStorageActivity).enableBackButton(false)
        super.onTreeDefined(treeUri)
        (activity as DefineStorageActivity).enableBackButton(true)
    }

    override fun actionNoMigrate() {
        activity?.setResult(Activity.RESULT_OK)
        activity?.finish()
    }
}