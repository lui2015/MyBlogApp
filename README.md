# 鲁力铭 - Android APP

一个用于浏览 [www.luliming.xyz](https://www.luliming.xyz/) 的 Android 原生应用。

## 功能特性

- **WebView 嵌入**：使用 Android WebView 加载 `https://www.luliming.xyz/`
- **启动页**：带动画效果的品牌启动页
- **顶部进度条**：页面加载时显示加载进度
- **下拉刷新**：支持下拉手势刷新页面
- **网络检测**：自动检测网络状态，离线时显示友好提示
- **错误处理**：加载失败时提供重试按钮
- **返回键导航**：按返回键可在网页历史中后退
- **外部链接**：非站内链接自动跳转系统浏览器

## 技术栈

- **语言**：Kotlin
- **最低 SDK**：Android 7.0 (API 24)
- **目标 SDK**：Android 14 (API 34)
- **构建工具**：Gradle 8.2 + AGP 8.2.0
- **UI**：ViewBinding + Material Design

## 构建方式

### 前置条件

- Android Studio Hedgehog (2023.1.1) 或更高版本
- JDK 17

### 步骤

1. 用 Android Studio 打开项目根目录
2. 等待 Gradle 同步完成
3. 点击 **Run** 或使用命令行构建：

```bash
# Debug APK
./gradlew assembleDebug

# Release APK（需要配置签名）
./gradlew assembleRelease
```

4. 生成的 APK 位于 `app/build/outputs/apk/`

## 项目结构

```
MyBlogApp/
├── app/
│   ├── src/main/
│   │   ├── java/xyz/luliming/app/
│   │   │   ├── SplashActivity.kt    # 启动页
│   │   │   └── MainActivity.kt      # 主界面 (WebView)
│   │   ├── res/
│   │   │   ├── layout/              # 布局文件
│   │   │   ├── values/              # 字符串、颜色、主题
│   │   │   ├── drawable/            # 图标资源
│   │   │   └── xml/                 # 网络安全配置
│   │   └── AndroidManifest.xml
│   └── build.gradle
├── build.gradle
├── settings.gradle
└── README.md
```

## 自定义

如需更换嵌入的网站地址，修改 `MainActivity.kt` 中的 `TARGET_URL` 常量即可：

```kotlin
companion object {
    private const val TARGET_URL = "https://www.luliming.xyz/"
}
```
