package com.example.idnliverecorder.monitoring.jkt48.api

import com.google.gson.JsonElement
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Retrofit API interface for JKT48Connect API v3.
 *
 * Endpoint: /api/jkt48/live?apikey=YOUR_API_KEY
 */
interface Jkt48ConnectApi {

    /**
     * Fetch active JKT48 live streams (v3 endpoint).
     * @param apiKey Authentication API key query parameter.
     */
    @GET("api/jkt48/live")
    suspend fun getLiveStreams(
        @Query("apikey") apiKey: String
    ): Response<JsonElement>

    /**
     * Backward-compatible alias for getIdnLive.
     */
    @GET("api/jkt48/live")
    suspend fun getIdnLive(
        @Query("apikey") apiKey: String
    ): Response<JsonElement>
}
