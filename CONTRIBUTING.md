# Android NAS Lite 开发者贡献指南与全栈架构规范

感谢参与 Android NAS Lite 的开发！本项目是 [完整版](https://github.com/styisme/android-nas) 的精简分支，专注于“文件服务主线”。在提交任何代码之前，请仔细阅读以下核心规范、架构说明以及开发注意事项。

## ⚠️ 关于 AI 生成代码的严正声明

本项目 99% 以上的代码（包括 Java, Python, HTML, YAML 等）由大型语言模型（LLM）直接输出，人类开发者主要担任“产品经理”与“测试工程师”。提交 PR 时请务必遵循以下原则：
1. **必须经过真机测试**：AI 极易写出大括号不匹配、Lambda 表达式受检异常未捕获、或跨线程操作 UI 的代码。请务必确保代码在你的本地或云端编译通过，且功能在物理设备上验证正常。
2. **声明使用的模型**：在 PR 描述中注明哪些部分由 AI 辅助生成（如：`fix(server): 修复 API 8 空指针闪退 (由 Claude 辅助生成)`）。
3. **禁止破坏原有极简架构**：如非必要，**严禁引入庞大的第三方依赖**。例如：
   - 不要将服务端的 NanoHTTPD 替换为 Spring Boot。
   - 不要将手机端的 OkHttp 替换为 Retrofit 或引入 RxJava。
   - 不要将桌面端的 Tkinter 替换为 PyQt 或 Electron（为了保持极低的内存占用与秒开速度）。

---

## 📂 项目结构解析 (v4.2.0-lite 架构)

Lite 版移除了独立端点与高危模块，当前保留 5 个核心端：

```text
android-nas-lite/
├── app/                         # 📱 服务端源码 (Android 2.2+)
│   ├── src/main/java/com/androidnas/
│   │   ├── MainActivity.java            # 本地控制中心
│   │   ├── HttpServerService.java       # 核心服务容器 (统一调度 HTTP/WebDAV/FTP)
│   │   ├── NasHttpServer.java           # 核心 HTTP 引擎 (含 PWA 路由、断点续传)
│   │   ├── WebDavServer.java            # 纯 Socket 实现的 WebDAV 协议栈
│   │   ├── FtpServer.java               # 纯 Socket 实现的 FTP 协议栈
│   │   ├── DatabaseManager.java         # SQLite 账号与秒传记录引擎
│   │   ├── WebhookManager.java          # Webhook 事件推送引擎
│   │   ├── SettingsManager.java         # 偏好设置管理
│   │   └── LogManager.java              # 运行日志
│   └── src/main/assets/                 # MD3 网页前端与 PWA 文件
│       ├── index.html                   # 网页前端源码
│       ├── manifest.json                # PWA 配置文件
│       └── sw.js                        # PWA Service Worker
│
├── client/                      # 🖥️ 手机客户端 (Java + MD3, Android 5.0+)
├── tv/                          # 📺 TV 端 (Leanback 卡片流, Android 4.4+)
├── desktop/                     # 💻 桌面端 GUI (Python + Tkinter)
│
├── .github/workflows/           # 自动化 CI/CD 矩阵 (全量发版与独立构建)
└── sync_version.py              # 🔥 版本号全自动同步核心脚本 (适配 -lite.x 后缀)
```

---

## 🛠 编译环境隔离与依赖 (严禁混用)

不同端的工具链天差地别，请务必使用对应的环境进行编译：

| 端类型 | 核心工具链 | 本地编译命令 |
|---|---|---|
| **服务端** | JDK 8 + Gradle 6.7.1 + AGP 4.2.2 | `./gradlew assembleDebug` |
| **安卓客户端** | JDK 11 + Gradle 7.5 + AGP 7.4.2 | `cd client && ./gradlew assembleDebug` |
| **桌面端** | Python 3.8+ (依赖 `requests`, `pyinstaller`) | `python desktop/nas_client.py` |

> 服务端为兼容 API 8，锁定了旧版 AGP 与 Gradle，**请勿升级工具链**。

---

## 📡 核心通信协议与底层规范

Lite 版高度依赖 HTTP，所有端的网络层必须严格遵守以下规范（完整细节请见 `docs/API_DOC.md`）。

### 1. 统一鉴权与沙箱隔离
除分享链接与 Webhook 外，所有 HTTP 请求必须携带 Header：
`Authorization: Basic Base64(账号:密码)`。
- **底层拦截**：`NasHttpServer.safeFile()` 会从 SQLite 中读取当前用户的 `root_dir`。
- **权限边界**：系统仅保留 `admin`（拥有全部存储读写权限）与 `guest`（被严格锁定在 `/Public` 目录）。
- **全盘模式已移除**：`SettingsManager.isGlobalMode()` 永远返回 `false`，无法越权访问底层目录。

### 2. 文件流式上传与大文件断点续传 (极重要)
- **小文件 PUT 上传**：必须携带准确的 `Content-Length`。禁止使用 `Transfer-Encoding: chunked`，否则会导致 NanoHTTPD 底层无限阻塞卡死。
- **大文件分片上传**：前端通过 `File.slice()` 按 2MB 切块，携带 `hash`、`index`、`total` 请求 `/api/upload/chunk`。服务端利用 `FileOutputStream(..., true)` 实现流式追加合并，并利用 `synchronized` 解决并发冲突。

### 3. Webhook 与 PWA 路由
- **PWA**：`NasHttpServer` 需拦截 `/manifest.json` 与 `/sw.js` 请求，并从 `assets` 目录流式返回。
- **Webhook**：`WebhookManager` 使用 `HttpURLConnection` 异步发送 HTTP POST。需内置对 Telegram 和企微的 Payload 格式适配。

### 4. 跨端协同 UDP 协议 (8090端口)
手机端与 TV 端通过 `255.255.255.255:8090` 发送 `DISCOVER_TV` 进行广播发现，实现配置下发与一键投屏。详见 `SyncHelper.java`。

---

## 🚀 核心发版机制与 CI/CD 规范

**🚨 严禁任何人（包括 AI）手动修改各端 `build.gradle` 中的版本号。**

Lite 版采用跟随式发版机制，发版过程全自动化：

1. **同步并精简代码**：在完整版发布大版本后，重新生成 Lite 版代码。
2. **修改更新日志**：在 `CHANGELOG.md` 顶部增加新版本号（格式为 `## [4.2.0-lite.1]`）及精简说明。
3. **推送代码**：Push 到 `main` 或 `master` 分支。
4. **自动矩阵编译**：GitHub Actions 会检测到 `CHANGELOG.md` 的变更，触发 `build-all.yml`。并行编译 4 个 job（server, client, tv, pc）。
5. **包名与签名**：Lite 版包名已迁移至 `com.androidnas.lite.*`。日常构建自动加 `.debug` 后缀；全量发版触发 `assembleRelease`，使用 GitHub Secrets 注入 keystore。
6. **自动打 Tag**：CI 正则支持提取带后缀的版本号（如 `v4.2.0-lite.1`），并自动打上 Git Tag。
7. **平铺直传发布**：CI 脚本使用 `find -type f` 提取产物并重命名，平铺展示在 GitHub Release 页面。

---

## 📝 提交规范 (Conventional Commits)

采用 `<type>(<scope>): <subject>` 规范：

- `feat`: 新功能（**Lite 版仓库极少使用**，绝大多数新功能应提交到完整版）
- `fix`: 修复 Bug（Lite 版多用于修复精简残留或裁剪导致的编译错误）
- `docs`: 文档变更
- `chore`: 构建/依赖更新
- `refactor`: 代码重构
- `ci`: CI/CD 配置修改

**Scope 必须是以下之一**：
`server` (服务端), `web` (网页端), `client` (安卓端), `tv`, `desktop` (桌面端), `lite` (精简范围与脚本相关), `ci`。

**示例**：`fix(lite): 清理 MainActivity 中残留的 FreeTunnelManager 引用`