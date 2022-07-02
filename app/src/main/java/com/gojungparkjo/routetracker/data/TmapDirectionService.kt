package com.gojungparkjo.routetracker.data

import com.gojungparkjo.routetracker.model.tmapdirection.TmapDirection
import retrofit2.Response
import retrofit2.http.POST
import retrofit2.http.Query

interface TmapDirectionService {
    @POST("routes/pedestrian?version=1")
    suspend fun getDirection(@Query("startX") lat:Double, @Query("startY") lon:Double, @Query("endX") lat2:Double, @Query("endY") lon2:Double, @Query("startName") starting: String, @Query("endName") ending: String, @Query("appKey") key:String): Response<TmapDirection>
}