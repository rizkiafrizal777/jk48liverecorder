package com.example.idnliverecorder.monitoring.jkt48.api

import com.google.gson.JsonElement
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface Jkt48ConnectApi {

    /**
     * Fetch active JKT48 live streams on IDN Live.
     * @param apiKey API key parameter.
     */
    @GET("api/jkt48/live/idn")
    suspend fun getIdnLive(
        @Query("apikey") apiKey: String
    ): Response<JsonElement>
}

