# Together - 位置共享应用

基于高德地图的实时位置共享 Android 应用。

## 功能特性

### 已实现
- [x] 用户注册与登录
- [x] 实时位置共享（Socket.io）
- [x] 好友系统（添加/删除好友）
- [x] 路线管理（WebView 实现）
  - [x] 添加/编辑/删除路线
  - [x] 地图选点（原生地图）
  - [x] 地址搜索（高德 PlaceSearch）
  - [x] 草稿自动保存
  - [x] 收藏功能
- [x] 后台位置上传服务

### 开发中
- [ ] 路线导航
- [ ] 路线分享

## 技术栈

- **语言**: Kotlin
- **最低 SDK**: 24
- **目标 SDK**: 34
- **地图 SDK**: 高德地图 3D Map v9.8.2
- **通信**: Socket.io
- **UI**: WebView + 原生混合

## 项目结构

```
app/src/main/
├── assets/web/
│   └── routes.html          # 路线管理 WebView 页面
├── java/com/example/locationshare/
│   ├── MainActivity.kt      # 主界面
│   ├── RouteManagerActivity.kt   # 路线管理（WebView）
│   ├── RouteMapPickerActivity.kt # 地图选点
│   ├── model/               # 数据模型
│   ├── service/             # 后台服务
│   └── utils/               # 工具类
│       └── PrefsManager.kt  # 数据持久化
└── res/layout/              # 布局文件
```

## 构建说明

```bash
./gradlew assembleDebug
```

## 配置

### 高德地图 API Key
在 `AndroidManifest.xml` 中配置：
```xml
<meta-data
    android:name="com.amap.api.v2.apikey"
    android:value="YOUR_API_KEY" />
```

## 最近更新

### 2026-03-18
- 修复地图选点崩溃问题（Android 16 兼容性）
- 添加路线编辑草稿自动保存功能
- 完成路线管理 WebView 重构

## License

MIT License
