
# Android App 設計文件：Battery Triggered API Caller

## 📱 目標功能

本 App 專為以下場景設計：
當手機電量達到指定百分比（預設為 80%）時，**自動發送 HTTP POST 請求** 到指定的 Web API，例如控制小米智慧插座斷電。適合應用於保護充電裝置、延長電池壽命等自動化情境。

---

## 🎯 功能需求

1. 背景監控手機電量（不可長時間保持螢幕喚醒）
2. 達到指定電量門檻時，自動發送 API 請求
3. 使用者可透過 UI 調整：
   - 電量門檻（預設 80）
   - API URL（預設為 placeholder）
4. UI 顯示：
   - 目前電池電量
   - 最近一次 API 呼叫結果與時間
5. 確保背景運作不影響省電機制（不常駐、不使用 Foreground Service）

---

## ⚙️ 技術需求

| 項目 | 說明 |
|------|------|
| 語言 | Kotlin |
| 支援版本 | Android 10+ |
| 架構 | 單 Activity 應用，簡易架構 |
| 電量監控 | 使用 `BroadcastReceiver` 監聽 `ACTION_BATTERY_CHANGED` |
| HTTP 請求 | 使用 OkHttp 或 HttpURLConnection |
| 儲存設定 | 使用 `SharedPreferences` |
| 後台執行 | 非長駐 Service，只在電量變化時觸發 |
| UI 類型 | 可為 XML 或 Jetpack Compose |

---

## 🖥️ UI 畫面元素

- Battery Status：目前電池電量
- Threshold Input：設定觸發門檻（整數欄位，預設 80）
- API URL Input：設定觸發用的 HTTP URL
- 呼叫結果：
  - 成功 or 失敗
  - 回應碼
  - 時間戳
- 測試 API 按鈕（選配功能）

---

## 🔗 API 呼叫方式

- 預設 URL：`https://example.com/api/poweroff`
- 方法：HTTP POST
- 無需額外 headers 或 body（POC）

---

## 🧠 邏輯說明

1. 每當電量變化（如插拔電源、持續充電）會觸發 `BroadcastReceiver`
2. 讀取目前電量
3. 與儲存的門檻值比較
4. 若達成條件且當前未觸發過，則：
   - 發送 POST 請求
   - 儲存觸發時間與結果以供 UI 顯示

---

## 💡 延伸建議（未來擴充）

- 加入觸發間隔限制（cooldown，避免重複觸發）
- 支援 API Token 或 Basic Auth 驗證
- 提供 Webhook 測試功能
- 整合通知系統或顯示系統 Toast

---

## ✅ 使用範例

適合使用於：
- 控制智慧插座停止充電（例如小米插座）
- 啟動家庭自動化流程
- 節電提醒或自動化

---

## 🔚 總結

這是一個單純但實用的 Android App，透過電量監控自動執行網路請求，無需常駐背景執行、不影響手機省電策略，非常適合作為智慧家庭自動化的入口點。
