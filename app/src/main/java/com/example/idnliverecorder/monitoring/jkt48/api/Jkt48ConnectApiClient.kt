package com.example.idnliverecorder.monitoring.jkt48.api

import android.util.Log
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Retrofit and OkHttp Client for JKT48Connect API.
 *
 * Security compliance:
 * - Query parameters named "apikey" are masked in internal interceptors to prevent leaking to Logcat.
 * - Timeouts are configured for connect, read, and write operations.
 */
object Jkt48ConnectApiClient {

    private const val TAG = "Jkt48ConnectClient"
    const val DEFAULT_BASE_URL = "https://v2.jkt48connect.com/"

    /**
     * Interceptor that logs sanitized HTTP requests and responses,
     * ensuring API keys are NEVER printed to Logcat.
     */
    private val securitySanitizingInterceptor = Interceptor { chain ->
        val request = chain.request()
        val originalUrl = request.url

        // Sanitize URL for logging: replace apikey query parameter with "***"
        val sanitizedUrl = if (originalUrl.queryParameter("apikey") != null) {
            originalUrl.newBuilder()
                .removeAllQueryParameters("apikey")
                .addQueryParameter("apikey", "***REDACTED***")
                .build()
        } else {
            originalUrl
        }

        Log.d(TAG, "--> ${request.method} $sanitizedUrl")

        val startTime = System.nanoTime()
        val response: Response = try {
            chain.proceed(request)
        } catch (e: Exception) {
            Log.e(TAG, "<-- HTTP FAILED: ${e.javaClass.simpleName} - ${e.message}")
            throw e
        }
        val durationMs = (System.nanoTime() - startTime) / 1_000_000

        Log.d(TAG, "<-- ${response.code} ${response.message} in ${durationMs}ms ($sanitizedUrl)")
        response
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(securitySanitizingInterceptor)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    fun create(baseUrl: String = DEFAULT_BASE_URL): Jkt48ConnectApi {
        val safeBaseUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        return Retrofit.Builder()
            .baseUrl(safeBaseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(Jkt48ConnectApi::class.java)
    }
}

