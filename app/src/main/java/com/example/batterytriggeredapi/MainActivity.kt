package com.example.batterytriggeredapi

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.batterytriggeredapi.databinding.ActivityMainBinding
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "MainActivity"
    }
    
    private lateinit var binding: ActivityMainBinding
    private lateinit var preferencesManager: PreferencesManager
    private lateinit var apiCaller: ApiCaller
    private var batteryReceiver: BroadcastReceiver? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        preferencesManager = PreferencesManager(this)
        apiCaller = ApiCaller()
        
        setupUI()
        registerBatteryReceiver()
        updateBatteryStatus()
        updateLastCallResult()
        
        // 根據監控開關狀態決定是否啟動服務
        updateMonitoringService()
    }
    
    
    private fun setupUI() {
        // 載入儲存的設定
        binding.etThreshold.setText(preferencesManager.getThreshold().toString())
        binding.etApiUrl.setText(preferencesManager.getApiUrl())
        
        // 載入低電量設定
        binding.etLowBatteryThreshold.setText(preferencesManager.getLowBatteryThreshold().toString())
        binding.etLowBatteryApiUrl.setText(preferencesManager.getLowBatteryApiUrl())
        
        // 儲存設定按鈕
        binding.btnSaveSettings.setOnClickListener {
            saveSettings()
        }
        
        // 測試高電量 API 按鈕
        binding.btnTestHighBatteryApi.setOnClickListener {
            testHighBatteryApi()
        }
        
        // 測試低電量 API 按鈕
        binding.btnTestLowBatteryApi.setOnClickListener {
            testLowBatteryApi()
        }
        
        
        
        // 高電量監控開關
        binding.switchMonitoring.isChecked = preferencesManager.isMonitoringEnabled()
        binding.switchMonitoring.setOnCheckedChangeListener { _, isChecked ->
            preferencesManager.setMonitoringEnabled(isChecked)
            updateMonitoringService()
            Toast.makeText(this, if (isChecked) "已啟用高電量監控" else "已停用高電量監控", Toast.LENGTH_SHORT).show()
        }
        
        // 低電量監控開關
        binding.switchLowBatteryMonitoring.isChecked = preferencesManager.isLowBatteryMonitoringEnabled()
        binding.switchLowBatteryMonitoring.setOnCheckedChangeListener { _, isChecked ->
            preferencesManager.setLowBatteryMonitoringEnabled(isChecked)
            updateMonitoringService()
            Toast.makeText(this, if (isChecked) "已啟用低電量監控" else "已停用低電量監控", Toast.LENGTH_SHORT).show()
        }

        
        // 手動觸發測試按鈕
        binding.btnManualTrigger.setOnClickListener {
            manualTriggerTest()
        }
        
        // 重置結果按鈕
        binding.btnResetResult.setOnClickListener {
            resetLastResult()
        }
        
        // 根據監控開關狀態決定是否啟動服務
        updateMonitoringService()
    }
    
    private fun saveSettings() {
        val thresholdText = binding.etThreshold.text.toString()
        val apiUrl = binding.etApiUrl.text.toString()
        val lowBatteryThresholdText = binding.etLowBatteryThreshold.text.toString()
        val lowBatteryApiUrl = binding.etLowBatteryApiUrl.text.toString()
        
        if (thresholdText.isBlank()) {
            Toast.makeText(this, "請輸入高電量門檻", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (lowBatteryThresholdText.isBlank()) {
            Toast.makeText(this, "請輸入低電量門檻", Toast.LENGTH_SHORT).show()
            return
        }
        
        val threshold = thresholdText.toIntOrNull()
        val lowBatteryThreshold = lowBatteryThresholdText.toIntOrNull()
        
        if (threshold == null || threshold < 1 || threshold > 100) {
            Toast.makeText(this, "高電量門檻必須在 1-100 之間", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (lowBatteryThreshold == null || lowBatteryThreshold < 1 || lowBatteryThreshold > 100) {
            Toast.makeText(this, "低電量門檻必須在 1-100 之間", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (lowBatteryThreshold >= threshold) {
            Toast.makeText(this, "低電量門檻必須小於高電量門檻", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (apiUrl.isBlank()) {
            Toast.makeText(this, "請輸入高電量 API URL", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (lowBatteryApiUrl.isBlank()) {
            Toast.makeText(this, "請輸入低電量 API URL", Toast.LENGTH_SHORT).show()
            return
        }
        
        // 保存所有設定
        preferencesManager.saveThreshold(threshold)
        preferencesManager.saveApiUrl(apiUrl)
        preferencesManager.saveLowBatteryThreshold(lowBatteryThreshold)
        preferencesManager.saveLowBatteryApiUrl(lowBatteryApiUrl)
        
        // 重新啟動監控服務以應用新設定
        updateMonitoringService()
        Toast.makeText(this, "設定已儲存並更新監控服務", Toast.LENGTH_SHORT).show()
    }
    
    private fun updateMonitoringService() {
        val highMonitoringEnabled = preferencesManager.isMonitoringEnabled()
        val lowMonitoringEnabled = preferencesManager.isLowBatteryMonitoringEnabled()
        
        if (highMonitoringEnabled || lowMonitoringEnabled) {
            startBatteryMonitorService()
            Log.d(TAG, "重新啟動監控服務 - 高電量監控: $highMonitoringEnabled, 低電量監控: $lowMonitoringEnabled")
        } else {
            stopBatteryMonitorService()
            Log.d(TAG, "停止監控服務 - 所有監控都已停用")
        }
    }
    
    private fun testHighBatteryApi() {
        val apiUrl = binding.etApiUrl.text.toString()
        if (apiUrl.isBlank()) {
            Toast.makeText(this, "請先輸入高電量 API URL", Toast.LENGTH_SHORT).show()
            return
        }
        
        lifecycleScope.launch {
            try {
                binding.btnTestHighBatteryApi.isEnabled = false
                binding.btnTestHighBatteryApi.text = "測試中..."
                
                val result = apiCaller.callApi(apiUrl)
                val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                
                val errorMessage = if (!result.success) result.message else ""
                preferencesManager.saveLastCallResult(
                    result.success, 
                    result.responseCode, 
                    timestamp, 
                    apiUrl, 
                    errorMessage, 
                    "高電量測試"
                )
                updateLastCallResult()
                
                Toast.makeText(this@MainActivity, 
                    if (result.success) "高電量 API 測試成功 (${result.responseCode})" else "高電量 API 測試失敗 (${result.responseCode})", 
                    Toast.LENGTH_SHORT).show()
                    
            } catch (e: Exception) {
                val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                preferencesManager.saveLastCallResult(
                    false, 
                    -1, 
                    timestamp, 
                    apiUrl, 
                    e.message ?: "未知錯誤", 
                    "高電量測試"
                )
                updateLastCallResult()
                Toast.makeText(this@MainActivity, "高電量 API 測試失敗: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                binding.btnTestHighBatteryApi.isEnabled = true
                binding.btnTestHighBatteryApi.text = "測試高電量 API"
            }
        }
    }
    
    private fun testLowBatteryApi() {
        val apiUrl = binding.etLowBatteryApiUrl.text.toString()
        if (apiUrl.isBlank()) {
            Toast.makeText(this, "請先輸入低電量 API URL", Toast.LENGTH_SHORT).show()
            return
        }
        
        lifecycleScope.launch {
            try {
                binding.btnTestLowBatteryApi.isEnabled = false
                binding.btnTestLowBatteryApi.text = "測試中..."
                
                val result = apiCaller.callApi(apiUrl)
                val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                
                val errorMessage = if (!result.success) result.message else ""
                preferencesManager.saveLastCallResult(
                    result.success, 
                    result.responseCode, 
                    timestamp, 
                    apiUrl, 
                    errorMessage, 
                    "低電量測試"
                )
                updateLastCallResult()
                
                Toast.makeText(this@MainActivity, 
                    if (result.success) "低電量 API 測試成功 (${result.responseCode})" else "低電量 API 測試失敗 (${result.responseCode})", 
                    Toast.LENGTH_SHORT).show()
                    
            } catch (e: Exception) {
                val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                preferencesManager.saveLastCallResult(
                    false, 
                    -1, 
                    timestamp, 
                    apiUrl, 
                    e.message ?: "未知錯誤", 
                    "低電量測試"
                )
                updateLastCallResult()
                Toast.makeText(this@MainActivity, "低電量 API 測試失敗: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                binding.btnTestLowBatteryApi.isEnabled = true
                binding.btnTestLowBatteryApi.text = "測試低電量 API"
            }
        }
    }
    
    private fun resetLastResult() {
        preferencesManager.clearLastCallResult()
        preferencesManager.clearApiCallHistory()
        updateLastCallResult()
        Toast.makeText(this, "已清除最近呼叫結果", Toast.LENGTH_SHORT).show()
    }
    
    private fun registerBatteryReceiver() {
        batteryReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                // 只更新 UI，不觸發 API（由 BatteryMonitorService 統一處理）
                updateBatteryStatus()
            }
        }
        
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }
        
        registerReceiver(batteryReceiver, filter)
    }
    
    private fun updateBatteryStatus() {
        val batteryStatus = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        batteryStatus?.let { intent ->
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            
            if (level == -1 || scale == -1) {
                Log.w(TAG, "無法取得電池資訊: level=$level, scale=$scale")
                binding.tvBatteryLevel.text = "未知"
                return
            }
            
            val batteryPct = (level * 100 / scale.toFloat()).toInt()
            
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                           status == BatteryManager.BATTERY_STATUS_FULL
            
            Log.d(TAG, "UI 電池狀態更新: level=$level, scale=$scale, 計算結果=$batteryPct% | 充電中: $isCharging")
            
            binding.tvBatteryLevel.text = String.format(Locale.getDefault(), "%d%%", batteryPct)
            binding.tvChargingStatus.text = if (isCharging) getString(R.string.charging) else getString(R.string.not_charging)
        }
    }
    
    private fun checkBatteryThreshold() {
        // 移除自動觸發邏輯，統一由 BatteryMonitorService 處理
        // 這裡只更新 UI 顯示
        Log.d(TAG, "MainActivity 電池狀態檢查 - 由 BatteryMonitorService 統一處理觸發邏輯")
    }
    
    private fun updateLastCallResult() {
        val historyList = preferencesManager.getApiCallHistory()
        
        if (historyList.isNotEmpty()) {
            // 显示最新一笔的状态
            val latestResult = historyList.first()
            binding.tvLastCallStatus.text = if (latestResult.success) getString(R.string.success) else getString(R.string.failed)
            
            // 显示所有记录的详细信息
            val allDetails = mutableListOf<String>()
            
            historyList.forEachIndexed { index, result ->
                val details = mutableListOf<String>()
                
                // 记录编号和时间
                val recordTitle = if (index == 0) "最新" else "第${index + 1}筆"
                details.add("[$recordTitle] ${result.timestamp}")
                
                // 基本信息
                val statusText = if (result.success) "✅成功" else "❌失敗"
                details.add("$statusText (${result.responseCode})")
                
                // 触发类型
                if (result.triggerType.isNotEmpty()) {
                    details.add("類型: ${result.triggerType}")
                }
                
                // URL (缩短显示)
                if (result.url.isNotEmpty()) {
                    val shortUrl = if (result.url.length > 30) {
                        result.url.take(30) + "..."
                    } else {
                        result.url
                    }
                    details.add("URL: $shortUrl")
                }
                
                // 错误信息
                if (!result.success && result.errorMessage.isNotEmpty()) {
                    val shortError = if (result.errorMessage.length > 50) {
                        result.errorMessage.take(50) + "..."
                    } else {
                        result.errorMessage
                    }
                    details.add("錯誤: $shortError")
                }
                
                allDetails.add(details.joinToString("\n"))
            }
            
            binding.tvLastCallCode.text = allDetails.joinToString("\n\n──────────────\n")
            binding.tvLastCallTime.text = "共 ${historyList.size} 筆記錄"
            
        } else {
            binding.tvLastCallStatus.text = getString(R.string.not_called_yet)
            binding.tvLastCallCode.text = ""
            binding.tvLastCallTime.text = ""
        }
    }
    
    private fun startBatteryMonitorService() {
        Log.d(TAG, "啟動電池監控前台服務")
        val serviceIntent = Intent(this, BatteryMonitorService::class.java)
        startForegroundService(serviceIntent)
        Log.d(TAG, "電池監控前台服務啟動命令已發送")
    }
    
    private fun stopBatteryMonitorService() {
        Log.d(TAG, "停止電池監控前台服務")
        val serviceIntent = Intent(this, BatteryMonitorService::class.java)
        stopService(serviceIntent)
        Log.d(TAG, "電池監控前台服務停止命令已發送")
    }
    
    
    
    private fun manualTriggerTest() {
        Log.d(TAG, "手動觸發測試開始")
        
        // 輸出詳細的除錯資訊
        DebugHelper.logCurrentBatteryStatus(this)
        DebugHelper.logAppSettings(this)
        
        // 取得當前電池狀態
        val batteryStatus = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        batteryStatus?.let { intent ->
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            
            if (level != -1 && scale != -1) {
                val batteryPct = (level * 100 / scale.toFloat()).toInt()
                val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                               status == BatteryManager.BATTERY_STATUS_FULL
                
                val highThreshold = preferencesManager.getThreshold()
                val lowThreshold = preferencesManager.getLowBatteryThreshold()
                val highMonitoringEnabled = preferencesManager.isMonitoringEnabled()
                val lowMonitoringEnabled = preferencesManager.isLowBatteryMonitoringEnabled()
                
                Log.d(TAG, "手動測試 - 電量: $batteryPct%, 高電量門檻: $highThreshold%, 低電量門檻: $lowThreshold%, 充電中: $isCharging")
                Log.d(TAG, "監控狀態 - 高電量監控: $highMonitoringEnabled, 低電量監控: $lowMonitoringEnabled")
                
                val results = mutableListOf<String>()
                
                // 檢查高電量觸發邏輯
                if (highMonitoringEnabled) {
                    val highThresholdMet = batteryPct >= highThreshold
                    val shouldTriggerHigh = isCharging && highThresholdMet
                    
                    if (shouldTriggerHigh) {
                        results.add("✅ 高電量觸發條件滿足 (${batteryPct}% ≥ ${highThreshold}% 且充電中)")
                    } else {
                        val reasons = mutableListOf<String>()
                        if (!highThresholdMet) {
                            reasons.add("電量未達高電量門檻 ($batteryPct% < $highThreshold%)")
                        }
                        if (!isCharging) {
                            reasons.add("未在充電中")
                        }
                        results.add("❌ 高電量不觸發: ${reasons.joinToString(", ")}")
                    }
                } else {
                    results.add("⚪ 高電量監控已停用")
                }
                
                // 檢查低電量觸發邏輯
                if (lowMonitoringEnabled) {
                    val lowThresholdMet = batteryPct <= lowThreshold
                    val shouldTriggerLow = !isCharging && lowThresholdMet
                    
                    if (shouldTriggerLow) {
                        results.add("✅ 低電量觸發條件滿足 (${batteryPct}% ≤ ${lowThreshold}% 且未充電)")
                    } else {
                        val reasons = mutableListOf<String>()
                        if (!lowThresholdMet) {
                            reasons.add("電量高於低電量門檻 ($batteryPct% > $lowThreshold%)")
                        }
                        if (isCharging) {
                            reasons.add("正在充電中")
                        }
                        results.add("❌ 低電量不觸發: ${reasons.joinToString(", ")}")
                    }
                } else {
                    results.add("⚪ 低電量監控已停用")
                }
                
                // 顯示結果 - 使用AlertDialog代替Toast
                val resultMessage = "觸發邏輯測試結果:\n\n${results.joinToString("\n\n")}"
                
                // 使用AlertDialog顯示完整結果
                androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("📊 觸發邏輯測試結果")
                    .setMessage(results.joinToString("\n\n"))
                    .setPositiveButton("確定", null)
                    .show()
                
                // 簡化的Toast提示
                val successCount = results.count { it.startsWith("✅") }
                val failCount = results.count { it.startsWith("❌") }
                val disabledCount = results.count { it.startsWith("⚪") }
                
                val summary = when {
                    successCount > 0 && failCount == 0 -> "所有啟用的監控都會觸發"
                    successCount == 0 && failCount > 0 -> "目前沒有監控會觸發"
                    successCount > 0 && failCount > 0 -> "部分監控會觸發"
                    else -> "所有監控都已停用"
                }
                
                Toast.makeText(this, summary, Toast.LENGTH_SHORT).show()
                Log.d(TAG, resultMessage.replace("\n", " | "))
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        batteryReceiver?.let { unregisterReceiver(it) }
    }
}