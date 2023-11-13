package org.phenoapps.cotton.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import org.phenoapps.cotton.database.entities.SampleEntity

@Parcelize
data class SampleModel(var sid: Long? = null,
                       var code: String? = null,
                       var weight: Double? = null,
                       var scanTime: Long? = null,
                       var scaleTime: Long? = null,
                       var person: String? = null,
                       var parent: Long? = null,
                       var type: Int,
                       var note: String? = null,
                       var experiment: String? = null) : Parcelable {

    constructor(e: SampleEntity): this(e.sid, e.code, e.weight, e.scanTime, e.scaleTime, e.person, e.parent, e.type, e.note, e.experiment)
}