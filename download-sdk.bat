@echo off
chcp 65001 >nul
echo === Android SDK 快速下载 ===
echo.

set "SDK_DIR=%USERPROFILE%\Android\Sdk"

if exist "%SDK_DIR%\platform-tools\adb.exe" (
    echo SDK 已存在: %SDK_DIR%
    echo.
    echo 如果版本过旧，请先删除该目录后重新运行此脚本
    pause
    exit /b 0
)

echo 将下载到: %SDK_DIR%
echo 约需 500MB-1GB 空间
echo.
pause

echo.
echo [1/4] 创建目录...
mkdir "%SDK_DIR%\cmdline-tools" 2>nul
mkdir "%SDK_DIR%\platforms" 2>nul
mkdir "%SDK_DIR%\build-tools" 2>nul
mkdir "%SDK_DIR%\platform-tools" 2>nul

echo [2/4] 下载命令行工具...
set "CMD_TOOLS_URL=https://dl.google.com/android/repository/commandlinetools-win-11076708_latest.zip"
powershell -Command "Invoke-WebRequest -Uri '%CMD_TOOLS_URL%' -OutFile '%TEMP%\cmdline-tools.zip'"

echo [3/4] 解压...
powershell -Command "Expand-Archive -Path '%TEMP%\cmdline-tools.zip' -DestinationPath '%SDK_DIR%\cmdline-tools' -Force"
move "%SDK_DIR%\cmdline-tools\cmdline-tools" "%SDK_DIR%\cmdline-tools\latest" 2>nul

echo [4/4] 安装必要组件...
set "SDKMANAGER=%SDK_DIR%\cmdline-tools\latest\bin\sdkmanager.bat"

REM 接受所有许可
echo y | "%SDKMANAGER%" --licenses >nul 2>&1

REM 安装组件
echo 正在安装 platform-tools...
"%SDKMANAGER%" "platform-tools" --channel=0

echo 正在安装 Android 34 平台...
"%SDKMANAGER%" "platforms;android-34" --channel=0

echo 正在安装 Build Tools 34...
"%SDKMANAGER%" "build-tools;34.0.0" --channel=0

del "%TEMP%\cmdline-tools.zip" 2>nul

echo.
echo === 安装完成 ===
echo.
echo 请设置环境变量:
echo   ANDROID_HOME = %SDK_DIR%
echo   PATH 添加 %%ANDROID_HOME%%\platform-tools
echo.
echo 临时使用（当前窗口有效）:
echo   set "ANDROID_HOME=%SDK_DIR%"
echo   set "PATH=%%ANDROID_HOME%%\platform-tools;%%PATH%%"
echo.
pause
