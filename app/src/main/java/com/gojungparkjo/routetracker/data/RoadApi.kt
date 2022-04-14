package com.gojungparkjo.routetracker.data

import com.gojungparkjo.routetracker.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RoadApi {
    val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .addConverterFactory(GsonConverterFactory.create())
            .client(buildOkHttpClient())
            .baseUrl(Url.BASE_URL)
            .build()
    }

    val roadService:RoadService by lazy{
        retrofit.create(RoadService::class.java)
    }

    fun buildOkHttpClient(): OkHttpClient {
        val interceptor = HttpLoggingInterceptor()
        if (BuildConfig.DEBUG) {
            interceptor.level = HttpLoggingInterceptor.Level.BODY
        } else {
            interceptor.level = HttpLoggingInterceptor.Level.NONE
        }
        return OkHttpClient.Builder()
            .addInterceptor(interceptor)
            .build()
    }
}