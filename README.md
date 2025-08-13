# Battery Triggered API

一個 Android 應用程式，當手機電量達到指定百分比時，自動發送 HTTP POST 請求到指定的 Web API。

## 📱 功能特色

- 🔋 **智慧電量監控**: 即時監控手機電池電量
- 🎯 **自動觸發**: 當電量達到設定門檻時自動發送 API 請求
- 🔄 **重試機制**: 每隔 1% 觸發一次，直到 API 成功為止
- 🛡️ **防重複呼叫**: 成功後停止觸發，直到電量降到門檻以下
- ⚙️ **可自訂設定**: 
  - 電量門檻（預設 80%）
  - API URL
- 📊 **狀態顯示**: 
  - 目前電池電量和充電狀態
  - 最近一次 API 呼叫結果與時間
- 🧪 **測試功能**: 手動測試 API 連線
- 🎨 **Material Design**: 現代化的使用者介面

## 🔧 技術規格

- **語言**: Kotlin
- **最低支援版本**: Android 10 (API 29)
- **架構**: 單 Activity 應用
- **網路庫**: OkHttp
- **UI**: Material Design Components
- **資料儲存**: SharedPreferences
- **背景服務**: BatteryMonitorService

## 🎯 使用場景

- 控制智慧插座停止充電（如小米插座）
- 啟動家庭自動化流程
- 節電提醒或自動化控制
- 任何需要基於電量觸發的自動化場景

## 📋 權限說明

- `INTERNET`: 發送網路請求
- `ACCESS_NETWORK_STATE`: 檢查網路連線狀態
- `BATTERY_STATS`: 讀取電池狀態
- `POST_NOTIFICATIONS`: 發送系統通知

## 🚀 安裝與使用

### 建置要求
- Android Studio Arctic Fox 或更新版本
- Android SDK 29+
- Kotlin 1.9+

### 建置步驟
1. Clone 此倉庫
2. 在 Android Studio 中開啟專案
3. 同步 Gradle 依賴
4. 建置並安裝到裝置

### 使用方式
1. 設定電量門檻（1-100%）
2. 輸入要呼叫的 API URL
3. 點擊「儲存設定」
4. 應用程式會在背景監控電量
5. 當電量達到門檻時自動發送 POST 請求

## 🔄 觸發邏輯

```
電量 79% → 80% → 觸發 API → 81% → 觸發 API → 82% → API 成功 ✅
→ 83%, 84%, 85%... → 不觸發 (已成功)
→ 電量降到 79% → 重置狀態
→ 電量回到 80% → 重新開始觸發流程
```

## 🛡️ 安全特性

- 觸發鎖定機制防止重複呼叫
- 統一的觸發管理
- 詳細的 Log 追蹤
- 錯誤處理和重試機制

## 📁 專案結構

```
app/src/main/java/com/example/batterytriggeredapi/
├── MainActivity.kt              # 主要 UI 活動
├── BatteryMonitorService.kt     # 電池監控服務
├── BatteryReceiver.kt           # 電池狀態接收器
├── ApiCaller.kt                 # API 呼叫器
├── PreferencesManager.kt        # 設定管理器
└── DebugHelper.kt              # 除錯輔助工具
```

## 🤝 貢獻

歡迎提交 Issue 和 Pull Request！

## 📄 授權

此專案採用 MIT 授權條款。

## 📞 聯絡

如有問題或建議，請開啟 Issue。