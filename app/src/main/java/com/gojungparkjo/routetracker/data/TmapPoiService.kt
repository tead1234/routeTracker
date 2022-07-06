package com.gojungparkjo.routetracker.data

import com.gojungparkjo.routetracker.model.tmappoi.TmapPoiResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface TmapPoiService {
    @GET("pois?version=1&reqCoordType=WGS84GEO&resCoordType=WGS84GEO")
    suspend fun getPoiQueryResult(@Query("searchKeyword")searchKeyword:String,@Query("appKey")key:String): Response<TmapPoiResponse>
}