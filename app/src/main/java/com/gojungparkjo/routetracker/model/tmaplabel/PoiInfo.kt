package com.gojungparkjo.routetracker.model.tmaplabel


import com.google.gson.annotations.SerializedName

data class PoiInfo(
    @SerializedName("id")
    val id: String?,
    @SerializedName("name")
    val name: String?,
    @SerializedName("poiLat")
    val poiLat: Double?,
    @SerializedName("poiLon")
    val poiLon: Double?
)