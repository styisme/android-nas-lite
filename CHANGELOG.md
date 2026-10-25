# Changelog

本项目遵循 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.0.0/) 规范，
版本号遵循 [Semantic Versioning](https://semver.org/lang/zh-CN/)。

**维护策略**：Lite 版不独立演进。每次完整版仓库（[android-nas](https://github.com/styisme/android-nas)）发布大版本后，会在该新版本基础上重新执行一次精简，并将结果同步推送到本仓库。因此，Lite 版的版本号始终对齐完整版，格式为 `<完整版版本号>-lite.<精简次数>`。

---

## [4.4.0-lite.2] - 2026-10-25

**基于 4.4.0-lite.1 的深度修复：封堵沙箱逃逸回归与 CI 流水线崩坏**

完整版未发布新大版本，本次为 Lite 版专属精简范围与残留缺陷修复。重点封堵了 lite.1 精简过程中回退的沙箱校验缺陷，并修复了 CI 发版流水线与包名隔离的回归。

### Fixed（致命回归修复）
- **沙箱逃逸与全站 404**：`NasHttpServer.safeFile()` 与 `FtpServer.safeFile()` 在精简时回退了 `new File(parent, "/abs")` 的致命写法，导致 `path` 带前导斜杠时父目录被忽略，`admin` 与 `guest` 的所有文件操作被 `getCanonicalPath()` 判定越权并返回 `null`。已恢复为强制 `replaceAll("^/+", "")` 后再拼接。
- **CI 发版流水线崩坏**：`build-all.yml` 的 `Extract Release Notes` 段在精简时残留了旧版 heredoc 裸文本，导致 Shell 解析直接报 `command not found`。已重写为 `{ ... } > release_notes.md` 的块结构，并同步适配 Lite 版 Assets 命名。
- **包名隔离回归**：`app/client/tv` 的 `applicationId` 在精简时被误改为 `com.androidnas.all.*`，与 Lite 版“与完整版共存”的核心卖点冲突。已恢复为 `com.androidnas.lite.*`。
- **越界依赖回流**：`app/build.gradle` 在精简时错误重新引入了 `jsch`（SSH 穿透）与 `Java-WebSocket`（远控），直接违反 Lite 版“物理移除高危依赖”的硬约束。已物理删除。

### Fixed（精简残留与边缘缺陷）
- **分片上传对 `content://` 崩溃**：`FileListActivity` 在分片上传分支里对 `content://` URI 使用 `new File(uri.getPath())` 二次打开，会抛 `FileNotFoundException`。已改为通过 `getContentResolver().openInputStream(uri)` 重新打开。
- **版本号注入截断**：`sync_version.py` 的正则只吃 `\d+\.\d+\.\d+`，`4.4.0-lite.1` 被截断成 `4.4.0`，注入 `versionName` 后与 CI 产物文件名不一致。已扩展为支持 `-lite.N` 后缀，`versionCode` 同步加入 lite 序号编码。
- **清理线程泄漏**：`HttpServerService.startCleanupTask()` 的 `while(true)` 没有退出条件，服务停止后线程仍驻留。已改为 `while(running)` 并监听中断。
- **默认密码提示失效**：`DatabaseManager.isDefaultPwd` 为静态变量，重启进程后即使已改密也回显“默认密码警告”。已改为 `isDefaultPassword()` 动态查询 SQLite 比对加盐哈希。
- **分享有效期被忽略**：`/api/share` 接口在精简时丢失了 `expire` 参数解析，固定 24 小时。已恢复对前端参数的支持，缺省仍为 24 小时。
- **无障碍描述残留**：`strings.xml` 中还残留着 `accessibility_service_description` 字符串，对应的 `NasAccessibilityService` 早已被物理删除。已清理。

### Changed（适配调整）
- `versionCode` 编码规则由 `major*10000 + minor*100 + patch` 调整为 `major*100000 + minor*1000 + patch*10 + lite序号`，以容纳 `-lite.N` 后缀而不冲突。

---

## [4.4.0-lite.1] - 2026-10-25

**基于完整版 v4.4.0 重新精简：顶级安全底座与底层换血**

从完整版 v4.4.0 出发，同步了企业级的安全防御与底层流处理修复，同时物理剔除了自毁机制、远控与多媒体模块。

### Kept（完美继承完整版 v4.4.0 的核心安全防御）
- **密码加盐哈希与防爆破**：彻底废弃明文存储，采用 SHA-256 + 随机盐；新增 Fail2Ban 防暴力破解，连续输错 5 次自动封禁 IP 15 分钟。
- **IP 白名单拦截**：支持通配符访问控制，在 HTTP/WebDAV/FTP 入口处全面拦截。
- **原生 HTTPS 支持**：动态生成自签证书，支持 Web 端加密传输。
- **安全审计与清理**：记录敏感操作日志；后台定时清理超过 24 小时的临时分片文件。
- **Range 视频流句柄修复**：引入 `LimitedInputStream` 解决视频拖拽播放的句柄泄漏。

### Removed（Lite 版专属深水区裁剪）
- **☢️ 终极自毁模式**：物理剔除了自毁接口与前端“核按钮”，防止误触导致数据毁灭。
- **多媒体扩展**：剔除了影视刮削（MediaScraper）、时光相册、硬件监控面板。
- **多端收敛**：继续保留 5 个端（Server, Web, Client, TV, Desktop），剔除 Watch、Apple、CLI 等。

### Changed（适配调整）
- **CI 矩阵瘦身**：`build-all.yml` 与相关独立流水线彻底移除了 watch、cli、extras。
- **包名隔离**：维持 `com.androidnas.lite.*`。

---

## [4.3.0-lite.1] - 2026-10-24

**基于完整版 v4.3.0 重新精简：史诗级安全重构**

从完整版 v4.3.0 出发，同步了底层并发隔离与沙箱修复，彻底解决越权与逃逸问题。

### Kept（完美继承完整版 v4.3.0 的核心安全防御）
- **`ThreadLocal` 并发隔离**：重构 `AuthManager`，彻底废弃全局静态变量，杜绝高并发下 A 用户请求串到 B 用户沙箱的越权漏洞。
- **多用户沙箱逃逸修复**：废弃了因绝对路径导致父目录被忽略的致命缺陷，底层强制剥离 `root_dir` 的前导斜杠。
- **秒传跨用户投毒防御**：为 `file_hashes` 增加用户路径匹配校验，杜绝恶意用户通过 MD5 命中他人文件。
- **分片上传流处理修复**：移除了分片接口中错误的 `parseBody` 调用，防止二进制流被拦截消费。
- **前端 401 鉴权死锁修复**：废弃了会导致 Unicode 崩溃的旧 `btoa` 逻辑，解决了密码错误时无限弹窗的 Promise 死锁。

### Removed（本次新增的移除项）
- **高危远控模块**：完全剔除了 WebSocket（8082）、H264 推流、无障碍触控、Root 截图。
- **高权限与激进保活**：移除了 Root 全盘模式、Web Shell 终端、Magisk 免死、静音保活与 AlarmManager 心跳。
- **冗余 API**：移除了 `/api/shell`、`/api/users`、`/api/media/wall` 接口。

---

## [4.2.0-lite.1] - 2026-10-24

**基于完整版 v4.2.0 重新精简**

同步了 PWA、断点续传等核心更新，并彻底剥离了高危依赖与冗余多媒体功能。

### Kept（保留并同步完整版新特性）
- **PWA 支持**：网页端支持“添加到主屏幕”，全屏无地址栏。
- **大文件断点续传与秒传**：实装 `/api/upload/check` 校验与 2MB 分片流式追加合并。
- **Webhook 事件推送**：支持向 Telegram / 企业微信群机器人推送事件。
- **CI 双轨制签名**：日常调试自动加 `.debug` 后缀；全量发版执行 `assembleRelease`。

### Removed（本次新增的移除项）
- **影视刮削引擎**：移除了 `MediaScraper.java` 及前端海报墙 UI。
- **高危依赖剔除**：完整版中重新引入的 `jsch`（SSH）与 `Java-WebSocket`（远控）依赖被再次剔除。

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