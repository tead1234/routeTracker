package com.gojungparkjo.routetracker.model.tmappoi


import com.google.gson.annotations.SerializedName

data class EvChargers(
    @SerializedName("evCharger")
    val evCharger: List<EvCharger>?
)