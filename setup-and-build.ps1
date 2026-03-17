# 位置共享App - 一键构建脚本
# 需要: JDK 17+ 和 Android SDK

param(
    [switch]$InstallSDK,
    [switch]$BuildDebug,
    [switch]$BuildRelease
)

$ErrorActionPreference = "Stop"

Write-Host "=== 位置共享App 构建工具 ===" -ForegroundColor Cyan

# 检查 Java
Write-Host "`n[1/5] 检查 Java 环境..." -ForegroundColor Yellow
$javaVersion = & java -version 2>&1 | Select-String "version" | Select-Object -First 1
if ($javaVersion) {
    Write-Host "  找到: $javaVersion" -ForegroundColor Green
} else {
    Write-Host "  错误: 未找到 Java，请先安装 JDK 17 或更高版本" -ForegroundColor Red
    Write-Host "  下载地址: https://adoptium.net/"
    exit 1
}

# 检查/安装 Android SDK
Write-Host "`n[2/5] 检查 Android SDK..." -ForegroundColor Yellow
$androidHome = $env:ANDROID_HOME

if (-not $androidHome -or -not (Test-Path $androidHome)) {
    Write-Host "  未设置 ANDROID_HOME 环境变量" -ForegroundColor Red

    # 检查常见位置
    $commonPaths = @(
        "$env:LOCALAPPDATA\Android\Sdk",
        "$env:USERPROFILE\AppData\Local\Android\Sdk",
        "C:\Android\Sdk"
    )

    foreach ($path in $commonPaths) {
        if (Test-Path $path) {
            $androidHome = $path
            Write-Host "  找到 SDK: $androidHome" -ForegroundColor Green
            break
        }
    }

    if (-not $androidHome -and $InstallSDK) {
        Write-Host "  正在下载 Android SDK..." -ForegroundColor Yellow
        $sdkUrl = "https://dl.google.com/android/repository/commandlinetools-win-11076708_latest.zip"
        $sdkZip = "$env:TEMP\android-cmdline-tools.zip"
        $sdkDir = "$env:USERPROFILE\Android\Sdk"

        Invoke-WebRequest -Uri $sdkUrl -OutFile $sdkZip
        Expand-Archive -Path $sdkZip -DestinationPath "$sdkDir\cmdline-tools" -Force
        Rename-Item "$sdkDir\cmdline-tools\cmdline-tools" "$sdkDir\cmdline-tools\latest" -ErrorAction SilentlyContinue

        $androidHome = $sdkDir
        Remove-Item $sdkZip

        Write-Host "  SDK 下载完成: $sdkDir" -ForegroundColor Green
    }
}

if (-not $androidHome) {
    Write-Host "`n  未找到 Android SDK，请执行以下操作之一:" -ForegroundColor Red
    Write-Host "  1. 运行: .\setup-and-build.ps1 -InstallSDK`n"
    Write-Host "  2. 手动安装 Android Studio 获取 SDK`n"
    Write-Host "  3. 设置 ANDROID_HOME 环境变量指向 SDK 目录`n"
    exit 1
}

$env:ANDROID_HOME = $androidHome
$env:PATH = "$androidHome\platform-tools;$androidHome\cmdline-tools\latest\bin;$env:PATH"

Write-Host "  ANDROID_HOME: $androidHome" -ForegroundColor Green

# 安装必要的 SDK 组件
Write-Host "`n[3/5] 检查 SDK 组件..." -ForegroundColor Yellow
$sdkManager = "$androidHome\cmdline-tools\latest\bin\sdkmanager.bat"

if (Test-Path $sdkManager) {
    # 接受许可
    Write-Host "  接受 SDK 许可..." -ForegroundColor Gray
    & $sdkManager --licenses 2>&1 | Out-Null

    # 安装必要组件
    $packages = @(
        "platform-tools",
        "platforms;android-34",
        "build-tools;34.0.0"
    )

    foreach ($pkg in $packages) {
        Write-Host "  安装 $pkg..." -ForegroundColor Gray
        & $sdkManager --install $pkg 2>&1 | Out-Null
    }
} else {
    Write-Host "  警告: 未找到 sdkmanager" -ForegroundColor Yellow
}

# 下载 Gradle Wrapper
Write-Host "`n[4/5] 准备 Gradle Wrapper..." -ForegroundColor Yellow
$gradleWrapperUrl = "https://raw.githubusercontent.com/gradle/gradle/v8.2.0/gradle/wrapper/gradle-wrapper.jar"
$wrapperDir = "$PSScriptRoot\gradle\wrapper"
$wrapperJar = "$wrapperDir\gradle-wrapper.jar"

if (-not (Test-Path $wrapperJar)) {
    New-Item -ItemType Directory -Path $wrapperDir -Force | Out-Null
    Write-Host "  下载 gradle-wrapper.jar..." -ForegroundColor Gray
    try {
        Invoke-WebRequest -Uri $gradleWrapperUrl -OutFile $wrapperJar -UseBasicParsing
    } catch {
        Write-Host "  下载失败，使用备用方式..." -ForegroundColor Yellow
        # 创建空文件，用户需要手动下载
        New-Item -ItemType File -Path $wrapperJar -Force | Out-Null
    }
}

Write-Host "  完成" -ForegroundColor Green

# 构建 APK
Write-Host "`n[5/5] 构建 APK..." -ForegroundColor Yellow

$buildType = if ($BuildRelease) { "assembleRelease" } else { "assembleDebug" }

# 设置内存
$env:GRADLE_OPTS = "-Xmx2g -XX:MaxMetaspaceSize=512m"

# 运行构建
try {
    & .\gradlew.bat $buildType --no-daemon --stacktrace

    if ($LASTEXITCODE -eq 0) {
        Write-Host "`n=== 构建成功! ===" -ForegroundColor Green

        $apkPath = if ($BuildRelease) {
            ".\app\build\outputs\apk\release\app-release-unsigned.apk"
        } else {
            ".\app\build\outputs\apk\debug\app-debug.apk"
        }

        Write-Host "`nAPK 位置: $apkPath" -ForegroundColor Cyan
        Write-Host "`n安装到手机:" -ForegroundColor Yellow
        Write-Host "  adb install $apkPath" -ForegroundColor White
        Write-Host "`n或直接复制 APK 到手机安装" -ForegroundColor White
    } else {
        Write-Host "`n构建失败，请查看错误信息" -ForegroundColor Red
    }
} catch {
    Write-Host "`n错误: $_" -ForegroundColor Red
}
