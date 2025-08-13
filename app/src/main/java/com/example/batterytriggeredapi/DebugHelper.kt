package com.example.batterytriggeredapi

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.util.Log

object DebugHelper {
    private const val TAG = "DebugHelper"
    
    fun logCurrentBatteryStatus(context: Context) {
        val batteryStatus = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        batteryStatus?.let { intent ->
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)
            
            val batteryPct = if (level != -1 && scale != -1) {
                (level * 100 / scale.toFloat()).toInt()
            } else -1
            
            val statusText = when (status) {
                BatteryManager.BATTERY_STATUS_CHARGING -> "CHARGING"
                BatteryManager.BATTERY_STATUS_DISCHARGING -> "DISCHARGING"
                BatteryManager.BATTERY_STATUS_FULL -> "FULL"
                BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "NOT_CHARGING"
                BatteryManager.BATTERY_STATUS_UNKNOWN -> "UNKNOWN"
                else -> "UNKNOWN($status)"
            }
            
            val pluggedText = when (plugged) {
                BatteryManager.BATTERY_PLUGGED_AC -> "AC"
                BatteryManager.BATTERY_PLUGGED_USB -> "USB"
                BatteryManager.BATTERY_PLUGGED_WIRELESS -> "WIRELESS"
                0 -> "UNPLUGGED"
                else -> "UNKNOWN($plugged)"
            }
            
            val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                           status == BatteryManager.BATTERY_STATUS_FULL
            
            Log.i(TAG, "=== 電池狀態詳細資訊 ===")
            Log.i(TAG, "電量: $batteryPct%")
            Log.i(TAG, "狀態: $statusText")
            Log.i(TAG, "電源: $pluggedText")
            Log.i(TAG, "充電中: $isCharging")
            Log.i(TAG, "原始數據: level=$level, scale=$scale, status=$status, plugged=$plugged")
            Log.i(TAG, "========================")
        }
    }
    
    fun logAppSettings(context: Context) {
        val preferencesManager = PreferencesManager(context)
        val threshold = preferencesManager.getThreshold()
        val apiUrl = preferencesManager.getApiUrl()
        val lastResult = preferencesManager.getLastCallResult()
        
        // 取得新的觸發狀態
        val prefs = context.getSharedPreferences("battery_triggered_api_prefs", Context.MODE_PRIVATE)
        val lastSuccessLevel = prefs.getInt("last_success_level", -1)
        val lastAttemptLevel = prefs.getInt("last_attempt_level", -1)
        
        Log.i(TAG, "=== 應用程式設定 ===")
        Log.i(TAG, "電量門檻: $threshold%")
        Log.i(TAG, "API URL: $apiUrl")
        Log.i(TAG, "上次成功電量: $lastSuccessLevel%")
        Log.i(TAG, "上次嘗試電量: $lastAttemptLevel%")
        Log.i(TAG, "上次呼叫: 成功=${lastResult.success}, 時間=${lastResult.timestamp}")
        Log.i(TAG, "==================")
    }
}