package com.gojungparkjo.routetracker.model.intersection

data class InterSectionResponse(
    val crs: Crs,
    val features: List<Feature>,
    val totalFeatures: Int,
    val type: String
)