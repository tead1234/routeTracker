package com.gojungparkjo.routetracker.data.room

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class RecentGuideItem(
    @PrimaryKey(autoGenerate = true) val uid: Int = 0,
    val name: String?,
    val addr: String?,
    val frontLat: String?,
    val frontLon: String?,
    val noorLat: String?,
    val noorLon: String?,
    val timeStamp:Long
)