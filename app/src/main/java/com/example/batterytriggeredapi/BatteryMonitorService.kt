package com.example.batterytriggeredapi

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class BatteryMonitorService : Service() {
    
    companion object {
        private const val TAG = "BatteryMonitorService"
        private const val NOTIFICATION_ID = 1000
        private const val CHANNEL_ID = "battery_monitor_service"
    }
    
    private var batteryReceiver: BroadcastReceiver? = null
    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "BatteryMonitorService onCreate() - 服務已啟動")
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        registerBatteryReceiver()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "BatteryMonitorService onStartCommand() - 服務命令已接收")
        return START_STICKY // 服務被殺死後會重新啟動
    }
    
    private fun registerBatteryReceiver() {
        Log.d(TAG, "註冊電池狀態監聽器")
        batteryReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                context ?: return
                intent ?: return
                
                Log.d(TAG, "收到電池狀態廣播: ${intent.action}")
                when (intent.action) {
                    Intent.ACTION_BATTERY_CHANGED -> {
                        checkBatteryLevel(context, intent)
                    }
                }
            }
        }
        
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        registerReceiver(batteryReceiver, filter)
        Log.d(TAG, "電池狀態監聽器註冊完成")
    }
    
    private fun checkBatteryLevel(context: Context, intent: Intent) {
        val currentTime = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", java.util.Locale.getDefault()).format(java.util.Date())
        
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        
        Log.d(TAG, "[$currentTime][BATTERY_CHECK_START][level=$level, scale=$scale, status=$status]")
        
        if (level == -1 || scale == -1) {
            Log.e(TAG, "[$currentTime][BATTERY_CHECK_ERROR][Invalid battery data: level=$level, scale=$scale]")
            return
        }
        
        val batteryPct = (level * 100 / scale.toFloat()).toInt()
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                       status == BatteryManager.BATTERY_STATUS_FULL
        
        val chargingStatus = when (status) {
            BatteryManager.BATTERY_STATUS_CHARGING -> "充電中"
            BatteryManager.BATTERY_STATUS_FULL -> "已充滿"
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "未充電"
            BatteryManager.BATTERY_STATUS_DISCHARGING -> "放電中"
            else -> "未知($status)"
        }
        
        Log.i(TAG, "[$currentTime][BATTERY_STATUS_UPDATE][batteryPct=${batteryPct}%, chargingStatus=$chargingStatus, isCharging=$isCharging]")
        
        val preferencesManager = PreferencesManager(context)
        val threshold = preferencesManager.getThreshold()
        val apiUrl = preferencesManager.getApiUrl()
        val monitoringEnabled = preferencesManager.isMonitoringEnabled()
        
        // 低電量監控設定
        val lowBatteryThreshold = preferencesManager.getLowBatteryThreshold()
        val lowBatteryApiUrl = preferencesManager.getLowBatteryApiUrl()
        val lowBatteryMonitoringEnabled = preferencesManager.isLowBatteryMonitoringEnabled()
        
        Log.i(TAG, "[$currentTime][SETTINGS_CHECK][highThreshold=${threshold}%, highApiUrl=$apiUrl, highMonitoring=$monitoringEnabled]")
        Log.i(TAG, "[$currentTime][SETTINGS_CHECK][lowThreshold=${lowBatteryThreshold}%, lowApiUrl=$lowBatteryApiUrl, lowMonitoring=$lowBatteryMonitoringEnabled]")
        
        // 檢查高電量觸發條件
        if (monitoringEnabled) {
            val shouldTrigger = isCharging && preferencesManager.shouldTriggerAtLevel(batteryPct, threshold)
            Log.i(TAG, "[$currentTime][HIGH_BATTERY_CHECK][batteryPct=${batteryPct}%, threshold=${threshold}%, isCharging=$isCharging, shouldTrigger=$shouldTrigger]")
            
            if (shouldTrigger) {
                Log.i(TAG, "[$currentTime][HIGH_BATTERY_TRIGGER_START][apiUrl=$apiUrl]")
                serviceScope.launch {
                    handleApiTrigger(preferencesManager, batteryPct, apiUrl, isHighBattery = true, currentTime)
                }
            }
        } else {
            Log.d(TAG, "[$currentTime][HIGH_BATTERY_DISABLED][Skipping high battery check]")
        }
        
        // 檢查低電量觸發條件
        if (lowBatteryMonitoringEnabled) {
            val shouldTriggerLowBattery = !isCharging && preferencesManager.shouldTriggerLowBattery(batteryPct, lowBatteryThreshold)
            Log.i(TAG, "[$currentTime][LOW_BATTERY_CHECK][batteryPct=${batteryPct}%, threshold=${lowBatteryThreshold}%, isCharging=$isCharging, shouldTrigger=$shouldTriggerLowBattery]")
            
            if (shouldTriggerLowBattery) {
                Log.i(TAG, "[$currentTime][LOW_BATTERY_TRIGGER_START][apiUrl=$lowBatteryApiUrl]")
                serviceScope.launch {
                    handleApiTrigger(preferencesManager, batteryPct, lowBatteryApiUrl, isHighBattery = false, currentTime)
                }
            }
        } else {
            Log.d(TAG, "[$currentTime][LOW_BATTERY_DISABLED][Skipping low battery check]")
        }
    }
    
    
    private suspend fun handleApiTrigger(
        preferencesManager: PreferencesManager,
        batteryLevel: Int,
        apiUrl: String,
        isHighBattery: Boolean,
        triggerStartTime: String
    ) {
        val triggerType = if (isHighBattery) "高電量" else "低電量"
        val apiCallTime = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", java.util.Locale.getDefault()).format(java.util.Date())
        
        Log.i(TAG, "[$apiCallTime][API_TRIGGER_START][triggerType=$triggerType, batteryLevel=${batteryLevel}%, apiUrl=$apiUrl]")
        
        // 記錄觸發嘗試
        if (isHighBattery) {
            preferencesManager.markAttemptAtLevel(batteryLevel)
            Log.d(TAG, "[$apiCallTime][HIGH_BATTERY_ATTEMPT_RECORDED][batteryLevel=${batteryLevel}%]")
        } else {
            preferencesManager.recordLowBatteryTriggerAttempt(batteryLevel)
            Log.d(TAG, "[$apiCallTime][LOW_BATTERY_ATTEMPT_RECORDED][batteryLevel=${batteryLevel}%]")
        }
        
        try {
            Log.i(TAG, "[$apiCallTime][API_CALL_START][url=$apiUrl, method=GET]")
            val apiCaller = ApiCaller()
            val result = apiCaller.callApi(apiUrl)
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
            val responseTime = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", java.util.Locale.getDefault()).format(java.util.Date())
            
            Log.i(TAG, "[$responseTime][API_CALL_RESPONSE][success=${result.success}, responseCode=${result.responseCode}, message=${result.message}]")
            
            if (result.success) {
                Log.i(TAG, "[$responseTime][API_CALL_SUCCESS][triggerType=$triggerType, batteryLevel=${batteryLevel}%, responseCode=${result.responseCode}]")
                
                // 記錄成功
                if (isHighBattery) {
                    preferencesManager.markSuccessAtLevel(batteryLevel)
                    Log.d(TAG, "[$responseTime][HIGH_BATTERY_SUCCESS_RECORDED][batteryLevel=${batteryLevel}%]")
                } else {
                    preferencesManager.recordLowBatteryTriggerSuccess(batteryLevel)
                    Log.d(TAG, "[$responseTime][LOW_BATTERY_SUCCESS_RECORDED][batteryLevel=${batteryLevel}%]")
                }
                
                // 發送成功通知
                showNotification("$triggerType API 呼叫成功", "電池電量 $batteryLevel% 觸發成功")
            } else {
                Log.w(TAG, "[$responseTime][API_CALL_FAILED][triggerType=$triggerType, batteryLevel=${batteryLevel}%, responseCode=${result.responseCode}, error=${result.message}]")
                
                // 記錄失敗
                if (isHighBattery) {
                    preferencesManager.clearTriggeringFlag()
                    Log.d(TAG, "[$responseTime][HIGH_BATTERY_FAILURE_RECORDED][batteryLevel=${batteryLevel}%]")
                } else {
                    preferencesManager.recordLowBatteryTriggerFailure()
                    Log.d(TAG, "[$responseTime][LOW_BATTERY_FAILURE_RECORDED][batteryLevel=${batteryLevel}%]")
                }
                
                // 發送失敗通知
                showNotification("$triggerType API 呼叫失敗", "電池電量 $batteryLevel% 觸發失敗: ${result.message}")
            }
            
            // 保存最後呼叫結果（共用顯示）
            val errorMessage = if (!result.success) result.message else ""
            preferencesManager.saveLastCallResult(
                result.success, 
                result.responseCode, 
                timestamp, 
                apiUrl, 
                errorMessage, 
                "${triggerType}觸發"
            )
            Log.d(TAG, "[$responseTime][CALL_RESULT_SAVED][success=${result.success}, responseCode=${result.responseCode}]")
            
        } catch (e: Exception) {
            val exceptionTime = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", java.util.Locale.getDefault()).format(java.util.Date())
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
            
            Log.e(TAG, "[$exceptionTime][API_CALL_EXCEPTION][triggerType=$triggerType, batteryLevel=${batteryLevel}%, url=$apiUrl, exception=${e.javaClass.simpleName}, message=${e.message}]", e)
            
            // 記錄失敗
            if (isHighBattery) {
                preferencesManager.clearTriggeringFlag()
                Log.d(TAG, "[$exceptionTime][HIGH_BATTERY_EXCEPTION_RECORDED][batteryLevel=${batteryLevel}%]")
            } else {
                preferencesManager.recordLowBatteryTriggerFailure()
                Log.d(TAG, "[$exceptionTime][LOW_BATTERY_EXCEPTION_RECORDED][batteryLevel=${batteryLevel}%]")
            }
            
            preferencesManager.saveLastCallResult(
                false, 
                -1, 
                timestamp, 
                apiUrl, 
                e.message ?: "未知錯誤", 
                "${triggerType}觸發"
            )
            Log.d(TAG, "[$exceptionTime][EXCEPTION_RESULT_SAVED][error=${e.message}]")
            
            showNotification("$triggerType API 呼叫異常", "電池電量 $batteryLevel% 觸發異常: ${e.message}")
        }
    }
    
    private fun showNotification(title: String, content: String) {
        val batteryReceiver = BatteryReceiver()
        batteryReceiver.sendNotification(this, content)
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "電池監控服務",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "背景監控電池狀態並自動觸發 Webhook"
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle("Battery Webhook Trigger")
        .setContentText("正在監控電池狀態...")
        .setSmallIcon(android.R.drawable.ic_menu_info_details)
        .setOngoing(true)
        .setShowWhen(false)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .setContentIntent(
            PendingIntent.getActivity(
                this, 0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )
        .build()
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "BatteryMonitorService onDestroy() - 服務即將停止")
        batteryReceiver?.let { 
            unregisterReceiver(it)
            Log.d(TAG, "電池狀態監聽器已取消註冊")
        }
        serviceJob.cancel()
    }
}