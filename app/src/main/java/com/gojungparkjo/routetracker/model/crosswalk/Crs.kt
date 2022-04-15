package com.gojungparkjo.routetracker.model.crosswalk


import com.google.gson.annotations.SerializedName

data class Crs(
    @SerializedName("properties")
    val properties: Properties?,
    @SerializedName("type")
    val type: String?
)