package com.example.batterytriggeredapi

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class BatteryReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "BatteryReceiver"
    }
    
    override fun onReceive(context: Context?, intent: Intent?) {
        context ?: return
        intent ?: return
        
        Log.d(TAG, "收到廣播: ${intent.action}")
        
        when (intent.action) {
            Intent.ACTION_POWER_CONNECTED,
            Intent.ACTION_POWER_DISCONNECTED -> {
                Log.d(TAG, "電源狀態變化，檢查電池狀態")
                // 當電源連接/斷開時，檢查電池狀態
                checkBatteryLevelFromSystem(context)
                // 確保監控服務運行
                startMonitoringService(context)
            }
            Intent.ACTION_BOOT_COMPLETED -> {
                Log.d(TAG, "系統開機完成，啟動電池監控服務")
                startMonitoringService(context)
            }
        }
    }
    
    private fun checkBatteryLevelFromSystem(context: Context) {
        Log.d(TAG, "主動查詢電池狀態")
        // 主動查詢電池狀態
        val batteryStatus = context.registerReceiver(null, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        batteryStatus?.let { intent ->
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
            
            Log.d(TAG, "BatteryReceiver - 電池狀態: $batteryPct% | 充電中: $isCharging")
            
            val preferencesManager = PreferencesManager(context)
            val threshold = preferencesManager.getThreshold()
            
            Log.d(TAG, "BatteryReceiver - 門檻: $threshold%")
            
            // 檢查是否應該觸發，但不實際觸發（避免與 BatteryMonitorService 重複）
            val shouldTrigger = isCharging && preferencesManager.shouldTriggerAtLevel(batteryPct, threshold)
            Log.d(TAG, "BatteryReceiver - 檢查觸發條件: $shouldTrigger (但不執行，由 BatteryMonitorService 負責)")
            
            // BatteryReceiver 只負責檢查，不執行 API 呼叫
            // 實際的 API 呼叫由 BatteryMonitorService 統一處理
        }
    }
    
    private fun startMonitoringService(context: Context) {
        try {
            val serviceIntent = Intent(context, BatteryMonitorService::class.java)
            context.startForegroundService(serviceIntent)
            Log.d(TAG, "已啟動電池監控前台服務")
        } catch (e: Exception) {
            Log.e(TAG, "啟動監控服務失敗: ${e.message}")
        }
    }
    
    fun sendNotification(context: Context, message: String) {
        val channelId = "battery_api_channel"
        val notificationId = 1001
        
        // 創建通知頻道（Android 8.0+）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "電池 Webhook 觸發通知",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "當電池達到門檻時的 Webhook 呼叫通知"
            }
            
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
        
        // 建立通知
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Battery Webhook Trigger")
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        
        // 發送通知
        try {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        } catch (e: SecurityException) {
            // 如果沒有通知權限，忽略錯誤
        }
    }
}