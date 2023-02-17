package org.phenoapps.cotton.fragments

import android.app.Activity
import androidx.documentfile.provider.DocumentFile
import org.phenoapps.cotton.activities.DefineStorageActivity
import org.phenoapps.fragments.storage.PhenoLibMigratorFragment

class StorageMigratorFragment: PhenoLibMigratorFragment() {

    override fun migrateStorage(from: DocumentFile, to: DocumentFile) {
        (activity as DefineStorageActivity).enableBackButton(false)
        super.migrateStorage(from, to)
        (activity as DefineStorageActivity).enableBackButton(true)
    }

    override fun navigateEnd() {
        activity?.runOnUiThread {
            activity?.setResult(Activity.RESULT_OK)
            activity?.finish()
        }
    }
}