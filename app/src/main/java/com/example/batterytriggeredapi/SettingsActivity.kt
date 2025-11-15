package com.example.batterytriggeredapi

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.batterytriggeredapi.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "SettingsActivity"
        
        // Debug Level 定義
        enum class DebugLevel(val displayName: String, val level: Int) {
            VERBOSE("VERBOSE - 所有訊息", Log.VERBOSE),
            DEBUG("DEBUG - 除錯訊息", Log.DEBUG),
            INFO("INFO - 一般資訊", Log.INFO),
            WARN("WARN - 警告訊息", Log.WARN),
            ERROR("ERROR - 錯誤訊息", Log.ERROR),
            ASSERT("ASSERT - 嚴重錯誤", Log.ASSERT)
        }
    }
    
    private lateinit var binding: ActivitySettingsBinding
    private lateinit var preferencesManager: PreferencesManager
    private val debugLevels = DebugLevel.values().toList()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val currentTime = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", java.util.Locale.getDefault()).format(java.util.Date())
        Log.i(TAG, "[$currentTime][SETTINGS_ACTIVITY_START][Creating SettingsActivity]")
        
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        preferencesManager = PreferencesManager(this)
        
        setupToolbar()
        setupDebugLevelSpinner()
        setupButtons()
        updateCurrentLevelDisplay()
        
        Log.d(TAG, "[$currentTime][SETTINGS_ACTIVITY_CREATED][UI setup completed]")
    }
    
    private fun setupToolbar() {
        supportActionBar?.apply {
            title = "應用程式設定"
            setDisplayHomeAsUpEnabled(true)
        }
    }
    
    private fun setupDebugLevelSpinner() {
        val currentTime = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", java.util.Locale.getDefault()).format(java.util.Date())
        
        // 創建 Adapter
        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            debugLevels.map { it.displayName }
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        
        binding.spinnerDebugLevel.adapter = adapter
        
        // 設定當前選中的等級
        val currentLevel = preferencesManager.getDebugLevel()
        val currentIndex = debugLevels.indexOfFirst { it.level == currentLevel }
        if (currentIndex != -1) {
            binding.spinnerDebugLevel.setSelection(currentIndex)
            Log.d(TAG, "[$currentTime][SPINNER_SETUP][Current level=$currentLevel, index=$currentIndex]")
        }
        
        // 設定選擇監聽器
        binding.spinnerDebugLevel.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedLevel = debugLevels[position]
                val changeTime = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", java.util.Locale.getDefault()).format(java.util.Date())
                
                Log.i(TAG, "[$changeTime][DEBUG_LEVEL_CHANGED][oldLevel=${preferencesManager.getDebugLevel()}, newLevel=${selectedLevel.level}, name=${selectedLevel.displayName}]")
                
                preferencesManager.setDebugLevel(selectedLevel.level)
                updateCurrentLevelDisplay()
                
                Toast.makeText(this@SettingsActivity, "Log 等級已設定為: ${selectedLevel.displayName}", Toast.LENGTH_SHORT).show()
            }
            
            override fun onNothingSelected(parent: AdapterView<*>?) {
                // Do nothing
            }
        }
    }
    
    private fun setupButtons() {
        val currentTime = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", java.util.Locale.getDefault()).format(java.util.Date())
        
        // 測試日誌等級按鈕
        binding.btnTestLogLevels.setOnClickListener {
            Log.i(TAG, "[$currentTime][TEST_LOG_LEVELS_BUTTON_CLICKED][Testing all log levels]")
            testLogLevels()
        }
        
        // 清空歷史記錄按鈕
        binding.btnClearHistory.setOnClickListener {
            Log.i(TAG, "[$currentTime][CLEAR_HISTORY_BUTTON_CLICKED][Showing confirmation dialog]")
            showClearHistoryDialog()
        }
        
        // 返回按鈕
        binding.btnBack.setOnClickListener {
            Log.i(TAG, "[$currentTime][BACK_BUTTON_CLICKED][Finishing activity]")
            finish()
        }
    }
    
    private fun showClearHistoryDialog() {
        val currentTime = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", java.util.Locale.getDefault()).format(java.util.Date())
        
        val historyCount = preferencesManager.getApiCallHistory().size
        
        AlertDialog.Builder(this)
            .setTitle("清空歷史記錄")
            .setMessage("確定要清空所有 API 調用歷史記錄嗎？\n\n目前共有 $historyCount 筆記錄\n\n此操作無法復原。")
            .setPositiveButton("確定") { _, _ ->
                val confirmTime = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", java.util.Locale.getDefault()).format(java.util.Date())
                Log.i(TAG, "[$confirmTime][CLEAR_HISTORY_CONFIRMED][Clearing $historyCount records]")
                
                preferencesManager.clearApiCallHistory()
                Toast.makeText(this, "已清空 $historyCount 筆歷史記錄", Toast.LENGTH_SHORT).show()
                
                Log.i(TAG, "[$confirmTime][CLEAR_HISTORY_COMPLETED][All history cleared]")
            }
            .setNegativeButton("取消") { _, _ ->
                Log.d(TAG, "[$currentTime][CLEAR_HISTORY_CANCELLED][User cancelled]")
            }
            .show()
    }
    
    private fun updateCurrentLevelDisplay() {
        val currentLevel = preferencesManager.getDebugLevel()
        val levelName = debugLevels.find { it.level == currentLevel }?.displayName ?: "未知"
        binding.tvCurrentLogLevel.text = "目前 Log 等級：$levelName"
        
        val currentTime = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", java.util.Locale.getDefault()).format(java.util.Date())
        Log.d(TAG, "[$currentTime][LEVEL_DISPLAY_UPDATED][level=$currentLevel, name=$levelName]")
    }
    
    private fun testLogLevels() {
        val currentTime = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", java.util.Locale.getDefault()).format(java.util.Date())
        
        // 初始化 LogManager
        LogManager.init(this)
        
        Log.i(TAG, "[$currentTime][LOG_LEVEL_TEST_START][Current level=${LogManager.getCurrentLogLevelInfo()}]")
        
        // 使用 LogManager 測試所有等級
        LogManager.testAllLogLevels(TAG)
        
        // 也使用傳統方式測試，確保能看到差異
        Log.v(TAG, "[$currentTime][TRADITIONAL_LOG_TEST][VERBOSE level - 最詳細的除錯資訊]")
        Log.d(TAG, "[$currentTime][TRADITIONAL_LOG_TEST][DEBUG level - 一般除錯資訊]")
        Log.i(TAG, "[$currentTime][TRADITIONAL_LOG_TEST][INFO level - 重要資訊]")
        Log.w(TAG, "[$currentTime][TRADITIONAL_LOG_TEST][WARN level - 警告訊息]")
        Log.e(TAG, "[$currentTime][TRADITIONAL_LOG_TEST][ERROR level - 錯誤訊息]")
        
        // 測試自定義日誌方法
        LogManager.logBatteryStatus(TAG, 85, true, "充電中")
        LogManager.logApiCall(TAG, "TEST123", "http://test.com", "GET")
        LogManager.logApiResponse(TAG, "TEST123", 200, true, 150)
        LogManager.logTriggerCheck(TAG, "HIGH", 85, 80, true, true)
        
        Log.i(TAG, "[$currentTime][LOG_LEVEL_TEST_COMPLETE][Check LogCat to see which messages appear based on current level]")
        
        // 顯示提示
        Toast.makeText(
            this, 
            "已輸出所有等級的測試訊息！\n目前等級: ${LogManager.getCurrentLogLevelInfo()}\n請查看 LogCat 確認顯示結果", 
            Toast.LENGTH_LONG
        ).show()
    }
    
    override fun onSupportNavigateUp(): Boolean {
        val currentTime = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", java.util.Locale.getDefault()).format(java.util.Date())
        Log.i(TAG, "[$currentTime][TOOLBAR_BACK_CLICKED][Finishing activity]")
        finish()
        return true
    }
    
    override fun onDestroy() {
        val currentTime = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", java.util.Locale.getDefault()).format(java.util.Date())
        Log.i(TAG, "[$currentTime][SETTINGS_ACTIVITY_DESTROY][Activity destroyed]")
        super.onDestroy()
    }
}