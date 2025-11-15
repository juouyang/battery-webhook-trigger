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
        val requestStartTime = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", java.util.Locale.getDefault()).format(java.util.Date())
        val requestId = System.nanoTime().toString().takeLast(6) // 簡單的請求ID
        
        try {
            android.util.Log.i("ApiCaller", "[$requestStartTime][HTTP_REQUEST_START][requestId=$requestId, url=$url, method=$method]")
            
            val requestBuilder = Request.Builder()
                .url(url)
                .addHeader("User-Agent", "BatteryTriggeredAPI/1.0")
            
            when (method) {
                HttpMethod.GET -> {
                    requestBuilder.get()
                    android.util.Log.d("ApiCaller", "[$requestStartTime][HTTP_REQUEST_CONFIG][requestId=$requestId, method=GET, headers=User-Agent]")
                }
                HttpMethod.POST -> {
                    val requestBody = (jsonData ?: "").toRequestBody("application/json".toMediaType())
                    requestBuilder
                        .post(requestBody)
                        .addHeader("Content-Type", "application/json")
                    android.util.Log.d("ApiCaller", "[$requestStartTime][HTTP_REQUEST_CONFIG][requestId=$requestId, method=POST, headers=User-Agent+Content-Type, bodySize=${jsonData?.length ?: 0}]")
                }
            }
            
            val request = requestBuilder.build()
            val callStartTime = System.currentTimeMillis()
            
            android.util.Log.i("ApiCaller", "[$requestStartTime][HTTP_CALL_EXECUTING][requestId=$requestId]")
            val response = client.newCall(request).execute()
            val callEndTime = System.currentTimeMillis()
            val responseTime = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", java.util.Locale.getDefault()).format(java.util.Date())
            val duration = callEndTime - callStartTime
            
            android.util.Log.i("ApiCaller", "[$responseTime][HTTP_RESPONSE_RECEIVED][requestId=$requestId, responseCode=${response.code}, message=${response.message}, duration=${duration}ms]")
            
            val result = ApiResult(
                success = response.isSuccessful,
                responseCode = response.code,
                message = response.message
            )
            
            if (response.isSuccessful) {
                android.util.Log.i("ApiCaller", "[$responseTime][HTTP_REQUEST_SUCCESS][requestId=$requestId, responseCode=${response.code}]")
            } else {
                android.util.Log.w("ApiCaller", "[$responseTime][HTTP_REQUEST_FAILED][requestId=$requestId, responseCode=${response.code}, message=${response.message}]")
            }
            
            result
            
        } catch (e: IOException) {
            val exceptionTime = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", java.util.Locale.getDefault()).format(java.util.Date())
            
            // 記錄詳細的錯誤信息以便調試
            val errorMessage = when {
                e.message?.contains("CLEARTEXT communication") == true -> 
                    "HTTP明文傳輸被阻止，請檢查網路安全配置"
                e.message?.contains("failed to connect") == true -> 
                    "連線失敗：${e.message}"
                e.message?.contains("timeout") == true ->
                    "請求超時：${e.message}"
                else -> e.message ?: "網路錯誤"
            }
            
            android.util.Log.e("ApiCaller", "[$exceptionTime][HTTP_IO_EXCEPTION][requestId=$requestId, url=$url, exception=${e.javaClass.simpleName}, originalMessage=${e.message}, processedMessage=$errorMessage]", e)
            
            ApiResult(
                success = false,
                responseCode = -1,
                message = errorMessage
            )
        } catch (e: Exception) {
            val exceptionTime = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", java.util.Locale.getDefault()).format(java.util.Date())
            
            android.util.Log.e("ApiCaller", "[$exceptionTime][HTTP_GENERAL_EXCEPTION][requestId=$requestId, url=$url, exception=${e.javaClass.simpleName}, message=${e.message}]", e)
            
            ApiResult(
                success = false,
                responseCode = -1,
                message = e.message ?: "未知錯誤"
            )
        }
    }
}