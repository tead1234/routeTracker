package com.gojungparkjo.routetracker.model.crosswalk


import com.google.gson.annotations.SerializedName

data class Feature(
    @SerializedName("geometry")
    val geometry: Geometry?,
    @SerializedName("geometry_name")
    val geometryName: String?,
    @SerializedName("id")
    val id: String?,
    @SerializedName("properties")
    val properties: PropertiesX?,
    @SerializedName("type")
    val type: String?
)