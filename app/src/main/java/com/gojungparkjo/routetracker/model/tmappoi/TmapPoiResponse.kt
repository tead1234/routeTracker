package com.gojungparkjo.routetracker.model.tmappoi


import com.google.gson.annotations.SerializedName

data class TmapPoiResponse(
    @SerializedName("searchPoiInfo")
    val searchPoiInfo: SearchPoiInfo?
)