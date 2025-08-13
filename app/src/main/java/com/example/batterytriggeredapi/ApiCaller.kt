package com.example.batterytriggeredapi

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

data class ApiResult(
    val success: Boolean,
    val responseCode: Int,
    val message: String = ""
)

class ApiCaller {
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    
    suspend fun callApi(url: String): ApiResult = withContext(Dispatchers.IO) {
        try {
            val requestBody = "".toRequestBody("application/json".toMediaType())
            
            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .addHeader("Content-Type", "application/json")
                .addHeader("User-Agent", "BatteryTriggeredAPI/1.0")
                .build()
            
            val response = client.newCall(request).execute()
            
            ApiResult(
                success = response.isSuccessful,
                responseCode = response.code,
                message = response.message
            )
            
        } catch (e: IOException) {
            ApiResult(
                success = false,
                responseCode = -1,
                message = e.message ?: "網路錯誤"
            )
        } catch (e: Exception) {
            ApiResult(
                success = false,
                responseCode = -1,
                message = e.message ?: "未知錯誤"
            )
        }
    }
}