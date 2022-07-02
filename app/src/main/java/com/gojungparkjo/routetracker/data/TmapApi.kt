package com.gojungparkjo.routetracker.data

import com.gojungparkjo.routetracker.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object TmapApi {
    val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .addConverterFactory(GsonConverterFactory.create())
            .client(buildOkHttpClient())
            .baseUrl(Url.TMAP_URL)
            .build()
    }

    val tmapLabelService:TmapLabelService by lazy{
        retrofit.create(TmapLabelService::class.java)
    }
    val tmapDirectionService: TmapDirectionService by lazy{
        retrofit.create(TmapDirectionService::class.java)
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