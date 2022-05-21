package com.gojungparkjo.routetracker.data

import com.gojungparkjo.routetracker.ProjUtil.toEPSG5186
import com.gojungparkjo.routetracker.model.crosswalk.CrossWalkResponse
import com.gojungparkjo.routetracker.model.intersection.InterSectionResponse
import com.gojungparkjo.routetracker.model.pedestrianroad.PedestrianRoadResponse
import com.gojungparkjo.routetracker.model.trafficlight.TrafficLightResponse
import com.naver.maps.geometry.LatLngBounds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.locationtech.jts.algorithm.Intersection

class RoadRepository {

    private val roadService = RoadApi.roadService

    suspend fun getRoadInBound(bound: LatLngBounds): CrossWalkResponse? =
        withContext(Dispatchers.IO) {
            val low = bound.southWest.toEPSG5186()
            val up = bound.northEast.toEPSG5186()
            val response =
                roadService.getRoadsInBound("${low.x},${low.y},${up.x},${up.y},EPSG:5186")
            response.body()
        }

    suspend fun getTrafficLightInBound(bound: LatLngBounds): TrafficLightResponse? =
        withContext(Dispatchers.IO) {
            val low = bound.southWest.toEPSG5186()
            val up = bound.northEast.toEPSG5186()
            val response =
                roadService.getTrafficLightInBound("${low.x},${low.y},${up.x},${up.y},EPSG:5186")
            response.body()
        }

    suspend fun getPedestrianRoadInBound(bound: LatLngBounds): PedestrianRoadResponse? =
        withContext(Dispatchers.IO) {
            val low = bound.southWest.toEPSG5186()
            val up = bound.northEast.toEPSG5186()
            val response =
                roadService.getPedestrianRoadInBound("${low.x},${low.y},${up.x},${up.y},EPSG:5186")
            response.body()
        }
    suspend fun getTrafficIslandInBound(bound: LatLngBounds): PedestrianRoadResponse? =
        withContext(Dispatchers.IO) {
            val low = bound.southWest.toEPSG5186()
            val up = bound.northEast.toEPSG5186()
            val response =
                roadService.getTrafficIslandInBound("${low.x},${low.y},${up.x},${up.y},EPSG:5186")
            response.body()
        }
    suspend fun getIntersectionInBound(bound: LatLngBounds): InterSectionResponse? =
        withContext(Dispatchers.IO) {
            val low = bound.southWest.toEPSG5186()
            val up = bound.northEast.toEPSG5186()
            val response =
                roadService.getIntersectionInBound("${low.x},${low.y},${up.x},${up.y},EPSG:5186")
            response.body()
        }
}