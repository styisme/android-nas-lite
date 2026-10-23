# Changelog

本项目遵循 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.0.0/) 规范，
版本号遵循 [Semantic Versioning](https://semver.org/lang/zh-CN/)。

**维护策略**：Lite 版不独立演进。每次完整版仓库（[android-nas](https://github.com/styisme/android-nas)）发布大版本后，会在该新版本基础上重新执行一次精简，并将结果同步推送到本仓库。因此，Lite 版的版本号始终对齐完整版，格式为 `<完整版版本号>-lite.<精简次数>`。

> 例：完整版 `v4.1.1` 首次精简 → Lite 版 `v4.1.1-lite.1`；若后续仅对精简范围做调整、不跟随完整版 → `v4.1.1-lite.2`。

---

## [4.1.1-lite.1] - 2026-10-23

**首次发布：基于完整版 v4.1.1 精简**

从完整版 `v4.1.1` 出发，移除所有与"局域网文件服务"主线无关或存在高危风险的能力，只保留服务端、Web 端、手机客户端、TV 端、桌面端五个端。

### 与完整版 v4.1.1 的差异

#### Removed（整端移除）

- **Watch 手表端**：代码量小、用户量低，维护成本不成比例。
- **Apple 端（SwiftUI）**：需 Xcode 真机签名，无法在 CI 中自动化验证。
- **Harmony 端（ArkTS）**：需 DevEco Studio 与 OpenHarmony 签名体系，同 Apple 端。
- **微信小程序端**：可作为 Web 端的轻量替代，功能高度重叠。
- **浏览器扩展**：仅服务于"右键下载"单一场景，与 Web 端功能重复。
- **CLI 命令行端**：与桌面端功能重叠，且仅服务极客用户。

#### Removed（核心模块）

- **多模态远程控制全套**：H264 硬件编码推流、Root 截图流、WebSocket 服务（8082 端口）、无障碍触控注入、Root `input` 注入。
  - 移除原因：与"NAS"核心价值无关，屏幕泄露与设备被完全接管的风险无法由维护者兜底。
- **Root 全盘模式与 Web Shell**：`/api/shell` 接口、`SettingsManager.isGlobalMode()` 越权分支、`RootManager`。
  - 移除原因：误操作可能导致系统变砖，且用户无法自行恢复。
- **Magisk 特权模块生成、OOM 免死注入、冻结厂商省电服务**：`AdvancedKeepAliveManager`。
  - 移除原因：属于"与系统对抗"的高危操作，副作用（如微信不通知）难以预期。
- **激进保活矩阵**：静音音频欺骗（`SilentPlayer`）、1 像素 Activity（`OnePixelActivity`）、AlarmManager 心跳（`AlarmReceiver`）、双进程守护。
  - 移除原因：Android 12+ 已被系统限制，且"偷偷放音频"一旦被发现会迅速失去用户信任。
- **内网穿透**：免费节点遍历（`FreeTunnelManager`）、自定义 SSH 隧道（`SshTunnelManager`）、`jsch` 依赖。
  - 移除原因：引导用户自行使用 frp / ngrok / Cloudflare Tunnel 更安全稳定。
- **DLNA 媒体服务器**（`DlnaServer`）。
  - 移除原因：TV 端直连 NAS 更稳定，且 DLNA 需处理 SSDP 组播与 SOAP 解析，维护面大。
- **LocalSend 接收端**（`LocalSendServer`、`LocalSendDiscovery`）。
  - 移除原因：LocalSend 官方客户端已足够好用，无需在 NAS 上重复实现。
- **多媒体增强**：图片缩略图引擎（`ThumbnailManager`）、时光相册（`/api/photos`）、影视海报墙、硬件仪表盘（`SystemMonitor`、`/api/monitor`）、MD5 秒传、分片合并。
  - 移除原因：属于增值功能而非 NAS 核心，且增加服务端负载与维护面。
- **多用户管理 UI**：网页端用户管理面板、`/api/users` 接口。
  - 移除原因：保留 `admin` / `guest` 双账号即可满足绝大多数家庭场景。

#### Kept（保留并同步完整版修复）

- **服务端**：HTTP 文件服务（8080）、WebDAV（8081，纯 Socket 手写）、FTP（2121，纯 Socket 手写）、UDP 跨端发现（8090）、前台服务 + `WakeLock` + `WifiLock` + `BOOT_COMPLETED` 基础保活三件套、`admin` / `guest` 双账号 Basic Auth。
- **Web 端**：MD3 文件管理器、上传 / 下载 / 重命名 / 删除 / 新建 / 文本预览 / 全局搜索 / Range 流媒体 / ZIP 在线解压 / 局域网剪贴板、系统控制台（改密、改端口、开关协议）。
- **手机客户端**：多协议（JSON / WebDAV / FTP）、局域网扫描、二维码添加、相册增量备份、批量操作、在线编辑、分享外链、投屏。
- **TV 端**：Leanback 卡片流、虚拟键盘、接收手机配置下发与投屏。
- **桌面端**：Tkinter 原生 GUI、全局搜索、剪贴板同步、一键复制外链、在线编辑与解压。
- **完整版 v4.1.1 的所有 Bug 修复**：网页端老旧 WebView 的 `SyntaxError` 修复、`/api/share` 空值容错、CI Release Notes 变量展开修复。
- **完整版 v4.1.0 的核心底座**：SQLite `nas_core.db`（仅用于账号与文件哈希，不再暴露多用户管理 UI）、`AuthManager` 鉴权拦截、`NasHttpServer.safeFile()` 路径沙箱映射。
- **完整版 v4.0.0 的兼容性修复**：移除 Lambda 表达式以适配 AGP 4.2.2、`BatteryManager` 的 API 8 版本守卫、`minSdkVersion` 锁定为 8。

#### Changed（精简过程中顺带修改的代码）

- `app/build.gradle`：移除 `jsch`、`Java-WebSocket` 依赖。
- `AndroidManifest.xml`：移除 `SYSTEM_ALERT_WINDOW`、`WRITE_SECURE_SETTINGS`、`FOREGROUND_SERVICE_MEDIA_PLAYBACK` 权限；移除 `AlarmReceiver`、`OnePixelActivity`、`NasAccessibilityService` 声明。
- `SettingsManager.isGlobalMode()`：永远返回 `false`（UI 入口已移除，避免残留的 SharedPreferences 开关在服务端生效）。
- `.github/workflows/build-all.yml`：删除 `build-watch`、`build-extras` job；`build-pc` 矩阵移除 `cli` 项。
- `.github/workflows/build-desktop.yml`：矩阵移除 `cli` 项，删除 CLI 构建与上传步骤。
- `sync_version.py`：移除对 `watch/app/build.gradle` 的版本同步。

---

## 未来版本记录模板

完整版发布新大版本后，Lite 版会新增如下条目：

```markdown
## [<完整版版本号>-lite.<N>] - YYYY-MM-DD

**基于完整版 v<版本号> 重新精简**

### 从完整版同步的能力与修复
- （列出完整版新增/修复的功能中，Lite 版保留的部分）

### 本次新增的移除项
- （若完整版新增了不适用于 Lite 版的功能，列在此处）

### 精简脚本调整
- （若完整版代码结构调整导致精简规则变化，说明变化点）
```

---

## 关于版本号的说明

Lite 版不独立演进小版本号。`-lite.N` 中的 `N` 只在两种情况下递增：

1. **完整版发布大版本后重新精简** → `N = 1`（重置）
2. **仅对精简范围做调整、完整版未发新版本** → `N += 1`

例如：

| 完整版版本 | Lite 版版本 | 说明 |
|---|---|---|
| `v4.1.1` | `v4.1.1-lite.1` | 首次精简 |
| `v4.1.1`（未发新版，仅调整精简范围） | `v4.1.1-lite.2` | 例如恢复某个功能、或移除更多功能 |
| `v4.2.0` | `v4.2.0-lite.1` | 完整版发新大版本，重新精简 |