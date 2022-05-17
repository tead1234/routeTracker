package com.gojungparkjo.routetracker

import com.naver.maps.geometry.LatLng
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

fun lengthenLine(p1:LatLng,p2:LatLng,delta:Double):List<LatLng>{
    val angle = atan2(p2.latitude-p1.latitude,p2.longitude-p1.longitude)
    val newPoint1 = LatLng(p1.latitude - (delta* sin(angle)),p1.longitude-(delta* cos(angle)))
    val newPoint2 = LatLng(p2.latitude + (delta*sin(angle)),p2.longitude+(delta*cos(angle)))
    return listOf(newPoint1,newPoint2)
}