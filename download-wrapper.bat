@echo off
echo 正在下载 Gradle Wrapper...
echo.

set "WRAPPER_URL=https://services.gradle.org/distributions/gradle-8.2-bin.zip"
set "WRAPPER_DIR=.gradle\wrapper\dists\gradle-8.2-bin\..."

powershell -Command "& {
    $url = '%WRAPPER_URL%'
    $output = '%TEMP%\gradle-8.2-bin.zip'

    Write-Host '下载 Gradle 8.2...'
    Invoke-WebRequest -Uri $url -OutFile $output

    Write-Host '解压到本地...'
    Expand-Archive -Path $output -DestinationPath '.gradle' -Force

    Remove-Item $output
    Write-Host '完成!'
}"

echo.
echo 创建启动脚本...
(
echo @echo off
echo set "GRADLE_USER_HOME=%%~dp0.gradle"
echo call .gradle\gradle-8.2\bin\gradle.bat %%*
) > gradle-local.bat

echo.
echo 用法:
echo   gradle-local.bat assembleDebug    - 构建 Debug APK
echo   gradle-local.bat assembleRelease  - 构建 Release APK
echo   gradle-local.bat clean            - 清理构建
echo.
pause
