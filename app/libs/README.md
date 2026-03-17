# 手动下载高德地图 SDK

由于网络问题无法自动下载，请手动下载：

## 下载地址

1. 访问 https://lbs.amap.com/api/android-sdk/download
2. 下载 "3D地图SDK" 和 "定位SDK"
3. 解压后将 .jar 和 .so 文件放入对应目录

## 目录结构

```
app/
├── libs/
│   ├── AMap3DMap_xxx.jar
│   ├── AMapLocation_xxx.jar
│   └── armeabi-v7a/
│       ├── libAMapSDK_MAP_vxxx.so
│       └── libAMapSDK_LOCATION_vxxx.so
│   └── arm64-v8a/
│       ├── libAMapSDK_MAP_vxxx.so
│       └── libAMapSDK_LOCATION_vxxx.so
```

## 备选方案

如果不想手动下载，可以改用 **Google Maps**（需要科学上网）或 **Mapbox**。

或者最简单的：先用 **WebView 加载在线地图** 代替原生地图。