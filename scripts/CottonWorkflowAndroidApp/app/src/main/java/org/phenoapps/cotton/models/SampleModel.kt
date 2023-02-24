package org.phenoapps.cotton.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import org.phenoapps.cotton.database.entities.SampleEntity
import java.text.SimpleDateFormat
import java.util.*

@Parcelize
data class SampleModel(var sid: Long? = null,
                       var code: String? = null,
                       var weight: Double? = null,
                       var scanTime: Long,
                       var scaleTime: Long? = null,
                       var person: String? = null,
                       var parent: Long? = null) : Parcelable {

    constructor(entity: SampleEntity): this(entity.sid, entity.code, entity.weight, entity.scanTime, entity.scaleTime, entity.person, entity.parent)

    fun toRowString(): String {

        val formatter = SimpleDateFormat.getDateTimeInstance()

        val scanTimestamp = try {
            formatter.format(scanTime)
                .replace(Regex("[:/, ]"), "_")
        } catch (e: IllegalArgumentException) {
            e.printStackTrace()
            null
        }

        val scaleTimestamp = try {
            formatter.format(scaleTime)
                .replace(Regex("[:/, ]"), "_")
        } catch (e: IllegalArgumentException) {
            e.printStackTrace()
            null
        }

        return "$sid, $code, $weight, $scanTimestamp, $scaleTimestamp, \"$person\", $parent"
    }
}