@echo off
chcp 65001 >nul
echo === 切换到简化版（无地图）===
echo.

cd /d "%~dp0"

if not exist "app\build-simple.gradle" (
    echo [错误] 找不到 build-simple.gradle
    pause
    exit /b 1
)

echo [1/3] 备份并切换 build.gradle...
if exist "app\build.gradle" (
    rename "app\build.gradle" "build.gradle.backup"
)
rename "app\build-simple.gradle" "build.gradle"

echo [2/3] 备份并切换 MainActivity...
cd "app\src\main\java\com\example\locationshare"
if exist "MainActivity.kt" (
    rename "MainActivity.kt" "MainActivity.kt.backup"
)
if exist "MainActivitySimple.kt" (
    rename "MainActivitySimple.kt" "MainActivity.kt"
)

echo [3/3] 备份并切换布局文件...
cd "..\..\..\..\..\res\layout"
if exist "activity_main.xml" (
    rename "activity_main.xml" "activity_main.xml.backup"
)
if exist "activity_main_simple.xml" (
    rename "activity_main_simple.xml" "activity_main.xml"
)

echo.
echo === 切换完成 ===
echo.
echo 请返回 Android Studio 并点击：
echo   File -^> Sync Project with Gradle Files
echo.
pause
