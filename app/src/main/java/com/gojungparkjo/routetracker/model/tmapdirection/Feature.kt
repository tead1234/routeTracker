package com.gojungparkjo.routetracker.model.tmapdirection

data class Feature(
    val geometry: Geometry,
    val properties: Properties,
    val type: String
)