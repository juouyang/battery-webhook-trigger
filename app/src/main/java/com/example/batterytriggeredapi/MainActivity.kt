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
import java.util.Locale

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
    }
    
    private fun setupUI() {
        // 載入儲存的設定
        binding.etThreshold.setText(preferencesManager.getThreshold().toString())
        binding.etApiUrl.setText(preferencesManager.getApiUrl())
        
        // 儲存設定按鈕
        binding.btnSaveSettings.setOnClickListener {
            saveSettings()
        }
        
        // 測試 API 按鈕
        binding.btnTestApi.setOnClickListener {
            testApi()
        }
        
        // 重置結果按鈕
        binding.btnResetResult.setOnClickListener {
            resetLastResult()
        }
        
        // 監控開關
        binding.switchMonitoring.isChecked = preferencesManager.isMonitoringEnabled()
        binding.switchMonitoring.setOnCheckedChangeListener { _, isChecked ->
            preferencesManager.setMonitoringEnabled(isChecked)
            if (isChecked) {
                startBatteryMonitorService()
                Toast.makeText(this, "已啟用電量監控", Toast.LENGTH_SHORT).show()
            } else {
                stopBatteryMonitorService()
                Toast.makeText(this, "已停用電量監控", Toast.LENGTH_SHORT).show()
            }
        }

        // 重置觸發狀態按鈕
        binding.btnResetTrigger.setOnClickListener {
            resetTriggerStatus()
        }
        
        // 手動觸發測試按鈕
        binding.btnManualTrigger.setOnClickListener {
            manualTriggerTest()
        }
        
        // 根據監控開關狀態決定是否啟動服務
        if (preferencesManager.isMonitoringEnabled()) {
            startBatteryMonitorService()
        }
    }
    
    private fun saveSettings() {
        val thresholdText = binding.etThreshold.text.toString()
        val apiUrl = binding.etApiUrl.text.toString()
        
        if (thresholdText.isBlank()) {
            Toast.makeText(this, "請輸入電量門檻", Toast.LENGTH_SHORT).show()
            return
        }
        
        val threshold = thresholdText.toIntOrNull()
        if (threshold == null || threshold < 1 || threshold > 100) {
            Toast.makeText(this, "電量門檻必須在 1-100 之間", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (apiUrl.isBlank()) {
            Toast.makeText(this, "請輸入 API URL", Toast.LENGTH_SHORT).show()
            return
        }
        
        preferencesManager.saveThreshold(threshold)
        preferencesManager.saveApiUrl(apiUrl)
        
        // 如果監控已啟用，重新啟動服務以應用新設定
        if (preferencesManager.isMonitoringEnabled()) {
            startBatteryMonitorService()
            Toast.makeText(this, "設定已儲存，電池監控已更新", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "設定已儲存", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun testApi() {
        val apiUrl = binding.etApiUrl.text.toString()
        if (apiUrl.isBlank()) {
            Toast.makeText(this, "請先輸入 API URL", Toast.LENGTH_SHORT).show()
            return
        }
        
        lifecycleScope.launch {
            try {
                binding.btnTestApi.isEnabled = false
                binding.btnTestApi.text = getString(R.string.testing)
                
                val result = apiCaller.callApi(apiUrl)
                val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                
                preferencesManager.saveLastCallResult(result.success, result.responseCode, timestamp)
                updateLastCallResult()
                
                Toast.makeText(this@MainActivity, 
                    if (result.success) "API 測試成功" else "API 測試失敗", 
                    Toast.LENGTH_SHORT).show()
                    
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "測試失敗: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                binding.btnTestApi.isEnabled = true
                binding.btnTestApi.text = getString(R.string.test_api)
            }
        }
    }
    
    private fun resetLastResult() {
        preferencesManager.clearLastCallResult()
        updateLastCallResult()
        Toast.makeText(this, "已清除上次呼叫結果", Toast.LENGTH_SHORT).show()
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
        val lastResult = preferencesManager.getLastCallResult()
        if (lastResult.timestamp.isNotEmpty()) {
            binding.tvLastCallStatus.text = if (lastResult.success) getString(R.string.success) else getString(R.string.failed)
            binding.tvLastCallCode.text = getString(R.string.response_code, lastResult.responseCode)
            binding.tvLastCallTime.text = getString(R.string.timestamp, lastResult.timestamp)
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
    
    private fun resetTriggerStatus() {
        Log.d(TAG, "重置觸發狀態")
        preferencesManager.resetTriggerStatus()
        Toast.makeText(this, "已重置觸發狀態，可重新觸發", Toast.LENGTH_SHORT).show()
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
                
                val threshold = preferencesManager.getThreshold()
                
                Log.d(TAG, "手動測試 - 電量: $batteryPct%, 門檻: $threshold%, 充電中: $isCharging")
                
                // 檢查觸發邏輯的各個條件
                val thresholdMet = batteryPct >= threshold
                val chargingActive = isCharging
                
                Log.d(TAG, "觸發邏輯測試: 電量達標=$thresholdMet, 充電中=$chargingActive")
                
                if (thresholdMet && chargingActive) {
                    Log.d(TAG, "觸發邏輯測試 - 條件滿足，執行 API 呼叫")
                    
                    lifecycleScope.launch {
                        try {
                            val apiUrl = preferencesManager.getApiUrl()
                            Log.d(TAG, "觸發邏輯測試 - 呼叫 API: $apiUrl")
                            val result = apiCaller.callApi(apiUrl)
                            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                            
                            Log.d(TAG, "觸發邏輯測試 - API 結果: 成功=${result.success}, 回應碼=${result.responseCode}")
                            
                            preferencesManager.saveLastCallResult(result.success, result.responseCode, timestamp)
                            updateLastCallResult()
                            
                            Toast.makeText(this@MainActivity, "觸發邏輯測試成功！回應碼: ${result.responseCode}", Toast.LENGTH_SHORT).show()
                            
                        } catch (e: Exception) {
                            Log.e(TAG, "觸發邏輯測試 API 呼叫失敗", e)
                            Toast.makeText(this@MainActivity, "觸發邏輯測試失敗: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    // 明確指出哪個條件不滿足
                    val reasons = mutableListOf<String>()
                    if (!thresholdMet) {
                        reasons.add("電量未達門檻 ($batteryPct% < $threshold%)")
                    }
                    if (!chargingActive) {
                        reasons.add("未在充電中")
                    }
                    
                    val reason = if (reasons.isNotEmpty()) reasons.joinToString(", ") else "未知原因"
                    Log.d(TAG, "觸發邏輯測試 - 條件不滿足: $reason")
                    Toast.makeText(this, "觸發條件不滿足: $reason", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        batteryReceiver?.let { unregisterReceiver(it) }
    }
}