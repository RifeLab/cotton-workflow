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
    val weight: Double? = null,

    //the timestamp in which a sample was scanned and entered into database,
    // or a sub sample was created
    @ColumnInfo(name = "scan_time")
    val scanTime: Long? = null,

    //the timestamp when the sample was weighed, can be null
    @ColumnInfo(name = "scale_time")
    val scaleTime: Long? = null,

    //person that scanned/weighed the sample
    @ColumnInfo(name = "person")
    val person: String? = null,

    //null if it is a sample, else it is a sub sample where parent is the sid of original
    @ColumnInfo(name = "parent")
    val parent: Long? = null,

    //the type of sample this is
    @ColumnInfo(name = "type")
    val type: Int,

    //note for sample
    @ColumnInfo(name = "note")
    val note: String? = null

) {

    constructor(s: SampleModel) : this(s.sid, s.code, s.weight, s.scanTime, s.scaleTime, s.person, s.parent, s.type, s.note)

}