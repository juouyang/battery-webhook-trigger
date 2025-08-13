#!/bin/bash

echo "🚀 準備上傳到 GitHub..."

# 1. 初始化 Git 倉庫
git init

# 2. 添加所有檔案（.gitignore 會自動排除不需要的檔案）
git add .

# 3. 檢查哪些檔案會被上傳
echo "📋 即將上傳的檔案："
git status

# 4. 確認沒有敏感檔案
echo "⚠️  請確認以下檔案沒有被包含："
echo "   - local.properties"
echo "   - build/ 資料夾"
echo "   - .gradle/ 資料夾"
echo "   - .idea/ 資料夾"
echo "   - *.apk 檔案"

echo ""
echo "如果看到上述檔案，請按 Ctrl+C 取消，否則按 Enter 繼續..."
read

# 5. 提交
git commit -m "Initial commit: Battery Triggered API Android App

Features:
- Battery level monitoring
- Automatic API calls when threshold reached
- Triggers every 1% until success
- Prevents duplicate calls
- Configurable threshold and API URL
- Material Design UI"

echo "✅ Git 倉庫已準備完成！"
echo ""
echo "📝 接下來的步驟："
echo "1. 在 GitHub 上創建新倉庫"
echo "2. 執行以下命令："
echo "   git remote add origin https://github.com/YOUR_USERNAME/YOUR_REPO_NAME.git"
echo "   git branch -M main"
echo "   git push -u origin main"