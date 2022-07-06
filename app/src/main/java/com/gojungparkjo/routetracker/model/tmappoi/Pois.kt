package com.gojungparkjo.routetracker.model.tmappoi


import com.google.gson.annotations.SerializedName

data class Pois(
    @SerializedName("poi")
    val poi: List<Poi>?
)