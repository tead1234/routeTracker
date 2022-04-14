package com.gojungparkjo.routetracker.data

import com.gojungparkjo.routetracker.model.TrafficSafetyResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface RoadService {
    @GET("geoserver/seoul/wfs?request=GetFeature&version=1.1.1&typename=seoul%3AA004_A&outputFormat=application%2Fjson")
    suspend fun getRoadsInBound(@Query("bbox") bound:String): Response<TrafficSafetyResponse>
}