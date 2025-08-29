package com.example.batterytriggeredapi

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import java.text.SimpleDateFormat
import java.util.*

data class LastCallResult(
    val success: Boolean,
    val responseCode: Int,
    val timestamp: String
)

class PreferencesManager(context: Context) {
    
    companion object {
        private const val TAG = "PreferencesManager"
        private const val KEY_THRESHOLD = "threshold"
        private const val KEY_API_URL = "api_url"
        private const val KEY_LAST_CALL_SUCCESS = "last_call_success"
        private const val KEY_LAST_CALL_CODE = "last_call_code"
        private const val KEY_LAST_CALL_TIMESTAMP = "last_call_timestamp"
        private const val KEY_LAST_SUCCESS_LEVEL = "last_success_level"
        private const val KEY_LAST_ATTEMPT_LEVEL = "last_attempt_level"
        private const val KEY_IS_TRIGGERING = "is_triggering"
        private const val KEY_MONITORING_ENABLED = "monitoring_enabled"
        
        private const val DEFAULT_THRESHOLD = 80
        private const val DEFAULT_API_URL = "https://example.com/api/poweroff"
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
    
    fun getLastCallResult(): LastCallResult {
        return LastCallResult(
            success = prefs.getBoolean(KEY_LAST_CALL_SUCCESS, false),
            responseCode = prefs.getInt(KEY_LAST_CALL_CODE, -1),
            timestamp = prefs.getString(KEY_LAST_CALL_TIMESTAMP, "") ?: ""
        )
    }
    
    fun clearLastCallResult() {
        prefs.edit()
            .remove(KEY_LAST_CALL_SUCCESS)
            .remove(KEY_LAST_CALL_CODE)
            .remove(KEY_LAST_CALL_TIMESTAMP)
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
        // 如果電量低於門檻，重置狀態
        if (batteryLevel < threshold) {
            Log.d(TAG, "電量 $batteryLevel% 低於門檻 $threshold%，重置觸發狀態")
            resetTriggerStatus()
            return false
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
        
        // 如果已經成功過，且電量還在門檻以上，不再觸發
        if (lastSuccessLevel >= threshold) {
            Log.d(TAG, "已在電量 $lastSuccessLevel% 時成功，不再觸發")
            return false
        }
        
        // 如果電量達到門檻且比上次嘗試高至少1%，則觸發
        val shouldTrigger = batteryLevel >= threshold && batteryLevel > lastAttemptLevel
        Log.d(TAG, "應該觸發: $shouldTrigger (電量=$batteryLevel% > 上次嘗試=$lastAttemptLevel%)")
        
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
}