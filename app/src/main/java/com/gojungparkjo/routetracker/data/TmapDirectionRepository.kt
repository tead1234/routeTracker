package com.gojungparkjo.routetracker.data
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.gojungparkjo.routetracker.BuildConfig
class TmapDirectionRepository {
    val directionService = TmapApi.tmapDirectionService
    suspend fun getLabelFromDepToDes(depX: Double,depY: Double, desX: Double,desY: Double) = withContext(Dispatchers.IO){
        val response = directionService.getDirection(depX,depY,desX,desY, "a", "b", BuildConfig.TMAP_API_KEY)
        response.body()
    }

}