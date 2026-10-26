# Android NAS Lite

[![Platform](https://img.shields.io/badge/Platform-Android%20%7C%20Win%20%7C%20Linux%20%7C%20Web-brightgreen.svg)]()
[![Min SDK Server](https://img.shields.io/badge/Server_Min_SDK-API_8-blue.svg)]()
[![Min SDK Client](https://img.shields.io/badge/Client_Min_SDK-API_21-blue.svg)]()
[![Min SDK TV](https://img.shields.io/badge/TV_Min_SDK-API_19-blue.svg)]()
[![License](https://img.shields.io/badge/License-MIT-orange.svg)](LICENSE)

> **把吃灰的 Android 手机变成一个干净、稳定、可长期挂机的局域网文件服务器。**

**Android NAS** 是一个多技术栈并行演进的开源生态矩阵，所有核心仓库均由 [styisme](https://github.com/styisme) 维护，并保持 API 100% 互通：

| 仓库 | 定位 | 核心技术栈 |
|---|---|---|
| **[android-nas](https://github.com/styisme/android-nas)**<br>（完整版） | 覆盖 10 个端的全功能私有云控制中枢。包含远程控制、Root 全盘、Web Shell、内网穿透、短信中心与真实影视刮削。 | Java / Python / Swift / ArkTS |
| **android-nas-lite**<br>（本项目） | 剥离高风险与冗余功能的文件服务主线。专注纯粹的局域网文件流转，仅保留 5 个端。 | Java / Python |
| **[android-nas-dotnet](https://github.com/styisme/android-nas-dotnet)** | 次世代现代重构。彻底拥抱现代 Android 权限模型，提供原生跨平台 GUI 体验。 | C# / .NET 10 (MAUI) |
| **[HarmonyNAS](https://github.com/styisme/HarmonyNAS)** | 纯血鸿蒙原生重构。拒绝黑科技，利用系统级 `backgroundTaskManager` 合法保活，适配鸿蒙全家桶。 | ArkTS / ArkUI |

Lite 版将“旧手机变文件服务器”这一件事做到极致，去除了“云端手机控制台”等过度设计。如果你需要全功能体验或探索其他技术栈，请参阅上表链接。

---

## 📌 维护策略（重要）

**Lite 版不是独立演进的分支，而是“跟随式”维护：**

> **每次完整版发布大版本（`CHANGELOG.md` 顶部出现新的 `## [x.y.0]`）后，我会在该新版本基础上重新执行一次精简，并将结果同步推送到本仓库。**

这意味着：

- Lite 版的底层代码、协议实现、兼容性修复**全部来自完整版**；
- 完整版修复的 Bug、提升的性能、新增的 API **会自动进入 Lite 版**；
- 完整版的“高危功能”、“激进保活”、“多端扩张”**不会被带入 Lite 版**；
- Lite 版的版本号始终对齐完整版，格式为 `<完整版版本号>-lite.<精简次数>`。

---

## 📚 全局文档矩阵

- 🧭 **[开发环境约束与诚实声明 (DEVELOPMENT_CONSTRAINTS.md)](DEVELOPMENT_CONSTRAINTS.md)**：
  本仓库的物理限制、实际执行的门禁与测试状态。所有贡献者必读。
- 📖 **[终极使用与运维指南 (docs/USAGE.md)](docs/USAGE.md)**：
  从零部署、网络映射（WebDAV/FTP）、五重安全防线配置教程以及深度故障排查。
- 🔌 **[开发者 API 接口规范 (docs/API_DOC.md)](docs/API_DOC.md)**：
  服务端 8080 端口的所有 HTTP API 细节、2FA/隐藏暗号/局域网锁协议、WebDAV 底层规范、跨端协同 UDP 协议。
- 🏗️ **[底层架构深度解析 (docs/ARCHITECTURE.md)](docs/ARCHITECTURE.md)**：
  深入剖析纯 Socket 手写协议栈、纯 Java TOTP 算法、极限兼容 API 8 的妥协之道以及基础保活矩阵。
- 🛡️ **[安全策略 (SECURITY.md)](SECURITY.md)**：
  说明 Lite 版与完整版的安全边界差异，包括 2FA 锁死风险、隐藏暗号遗忘风险、局域网锁误杀、明文传输风险。
- 🤝 **[开发者贡献指南 (CONTRIBUTING.md)](CONTRIBUTING.md)**：
  AI 生成代码的声明、深度项目结构图、多环境编译隔离说明、代码提交规范与精简雷区速查表。
- 🤖 **[AI 生成声明 (docs/AI_DISCLOSURE.md)](docs/AI_DISCLOSURE.md)**：
  关于本项目代码由大模型生成的真实开发流程与局限性声明。
- 📝 **[版本更新日志 (CHANGELOG.md)](CHANGELOG.md)**：
  追溯项目从 v1.0.0 到 v4.5.0-lite.1 的完整演进史。

---

## ✅ 保留的能力

### 📱 服务端（Android 2.2+，装在闲置手机）
- **企业级五重安全防线 (v4.5.0 同步)**：
  - **原生 2FA 两步验证**：纯 Java 手写 TOTP（HMAC-SHA1，RFC 6238），兼容 Google Authenticator，默认关闭，**开启前强制验证一次验证码防止锁死**。
  - **隐藏入口暗号**：自定义 Secret Path，未携带 `?s=xxx` 的非 API 请求统一返回伪装 404，防扫站脚本探测。
  - **算数图形验证码**：触发 Fail2Ban 后前端弹算数题，`X-Captcha` Header 校验通过前拒绝继续请求，阻断并发爆破脚本。
  - **高危 API 局域网物理锁**：`/api/settings` 等核心接口强制校验来源 IP，公网 IP 一律拒绝，`isLanIp()` 用正则精准覆盖 RFC1918 网段。
  - **防 CC 限流与会话防护**：单 IP QPS 滑动窗口限流，闲置自动登出，隐藏 `Server` 响应头防指纹泄露。
- **企业级安全底座 (v4.4.0 同步)**：
  - **密码加盐哈希**：SHA-256 + 随机盐，杜绝明文存储。
  - **防爆破 (Fail2Ban)**：连续输错 5 次密码自动封禁 IP 15 分钟。
  - **IP 白名单**：支持通配符拦截，仅允许受信任网络访问。
  - **原生 HTTPS**：动态生成自签证书，支持 Web 端加密传输（旧设备自动降级 HTTP）。
  - **审计与清理**：持久化敏感操作日志，定时清理超过 24 小时的临时分片。
- **并发与沙箱隔离 (v4.3.0 同步)**：`ThreadLocal` 彻底杜绝越权串号；`safeFile()` 强制剥离前导斜杠，杜绝沙箱逃逸。
- **电池温度告警 (v4.5.0 同步)**：实时读取物理温度，超过 45℃ 触发 Webhook 报警，42℃ 以下自动解除（**Root 自动停充已剔除**）。
- **HTTP 文件服务（8080）**：目录浏览、上传、下载、重命名、删除、新建、文本预览、全局搜索、Range 流媒体、ZIP 在线解压、局域网剪贴板。
- **WebDAV（8081）**：纯 Socket 手写实现，支持 Windows 网络驱动器映射。
- **FTP（2121）**：纯 Socket 手写实现，支持 PASV 被动模式。
- **UDP 协同发现（8090）**：TV 端与手机端的局域网广播发现、配置下发、一键投屏。
- **大文件秒传与断点续传**：底层实装 MD5 秒传校验与 2MB 分片流式合并，无视网络波动。
- **Webhook 通知**：支持配置 Telegram/企微 Webhook，实现文件上传、温度报警、异常登录等事件的实时推送。
- **基础保活三件套**：前台服务 + `WakeLock` + `WifiLock` + 开机自启。
- **双账号鉴权**：内置 `admin` 与 `guest` 双账号，`guest` 仅限访问 `/Public`。

### 🌐 Web 网页端（MD3 Web）
- **PWA 渐进式应用**：集成 `manifest.json` 与 `Service Worker`，可添加到手机主屏幕，全屏无地址栏运行。
- **系统控制台与五重安全面板**：在网页端修改密码、端口、开关协议、配置 Webhook，以及开启 2FA、隐藏暗号、局域网锁、算数验证码、防 CC 限流、会话超时。
- **高级文件工具**：多选流式打包下载、带提取期的安全分享、在线解压与文本编辑（自动生成 `.bak` 备份）。
- **媒体与剪贴板**：支持 `Range` 拖动播放、局域网剪贴板跨设备复制。
- **MD3 SVG 矢量图标**：菜单、顶部导航与 PWA 桌面图标全部为 Material Symbols，告别 Emoji。

### 🖥️ 手机客户端（Android 5.0+）
- **相册自动增量备份**：利用 `MediaStore` 扫描本地，比对 NAS `/Photos/` 目录，只传新照片。
- **多协议智能路由**：支持原生 JSON、WebDAV、FTP 切换。
- **局域网一键扫描**：多线程并发探测发现设备。
- **分享外链与投屏**：一键调起系统分享面板，或发送 Play 指令投屏到 TV。

### 📺 TV 电视端（Android 4.4+）
- **Leanback 卡片流**：4 列网格，遥控器焦点移动 1.1 倍放大动画。
- **协同接收**：支持接收手机端通过 UDP 下发的配置（自动填入密码）与投屏指令。

### 💻 桌面端（Windows 7+ / Linux）
- **纯原生 Tkinter**：无内置浏览器，秒开不卡顿。
- **高级交互**：全局实时搜索、局域网剪贴板同步、一键复制短链、多选并发上传与在线编辑、系统托盘。

---

## ❌ 已从 Lite 版移除的能力

以下功能**在完整版中仍然可用**，Lite 版主动移除：

| 分类 | 移除项 | 移除原因 |
|---|---|---|
| **短信中心** | `SmsManager` 整个类、`RECEIVE_SMS` / `READ_SMS` / `SEND_SMS` 三项权限、`/api/sms/send` / `/api/sms/logs` 接口、前端短信 UI | 与“文件服务”定位无关；短信隐私敏感度极高；误发短信可能造成财产损失 |
| **远程控制** | H264 硬件编码推流、Root 截图流、WebSocket 服务（8082端口）、无障碍触控注入 | 与“NAS”核心价值无关，存在屏幕泄露与设备被完全接管的风险 |
| **高权限** | Root 全盘模式、Web Shell 终端、Magisk 特权模块、OOM 免死注入、冻结厂商服务 | 误操作可能导致系统变砖或厂商服务异常，无法由维护者兜底 |
| **激进保活** | 静音音频欺骗、1 像素 Activity、AlarmManager 心跳、双进程守护 | Android 12+ 已被系统限制，且“偷偷放音频”会迅速失去用户信任 |
| **终极自毁** | Panic Wipe 核按钮（含 `/api/system/wipe`，连同系统底层格式化） | 误触即灾难，不适合作为家庭文件服务器的特性 |
| **电池 Root 停充** | `BatteryProtector` 中的 `RootManager.execCommand("...charging_enabled")` 分支 | 依赖 Root，Lite 版无 Root 模块；仅保留温度告警 |
| **网络穿透** | 免费内网穿透节点遍历、自定义 SSH 隧道（连同 `jsch` 依赖一并剔除） | 引导用户自行使用 frp / ngrok / Cloudflare Tunnel 更安全稳定 |
| **局域网协议** | DLNA 媒体服务器（SSDP）、LocalSend 接收端 | 属于增值功能，TV 端直连 NAS 更稳定 |
| **多媒体增强** | 图片缩略图引擎、时光相册、影视海报墙与元数据刮削引擎 | 属于增值功能，且刮削需向外网发请求，增加服务端负载与隐私暴露面 |
| **多用户 UI** | 网页端用户管理面板、`/api/users` 接口 | 保留 `admin` / `guest` 双账号即可满足绝大多数家庭场景 |
| **其它端** | Watch 端、Apple（SwiftUI）端、Harmony（ArkTS）端、微信小程序、浏览器扩展、CLI 命令行端 | 这些端代码量小、用户量低，但在 CI 中占用等量维护成本 |

---

## 🚀 快速上手

### 方式一：从本仓库 Actions 下载构建产物
前往 **[本仓库的 Actions 页面](../../actions)**，下载最新的 `AndroidNAS-Server-v4.5.0-lite.1.apk`。

> ⚠️ **不要去完整版仓库下载 APK。** 完整版的包包含远程控制、Root 全盘、短信中心等高危模块，Lite 版的包只在本仓库发布。

### 方式二：从本仓库源码编译
```bash
git clone <本仓库地址>
cd android-nas-lite
chmod +x gradlew
./gradlew assembleRelease --no-daemon
```
> 编译环境：JDK 8 + Android SDK（compileSdk 30 / buildTools 30.0.3）。工程已配置双轨制签名，若本地未注入 keystore，会自动降级为 Debug 签名并带有 `.debug` 包名后缀，不会与正式版冲突。

---

## 🛠 兼容性矩阵

| 端 | 最低兼容系统 | 核心技术栈 |
|---|---|---|
| 服务端（Java） | Android 2.2（API 8） | 纯 Java + NanoHTTPD + 手写 Socket + 纯 Java TOTP |
| 手机客户端 | Android 5.0（API 21） | Java + Material Design 3 + OkHttp |
| TV 端 | Android 4.4（API 19） | Java + Leanback 风格卡片流 + RecyclerView |
| 桌面端 | Windows 7+ / 主流 Linux | Python 3.8+ + Tkinter |
| Web 端 | 任意现代浏览器 | HTML / CSS / JS + Material Design 3 变量 |

- **包名隔离**：Lite 版的包名统一迁移至 `com.androidnas.lite.*`，可与完整版在同一设备上多开共存。
- **版本号控制**：沿用完整版的 `sync_version.py`，支持 `<完整版版本号>-lite.<精简次数>` 后缀解析，`versionCode` 编码为 `major*100000 + minor*1000 + patch*10 + lite序号`，并自动注入 `/api/ota` 的 `__VERSION__` 占位符。

---

## ⚠️ 已知局限与风险

1. **2FA 锁死风险（高危）**：开启 2FA 后，如果 Authenticator 密钥丢失或手机损坏，**唯一补救方式是清除服务端应用数据重置**（会丢失 `nas_core.db` 数据库，但不影响存储文件）。Lite 版已在开启前强制验证一次验证码，但仍需妥善保存密钥。
2. **隐藏暗号遗忘风险**：开启隐藏入口暗号后，若忘记暗号，网页端会永远返回 404。补救方法：在手机端 App 内通过「NAS 驾驶舱」清空 `secretPath`，或清除应用数据重置。
3. **局域网锁误杀风险**：Lite 版默认开启「高危 API 局域网锁」。如果你在公网穿透环境下需要修改设置，会被 403 拦截。补救方法：先用 SSH 隧道 / VPN 接入内网，或在网页端安全面板临时关闭该锁。
4. **明文 HTTP 传输**：默认不带 TLS。请勿将 8080 / 8081 / 2121 端口直接暴露到公网。需要远程访问请自行配置 frp / ngrok 并开启 TLS。
5. **保活仅到“前台服务”级别**：在极低内存设备或激进 ROM 上仍可能被杀，重新打开 App 或重启设备后会自动恢复。
6. **Webhook 数据外发风险**：如果配置了 Webhook，NAS 的事件（文件名、温度告警等）会通过 HTTP 发送至第三方服务器（如 Telegram/企微），请确保接收端安全可信。
7. **安全沙箱限制**：由于彻底移除了 Root 全盘模式，即使是 `admin` 账号，也只能访问 `/sdcard` 范围内的文件，无法突破 Android 系统底层目录（如 `/data`、`/system`）。
8. **`/api/ports` 探针仅探本机**：网页端「服务状态大盘」的端口探测只对 `127.0.0.1` 发起连接。若 Jellyfin / Alist 等部署在另一台局域网机器上，会显示「未监听」。这是当前实现的已知边界。
9. **`/api/speedtest` 无频率限制**：网页端测速会消耗 5MB 流量，请勿频繁触发以免旧手机 WiFi 过载。

---

## 🔗 与原项目的关系

- **功能建议、Bug 报告、新特性讨论** → 请提交到 **[完整版仓库 Issues](https://github.com/styisme/android-nas/issues)**。完整版修复后，Lite 版在下一次同步中自动受益。
- **Lite 版专属讨论**（精简范围、2FA 锁死、局域网锁策略等） → 请提交到**本仓库 Issues**。

---

## 协议

本项目遵循 [MIT License](LICENSE)。