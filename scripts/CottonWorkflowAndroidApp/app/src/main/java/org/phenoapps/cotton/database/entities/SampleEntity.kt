package org.phenoapps.cotton.database.entities

import androidx.annotation.Keep
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import org.phenoapps.cotton.models.SampleModel

@Keep
@Entity(tableName = "samples")
data class SampleEntity(

    //the primary key, just a long, set by app
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "sid", index = true)
    val sid: Long? = null,

    //the barcode string, this will never be null
    @ColumnInfo(name = "code")
    val code: String? = null,

    //the raw weight string, this will always start as null before the scale is used
    @ColumnInfo(name = "weight")
    val weight: String? = null,

    //the timestamp in which a sample was scanned and entered into database,
    // or a sub sample was created
    @ColumnInfo(name = "scan_time")
    val scanTime: Long,

    //the timestamp when the sample was weighed, can be null
    @ColumnInfo(name = "scale_time")
    val scaleTime: Long? = null,

    //null if it is a sample, else it is a sub sample where parent is the sid of original
    @ColumnInfo(name = "parent")
    val parent: Long? = null
) {

    constructor(sample: SampleModel) : this(sample.sid, sample.code, sample.weight, sample.scanTime, sample.scaleTime, sample.parent)

}