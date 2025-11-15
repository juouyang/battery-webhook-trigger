package com.example.batterytriggeredapi

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import java.text.SimpleDateFormat
import java.util.*

data class LastCallResult(
    val success: Boolean,
    val responseCode: Int,
    val timestamp: String,
    val url: String = "",
    val errorMessage: String = "",
    val triggerType: String = "" // "高電量" 或 "低電量" 或 "手動測試"
)

class PreferencesManager(context: Context) {
    
    companion object {
        private const val TAG = "PreferencesManager"
        private const val KEY_THRESHOLD = "threshold"
        private const val KEY_API_URL = "api_url"
        private const val KEY_LAST_CALL_SUCCESS = "last_call_success"
        private const val KEY_LAST_CALL_CODE = "last_call_code"
        private const val KEY_LAST_CALL_TIMESTAMP = "last_call_timestamp"
        private const val KEY_LAST_CALL_URL = "last_call_url"
        private const val KEY_LAST_CALL_ERROR_MESSAGE = "last_call_error_message"
        private const val KEY_LAST_CALL_TRIGGER_TYPE = "last_call_trigger_type"
        
        // 新增：API調用歷史記錄
        private const val KEY_API_CALL_HISTORY = "api_call_history"
        private const val MAX_HISTORY_RECORDS = 50 // 最多保存50筆記錄
        
        // Debug Level 設定
        private const val KEY_DEBUG_LEVEL = "debug_level"
        private const val DEFAULT_DEBUG_LEVEL = android.util.Log.INFO // 預設為 INFO 等級
        private const val KEY_LAST_SUCCESS_LEVEL = "last_success_level"
        private const val KEY_LAST_ATTEMPT_LEVEL = "last_attempt_level"
        private const val KEY_IS_TRIGGERING = "is_triggering"
        private const val KEY_MONITORING_ENABLED = "monitoring_enabled"
        
        // 低電量監控相關
        private const val KEY_LOW_BATTERY_THRESHOLD = "low_battery_threshold"
        private const val KEY_LOW_BATTERY_API_URL = "low_battery_api_url"
        private const val KEY_LOW_BATTERY_MONITORING_ENABLED = "low_battery_monitoring_enabled"
        private const val KEY_LOW_BATTERY_LAST_SUCCESS_LEVEL = "low_battery_last_success_level"
        private const val KEY_LOW_BATTERY_LAST_ATTEMPT_LEVEL = "low_battery_last_attempt_level"
        private const val KEY_LOW_BATTERY_IS_TRIGGERING = "low_battery_is_triggering"
        
        private const val DEFAULT_THRESHOLD = 80
        private const val DEFAULT_API_URL = "http://httpbin.org/get"
        private const val DEFAULT_LOW_BATTERY_THRESHOLD = 20
        private const val DEFAULT_LOW_BATTERY_API_URL = "http://httpbin.org/get"
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(
        "battery_triggered_api_prefs", 
        Context.MODE_PRIVATE
    )
    
    fun getThreshold(): Int {
        return prefs.getInt(KEY_THRESHOLD, DEFAULT_THRESHOLD)
    }
    
    fun saveThreshold(threshold: Int) {
        prefs.edit().putInt(KEY_THRESHOLD, threshold).apply()
    }
    
    fun getApiUrl(): String {
        return prefs.getString(KEY_API_URL, DEFAULT_API_URL) ?: DEFAULT_API_URL
    }
    
    fun saveApiUrl(url: String) {
        prefs.edit().putString(KEY_API_URL, url).apply()
    }
    
    fun saveLastCallResult(success: Boolean, responseCode: Int, timestamp: String) {
        prefs.edit()
            .putBoolean(KEY_LAST_CALL_SUCCESS, success)
            .putInt(KEY_LAST_CALL_CODE, responseCode)
            .putString(KEY_LAST_CALL_TIMESTAMP, timestamp)
            .apply()
    }
    
    fun saveLastCallResult(
        success: Boolean, 
        responseCode: Int, 
        timestamp: String, 
        url: String, 
        errorMessage: String = "", 
        triggerType: String = ""
    ) {
        val currentTime = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", java.util.Locale.getDefault()).format(java.util.Date())
        android.util.Log.i(TAG, "[$currentTime][SAVE_CALL_RESULT][success=$success, responseCode=$responseCode, triggerType=$triggerType]")
        
        // 保存最新一筆（用於UI顯示）
        prefs.edit()
            .putBoolean(KEY_LAST_CALL_SUCCESS, success)
            .putInt(KEY_LAST_CALL_CODE, responseCode)
            .putString(KEY_LAST_CALL_TIMESTAMP, timestamp)
            .putString(KEY_LAST_CALL_URL, url)
            .putString(KEY_LAST_CALL_ERROR_MESSAGE, errorMessage)
            .putString(KEY_LAST_CALL_TRIGGER_TYPE, triggerType)
            .apply()
        
        // 同時保存到歷史記錄
        saveToHistory(success, responseCode, timestamp, url, errorMessage, triggerType)
    }
    
    private fun saveToHistory(
        success: Boolean, 
        responseCode: Int, 
        timestamp: String, 
        url: String, 
        errorMessage: String, 
        triggerType: String
    ) {
        try {
            val currentTime = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", java.util.Locale.getDefault()).format(java.util.Date())
            
            // 讀取現有歷史記錄
            val historyJson = prefs.getString(KEY_API_CALL_HISTORY, "[]") ?: "[]"
            val historyArray = org.json.JSONArray(historyJson)
            
            // 創建新記錄
            val newRecord = org.json.JSONObject().apply {
                put("success", success)
                put("responseCode", responseCode)
                put("timestamp", timestamp)
                put("url", url)
                put("errorMessage", errorMessage)
                put("triggerType", triggerType)
                put("savedAt", currentTime)
            }
            
            // 添加到歷史記錄開頭
            val newHistoryArray = org.json.JSONArray()
            newHistoryArray.put(newRecord)
            
            // 複製現有記錄，但限制總數量
            var recordCount = 1
            for (i in 0 until historyArray.length()) {
                if (recordCount >= MAX_HISTORY_RECORDS) break
                newHistoryArray.put(historyArray.getJSONObject(i))
                recordCount++
            }
            
            // 保存更新的歷史記錄
            prefs.edit()
                .putString(KEY_API_CALL_HISTORY, newHistoryArray.toString())
                .apply()
                
            android.util.Log.d(TAG, "[$currentTime][HISTORY_SAVED][totalRecords=$recordCount, newRecord={success=$success, responseCode=$responseCode, triggerType=$triggerType}]")
            
        } catch (e: Exception) {
            val currentTime = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", java.util.Locale.getDefault()).format(java.util.Date())
            android.util.Log.e(TAG, "[$currentTime][HISTORY_SAVE_ERROR][exception=${e.javaClass.simpleName}, message=${e.message}]", e)
        }
    }
    
    fun getLastCallResult(): LastCallResult {
        return LastCallResult(
            success = prefs.getBoolean(KEY_LAST_CALL_SUCCESS, false),
            responseCode = prefs.getInt(KEY_LAST_CALL_CODE, -1),
            timestamp = prefs.getString(KEY_LAST_CALL_TIMESTAMP, "") ?: "",
            url = prefs.getString(KEY_LAST_CALL_URL, "") ?: "",
            errorMessage = prefs.getString(KEY_LAST_CALL_ERROR_MESSAGE, "") ?: "",
            triggerType = prefs.getString(KEY_LAST_CALL_TRIGGER_TYPE, "") ?: ""
        )
    }
    
    fun clearLastCallResult() {
        prefs.edit()
            .remove(KEY_LAST_CALL_SUCCESS)
            .remove(KEY_LAST_CALL_CODE)
            .remove(KEY_LAST_CALL_TIMESTAMP)
            .remove(KEY_LAST_CALL_URL)
            .remove(KEY_LAST_CALL_ERROR_MESSAGE)
            .remove(KEY_LAST_CALL_TRIGGER_TYPE)
            .apply()
    }
    
    fun markSuccessAtLevel(batteryLevel: Int) {
        Log.d(TAG, "標記在電量 $batteryLevel% 時成功觸發")
        prefs.edit()
            .putInt(KEY_LAST_SUCCESS_LEVEL, batteryLevel)
            .putBoolean(KEY_IS_TRIGGERING, false)
            .apply()
    }
    
    fun markAttemptAtLevel(batteryLevel: Int) {
        Log.d(TAG, "標記在電量 $batteryLevel% 時嘗試觸發")
        prefs.edit()
            .putInt(KEY_LAST_ATTEMPT_LEVEL, batteryLevel)
            .putBoolean(KEY_IS_TRIGGERING, true)
            .apply()
    }
    
    fun shouldTriggerAtLevel(batteryLevel: Int, threshold: Int): Boolean {
        // 先檢查並重置狀態（如果需要），但不立即返回
        if (batteryLevel < threshold) {
            Log.d(TAG, "電量 $batteryLevel% 低於門檻 $threshold%，重置觸發狀態")
            resetTriggerStatus()
            // 不要立即返回，讓函數繼續執行完整的邏輯檢查
        }
        
        // 檢查是否正在觸發中（防止重複觸發）
        val isTriggering = prefs.getBoolean(KEY_IS_TRIGGERING, false)
        if (isTriggering) {
            Log.d(TAG, "正在觸發中，跳過此次檢查")
            return false
        }
        
        val lastSuccessLevel = prefs.getInt(KEY_LAST_SUCCESS_LEVEL, -1)
        val lastAttemptLevel = prefs.getInt(KEY_LAST_ATTEMPT_LEVEL, -1)
        
        Log.d(TAG, "觸發檢查: 當前電量=$batteryLevel%, 門檻=$threshold%, 上次成功=$lastSuccessLevel%, 上次嘗試=$lastAttemptLevel%")
        
        // 如果電量低於門檻，不觸發
        if (batteryLevel < threshold) {
            Log.d(TAG, "電量未達門檻，不觸發")
            return false
        }
        
        // 如果已經成功過，且電量還在門檻以上，不再觸發
        if (lastSuccessLevel >= threshold) {
            Log.d(TAG, "已在電量 $lastSuccessLevel% 時成功，不再觸發")
            return false
        }
        
        // 如果電量達到門檻且比上次嘗試高至少1%，則觸發
        val shouldTrigger = batteryLevel >= threshold && batteryLevel > lastAttemptLevel
        Log.d(TAG, "應該觸發: $shouldTrigger (電量=$batteryLevel% >= 門檻=$threshold% && $batteryLevel% > 上次嘗試=$lastAttemptLevel%)")
        
        return shouldTrigger
    }
    
    fun resetTriggerStatus() {
        Log.d(TAG, "重置觸發狀態")
        prefs.edit()
            .remove(KEY_LAST_SUCCESS_LEVEL)
            .remove(KEY_LAST_ATTEMPT_LEVEL)
            .putBoolean(KEY_IS_TRIGGERING, false)
            .apply()
    }
    
    fun clearTriggeringFlag() {
        Log.d(TAG, "清除觸發中標記")
        prefs.edit().putBoolean(KEY_IS_TRIGGERING, false).apply()
    }
    
    fun isMonitoringEnabled(): Boolean {
        return prefs.getBoolean(KEY_MONITORING_ENABLED, true)
    }
    
    fun setMonitoringEnabled(enabled: Boolean) {
        Log.d(TAG, "設定監控狀態: $enabled")
        prefs.edit().putBoolean(KEY_MONITORING_ENABLED, enabled).apply()
    }
    
    // 低電量監控相關方法
    fun getLowBatteryThreshold(): Int {
        return prefs.getInt(KEY_LOW_BATTERY_THRESHOLD, DEFAULT_LOW_BATTERY_THRESHOLD)
    }
    
    fun saveLowBatteryThreshold(threshold: Int) {
        Log.d(TAG, "儲存低電量門檻: $threshold%")
        prefs.edit().putInt(KEY_LOW_BATTERY_THRESHOLD, threshold).apply()
    }
    
    fun getLowBatteryApiUrl(): String {
        return prefs.getString(KEY_LOW_BATTERY_API_URL, DEFAULT_LOW_BATTERY_API_URL) ?: DEFAULT_LOW_BATTERY_API_URL
    }
    
    fun saveLowBatteryApiUrl(url: String) {
        Log.d(TAG, "儲存低電量 API URL: $url")
        prefs.edit().putString(KEY_LOW_BATTERY_API_URL, url).apply()
    }
    
    fun isLowBatteryMonitoringEnabled(): Boolean {
        return prefs.getBoolean(KEY_LOW_BATTERY_MONITORING_ENABLED, false)
    }
    
    fun setLowBatteryMonitoringEnabled(enabled: Boolean) {
        Log.d(TAG, "設定低電量監控狀態: $enabled")
        prefs.edit().putBoolean(KEY_LOW_BATTERY_MONITORING_ENABLED, enabled).apply()
    }
    
    fun shouldTriggerLowBattery(batteryLevel: Int, threshold: Int): Boolean {
        // 如果電量高於門檻，重置狀態
        if (batteryLevel > threshold) {
            Log.d(TAG, "電量 $batteryLevel% 高於低電量門檻 $threshold%，重置低電量觸發狀態")
            resetLowBatteryTriggerStatus()
        }
        
        // 檢查是否正在觸發中（防止重複觸發）
        val isTriggering = prefs.getBoolean(KEY_LOW_BATTERY_IS_TRIGGERING, false)
        if (isTriggering) {
            Log.d(TAG, "低電量正在觸發中，跳過此次檢查")
            return false
        }
        
        val lastSuccessLevel = prefs.getInt(KEY_LOW_BATTERY_LAST_SUCCESS_LEVEL, -1)
        val lastAttemptLevel = prefs.getInt(KEY_LOW_BATTERY_LAST_ATTEMPT_LEVEL, 101) // 默認值設為101，確保首次會觸發
        
        Log.d(TAG, "低電量觸發檢查: 當前電量=$batteryLevel%, 門檻=$threshold%, 上次成功=$lastSuccessLevel%, 上次嘗試=$lastAttemptLevel%")
        
        // 如果電量高於門檻，不觸發
        if (batteryLevel > threshold) {
            Log.d(TAG, "電量高於低電量門檻，不觸發")
            return false
        }
        
        // 如果已經成功過，且電量還在門檻以下，不再觸發
        if (lastSuccessLevel >= 0 && lastSuccessLevel <= threshold) {
            Log.d(TAG, "已在電量 $lastSuccessLevel% 時成功觸發低電量警報，不再觸發")
            return false
        }
        
        // 如果電量低於門檻且比上次嘗試低至少1%，則觸發
        val shouldTrigger = batteryLevel <= threshold && batteryLevel < lastAttemptLevel
        Log.d(TAG, "低電量應該觸發: $shouldTrigger (電量=$batteryLevel% <= 門檻=$threshold% && $batteryLevel% < 上次嘗試=$lastAttemptLevel%)")
        
        return shouldTrigger
    }
    
    fun recordLowBatteryTriggerAttempt(batteryLevel: Int) {
        Log.d(TAG, "記錄低電量觸發嘗試: $batteryLevel%")
        prefs.edit()
            .putInt(KEY_LOW_BATTERY_LAST_ATTEMPT_LEVEL, batteryLevel)
            .putBoolean(KEY_LOW_BATTERY_IS_TRIGGERING, true)
            .apply()
    }
    
    fun recordLowBatteryTriggerSuccess(batteryLevel: Int) {
        Log.d(TAG, "記錄低電量觸發成功: $batteryLevel%")
        prefs.edit()
            .putInt(KEY_LOW_BATTERY_LAST_SUCCESS_LEVEL, batteryLevel)
            .putBoolean(KEY_LOW_BATTERY_IS_TRIGGERING, false)
            .apply()
    }
    
    fun recordLowBatteryTriggerFailure() {
        Log.d(TAG, "記錄低電量觸發失敗")
        prefs.edit().putBoolean(KEY_LOW_BATTERY_IS_TRIGGERING, false).apply()
    }
    
    fun resetLowBatteryTriggerStatus() {
        Log.d(TAG, "重置低電量觸發狀態")
        prefs.edit()
            .putInt(KEY_LOW_BATTERY_LAST_SUCCESS_LEVEL, -1)
            .putInt(KEY_LOW_BATTERY_LAST_ATTEMPT_LEVEL, 101)
            .putBoolean(KEY_LOW_BATTERY_IS_TRIGGERING, false)
            .apply()
    }
    
    // 為BatteryMonitorService節電功能提供的方法
    fun getLastSuccessLevel(): Int {
        return prefs.getInt(KEY_LAST_SUCCESS_LEVEL, -1)
    }
    
    fun getLowBatteryLastSuccessLevel(): Int {
        return prefs.getInt(KEY_LOW_BATTERY_LAST_SUCCESS_LEVEL, -1)
    }
    
    // 取得API調用歷史記錄
    fun getApiCallHistory(): List<LastCallResult> {
        val currentTime = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", java.util.Locale.getDefault()).format(java.util.Date())
        
        return try {
            val historyJson = prefs.getString(KEY_API_CALL_HISTORY, "[]") ?: "[]"
            val historyArray = org.json.JSONArray(historyJson)
            val historyList = mutableListOf<LastCallResult>()
            
            for (i in 0 until historyArray.length()) {
                try {
                    val record = historyArray.getJSONObject(i)
                    historyList.add(
                        LastCallResult(
                            success = record.getBoolean("success"),
                            responseCode = record.getInt("responseCode"),
                            timestamp = record.getString("timestamp"),
                            url = record.optString("url", ""),
                            errorMessage = record.optString("errorMessage", ""),
                            triggerType = record.optString("triggerType", "")
                        )
                    )
                } catch (e: Exception) {
                    android.util.Log.w(TAG, "[$currentTime][HISTORY_PARSE_ERROR][recordIndex=$i, exception=${e.message}]")
                }
            }
            
            android.util.Log.d(TAG, "[$currentTime][HISTORY_LOADED][recordCount=${historyList.size}]")
            historyList
            
        } catch (e: Exception) {
            android.util.Log.e(TAG, "[$currentTime][HISTORY_LOAD_ERROR][exception=${e.javaClass.simpleName}, message=${e.message}]", e)
            emptyList()
        }
    }
    
    // 清空API調用歷史記錄
    fun clearApiCallHistory() {
        val currentTime = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", java.util.Locale.getDefault()).format(java.util.Date())
        
        prefs.edit()
            .remove(KEY_API_CALL_HISTORY)
            .apply()
            
        android.util.Log.i(TAG, "[$currentTime][HISTORY_CLEARED][All history records removed]")
    }
    
    // Debug Level 相關方法
    fun getDebugLevel(): Int {
        return prefs.getInt(KEY_DEBUG_LEVEL, DEFAULT_DEBUG_LEVEL)
    }
    
    fun setDebugLevel(level: Int) {
        val currentTime = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", java.util.Locale.getDefault()).format(java.util.Date())
        val oldLevel = getDebugLevel()
        
        prefs.edit()
            .putInt(KEY_DEBUG_LEVEL, level)
            .apply()
            
        android.util.Log.i(TAG, "[$currentTime][DEBUG_LEVEL_CHANGED][oldLevel=$oldLevel, newLevel=$level]")
    }
    
    fun getDebugLevelName(): String {
        val level = getDebugLevel()
        return when (level) {
            android.util.Log.VERBOSE -> "VERBOSE"
            android.util.Log.DEBUG -> "DEBUG"
            android.util.Log.INFO -> "INFO"
            android.util.Log.WARN -> "WARN"
            android.util.Log.ERROR -> "ERROR"
            android.util.Log.ASSERT -> "ASSERT"
            else -> "UNKNOWN($level)"
        }
    }
    
    fun isLogLevelEnabled(level: Int): Boolean {
        return level >= getDebugLevel()
    }
}