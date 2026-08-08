package com.edgesync.app.network

import com.edgesync.app.data.InsightResponse
import com.edgesync.app.data.ReadingRequest
import com.edgesync.app.data.ReadingResponse
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface ApiService {
    @POST("readings")
    suspend fun postReading(@Body reading: ReadingRequest): ReadingResponse

    @GET("insights/{deviceId}")
    suspend fun getLatestInsight(@Path("deviceId") deviceId: String): InsightResponse

    companion object {
        // Replace with your actual API Gateway invoke URL after `sam deploy`
        // e.g. https://abc123xyz.execute-api.us-east-1.amazonaws.com/prod/
        private const val BASE_URL = "https://REPLACE_ME.execute-api.us-east-1.amazonaws.com/prod/"

        fun create(): ApiService = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}
