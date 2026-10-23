# Android NAS Lite

[![Platform](https://img.shields.io/badge/Platform-Android%20%7C%20Win%20%7C%20Linux%20%7C%20Web-brightgreen.svg)]()
[![Min SDK Server](https://img.shields.io/badge/Server_Min_SDK-API_8-blue.svg)]()
[![Min SDK Client](https://img.shields.io/badge/Client_Min_SDK-API_21-blue.svg)]()
[![Min SDK TV](https://img.shields.io/badge/TV_Min_SDK-API_19-blue.svg)]()
[![License](https://img.shields.io/badge/License-MIT-orange.svg)](LICENSE)

> 把吃灰的 Android 手机变成一个干净、稳定、可长期挂机的局域网文件服务器。

**Android NAS** 目前分为两个并行仓库，均由 [styisme](https://github.com/styisme) 维护：

| 仓库 | 定位 |
|---|---|
| **[android-nas](https://github.com/styisme/android-nas)**（完整版） | 覆盖 10 个端的全功能私有云控制中枢，含远程控制、Root 全盘、Web Shell、内网穿透 |
| **android-nas-lite**（本项目） | 从完整版中剥离"与 NAS 核心无关"的部分，只保留文件服务主线 |

两套代码同源、同作者、同一套 API 规范；区别仅在于 **Lite 版主动裁掉了高风险与低使用率的功能**。

---

## 📌 维护策略（重要）

**Lite 版不是独立演进的分支，而是"跟随式"维护：**

> **每次完整版发布大版本（`CHANGELOG.md` 顶部出现新的 `## [x.y.0]`）后，我会在该新版本基础上重新执行一次精简，并将结果同步推送到本仓库。**

这意味着：

- Lite 版的底层代码、协议实现、兼容性修复**全部来自完整版**；
- 完整版修复的 Bug、提升的性能、新增的 API **会自动进入 Lite 版**；
- 完整版的"高危功能"、"激进保活"、"多端扩张"**不会被带入 Lite 版**；
- Lite 版与最新完整版之间不会出现"代码分叉"。

如果你需要远程控制、Root 全盘、内网穿透、Watch / Apple / Harmony / 小程序 / CLI 端，请使用完整版：**[styisme/android-nas](https://github.com/styisme/android-nas)**。

---

## ✅ 保留的能力

### 📱 服务端（Android 2.2+，装在闲置手机）

- **HTTP 文件服务（8080）**：目录浏览、上传、下载、重命名、递归删除、新建文件夹、文本预览、全局搜索、Range 流媒体分片、ZIP 在线解压、局域网剪贴板。
- **WebDAV（8081）**：纯 Socket 手写协议栈，支持 Windows 资源管理器直接映射网络驱动器。
- **FTP（2121）**：纯 Socket 手写协议栈，支持 PASV 被动模式。
- **跨端协同发现（UDP 8090）**：TV 端与手机端的局域网广播发现、配置下发、一键投屏。
- **基础保活三件套**：前台服务常驻通知 + `WakeLock`（CPU）+ `WifiLock`（Wi-Fi 高性能）+ `BOOT_COMPLETED` 开机自启。
- **账号鉴权**：内置 `admin` 与 `guest` 双账号，Basic Auth。

### 🌐 Web 网页端（MD3）

- 移动端优先的 Material Design 3 文件管理器。
- 支持列表 / 网格、上传进度条、拖拽上传、多选打包 ZIP 下载、流式媒体播放、文本在线编辑、安全分享（提取码 + 有效期）。
- 系统控制台：修改密码、修改端口、开关 WebDAV / FTP / 流媒体 / 剪贴板。

### 🖥️ 手机客户端（Android 5.0+）

- 多协议支持：原生 JSON / WebDAV / FTP。
- 多设备管理、局域网一键扫描、二维码扫码添加。
- 相册自动增量备份（比对 NAS `/Photos/` 目录，只传新照片）。
- 文件列表网格 / 线性视图、多选批处理、批量下载 / 删除、在线文本编辑、分享外链调起系统分享面板、一键投屏到 TV。

### 📺 TV 电视端（Android 4.4+）

- 专为老旧电视盒子打造，纯原生无第三方库，包体极小。
- Leanback 卡片流 4 列网格，遥控器焦点移动 1.1 倍放大动画。
- 内置"全屏虚拟键盘 + 边打边搜"、接收手机配置下发与投屏指令。

### 💻 桌面端（Windows 7+ / Linux）

- 纯原生 Python + Tkinter，无内置浏览器，秒开不卡顿。
- 全局搜索、局域网剪贴板双向同步、一键复制免密外链。
- 多选并发上传、在线文本编辑、ZIP 在线解压。

---

## ❌ 已从 Lite 版移除的能力

以下功能**在完整版中仍然可用**，Lite 版主动移除：

| 分类 | 移除项 | 移除原因 |
|---|---|---|
| **远程控制** | H264 硬件编码推流、Root 截图流、WebSocket 服务（8082）、无障碍触控注入、Root `input` 注入 | 与"NAS"核心价值无关，且存在屏幕泄露与设备被完全接管的风险 |
| **高权限** | Root 全盘模式、Web Shell 终端、Magisk 特权模块生成、OOM 免死注入、冻结厂商省电服务 | 任何误操作都可能导致系统变砖或厂商服务异常，无法由维护者兜底 |
| **激进保活** | 静音音频欺骗、1 像素 Activity、AlarmManager 心跳、双进程守护 | Android 12+ 已被系统限制，且用户发现"偷偷放音频"会迅速失去信任 |
| **网络穿透** | 免费内网穿透节点遍历、自定义 SSH 隧道 | 引导用户自行使用 frp / ngrok / Cloudflare Tunnel 更安全稳定 |
| **局域网协议** | DLNA 媒体服务器（SSDP）、LocalSend 接收端 | TV 端直连 NAS 更稳；LocalSend 有官方客户端可用 |
| **多媒体增强** | 图片缩略图引擎、时光相册、影视海报墙、硬件仪表盘、文件秒传（MD5）、分片合并 | 属于增值功能而非 NAS 核心，且增加服务端负载与维护面 |
| **多用户管理** | 网页端用户管理面板、`/api/users` 接口 | 保留 admin / guest 双账号即可满足绝大多数家庭场景 |
| **其它端** | Watch 端、Apple（SwiftUI）端、Harmony（ArkTS）端、微信小程序、浏览器扩展、CLI 命令行端 | 这些端代码量小、用户量低，但在 CI 中占用等量维护成本 |

---

## 🔌 端口一览

| 端口 | 协议 | 用途 |
|---|---|---|
| `8080` | HTTP | Web 网页管理 / JSON API / 流媒体分片 / 分享短链 |
| `8081` | WebDAV | 标准 WebDAV 协议，可直接在 Windows 资源管理器映射 |
| `2121` | FTP | 原生 FTP 服务，PASV 被动模式 |
| `8090` | UDP + HTTP | 跨端协同发现、配置下发、投屏指令 |

---

## 🚀 快速上手

### 方式一：从本仓库 Actions 下载构建产物

前往 **[本仓库的 Actions 页面](../../actions)**，下载最新的 `android-nas-server-lite-v*.apk`。

> ⚠️ **不要去完整版仓库下载 APK。** 完整版的包包含远程控制、Root 全盘与 Web Shell，Lite 版的包只在本仓库发布。

安装到闲置手机后，**首次必须手动打开一次 App** 并授予存储与通知权限，之后服务才能开机自启。然后用浏览器访问 `http://手机IP:8080`。

### 方式二：从本仓库源码编译

```bash
git clone <本仓库地址>
cd android-nas-lite
chmod +x gradlew
./gradlew assembleDebug --no-daemon
```

产物位置：`app/build/outputs/apk/debug/app-debug.apk`

> 编译环境：JDK 8 + Android SDK（compileSdk 30 / buildTools 30.0.3）。服务端为兼容 API 8，工程锁定了旧版 AGP 与 Gradle，请勿升级工具链。

### 方式三：把 NAS 映射到电脑

> 前置条件：方式一或方式二已完成，服务端 App 已在手机上运行。

**WebDAV（推荐）**
1. Windows 资源管理器 → 右键「此电脑」→「映射网络驱动器」
2. 文件夹填写：`http://手机IP:8081`
3. 输入 admin / admin（或你修改后的密码）

**FTP**
1. Windows 资源管理器地址栏直接输入：`ftp://手机IP:2121`
2. 回车后输入账号密码

---

## 🛠 兼容性矩阵

| 端 | 最低兼容系统 | 核心技术栈 |
|---|---|---|
| 服务端（Java） | Android 2.2（API 8） | 纯 Java + NanoHTTPD + 手写 Socket |
| 手机客户端 | Android 5.0（API 21） | Java + Material Design 3 + OkHttp |
| TV 端 | Android 4.4（API 19） | Java + Leanback 风格卡片流 + RecyclerView |
| 桌面端 | Windows 7+ / 主流 Linux 发行版 | Python 3.8+ + Tkinter |
| Web 端 | 任意现代浏览器 | 原生 HTML / CSS / JS + Material Design 3 变量 |

- **版本号控制**：沿用完整版的 `sync_version.py`，CI 会自动读取 `CHANGELOG.md` 的版本号并注入各端工程配置。
- **服务端锁定 `targetSdk 22`**：通过系统兼容模式授予存储权限，从而在 API 8 上稳定运行，代价是无法使用 `MANAGE_EXTERNAL_STORAGE` 等现代 API。

---

## ⚠️ 已知局限与风险

1. **明文 HTTP 传输**：默认不带 TLS。局域网内可接受，**请勿将 8080 / 8081 / 2121 端口直接暴露到公网**。需要远程访问请自行配置 frp / ngrok / Cloudflare Tunnel。
2. **保活仅到"前台服务"级别**：`WakeLock` + `WifiLock` 能显著降低被系统回收的概率，但在极低内存设备或激进 ROM（如 MIUI 深度清理）上仍可能被杀。重新打开 App 或重启设备后服务会自动恢复。
3. **无 SSL 证书**：WebDAV / FTP 均为明文协议，抓包可读。敏感数据请勿在公共 Wi-Fi 下传输。
4. **单机 SQLite**：`nas_core.db` 仍用于账号与文件哈希，但 Lite 版移除了多用户管理 UI，实际只使用 `admin` / `guest` 两个预置账号。
5. **精简由维护者执行**：Lite 版不提供面向用户的一键精简工具。若你希望基于某个特定提交做自定义裁剪，请自行 fork 完整版并参考本仓库的最终状态手动删除。

---

## 🔗 两个仓库的分工

- **功能建议、Bug 报告、新特性讨论** → 提交到 **[完整版仓库 Issues](https://github.com/styisme/android-nas/issues)**。完整版修好之后，Lite 版在下一次同步中自动受益，无需重复提交。
- **Lite 版专属的讨论**（某个功能是否应当进一步移除、是否应当从完整版恢复）→ 提交到**本仓库 Issues**。

两个仓库由同一维护者跟进，Issue 之间不会来回踢皮球。

---

## 协议

本项目遵循 [MIT License](LICENSE)。