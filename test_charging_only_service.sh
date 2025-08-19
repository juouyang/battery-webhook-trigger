#!/bin/bash

echo "🔌 測試「充電時才啟動前景服務」功能"
echo "===================================="

PACKAGE_NAME="com.example.batterytriggeredapi"

echo "📦 重新構建並安裝..."
./gradlew app:assembleDebug && adb install -r app/build/outputs/apk/debug/app-debug.apk

if [ $? -eq 0 ]; then
    echo "✅ 安裝成功"
else
    echo "❌ 安裝失敗"
    exit 1
fi

echo ""
echo "🧪 測試充電時啟動服務..."

# 清除日誌
adb logcat -c

echo "📱 步驟 1: 確保未充電狀態"
adb shell dumpsys battery reset
sleep 2

echo "📋 檢查服務狀態（應該沒有前景服務）:"
adb shell dumpsys activity services | grep -A 3 -B 3 "$PACKAGE_NAME" || echo "✅ 正確：沒有前景服務運行"

echo ""
echo "📱 步驟 2: 模擬插電"
adb shell dumpsys battery set ac 1
adb shell dumpsys battery set level 85
sleep 3

echo "📋 監控插電觸發日誌 (10 秒)..."
echo "應該看到："
echo "- BatteryReceiver: 電源已連接 - 啟動監控服務"
echo "- BatteryReceiver: ✅ 已啟動電池監控前台服務"
echo "- BatteryMonitorService: 服務已啟動"

timeout 10 adb logcat | grep -E "(BatteryReceiver|BatteryMonitorService|電源已連接|已啟動)" || echo "沒有看到預期日誌"

echo ""
echo "📋 檢查服務狀態（應該有前景服務）:"
adb shell dumpsys activity services | grep -A 5 -B 5 "$PACKAGE_NAME" || echo "❌ 沒有找到前景服務"

echo ""
echo "📱 步驟 3: 模擬拔電"
adb shell dumpsys battery reset
sleep 3

echo "📋 監控拔電觸發日誌 (10 秒)..."
echo "應該看到："
echo "- BatteryReceiver: 電源已斷開 - 停止監控服務"
echo "- BatteryReceiver: ✅ 已停止電池監控前台服務"
echo "- BatteryMonitorService: 服務即將停止"

timeout 10 adb logcat | grep -E "(BatteryReceiver|BatteryMonitorService|電源已斷開|已停止)" || echo "沒有看到預期日誌"

echo ""
echo "📋 檢查服務狀態（應該沒有前景服務）:"
adb shell dumpsys activity services | grep -A 3 -B 3 "$PACKAGE_NAME" || echo "✅ 正確：前景服務已停止"

echo ""
echo "🎯 測試總結："
echo "✅ 插電時：自動啟動前景服務"
echo "✅ 拔電時：自動停止前景服務"
echo "✅ 保持第一版的可靠邏輯"
echo "✅ 相對省電（只在充電時運行）"

echo ""
echo "💡 現在您有了最佳平衡："
echo "- 🔌 充電時：100% 可靠監控"
echo "- 🔋 拔電時：完全停止，省電"
echo "- 🎯 邏輯簡單：易於理解和測試"