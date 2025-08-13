#!/bin/bash

echo "🔨 開始建置 Android APK..."

# 檢查是否有 gradlew 檔案
if [ ! -f "./gradlew" ]; then
    echo "❌ 錯誤：找不到 gradlew 檔案，請確認您在專案根目錄"
    exit 1
fi

# 給予執行權限
chmod +x ./gradlew

echo "📦 清理舊的建置檔案..."
./gradlew clean

echo "🔧 建置 Debug APK..."
./gradlew assembleDebug

if [ $? -eq 0 ]; then
    echo "✅ 建置成功！"
    echo "📱 APK 檔案位置：app/build/outputs/apk/debug/app-debug.apk"
    echo ""
    echo "📋 安裝指令："
    echo "adb install app/build/outputs/apk/debug/app-debug.apk"
else
    echo "❌ 建置失敗，請檢查錯誤訊息"
    exit 1
fi