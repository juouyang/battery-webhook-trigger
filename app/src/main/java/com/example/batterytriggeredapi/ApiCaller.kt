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

enum class HttpMethod {
    GET, POST
}

class ApiCaller {
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    
    // 目前使用的簡化版本 - 只支援GET
    suspend fun callApi(url: String): ApiResult = withContext(Dispatchers.IO) {
        callApi(url, HttpMethod.GET, null)
    }
    
    // 擴展版本 - 支援GET/POST和自定義JSON (為將來準備)
    suspend fun callApi(
        url: String, 
        method: HttpMethod = HttpMethod.GET, 
        jsonData: String? = null
    ): ApiResult = withContext(Dispatchers.IO) {
        try {
            val requestBuilder = Request.Builder()
                .url(url)
                .addHeader("User-Agent", "BatteryTriggeredAPI/1.0")
            
            when (method) {
                HttpMethod.GET -> {
                    requestBuilder.get()
                }
                HttpMethod.POST -> {
                    val requestBody = (jsonData ?: "").toRequestBody("application/json".toMediaType())
                    requestBuilder
                        .post(requestBody)
                        .addHeader("Content-Type", "application/json")
                }
            }
            
            val request = requestBuilder.build()
            val response = client.newCall(request).execute()
            
            ApiResult(
                success = response.isSuccessful,
                responseCode = response.code,
                message = response.message
            )
            
        } catch (e: IOException) {
            // 記錄詳細的錯誤信息以便調試
            val errorMessage = when {
                e.message?.contains("CLEARTEXT communication") == true -> 
                    "HTTP明文傳輸被阻止，請檢查網路安全配置"
                e.message?.contains("failed to connect") == true -> 
                    "連線失敗：${e.message}"
                else -> e.message ?: "網路錯誤"
            }
            
            ApiResult(
                success = false,
                responseCode = -1,
                message = errorMessage
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