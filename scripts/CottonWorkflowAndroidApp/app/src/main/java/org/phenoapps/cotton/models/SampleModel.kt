package org.phenoapps.cotton.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import org.phenoapps.cotton.database.entities.SampleEntity

@Parcelize
data class SampleModel(var sid: Long? = null,
                       var code: String? = null,
                       var weight: String? = null,
                       var scanTime: Long,
                       var scaleTime: Long? = null,
                       var parent: Long? = null) : Parcelable {

    constructor(entity: SampleEntity): this(entity.sid, entity.code, entity.weight, entity.scanTime, entity.scaleTime, entity.parent)

}