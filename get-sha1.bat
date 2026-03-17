@echo off
chcp 65001 >nul
echo === 获取调试版 SHA1 ===
echo.

REM 找 keytool
for /f "tokens=*" %%a in ('where keytool 2^>nul') do set "KEYTOOL=%%a"

if "%KEYTOOL%"=="" (
    if exist "%JAVA_HOME%\bin\keytool.exe" (
        set "KEYTOOL=%JAVA_HOME%\bin\keytool.exe"
    ) else (
        echo [错误] 找不到 keytool，请确保 JAVA_HOME 设置正确
        pause
        exit /b 1
    )
)

echo 使用 keytool: %KEYTOOL%
echo.

REM Windows 路径
set "KEYSTORE=%USERPROFILE%\.android\debug.keystore"

if not exist "%KEYSTORE%" (
    echo [错误] 调试密钥不存在: %KEYSTORE%
    echo.
    echo 请先运行一次 Android Studio 的 Build，
    echo 或在 AS 中点击 Gradle → Tasks → android → signingReport
    pause
    exit /b 1
)

"%KEYTOOL%" -list -v -keystore "%KEYSTORE%" -alias androiddebugkey -storepass android -keypass android | findstr "SHA1"

echo.
echo 复制上面的 SHA1 值到高德地图控制台
echo.
pause
