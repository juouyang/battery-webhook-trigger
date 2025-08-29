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
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        
        if (level == -1 || scale == -1) {
            Log.w(TAG, "無法取得電池資訊: level=$level, scale=$scale")
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
        
        Log.d(TAG, "電池狀態更新: $batteryPct% | $chargingStatus | 充電狀態: $isCharging")
        
        val preferencesManager = PreferencesManager(context)
        val threshold = preferencesManager.getThreshold()
        val apiUrl = preferencesManager.getApiUrl()
        val monitoringEnabled = preferencesManager.isMonitoringEnabled()
        
        Log.d(TAG, "設定檢查: 門檻=$threshold% | API URL=$apiUrl | 監控啟用=$monitoringEnabled")
        
        // 如果監控被停用，直接返回
        if (!monitoringEnabled) {
            Log.d(TAG, "監控已停用，跳過觸發檢查")
            return
        }
        
        // 檢查觸發條件 - 新邏輯：每隔1%觸發直到成功
        val shouldTrigger = isCharging && preferencesManager.shouldTriggerAtLevel(batteryPct, threshold)
        Log.d(TAG, "觸發條件檢查: 充電中=$isCharging | 應該觸發=$shouldTrigger")
        
        if (shouldTrigger) {
            // 標記嘗試觸發
            preferencesManager.markAttemptAtLevel(batteryPct)
            Log.i(TAG, "🚀 觸發條件滿足！開始呼叫 API...")
            
            serviceScope.launch {
                try {
                    Log.d(TAG, "正在呼叫 API: $apiUrl")
                    val apiCaller = ApiCaller()
                    val result = apiCaller.callApi(apiUrl)
                    val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                    
                    Log.i(TAG, "✅ API 呼叫完成: 成功=${result.success}, 回應碼=${result.responseCode}")
                    
                    preferencesManager.saveLastCallResult(result.success, result.responseCode, timestamp)
                    
                    // 如果成功，標記成功的電量等級
                    if (result.success) {
                        preferencesManager.markSuccessAtLevel(batteryPct)
                        Log.i(TAG, "✅ API 成功，標記電量 $batteryPct% 為成功等級")
                    }
                    
                    // 發送通知
                    val batteryReceiver = BatteryReceiver()
                    batteryReceiver.sendNotification(context, "電量達到 ${threshold}%，已自動呼叫 API")
                    
                } catch (e: Exception) {
                    Log.e(TAG, "❌ API 呼叫失敗: ${e.message}", e)
                    val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                    preferencesManager.saveLastCallResult(false, -1, timestamp)
                    
                    // 清除觸發中標記，允許下次觸發
                    preferencesManager.clearTriggeringFlag()
                    
                    val batteryReceiver = BatteryReceiver()
                    batteryReceiver.sendNotification(context, "API 呼叫失敗: ${e.message}")
                }
            }
        } else {
            Log.d(TAG, "不觸發 API 呼叫")
        }
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