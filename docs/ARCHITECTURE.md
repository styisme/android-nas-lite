# Android NAS Lite 底层架构深度解析 (v4.3.0-lite)

本项目之所以能在极低兼容性（API 8）下实现极高的性能，并且保持极小的包体积，源于底层大量抛弃了第三方重型框架，采用了**纯手写 Socket、原生硬件调用、SQLite 数据库引擎与极简设计**。

本文档将深度剖析 Lite 版（精简版）的核心架构设计，带你了解代码底层的运行原理。

---

## 一、 核心容器调度与拦截器 (HttpServerService)

服务端以 `Foreground Service`（前台服务）的形式常驻内存，在内部开启了多个独立的线程池并发监听不同端口，互不干扰：
- **8080 (HTTP JSON API)**：基于 NanoHTTPD 二次封装。在路由分发前，底层加入了**并发身份拦截器**与**路径沙箱映射器**，处理前端鉴权、流媒体请求、PWA 静态资源分发（`manifest.json`, `sw.js`）以及大文件断点续传。
- **8081 (WebDAV)**：**纯 Socket 手写实现**。直接解析 HTTP 报文，处理 `PROPFIND` 的 XML 拼装、`PUT` 的流式二进制写入。
- **2121 (FTP)**：**纯 Socket 手写实现**。处理 FTP 控制连接，支持 `PASV` 被动模式，管理数据通道。
- **8090 (UDP 协同)**：监听广播报文，用于 TV 端与手机端的局域网发现与配置下发。

> *(注：完整版中的 LocalSend 接收端、DLNA 投屏发现以及 8082 WebSocket 远控端口在 Lite 版中已被彻底移除。)*

---

## 二、 SQLite 架构与并发安全隔离 (v4.3.0 核心重构)

Lite 版保留 `DatabaseManager` 与 `AuthManager`，用于处理鉴权与秒传哈希，但剥离了复杂的面板 UI。在 v4.3.0 中，底层引入了企业级的安全防御：

### 1. 数据库设计
服务端首次启动时，会自动在应用沙盒内生成 `nas_core.db`，包含两张核心表：
- `users`：仅预置 `admin`（全盘只读）与 `guest`（锁定 `/Public`）两个账号。
- `file_hashes`：记录文件 MD5 与路径的映射，用于大文件“秒传”比对。

### 2. 动态沙箱映射与防逃逸 (Dynamic Path Mapping)
所有的文件操作不再直接读写 `/sdcard`。
- **`ThreadLocal` 并发隔离**：`AuthManager` 彻底废弃了全局静态变量，引入 `ThreadLocal` 存储当前请求用户身份。在 HTTP、WebDAV、FTP 请求的 `finally` 块中强制清理，杜绝了高并发下 A 用户的请求串到 B 用户沙箱的越权漏洞。
- **前导斜杠剥离**：修复了 `new File(ROOT, "/Public")` 这种因绝对路径导致父目录被忽略的致命缺陷。底层强制剥离 `root_dir` 的前导斜杠。
- **秒传防投毒**：为 `file_hashes` 增加了用户路径匹配校验，杜绝了 A 用户通过相同 MD5 命中 B 用户文件，导致“假秒传”数据泄露。
- **全盘 Root 模式已移除**：`SettingsManager.isGlobalMode()` 永远返回 `false`，任何人无法通过底层突破沙盒。

---

## 三、 核心文件处理引擎 (v4.3.0 修复与升级)

为了保证极低的内存占用与极高的流转效率，底层实装了两大文件引擎，并修复了严重的资源泄漏。

### 1. 大文件秒传与分片断点续传
- **秒传机制**：前端计算文件 MD5 后请求 `/api/upload/check`。服务端通过查询 SQLite 的 `file_hashes` 表，若已存在则直接返回 `SECONDS_UPLOAD`，实现 0 秒极速上传。
- **分片合并**：前端将大文件切为 2MB 分片。服务端通过 `FileOutputStream(file, true)` 将分片流式追加至 `.part` 临时文件，并在文件名中加入 Hash 后缀防止并发覆盖损坏。
- **流处理修复**：移除了分片上传接口中错误的 `parseBody` 调用，避免二进制流被拦截消费。
- **线程安全**：在最后一片到达触发合并重命名时，使用 `synchronized (NasHttpServer.class)` 加锁，防止并发写入导致文件损坏。

