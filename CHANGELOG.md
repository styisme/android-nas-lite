# Changelog

本项目遵循 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.0.0/) 规范，
版本号遵循 [Semantic Versioning](https://semver.org/lang/zh-CN/)。

**维护策略**：Lite 版不独立演进。每次完整版仓库（[android-nas](https://github.com/styisme/android-nas)）发布大版本后，会在该新版本基础上重新执行一次精简，并将结果同步推送到本仓库。因此，Lite 版的版本号始终对齐完整版，格式为 `<完整版版本号>-lite.<精简次数>`。

---

## [4.3.0-lite.1] - 2026-10-24

**🛡️ 基于完整版 v4.3.0 重新精简：史诗级安全重构与底层换血**

从完整版 v4.3.0 出发，同步了企业级的并发安全防御与底层流处理修复，同时彻底剔除了所有远控与监控模块，Lite 版回归最纯粹的“局域网文件服务器”形态。

### Kept（完美继承完整版 v4.3.0 的核心安全防御）
- **`ThreadLocal` 并发隔离**：重构 `AuthManager`，彻底废弃全局静态变量，杜绝高并发下 A 用户请求串到 B 用户沙箱的越权漏洞，并在请求结束的 `finally` 块中强制清理。
- **多用户沙箱逃逸修复**：废弃了因绝对路径导致父目录被忽略的致命缺陷（`new File(ROOT, "/Public")`），底层强制剥离 `root_dir` 的前导斜杠，普通用户再也无法越权。
- **秒传跨用户投毒防御**：为 `file_hashes` 增加用户路径匹配校验，杜绝了恶意用户通过相同 MD5 命中他人私密文件。
- **分片上传流处理修复**：移除了分片接口中错误的 `parseBody` 调用，防止二进制流被拦截消费；分片临时文件加入 Hash 后缀，防止并发覆盖损坏。
- **Range 视频流句柄泄漏修复**：引入 `LimitedInputStream`，重写了视频拖拽播放逻辑，彻底解决 `RandomAccessFile` 导致的高并发句柄泄漏与越界崩溃。
- **前端 401 鉴权死锁修复**：废弃了会导致 Unicode 崩溃的旧 `btoa` 逻辑，解决了密码错误时无限弹窗的 Promise 死锁。
- **前端媒体标签 401 黑屏修复**：重写了 `<img>` / `<video>` 预览逻辑，改用短效 Share Token 进行鉴权，解决了原生标签无法携带 Header 的问题。

### Removed（Lite 版专属深水区裁剪）
- **高危远控模块**：完全剔除了 WebSocket（8082）、H264 推流、无障碍触控、Root 截图。
- **高权限与激进保活**：移除了 Root 全盘模式、Web Shell 终端、Magisk 免死、静音保活与 AlarmManager 心跳。
- **多媒体扩展**：物理删除了 `MediaScraper.java`（影视刮削）、`SystemMonitor.java`（硬件监控）、`ThumbnailManager.java`（缩略图引擎），前端彻底剥离海报墙与仪表盘 UI。
- **冗余 API**：移除了 `/api/shell`、`/api/users`、`/api/monitor`、`/api/media/wall` 接口。

### Changed（Lite 版适配调整）
- **CI 签名容错**：修复了未配置 GitHub Secrets 时 Gradle 误判导致编译崩溃的问题，现支持无密钥时自动回退 Debug 签名。
- **UI 极简重写**：`index.html` 完全重写，保留极致的 Material Design 3 交互，仅保留文件流转、PWA、断点续传与 Webhook。
- **包名隔离**：维持 `com.androidnas.lite.*` 的前缀，确保与完整版完美共存。

---

## [4.2.0-lite.1] - 2026-10-24

**基于完整版 v4.2.0 重新精简**

从完整版 v4.2.0 出发，同步了 PWA、断点续传等核心更新，并彻底剥离了高危依赖与冗余多媒体功能。

### Kept（保留并同步完整版新特性）
- **PWA 支持**：网页端支持“添加到主屏幕”，全屏无地址栏，基于 Service Worker 实现部分离线缓存。
- **大文件断点续传与秒传**：网页端引入 `SparkMD5` 计算 Hash，服务端实装 `/api/upload/check` 校验与 2MB 分片流式追加合并。
- **Webhook 事件推送**：支持向 Telegram / 企业微信群机器人推送文件上传与异常事件。
- **CI 双轨制签名与构建**：日常调试自动加 `.debug` 后缀；全量发版通过 GitHub Secrets 注入正式 keystore 执行 `assembleRelease`。

### Removed（本次新增的移除项）
- **影视刮削引擎**：移除了 `MediaScraper.java` 及前端海报墙 UI，避免 NAS 向外发起第三方 API 请求。
- **高危依赖剔除**：完整版中重新引入的 `jsch`（SSH）与 `Java-WebSocket`（远控）依赖被再次剔除。

### Changed（包名与 CI 调整）
- **应用包名隔离**：将完整版的 `com.androidnas.all.*` 统一调整为 `com.androidnas.lite.*`，确保双版本多开共存。
- **版本号正则修复**：修复了 `sync_version.py` 和 CI 脚本中无法正确提取 `-lite.x` 后缀导致构建崩溃的问题。

---

## [4.1.1-lite.1] - 2026-10-23

**首次发布：基于完整版 v4.1.1 精简**

从完整版 `v4.1.1` 出发，移除所有与"局域网文件服务"主线无关或存在高危风险的能力。

### Removed（整端移除）
- Watch 手表端 / Apple 端 / Harmony 端 / 微信小程序 / 浏览器扩展 / CLI 命令行端。

### Removed（核心模块）
- 多模态远程控制全套（H264 推流、Root 截图、WebSocket、无障碍注入）。
- Root 全盘模式与 Web Shell（`/api/shell`）。
- Magisk 特权模块 / OOM 免死 / 激进保活矩阵。
- 内网穿透、DLNA 服务器、LocalSend 接收端。
- 图片缩略图引擎、时光相册、硬件仪表盘。
- 多用户管理 UI 及 `/api/users` 接口。

### Kept（保留并同步完整版修复）
- HTTP 文件服务、纯 Socket WebDAV/FTP、UDP 跨端发现、基础保活三件套。
- 完整版 v4.1.1 的所有底层兼容性与 Bug 修复。