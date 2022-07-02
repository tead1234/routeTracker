package com.gojungparkjo.routetracker.model.tmaplabel
import com.google.gson.annotations.SerializedName
data class TmapLabelResponse(
    @SerializedName("poiInfo")
    val poiInfo: PoiInfo?
)