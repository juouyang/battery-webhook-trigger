@echo off
echo 🔨 開始建置 Android APK...

if not exist "gradlew.bat" (
    echo ❌ 錯誤：找不到 gradlew.bat 檔案，請確認您在專案根目錄
    pause
    exit /b 1
)

echo 📦 清理舊的建置檔案...
call gradlew.bat clean

echo 🔧 建置 Debug APK...
call gradlew.bat assembleDebug

if %errorlevel% equ 0 (
    echo ✅ 建置成功！
    echo 📱 APK 檔案位置：app\build\outputs\apk\debug\app-debug.apk
    echo.
    echo 📋 安裝指令：
    echo adb install app\build\outputs\apk\debug\app-debug.apk
) else (
    echo ❌ 建置失敗，請檢查錯誤訊息
)

pause