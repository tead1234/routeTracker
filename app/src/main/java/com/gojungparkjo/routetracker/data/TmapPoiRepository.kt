package com.gojungparkjo.routetracker.data

import com.gojungparkjo.routetracker.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TmapPoiRepository {
    val poiService = TmapApi.tmapPoiService

    suspend fun getPoiQueryResult(searchKeyword: String) = withContext(Dispatchers.IO) {
        val response = poiService.getPoiQueryResult(searchKeyword, BuildConfig.TMAP_API_KEY)
        response.body()?.searchPoiInfo?.pois?.poi
    }

}