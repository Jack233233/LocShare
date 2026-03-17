# 位置共享App - 快速构建指南

## 环境要求

- Android Studio Hedgehog (2023.1.1) 或更高版本
- JDK 17
- Android SDK 34

---

## 快速开始

### 1. 打开项目

```
File → Open → 选择 E:\Together\LocationShareApp
```

### 2. 同步项目

点击工具栏 🔄 **Sync Project with Gradle Files**

> 首次同步会下载高德地图 SDK，请确保网络通畅

### 3. 运行

1. 连接手机或启动模拟器
2. 点击工具栏 ▶️ **Run 'app'**

---

## 使用说明

### 界面说明

| 元素 | 说明 |
|------|------|
| 你的名字 | 在共享中显示的名称 |
| 双人共享/多人共享 | 切换共享模式 |
| 生成配对码 | 双人模式下生成6位数字配对码 |
| 输入配对码 | 双人模式下添加好友 |
| 好友列表 | 显示已添加的好友 |
| 房间号输入框 | 多人模式下输入房间号 |
| 开始共享/退出共享 | 连接/断开服务器 |
| 跟随开启/跟随关闭 | 控制地图是否持续跟随自己位置 |
| 地图区域 | 显示自己和他人位置 |

### 标记说明

- 🟢 **绿色气泡** - 自己的位置（显示"我"）
- 🔴 **红色气泡** - 其他用户位置（显示用户名）
- 🔵 **系统蓝点** - 高德地图定位点，点击地图右下角定位按钮可快速回到自己位置

### 查看用户详细信息

点击地图上的任意标记（自己或他人），右上角会显示信息面板：
- **用户名** - 标记对应的用户名称
- **速度** - 实时移动速度（km/h），静止时显示为 0
- **方向** - 移动方向（北/东北/东/东南/南/西南/西/西北），带箭头指示

点击地图空白处可关闭信息面板。

> **注意**：速度和方向需要 GPS 定位才能准确计算，室内或静止时可能显示为 0 或不变。

### 双人共享模式

**添加好友流程：**
1. 点击 **"双人共享"** 切换模式
2. 点击 **"生成配对码"**
3. 将显示的6位数字告诉对方
4. 对方点击 **"输入配对码"**，输入你的配对码
5. 双方成为好友，可在好友列表看到对方

**开始共享：**
1. 从好友列表点击选择要共享的好友
2. 点击 **"开始共享"**
3. 双方都能实时看到彼此位置

### 多人共享模式

1. 点击 **"多人共享"** 切换模式
2. 输入房间号（如 `test123`）
3. 输入你的名字
4. 点击 **"开始共享"**
5. 地图上会显示房间内所有人的位置

### 路线管理功能（通勤场景）

**功能介绍**：
- 创建常用路线（如上班、回家路线）
- 保存起点和终点位置
- 设置自动共享的对象
- 一键开始导航和位置共享

**使用流程**：

1. 点击主界面 **"路线"** 按钮进入路线管理
2. 点击右下角 **"+"** 添加新路线
3. 填写路线名称（如"上班路线"）
4. 在地图页面输入**起点**和**终点**地址
   - 输入时会有地址联想提示
   - 从下拉列表选择准确地址
   - 地图会显示起点（绿标）和终点（红标）
5. 选择**自动共享给**哪位好友
6. 点击**保存路线**

**路线列表操作**：
- 点击⭐收藏/取消收藏路线
- 点击**开始**启动导航和位置共享（TODO）
- 点击🗑️删除路线

**TODO 功能**：
- 实时导航显示
- 到达检测自动停止共享
- 路线编辑

### 单设备测试（只有一部手机）

只有一台设备时，可以这样验证功能：

**1. 验证配对码生成**
- 点击 **"双人共享"** → **"生成配对码"**
- 应显示6位数字（如 `123456`）
- 5分钟后自动过期

**2. 验证地图定位**
- 点击 **"多人共享"** 或直接 **"开始共享"**
- 地图应显示：
  - 🟢 绿色气泡标记（显示"我"）
  - 🔵 系统定位蓝点
- 首次会自动移动到你的位置

**3. 用命令行模拟好友添加**
```bash
# 步骤1：生成配对码（在App上操作，假设得到 123456）
# 步骤2：用curl模拟另一个用户添加好友
curl -X POST http://47.109.86.151/api/pair \
  -H "Content-Type: application/json" \
  -d '{"userId":"friend001","userName":"测试好友","code":"123456"}'

# 成功后，下拉刷新或重新进入双人模式
# 好友列表应显示"测试好友"
```

**4. 用Android模拟器测试双人**
- 在Android Studio中启动两个模拟器
- 两个模拟器分别运行App
- 一个生成配对码，另一个输入配对码添加
- 双方选择彼此开始共享

---

---

## 构建 Release APK

### 方式1：Android Studio

```
Build → Generate App Bundle or APK → APK → Release
```

### 方式2：命令行

```bash
cd E:\Together\LocationShareApp
gradlew assembleRelease
```

输出路径：
```
app/build/outputs/apk/release/app-release.apk
```

---

## 项目配置

### 高德地图 Key

**Android SDK Key**（用于原生地图）：

如需更换请修改 `app/src/main/AndroidManifest.xml`：

