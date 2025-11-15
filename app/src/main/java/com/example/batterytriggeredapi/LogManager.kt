package com.example.batterytriggeredapi

import android.content.Context
import android.util.Log

/**
 * 統一的日誌管理器
 * 根據設定的 Debug Level 來控制日誌輸出
 */
object LogManager {
    
    private var preferencesManager: PreferencesManager? = null
    private const val DEFAULT_TAG = "BatteryApp"
    
    fun init(context: Context) {
        preferencesManager = PreferencesManager(context)
    }
    
    private fun shouldLog(level: Int): Boolean {
        return preferencesManager?.isLogLevelEnabled(level) ?: true
    }
    
    private fun getCurrentTime(): String {
        return java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", java.util.Locale.getDefault())
            .format(java.util.Date())
    }
    
    fun v(tag: String, message: String) {
        if (shouldLog(Log.VERBOSE)) {
            Log.v(tag, message)
        }
    }
    
    fun d(tag: String, message: String) {
        if (shouldLog(Log.DEBUG)) {
            Log.d(tag, message)
        }
    }
    
    fun i(tag: String, message: String) {
        if (shouldLog(Log.INFO)) {
            Log.i(tag, message)
        }
    }
    
    fun w(tag: String, message: String) {
        if (shouldLog(Log.WARN)) {
            Log.w(tag, message)
        }
    }
    
    fun e(tag: String, message: String, throwable: Throwable? = null) {
        if (shouldLog(Log.ERROR)) {
            if (throwable != null) {
                Log.e(tag, message, throwable)
            } else {
                Log.e(tag, message)
            }
        }
    }
    
    // 帶時間戳的便捷方法
    fun logWithTimestamp(level: Int, tag: String, event: String, data: String = "") {
        val timestamp = getCurrentTime()
        val message = "[$timestamp][$event][$data]"
        
        when (level) {
            Log.VERBOSE -> v(tag, message)
            Log.DEBUG -> d(tag, message)
            Log.INFO -> i(tag, message)
            Log.WARN -> w(tag, message)
            Log.ERROR -> e(tag, message)
        }
    }
    
    // 電池狀態專用日誌
    fun logBatteryStatus(tag: String, batteryPct: Int, isCharging: Boolean, chargingStatus: String) {
        logWithTimestamp(Log.INFO, tag, "BATTERY_STATUS_UPDATE", "batteryPct=${batteryPct}%, isCharging=$isCharging, status=$chargingStatus")
    }
    
    // API 調用專用日誌
    fun logApiCall(tag: String, requestId: String, url: String, method: String) {
        logWithTimestamp(Log.INFO, tag, "HTTP_REQUEST_START", "requestId=$requestId, url=$url, method=$method")
    }
    
    fun logApiResponse(tag: String, requestId: String, responseCode: Int, success: Boolean, duration: Long) {
        logWithTimestamp(Log.INFO, tag, "HTTP_RESPONSE_RECEIVED", "requestId=$requestId, responseCode=$responseCode, success=$success, duration=${duration}ms")
    }
    
    // 觸發檢查專用日誌
    fun logTriggerCheck(tag: String, triggerType: String, batteryPct: Int, threshold: Int, isCharging: Boolean, shouldTrigger: Boolean) {
        logWithTimestamp(Log.INFO, tag, "${triggerType}_BATTERY_CHECK", "batteryPct=${batteryPct}%, threshold=${threshold}%, isCharging=$isCharging, shouldTrigger=$shouldTrigger")
    }
    
    // 例外處理專用日誌
    fun logException(tag: String, event: String, exception: Throwable, additionalData: String = "") {
        val timestamp = getCurrentTime()
        val message = "[$timestamp][$event][exception=${exception.javaClass.simpleName}, message=${exception.message}, data=$additionalData]"
        e(tag, message, exception)
    }
    
    // 獲取當前日誌等級資訊
    fun getCurrentLogLevelInfo(): String {
        return preferencesManager?.getDebugLevelName() ?: "未初始化"
    }
    
    fun getCurrentLogLevel(): Int {
        return preferencesManager?.getDebugLevel() ?: Log.INFO
    }
    
    // 日誌等級測試方法
    fun testAllLogLevels(tag: String) {
        val timestamp = getCurrentTime()
        v(tag, "[$timestamp][LOG_LEVEL_TEST][VERBOSE level test message]")
        d(tag, "[$timestamp][LOG_LEVEL_TEST][DEBUG level test message]")
        i(tag, "[$timestamp][LOG_LEVEL_TEST][INFO level test message]")
        w(tag, "[$timestamp][LOG_LEVEL_TEST][WARN level test message]")
        e(tag, "[$timestamp][LOG_LEVEL_TEST][ERROR level test message]")
    }
}