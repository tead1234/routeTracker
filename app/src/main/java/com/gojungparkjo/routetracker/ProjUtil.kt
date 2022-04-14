package com.gojungparkjo.routetracker

import com.gojungparkjo.routetracker.ProjUtil.toEPSG5186
import com.naver.maps.geometry.LatLng
import org.locationtech.proj4j.CRSFactory
import org.locationtech.proj4j.CoordinateTransformFactory
import org.locationtech.proj4j.ProjCoordinate


object ProjUtil {
    var crsFactory = CRSFactory()
    var WGS84 = crsFactory.createFromName("epsg:4326")
    var EPSG5186 = crsFactory.createFromParameters(
        "EPSG5186",
        "+proj=tmerc +lat_0=38 +lon_0=127 +k=1 +x_0=200000 +y_0=600000 +ellps=GRS80 +units=m +no_defs"
    )

    var ctFactory = CoordinateTransformFactory()
    var wgsToEPSG5186 = ctFactory.createTransform(WGS84, EPSG5186)
    var epsg5186ToWgs = ctFactory.createTransform(EPSG5186,WGS84)


    fun LatLng.toEPSG5186():ProjCoordinate{
        wgsToEPSG5186.transform(ProjCoordinate(this.longitude,this.latitude),ProjCoordinate()).also {
            return it
        }
    }

    fun ProjCoordinate.toLatLng():LatLng{
        epsg5186ToWgs.transform(this,ProjCoordinate()).also {
            return LatLng(it.y,it.x)
        }
    }

}