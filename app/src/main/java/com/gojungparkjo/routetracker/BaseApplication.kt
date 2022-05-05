package com.gojungparkjo.routetracker

import android.app.Application
import com.naver.maps.map.NaverMapSdk
import com.naver.maps.map.NaverMapSdk.NaverCloudPlatformClient


class BaseApplication:Application() {
    override fun onCreate() {
        super.onCreate()
        NaverMapSdk.getInstance(this).client = NaverCloudPlatformClient("n7gct2pkrc")
    }
}