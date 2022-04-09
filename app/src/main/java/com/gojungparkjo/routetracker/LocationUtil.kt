package com.gojungparkjo.routetracker

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import net.daum.mf.map.api.MapPoint
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin

suspend fun List<MapPoint>.nearestSign(currentLocation: MapPoint): MapPoint {
    var dist = Double.MAX_VALUE
    var rtn = MapPoint.mapPointWithGeoCoord(0.0, 0.0)
    var list = this

    // 요부분에 이진트리를 넣음 더 빨라지려나??
    CoroutineScope(Dispatchers.Default).async {
        list.forEach {
            val d = distanceInKm(
                it.mapPointGeoCoord.latitude,
                it.mapPointGeoCoord.longitude,
                currentLocation.mapPointGeoCoord.latitude,
                currentLocation.mapPointGeoCoord.longitude
            )
            if (dist > d) {
                dist = d
                rtn = it
            }
        }
    }.await()
    return rtn
}

fun distanceInKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val theta = lon1 - lon2
    var dist =
        sin(deg2rad(lat1)) * sin(deg2rad(lat2)) + cos(deg2rad(lat1)) * cos(deg2rad(lat2)) * cos(
            deg2rad(theta)
        )
    dist = acos(dist)
    dist = rad2deg(dist)
    dist *= 60 * 1.1515
    dist *= 1.609344
    Log.d("Dist", "distanceInKm: $dist")
    return dist
}

private fun deg2rad(deg: Double): Double {
    return deg * Math.PI / 180.0
}

private fun rad2deg(rad: Double): Double {
    return rad * 180.0 / Math.PI
}