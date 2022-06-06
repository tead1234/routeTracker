package com.gojungparkjo.routetracker.data

import com.gojungparkjo.routetracker.BuildConfig
import com.naver.maps.geometry.LatLng
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TmapLabelRepository {

    val labelService = TmapApi.tmapLabelService

    suspend fun getLabelFromLatLng(latLng: LatLng) = withContext(Dispatchers.IO){
        val response = labelService.getTrafficIslandInBound(latLng.latitude,latLng.longitude,BuildConfig.TMAP_API_KEY)
        response.body()
    }

}