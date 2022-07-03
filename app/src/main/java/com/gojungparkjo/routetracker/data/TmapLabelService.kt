package com.gojungparkjo.routetracker.data

import com.gojungparkjo.routetracker.model.pedestrianroad.PedestrianRoadResponse
import com.gojungparkjo.routetracker.model.tmaplabel.TmapLabelResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface TmapLabelService {
    @GET("geo/reverseLabel?version=1&format=json&reqCoordType=WGS84GEO&resCoordType=WGS84GEO&reqLevel=17")
    suspend fun getLabelFromLatLng(@Query("centerLat") lat:Double, @Query("centerLon") lon:Double, @Query("appKey") key:String): Response<TmapLabelResponse>
}