### 2. 流式打包与视频切片
- **流式打包下载**：底层利用 `ZipOutputStream`，将多选文件分块读取并直接写入 HTTP 响应流，实现“边压边传”，杜绝手机本地临时空间的占用。
- **`LimitedInputStream` 句柄防泄漏**：重写了 Range 视频拖拽播放逻辑，彻底解决了 `RandomAccessFile` 导致的高并发句柄泄漏与视频越界崩溃。

---

## 四、 Webhook 与 PWA 实现

### 1. Webhook 事件通知中枢 (WebhookManager)
- **轻量化实现**：放弃第三方 HTTP 库，使用 `HttpURLConnection` 异步发送 JSON 格式的事件通知。
- **协议适配**：底层内置对 Telegram (Form 表单) 与企业微信 (JSON 文本) 的格式适配，在上传完成时实时推送到用户终端。

### 2. PWA 渐进式 Web 应用
- **Service Worker**：前端注入 `sw.js`，在用户“添加到主屏幕”后，实现对静态资源的缓存加速。
- **路由拦截**：服务端 `NasHttpServer` 补全了对 `/manifest.json` 与 `/sw.js` 的拦截，从 assets 目录流式读取并返回，防止 404。

---

## 五、 极限兼容与适配策略

- **API 8 的妥协**：为了能在十年前的设备上跑，服务端 `minSdkVersion` 锁死在 8。代码中全面废弃了 Lambda 表达式，改用传统匿名内部类（避免 AGP 4.2.2 编译报错）。
- **高版本适配守卫**：针对 Android 11+ 强制接入 `MANAGE_EXTERNAL_STORAGE`；针对 Android 13+ 引入相册媒体权限；针对电池读取 API 引入 21 版本守卫。

---

## 六、 基础保活策略 (Keep-Alive)

Lite 版移除了与系统对抗的流氓保活，仅保留最基础的保活三件套：
1. **前台服务与锁**：`START_STICKY` + 通知栏常驻，辅以 `WakeLock`（CPU 锁）和 `WifiLock`（Wi-Fi 高性能锁）。
2. **开机自启**：监听 `BOOT_COMPLETED` 广播。
*(注：完整版中的静音音频欺骗、1像素 Activity、AlarmManager 心跳在 Lite 版中均已被移除。)*

---

## 七、 跨端协同协议 (UDP Discovery)

设备之间通过 `255.255.255.255:8090` 发送 `DISCOVER_TV` 广播。接收方（TV）回复自身 IP。发送方拿到 IP 后，通过 HTTP POST 将密码与视频流地址直接推送到目标设备，实现秒级联动。

---

## 八、 自动化构建与全量发版矩阵 (CI/CD)

`sync_version.py` 是整个 DevOps 流程的核心枢纽。在 Lite 版中，它经过特殊适配，能够解析带后缀的版本号（如 `4.3.0-lite.1`）。

### 1. 双轨制构建与签名机制
- **日常调试**：只触发对应端的独立编译（`assembleDebug`），包名自动追加 `.debug` 后缀，防止覆盖正式版。
- **全量发版**：修改 `CHANGELOG.md` 触发矩阵（`assembleRelease`）。通过 GitHub Secrets 注入 `release.keystore` 完成正式签名。
- **Lint 致命拦截修复**：在 `build.gradle` 中全局关闭了阻塞式 Lint 检查，彻底解决 `targetSdkVersion 22` 在 `assembleRelease` 时报错导致编译中断的问题。

### 2. 高容错与平铺直传
- 脚本使用 `find -type f` 和 `|| true` 提取产物，提取后的文件会按 `AndroidNAS-*-v${VERSION}` 规范重命名。
- 所有产物直接平铺展示在 GitHub Release 页面，供用户自由下载。

### 3. 应用标识隔离
为避免与完整版在手机上安装冲突，Lite 版的所有 Android 端包名统一迁移至 `com.androidnas.lite.*`（如 `com.androidnas.lite.server`）。