package com.gojungparkjo.routetracker.data

import com.gojungparkjo.routetracker.BuildConfig
import com.gojungparkjo.routetracker.model.tmaplabel.TmapLabelResponse
import com.naver.maps.geometry.LatLng
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext
import retrofit2.Response

class TmapLabelRepository {

    val labelService = TmapApi.tmapLabelService

    private val semaphore = Semaphore(permits = 1)

    suspend fun getLabelFromLatLng(latLng: LatLng) = withContext(Dispatchers.IO) {

        semaphore.acquire()
        delay(500L)
        val response: Response<TmapLabelResponse> = labelService.getLabelFromLatLng(
            latLng.latitude,
            latLng.longitude,
            BuildConfig.TMAP_API_KEY
        )
        semaphore.release()
        response.body()
    }

}