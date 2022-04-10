package com.gojungparkjo.routetracker

import com.naver.maps.geometry.LatLng
import com.naver.maps.map.overlay.Marker
import net.daum.mf.map.api.MapPoint

data class TrafficSign(
    val attachMgtNumber:String? = null,
    val status : String? = null,
    val attachType:String? = null,
    val attachLength:String? = null,
    val goga:String? = null,
    val attachDirection:String? = null,
    val signalQuant:String? = null,
    val backwardAttachQuant:String? = null,
    val signalType:Int? = null,
    val backwardAttachType:String? = null,
    val installDate:String? = null,
    val replaceDate:String? = null,
    val pillarMgtNumber:String? = null,
    val signalLightClass:String? = null,
    val manufacturer:String? = null,
    val workClass:String? = null,
    val displayClass:String? = null,
    val newId:String? = null,
    val constructMgtNumber:String? = null,
    val attachMgtNumber2:String? = null,
    val recordId:String? = null,
    val locationInfo:String? = null,
    val x:Double? = null,
    val y:Double? = null,
    val constructionType:String? = null,
    val marker: Marker
)