@echo off
chcp 65001 >nul
echo === 切换到完整版（含地图）===
echo.
echo 注意：完整版需要手动下载高德地图SDK
echo 详见 PROJECT_STATUS.md
echo.

cd /d "%~dp0"

echo [1/3] 恢复 build.gradle...
cd "app"
if exist "build-simple.gradle" (
    rename "build-simple.gradle" "build-simple.gradle.temp"
)
if exist "build.gradle" (
    rename "build.gradle" "build-simple.gradle"
)
if exist "build.gradle.backup" (
    rename "build.gradle.backup" "build.gradle"
) else (
    echo [警告] 找不到 build.gradle.backup
    echo 请确保有完整版的备份文件
)
if exist "build-simple.gradle.temp" (
    rename "build-simple.gradle.temp" "build-simple.gradle"
)

echo [2/3] 恢复 MainActivity...
cd "src\main\java\com\example\locationshare"
if exist "MainActivitySimple.kt" (
    rename "MainActivitySimple.kt" "MainActivitySimple.kt.temp"
)
if exist "MainActivity.kt" (
    rename "MainActivity.kt" "MainActivitySimple.kt"
)
if exist "MainActivity.kt.backup" (
    rename "MainActivity.kt.backup" "MainActivity.kt"
) else (
    echo [警告] 找不到 MainActivity.kt.backup
)
if exist "MainActivitySimple.kt.temp" (
    rename "MainActivitySimple.kt.temp" "MainActivitySimple.kt"
)

echo [3/3] 恢复布局文件...
cd "..\..\..\..\..\res\layout"
if exist "activity_main_simple.xml" (
    rename "activity_main_simple.xml" "activity_main_simple.xml.temp"
)
if exist "activity_main.xml" (
    rename "activity_main.xml" "activity_main_simple.xml"
)
if exist "activity_main.xml.backup" (
    rename "activity_main.xml.backup" "activity_main.xml"
) else (
    echo [警告] 找不到 activity_main.xml.backup
)
if exist "activity_main_simple.xml.temp" (
    rename "activity_main_simple.xml.temp" "activity_main_simple.xml"
)

echo.
echo === 切换完成 ===
echo.
echo 如果使用完整版，请确保：
echo 1. 已下载高德地图SDK放入 app/libs/
echo 2. 已申请高德地图API Key
echo 3. 已修改 AndroidManifest.xml
echo.
echo 然后返回 Android Studio 点击：
echo   File -^> Sync Project with Gradle Files
echo.
pause
