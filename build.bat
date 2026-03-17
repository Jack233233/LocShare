@echo off
chcp 65001 >nul
echo === 位置共享App 命令行构建 ===
echo.

REM 检查 Java
java -version 2>nul
if errorlevel 1 (
    echo [错误] 未找到 Java，请先安装 JDK 17+
    echo 下载地址: https://adoptium.net/
    pause
    exit /b 1
)

REM 检查 ANDROID_HOME
if "%ANDROID_HOME%"=="" (
    echo [错误] 未设置 ANDROID_HOME 环境变量
    echo.
    echo 请执行以下步骤：
    echo 1. 下载 Android SDK 命令行工具：
    echo    https://developer.android.com/studio#command-tools
    echo 2. 解压到 C:\Android\Sdk
    echo 3. 设置环境变量：
    echo    ANDROID_HOME=C:\Android\Sdk
    echo    PATH=%%ANDROID_HOME%%\platform-tools;%%PATH%%
    pause
    exit /b 1
)

echo [1/3] 使用 Android SDK: %ANDROID_HOME%

REM 检查 Gradle
set "GRADLE_BIN=gradle"
gradle --version >nul 2>&1
if errorlevel 1 (
    echo [2/3] 未找到 Gradle，尝试使用项目自带版本...

    REM 检查是否有 wrapper
    if exist "gradle\wrapper\gradle-wrapper.jar" (
        echo       使用 Gradle Wrapper...
        set "GRADLE_BIN=gradlew.bat"
    ) else (
        echo       下载 Gradle...
        call :download_gradle
        if errorlevel 1 (
            echo [错误] 下载 Gradle 失败
            pause
            exit /b 1
        )
    )
) else (
    echo [2/3] 使用系统 Gradle
)

echo.
echo [3/3] 开始构建 Debug APK...
echo.

REM 构建
%GRADLE_BIN% assembleDebug --no-daemon

if errorlevel 1 (
    echo.
    echo [错误] 构建失败
    pause
    exit /b 1
)

echo.
echo === 构建成功 ===
echo.
echo APK 位置:
echo   app\build\outputs\apk\debug\app-debug.apk
echo.
echo 安装到手机:
echo   adb install app\build\outputs\apk\debug\app-debug.apk
echo.
pause
exit /b 0

:download_gradle
    set "GRADLE_VERSION=8.2"
    set "GRADLE_ZIP=gradle-%GRADLE_VERSION%-bin.zip"
    set "GRADLE_URL=https://services.gradle.org/distributions/%GRADLE_ZIP%"

    echo       下载 Gradle %GRADLE_VERSION%...
    powershell -Command "Invoke-WebRequest -Uri '%GRADLE_URL%' -OutFile '%TEMP%\%GRADLE_ZIP%'"

    if errorlevel 1 exit /b 1

    echo       解压 Gradle...
    powershell -Command "Expand-Archive -Path '%TEMP%\%GRADLE_ZIP%' -DestinationPath '%CD%\.gradle' -Force"

    set "GRADLE_BIN=.gradle\gradle-%GRADLE_VERSION%\bin\gradle.bat"
    exit /b 0
