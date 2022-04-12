package com.gojungparkjo.routetracker.model.trafficlight


import com.google.gson.annotations.SerializedName

data class Properties(
    @SerializedName("name")
    val name: String?
)