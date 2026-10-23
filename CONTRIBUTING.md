# Android NAS Lite 贡献指南

感谢关注 Android NAS Lite。在提交任何代码之前，请先读完这一节：**本仓库接受什么，不接受什么。**

---

## 📌 贡献分流（最重要）

Android NAS 目前分为两个并行仓库，均由 [styisme](https://github.com/styisme) 维护：

| 仓库 | 定位 | 接受什么 |
|---|---|---|
| **[android-nas](https://github.com/styisme/android-nas)**（完整版） | 10 端全功能私有云控制中枢 | **功能开发、Bug 修复、新协议、新端** |
| **android-nas-lite**（本项目） | 从完整版剥离高风险与低使用率功能后的文件服务主线 | **精简范围讨论、精简后残留修复、Lite 版文档改进** |

### 绝大多数贡献应该提交到完整版

Lite 版不独立演进。每次完整版发布大版本后，维护者会在该新版本基础上重新执行一次精简，并将结果同步推送到本仓库。因此：

- **新增功能**、**修复 Bug**、**性能优化**、**协议改进** → 请提交到 **[完整版仓库](https://github.com/styisme/android-nas)**。完整版合并后，Lite 版会在下一次同步中自动受益，无需重复提交。
- **只提交到 Lite 版仓库的改动，会在下一次"重新精简"时被完整版代码覆盖**。除非该改动本身就是精简规则的一部分。

### 本仓库接受哪些 PR

1. **精简范围调整**：你认为某个功能应当从 Lite 版一并移除，或某个已移除功能应当恢复（需给出理由）。
2. **精简残留修复**：精简后残留的编译错误、未清理的 import、失效的菜单入口、CI 矩阵中指向已删端的 job。
3. **Lite 版专属文档**：本仓库的 `README.md`、`SECURITY.md`、`CONTRIBUTING.md`、`CHANGELOG.md`。
4. **Lite 版专属 CI 修复**：只涉及本仓库 `.github/workflows/` 的配置问题。

### 本仓库不接受哪些 PR

- 新增功能、新协议、新端（→ 完整版）
- 修改 `app/`、`client/`、`tv/`、`desktop/` 下的业务逻辑（→ 完整版）
- 手动修改 `build.gradle` 中的版本号（→ 见下文「发版机制」）
- 引入新的第三方依赖

---

## ⚠️ 关于 AI 生成代码

Lite 版的业务代码 **100% 来自完整版**。完整版 99% 以上的代码（Java / Python / HTML / YAML 等）由大型语言模型（LLM）直接输出，人类开发者担任"产品经理"与"测试工程师"。完整版的 AI 声明见 [完整版仓库的 docs/AI_DISCLOSURE.md](https://github.com/styisme/android-nas/blob/master/docs/AI_DISCLOSURE.md)。

Lite 版仓库中由维护者手写的部分（精简规则、残留修复、本仓库文档）同样可能由 AI 辅助生成，但**必须经过维护者本人在真机上验证**。

提交 PR 时请遵循：

1. **必须经过真机测试**：AI 极易写出大括号不匹配、Lambda 表达式受检异常未捕获、或跨线程操作 UI 的代码。请确保代码在你的本地编译通过，且功能在物理设备上验证正常。
2. **声明使用的模型**：在 PR 描述中注明哪些部分由 AI 辅助生成（如：`fix(lite): 清理精简后残留的 DlnaServer import (由 Claude 辅助生成)`）。
3. **禁止破坏原有极简架构**：如非必要，**严禁引入庞大的第三方依赖**。
   - 不要将服务端的 NanoHTTPD 替换为 Spring Boot。
   - 不要将手机端的 OkHttp 替换为 Retrofit 或引入 RxJava。
   - 不要将桌面端的 Tkinter 替换为 PyQt 或 Electron。

---

## 📂 Lite 版项目结构

```text
android-nas-lite/
├── app/                         # 📱 服务端源码 (Android 2.2+)
│   ├── src/main/java/com/androidnas/
│   │   ├── MainActivity.java            # 本地控制中心 (文件管理 + 服务启停)
│   │   ├── HttpServerService.java       # 核心服务容器 (调度 Web / WebDAV / FTP)
│   │   ├── NasHttpServer.java           # 核心 HTTP 引擎 (NanoHTTPD 路由与鉴权)
│   │   ├── WebDavServer.java            # 纯 Socket 实现的 WebDAV 协议栈
│   │   ├── FtpServer.java               # 纯 Socket 实现的 FTP 协议栈
│   │   ├── AuthManager.java             # Basic Auth 鉴权
│   │   ├── DatabaseManager.java         # SQLite (账号 + 文件哈希)
│   │   ├── SettingsManager.java         # 端口 / 服务开关 / 极寒模式
│   │   ├── LogManager.java              # 运行日志
│   │   └── BootReceiver.java            # 开机自启
│   └── src/main/assets/index.html       # MD3 网页前端源码
│
├── client/                      # 🖥️ 手机客户端 (Java + MD3, Android 5.0+)
├── tv/                          # 📺 TV 端 (Leanback 卡片流, Android 4.4+)
├── desktop/                     # 💻 桌面端 GUI (Python + Tkinter)
│
├── .github/workflows/           # CI/CD (build-all / build-client / build-tv / build-desktop)
└── sync_version.py              # 版本号同步脚本
```

完整版的 `watch/`、`apple/`、`harmony/`、`miniprogram/`、`browser-extension/`、`cli/` 在 Lite 版中不存在。

---

## 🛠 编译环境隔离与依赖

不同端的工具链天差地别，请务必使用对应的环境进行编译：

| 端类型 | 核心工具链 | 本地编译命令 |
|---|---|---|
| **服务端** | JDK 8 + Gradle 6.7.1 + AGP 4.2.2 | `./gradlew assembleDebug` |
| **安卓客户端** | JDK 11 + Gradle 7.5 + AGP 7.4.2 | `cd client && ./gradlew assembleDebug` |
| **TV 端** | JDK 11 + Gradle 7.5 + AGP 7.4.2 | `cd tv && ./gradlew assembleDebug` |
| **桌面端** | Python 3.8+（依赖 `requests`、`pyinstaller`） | `python desktop/nas_client.py` |

> 服务端为兼容 API 8，锁定了旧版 AGP 与 Gradle，**请勿升级工具链**。

---

## 📡 核心通信协议与底层规范

Lite 版保留的协议与完整版一致，完整细节见 [完整版 docs/API_DOC.md](https://github.com/styisme/android-nas/blob/master/docs/API_DOC.md)。以下是与 Lite 版相关的关键规范：

### 1. 统一鉴权

除分享链接外，所有 HTTP 请求必须携带 Header：
`Authorization: Basic Base64(账号:密码)`。

- **`admin`**：拥有 `WRITE_EXTERNAL_STORAGE` 范围内所有目录的读写权限。
- **`guest`**：被锁定在 `/Public` 目录。
- **路径拦截**：`NasHttpServer.safeFile()` 会从 `DatabaseManager` 读取当前用户的 `root_dir`，并用 `getCanonicalPath` 校验，防止 `../` 越权。
- **全盘模式已移除**：Lite 版的 `SettingsManager.isGlobalMode()` 永远返回 `false`，`admin` 无法访问 `/data`、`/system` 等底层目录。

### 2. 文件流式上传

手机端与桌面端使用 `PUT` 方法上传文件时，**必须在 HTTP Header 中携带准确的 `Content-Length`**。禁止使用 `Transfer-Encoding: chunked`，否则会导致 NanoHTTPD 底层无限阻塞卡死。

### 3. 流式打包下载

前端 POST 包含 `paths` 字段到 `/api/download/zip`，服务端利用 `ZipOutputStream` 实时将多个文件压缩成临时 ZIP 并返回，避免内存溢出。临时 ZIP 在响应结束后删除。

### 4. 跨端协同 UDP 协议（8090 端口）

手机端与 TV 端通过 `255.255.255.255:8090` 发送 `DISCOVER_TV` 进行广播发现，实现配置下发与一键投屏。详见 `client/app/src/main/java/com/androidnas/client/SyncHelper.java` 与 `tv/app/src/main/java/com/androidnas/tv/TvSyncServer.java`。

> **完整版中的 WebSocket 远控协议（8082 端口）在 Lite 版中不存在**，请勿在 Lite 版仓库中提交相关代码。

---

## 🚀 发版机制

**🚨 严禁任何人（包括 AI）手动修改各端 `build.gradle` 中的版本号。**

Lite 版不独立发布小版本。发版流程如下：

1. **完整版发布大版本**：维护者在完整版仓库的 `CHANGELOG.md` 顶部增加新版本号（如 `## [4.2.0]`）并推送。
2. **重新精简**：维护者在完整版新版本基础上执行一次精简，将结果同步推送到本仓库。
3. **更新 Lite 版 CHANGELOG**：本仓库 `CHANGELOG.md` 顶部增加形如 `## [4.2.0-lite.1]` 的条目，记录本次精简的差异。
4. **自动矩阵编译**：本仓库的 `build-all.yml` 检测到 `CHANGELOG.md` 变更，触发 4 个 job 并行编译：`build-server`、`build-client`、`build-tv`、`build-pc`（内含 gui-linux / gui-windows 两个 matrix 项）。
5. **自动打 Tag**：CI 会解析 CHANGELOG 中的版本号（含 `-lite.N` 后缀），自动打上 `v4.2.0-lite.1` 标签。
6. **平铺直传发布**：CI 脚本使用 `find -type f` 提取所有产物并重命名，直接平铺展示在 GitHub Release 页面。

*(日常修改代码如果未动 `CHANGELOG.md`，只会触发对应端的快速独立编译，不会发版)*

---

## 📝 提交规范 (Conventional Commits)

采用 `<type>(<scope>): <subject>` 规范：

- `feat`: 新功能（**Lite 版仓库极少使用**，绝大多数功能应提交到完整版）
- `fix`: 修复 Bug（Lite 版仓库多用于修复精简残留）
- `docs`: 文档变更
- `chore`: 构建 / 依赖更新
- `refactor`: 代码重构
- `ci`: CI/CD 配置修改

**Scope 必须是以下之一**：
`server`（服务端）、`web`（网页端）、`client`（安卓客户端）、`tv`、`desktop`（桌面端）、`lite`（精简范围相关）、`ci`。

**示例**：

```text
fix(lite): 清理精简后残留的 DlnaServer import
docs(lite): README 补充 WebDAV 映射前置条件
ci(lite): build-all.yml 移除 watch job
fix(server): 修复 SystemMonitor 读取电池信息在 API 8 导致 NoSuchFieldError 闪退
```

> 若 PR 的 scope 是 `server` / `web` / `client` / `tv` / `desktop` 且属于**功能开发或 Bug 修复**，请改为提交到 [完整版仓库](https://github.com/styisme/android-nas)。