```xml
<meta-data
    android:name="com.amap.api.v2.apikey"
    android:value="你的新Key" /
>
```

**Web JS API Key**（用于路线地址搜索）：

如需更换请修改 `RouteManagerActivity.kt`：

```kotlin
private val AMAP_WEB_KEY = "你的Web JS API Key"
private val AMAP_SECURITY_KEY = "你的安全密钥"
```

申请地址：https://lbs.amap.com

- Android SDK Key 平台：Android
- Web JS API Key 平台：Web端(JS API)

### 服务器地址

如需更换服务器，修改 `MainActivity.kt`：

```kotlin
private const val SERVER_URL = "http://你的服务器IP"
```

---

## 服务器部署

服务器代码位于 `E:\Together\location-share`，使用 Docker Compose 部署在阿里云 ECS 上。

### 项目结构

```
/opt/location-share/
├── backend/
│   ├── server.js      # 主服务代码
│   ├── Dockerfile
│   └── package.json
├── docker-compose.yml  # Docker 编排配置
└── nginx.conf          # Nginx 反向代理配置
```

### 部署后端更新

修改 `server.js` 后，按以下步骤部署：

**1. 上传修改后的文件到服务器**

```bash
scp E:\Together\location-share\backend\server.js root@47.109.86.151:/opt/location-share/backend/
```

**2. SSH 登录服务器并重启服务**

```bash
ssh root@47.109.86.151
cd /opt/location-share
docker compose down
docker compose up -d --build
```

**3. 查看日志确认部署成功**

```bash
docker compose logs -f backend
```

**4. 验证服务状态**

```bash
curl http://47.109.86.151/health
# 应返回: {"status":"ok","time":...}
```

### 服务器架构

- **backend**: Node.js + Socket.io (内部端口3000)
- **nginx**: 反向代理 (外部端口80)
- **redis**: 数据存储

API 接口通过 Nginx 代理，外部访问端口为 **80**。

---

## 架构说明

**WebView + 原生混合架构**：
- 前端：HTML5/CSS3/JavaScript 实现界面
- 原生：Android WebView 容器 + 高德地图 SDK
- 通信：JavaScript Bridge 双向调用

**前端位置**：`app/src/main/assets/web/`
- `index.html` - 主界面（主菜单、好友模式、多人模式）
- `style.css` - 样式（渐变、动画、响应式）
- `app.js` - 前端逻辑（页面切换、好友管理、共享控制）

**修改前端**：
直接修改 `assets/web/` 下的文件，重新运行即可生效（无需重新编译原生代码）。

---

## 后台运行说明

开始共享后，应用会自动启动前台服务：
- 通知栏显示"位置共享中"的持续通知
- 点击通知可返回应用
- 点击通知栏的"停止共享"按钮可快速停止
- 应用退到后台后，位置共享会继续运行
- 清除应用后台会彻底停止共享

**权限要求**：
- Android 10+ 需要"允许后台定位"权限
- Android 13+ 需要"通知权限"（首次开始共享时会申请）
- 部分手机需要关闭电池优化才能保持后台运行

**通知权限说明**：
- Android 13 及以上版本需要用户授权通知权限才能显示前台服务通知
- 如果拒绝通知权限，应用将无法在后台保持共享
- 可以在系统设置中随时开启通知权限

---

## 常见问题

### 1. Gradle 同步失败

**解决**：检查网络连接，高德SDK需要下载

### 2. 地图显示空白

**检查**：
- API Key 是否正确
- 包名是否为 `com.example.locationshare`
- 手机的 SHA1 是否已添加到高德控制台

### 3. 无法连接服务器

**检查**：
- 手机网络是否正常
- 服务器 `http://47.109.86.151/health` 是否可访问
- AndroidManifest.xml 是否设置了 `android:usesCleartextTraffic="true"`

### 4. 定位不工作

**检查**：
- 是否授予定位权限
- 手机定位服务是否开启

### 5. 后台共享被中断

**检查**：
- 是否授予"允许后台定位"权限（Android 10+）
- 是否授予"通知权限"（Android 13+）
- 是否关闭了电池优化（部分手机需要）
- 是否启用了省电模式

### 6. 不显示通知栏通知

**检查**：
- 是否授予通知权限（Android 13+ 首次使用时会申请）
- 检查系统设置中的应用通知权限是否开启
- 重启应用后重试

### 7. 路线管理页面地图不显示

**检查**：
- Web JS API Key 是否正确配置
- 安全密钥（securityJsCode）是否匹配
- Key 是否启用了 **JS API** 权限
- Referer 白名单是否留空或包含 `https://webapi.amap.com`
- 查看 logcat 日志过滤 `WebView` 查看加载错误

---

## 技术栈

- **地图**：高德地图 3D SDK（原生）+ 高德 JS API 2.0（WebView）
- **定位**：高德定位 SDK（内置于3D地图）
- **通信**：Socket.io
- **架构**：原生 Android + Kotlin
- **混合开发**：WebView + JavaScript Bridge（路线管理模块）

---

## 相关文档

- [项目状态](../PROJECT_STATUS.md)
- [高德地图 SDK 文档](https://lbs.amap.com/api/android-sdk/summary/)
