@echo off
chcp 65001 >nul
echo === 位置共享App 构建 ===
echo.

REM 检查 Java
java -version >nul 2>&1
if errorlevel 1 (
    echo [错误] 需要安装 JDK 17+
    echo 下载: https://adoptium.net/
    pause
    exit /b 1
)

REM 检查 ANDROID_HOME
if "%ANDROID_HOME%"=="" (
    set "ANDROID_HOME=%USERPROFILE%\Android\Sdk"
)

if not exist "%ANDROID_HOME%\platform-tools\adb.exe" (
    echo [错误] 未找到 Android SDK
    echo 请运行 download-sdk.bat 先安装 SDK
    pause
    exit /b 1
)

echo [1/2] 准备 Gradle...

REM 使用本地 Gradle 或下载
if not exist ".gradle\gradle-8.2\bin\gradle.bat" (
    echo   下载 Gradle 8.2...
    powershell -Command "Invoke-WebRequest -Uri 'https://services.gradle.org/distributions/gradle-8.2-bin.zip' -OutFile '%TEMP%\gradle.zip'"
    powershell -Command "Expand-Archive -Path '%TEMP%\gradle.zip' -DestinationPath '.gradle' -Force"
    del "%TEMP%\gradle.zip" 2>nul
)

echo [2/2] 构建 Debug APK...
echo.

set "GRADLE_BIN=.gradle\gradle-8.2\bin\gradle.bat"

REM 设置环境变量并构建
set "ANDROID_HOME=%ANDROID_HOME%"
set "PATH=%ANDROID_HOME%\platform-tools;%PATH%"

%GRADLE_BIN% assembleDebug --no-daemon --offline 2>nul
if errorlevel 1 (
    echo 首次构建需要下载依赖，正在在线构建...
    %GRADLE_BIN% assembleDebug --no-daemon
)

if errorlevel 1 (
    echo.
    echo [失败] 构建出错
    pause
    exit /b 1
)

echo.
echo === 构建成功 ===
echo.
echo APK: app\build\outputs\apk\debug\app-debug.apk
echo.
set /p INSTALL="是否安装到手机? (y/n): "
if /i "%INSTALL%"=="y" (
    adb install -r app\build\outputs\apk\debug\app-debug.apk
)
pause
