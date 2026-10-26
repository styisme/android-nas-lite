# Android NAS Lite 开发者贡献指南与全栈架构规范

感谢参与 Android NAS Lite 的开发！本项目是 [完整版](https://github.com/styisme/android-nas) 的精简分支，专注于“文件服务主线”。在提交任何代码之前，请仔细阅读以下核心规范、架构说明以及开发注意事项。

> **⚠️ 开发环境约束**：本项目的开发环境极度受限（无本地 IDE、无 PR 流程、直推 master）。请在提交 PR 前务必阅读 [`DEVELOPMENT_CONSTRAINTS.md`](DEVELOPMENT_CONSTRAINTS.md)，了解实际执行的门禁与测试状态。

---

## ⚠️ 关于 AI 生成代码的严正声明

本项目 99% 以上的代码（包括 Java, Python, HTML, YAML 等）由大型语言模型（LLM）直接输出，人类开发者主要担任“产品经理”与“测试工程师”。提交 PR 时请务必遵循以下原则：
1. **必须经过真机测试**：AI 极易写出大括号不匹配、Lambda 表达式受检异常未捕获、或跨线程操作 UI 的代码。请务必确保代码在你的本地或云端编译通过，且功能在物理设备上验证正常。
2. **声明使用的模型**：在 PR 描述中注明哪些部分由 AI 辅助生成（如：`fix(server): 修复 API 8 空指针闪退 (由 Claude 辅助生成)`）。
3. **禁止破坏原有极简架构**：如非必要，**严禁引入庞大的第三方依赖**。例如：
   - 不要将服务端的 NanoHTTPD 替换为 Spring Boot。
   - 不要将手机端的 OkHttp 替换为 Retrofit 或引入 RxJava。
   - 不要将桌面端的 Tkinter 替换为 PyQt 或 Electron（为了保持极低的内存占用与秒开速度）。
   - **不要为 TOTP 引入第三方库**（如 `googleauth`），`TotpManager` 已是纯 Java 手写实现。

---

## 📂 项目结构解析 (v4.5.0-lite.1 架构)

Lite 版移除了独立端点与高危模块，当前保留 5 个核心端：

```text
android-nas-lite/
├── app/                         # 📱 服务端源码 (Android 2.2+)
│   ├── src/main/java/com/androidnas/
│   │   ├── MainActivity.java            # 本地控制中心
│   │   ├── HttpServerService.java       # 核心服务容器 (统一调度 HTTP/WebDAV/FTP)
│   │   ├── NasHttpServer.java           # 核心 HTTP 引擎 (含 PWA 路由、断点续传、五重防线)
│   │   ├── WebDavServer.java            # 纯 Socket 实现的 WebDAV 协议栈
│   │   ├── FtpServer.java               # 纯 Socket 实现的 FTP 协议栈
│   │   ├── AuthManager.java             # ThreadLocal 并发隔离 + 双参数 2FA 校验
│   │   ├── TotpManager.java             # 🆕 v4.5.0 纯 Java TOTP (HMAC-SHA1, RFC 6238)
│   │   ├── SecurityFilter.java          # 防爆破 + 防 CC 限流 + 在线设备审计
│   │   ├── BatteryProtector.java        # 🆕 v4.5.0 电池温度告警 (无 Root 停充)
│   │   ├── DatabaseManager.java         # SQLite 账号与秒传记录引擎
│   │   ├── WebhookManager.java          # Webhook 事件推送引擎
│   │   ├── SettingsManager.java         # 偏好设置管理
│   │   └── LogManager.java              # 运行日志
│   └── src/main/assets/                 # MD3 网页前端与 PWA 文件
│       ├── index.html                   # 网页前端源码 (含五重防线 UI)
│       ├── manifest.json                # PWA 配置文件
│       └── sw.js                        # PWA Service Worker
│
├── client/                      # 🖥️ 手机客户端 (Java + MD3, Android 5.0+)
├── tv/                          # 📺 TV 端 (Leanback 卡片流, Android 4.4+)
├── desktop/                     # 💻 桌面端 GUI (Python + Tkinter)
│
├── .github/workflows/           # 自动化 CI/CD 矩阵 (全量发版与独立构建)
└── sync_version.py              # 🔥 版本号全自动同步核心脚本 (适配 -lite.x 后缀 + __VERSION__ 占位符)
```

> **⚠️ 已物理删除的类**（勿在 Lite 版仓库重新引入）：`SmsManager`、`RemoteDesktopManager`、`NasAccessibilityService`、`RootManager`、`AdvancedKeepAliveManager`、`DlnaServer`、`LocalSendServer`、`FreeTunnelManager`、`SshTunnelManager`、`MediaScraper`、`ThumbnailManager`、`SystemMonitor`、`SilentPlayer`、`AlarmReceiver`、`OnePixelActivity`、`RemoteService`。

---

## 🛠 编译环境隔离与依赖 (严禁混用)

不同端的工具链天差地别，请务必使用对应的环境进行编译：

| 端类型 | 核心工具链 | 本地编译命令 |
|---|---|---|
| **服务端** | JDK 8 + Gradle 6.7.1 + AGP 4.2.2 | `./gradlew assembleDebug` |
| **安卓客户端** | JDK 11 + Gradle 7.5 + AGP 7.4.2 | `cd client && ./gradlew assembleDebug` |
| **TV 端** | JDK 11 + Gradle 7.5 + AGP 7.4.2 | `cd tv && ./gradlew assembleDebug` |
| **桌面端** | Python 3.8+ (依赖 `requests`, `pystray`, `pillow`, `pyinstaller`) | `python desktop/nas_client.py` |

> 服务端为兼容 API 8，锁定了旧版 AGP 与 Gradle，**请勿升级工具链**。TV 端为了控制包体积，只引入了 `androidx.recyclerview`，**严禁**再引入 MD3 组件库。

---

## 📡 核心通信协议与底层规范

Lite 版高度依赖 HTTP，所有端的网络层必须严格遵守以下规范（完整细节请见 `docs/API_DOC.md`）。

### 1. 统一鉴权、2FA 与并发沙箱隔离 (v4.5.0 升级)

除分享链接、`/manifest.json`、`/sw.js` 外，所有 HTTP 请求必须携带 Header：
`Authorization: Basic Base64(账号:密码)`。

- **`ThreadLocal` 身份隔离**：`AuthManager` 底层废弃了全局静态变量，在 `verifyRole` 时写入 `ThreadLocal`，并在请求结束的 `finally` 块中强制调用 `clearCurrentUser()` 清理。
- **2FA 双参数校验**：`AuthManager.verifyRole(authHeader, totpCode)` 是 2FA 核心入口。`totpCode` 为 `null` 时**只校验密码**（供 FTP 等不支持自定义 Header 的协议使用，会跳过 2FA）。
  - ⚠️ **lite.1 专项禁令**：判断「是否需要提示 2FA」**严禁**写成 `AuthManager.verifyRole(auth, null) > 0`。`null` 会让 `verifyRole` 内部走 `return 0`（因为 `totpCode` 为空），导致 `2FA_REQUIRED` 永远不返回，前端永远弹不出输入框，用户**开启 2FA 后直接锁死**。
  - ✅ **正确写法**：必须调用 `AuthManager.verifyPasswordOnly(authHeader) > 0`（该方法只验密码，忽略 2FA）。
- **`X-TOTP-Code` Header**：服务端开启了 2FA 且当前账号为 `admin` 时，所有请求必须携带此 Header（6 位动态验证码），否则返回 `401` 或 `2FA_REQUIRED`。
- **强制路径校验**：`NasHttpServer.safeFile()` 会从 SQLite 读取 `root_dir`，**必须先 `replaceAll("^/+", "")` 剥离前导斜杠**，再 `new File(rootDir, rel)` 拼接，最后用 `getCanonicalPath` 校验。
  - ⚠️ **lite.2 专项禁令**：严禁写成 `new File(rootDir, path)`。Java 的 `File` 构造器遇到以 `/` 开头的第二个参数时，会**直接忽略第一个参数**，导致 `admin` 与 `guest` 全站 404，且 `getCanonicalPath` 会判定为沙箱逃逸并返回 `null`。`FtpServer.safeFile()` 同源同罪。
- **权限边界**：系统仅保留 `admin`（拥有全部存储读写权限）与 `guest`（被严格锁定在 `/Public` 目录）。
- **全盘模式已移除**：`SettingsManager.isGlobalMode()` 永远返回 `false`，无法越权访问底层目录。

### 2. 五重高级安全防线 (v4.5.0 新增)

除 2FA 外的四条防线，全部在 `NasHttpServer.serve()` 路由最前端拦截，**顺序不可调换**：

```text
① 隐藏暗号 (?s=xxx) → 非 API 请求未携带暗号 → 伪装 404
② Fail2Ban 封禁 → IP 在黑名单 → 403
③ 高危 API 局域网锁 → 公网 IP 访问 /api/settings 等 → 403
④ IP 白名单 → IP 不在白名单 → 403
⑤ 验证码 (X-Captcha) → 触发 Fail2Ban 后必须通过算数验证 → 401 CAPTCHA_FAILED
```

- **隐藏暗号**：`SettingsManager.getSecretPath()` 非空时，`uri` 不以 `/api/` 开头且未携带 `?s=` 的请求一律 404。豁免 `/share/{token}`。
- **防 CC 限流**：`SecurityFilter.isBlocked()` 内基于 `ConcurrentHashMap` 的滑动窗口计数器，单 IP 每秒超 30 次请求直接熔断 15 分钟。
- **局域网锁**：`isLanIp()` **必须用正则**精准覆盖 RFC1918 网段：
  ```java
  return ip.equals("::1") || ip.startsWith("127.0.0.1")
      || ip.startsWith("192.168.") || ip.startsWith("10.")
      || ip.matches("172\\.(1[6-9]|2[0-9]|3[01])\\..*");
  ```
  - ⚠️ **完整版历史 bug**：曾写成 `ip.startsWith("172.2")`，会误命中 `172.200.x.x` ~ `172.299.x.x` 等**公网 IP**（如 `172.217.163.46` 是 Google CDN），导致公网 IP 被误判为内网，**局域网锁完全失效**。Lite 版已用正则修复，**精简时务必同步**。
- **算数验证码**：`/api/captcha` 用 `ConcurrentHashMap` 缓存题目与答案（key 为 clientIp），校验通过后立即移除防重放。`SecurityFilter.recordFail()` 触发封禁时同时写入 `captchaMap`。

### 3. 文件流式上传与大文件断点续传 (极重要)
- **小文件 PUT 上传**：必须携带准确的 `Content-Length`。禁止使用 `Transfer-Encoding: chunked`，否则会导致 NanoHTTPD 底层无限阻塞卡死。
- **大文件分片上传**：前端通过 `File.slice()` 按 2MB 切块，携带 `hash`、`index`、`total` 请求 `/api/upload/chunk`。
- **流处理修复**：分片上传接口中**严禁**调用 `session.parseBody()`，以避免二进制数据流被 NanoHTTPD 提前拦截消费。服务端统一利用 `FileOutputStream(..., true)` 将流追加至带 Hash 后缀的临时文件，并利用 `synchronized` 解决并发合并冲突。
- **`content://` URI 陷阱**：客户端分片上传时若需重开输入流，**禁止**使用 `new File(uri.getPath())`（会抛 `FileNotFoundException`）。必须通过 `getContentResolver().openInputStream(uri)` 重开。

### 4. Webhook、PWA 与电池告警
- **PWA**：`NasHttpServer` 需拦截 `/manifest.json` 与 `/sw.js` 请求，并从 `assets` 目录流式返回。
- **Webhook**：`WebhookManager` 使用 `HttpURLConnection` 异步发送 HTTP POST。需内置对 Telegram 和企微的 Payload 格式适配。
- **电池告警**：`BatteryProtector.init(this)` 必须在 `HttpServerService.onCreate()` 中调用（**位置：`serverHttp.start()` 之后**），通过 `BroadcastReceiver` 监听 `ACTION_BATTERY_CHANGED`，超 45℃ 触发 Webhook，42℃ 以下自动解除。
  - ⚠️ **Lite 版禁令**：`BatteryProtector` 中**严禁**保留 `AdvancedKeepAliveManager.hasRoot()` 与 `RootManager.execCommand("...charging_enabled")` 的 Root 停充分支（这两个类已在 Lite 版物理删除，保留会导致 `cannot find symbol`）。

### 5. 跨端协同 UDP 协议 (8090端口)
手机端与 TV 端通过 `255.255.255.255:8090` 发送 `DISCOVER_TV` 进行广播发现，实现配置下发与一键投屏。详见 `SyncHelper.java`。

---

## 🚀 核心发版机制与 CI/CD 规范

**🚨 严禁任何人（包括 AI）手动修改各端 `build.gradle` 中的版本号。**

Lite 版采用跟随式发版机制，发版过程全自动化：

1. **同步并精简代码**：在完整版发布大版本后，重新生成 Lite 版代码。
2. **修改更新日志**：在 `CHANGELOG.md` 顶部增加新版本号（格式为 `## [4.5.0-lite.1]`）及精简说明。**完整版发新大版本时 `N=1`（重置）；完整版未发新大版本、仅调整精简范围或修复残留时，`N` 自增**。
3. **推送代码**：Push 到 `main` 或 `master` 分支。
4. **自动矩阵编译**：GitHub Actions 会检测到 `CHANGELOG.md` 的变更，触发 `build-all.yml`。并行编译 4 个 job（server, client, tv, pc）。
5. **包名与签名**：
   - Lite 版包名已迁移至 `com.androidnas.lite.*`，**严禁**回退为 `com.androidnas.all.*` 或 `com.androidnas.*`（会与完整版安装冲突）。
   - 日常构建自动加 `.debug` 后缀。
   - 全量发版触发 `assembleRelease`，使用 GitHub Secrets 注入 keystore。
6. **自动打 Tag**：CI 正则支持提取带后缀的版本号（如 `v4.5.0-lite.1`），并自动打上 Git Tag。
7. **平铺直传发布**：CI 脚本使用 `find -type f` 提取产物并重命名，平铺展示在 GitHub Release 页面。**注意**：`Extract Release Notes` 段**严禁使用 heredoc (`<<EOF`)**，必须用 `printf` 或 `{ ... } > file` 块结构——完整版 v4.5.0 曾因 heredoc 在 YAML 中缩进错乱，导致 CI 直接报 `command not found`。

### 版本号编码规则 (sync_version.py)
- **`versionName`**：保留完整的 `-lite.N` 后缀，如 `4.5.0-lite.1`。
- **`versionCode`**：编码为 `major*100000 + minor*1000 + patch*10 + lite序号`。
  - 例：`4.5.0-lite.1` → `4*100000 + 5*1000 + 0*10 + 1` = **`405001`**。
  - **注意**：`4.4.0-lite.2` 的正确编码是 `404002`，**不是** `440002`（`4*100000=400000`，不是 `440000`）。历史文档曾误写为 `440002`，请以本规则为准。
- **正则**：`##\s*\[?v?(\d+\.\d+\.\d+(?:-lite\.\d+)?)\]?`，**不允许**只匹配纯 `\d+\.\d+\.\d+`（会把 lite 后缀截断，导致 `versionName` 与 CI 产物文件名不一致）。
- **`__VERSION__` 占位符**：`/api/ota` 的响应中版本号为 `{"version":"__VERSION__"}`，`sync_version.py` 的 `update_ota_placeholder()` 会在发版时自动注入。**精简后必须验证**：`grep -n '__VERSION__' app/src/main/java/com/androidnas/NasHttpServer.java` 应只在源码中存在一处，发版产物中应被替换为真实版本。

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

**示例**：`fix(lite): 修复 safeFile 未剥离前导斜杠导致全站 404`

---

## 🧯 精简高危雷区速查 (历史踩坑归档)

精简脚本每次运行都可能把完整版的新代码“重新拉回来”，以下是已经踩过的坑，精简后必须逐条 grep 复查：

| 雷区 | 检测命令 | 修复方式 |
|---|---|---|
| `safeFile` 未剥离前导斜杠 | `grep -n 'new File(rootDir, path)' app/src/main/java/com/androidnas/*.java` | 必须先 `replaceAll("^/+", "")` |
| **2FA 死锁（`verifyRole(auth, null)`）** | `grep -n 'verifyRole.*null' app/src/main/java/com/androidnas/NasHttpServer.java` | 改用 `verifyPasswordOnly(auth)` |
| **2FA secret 未持久化** | `grep -nA3 'generateSecret' app/src/main/java/com/androidnas/NasHttpServer.java` | 生成后必须 `SettingsManager.set2FASecret(secret)` |
| **`isLanIp` 前缀误判** | `grep -n '172\\.2"' app/src/main/java/com/androidnas/NasHttpServer.java` | 改用正则 `172\\.(1[6-9]\|2[0-9]\|3[01])\\..*` |
| **`ccLimitEnabled` 前端漏 append** | `grep -n 'ccLimitEnabled' app/src/main/assets/index.html` | 必须在 `saveSettings()` 里有 `fd.append("ccLimitEnabled", ...)` |
| **`webhookTemplate` GET 缺失** | `grep -n 'webhookTemplate' app/src/main/java/com/androidnas/NasHttpServer.java` | GET Response 必须输出该字段 |
| **`BatteryProtector` 引用 Root 类** | `grep -n 'RootManager\|AdvancedKeepAliveManager' app/src/main/java/com/androidnas/BatteryProtector.java` | 必须为空，仅保留温度告警 |
| **`BatteryProtector.init()` 未调用** | `grep -n 'BatteryProtector.init' app/src/main/java/com/androidnas/HttpServerService.java` | 必须有 `BatteryProtector.init(this);` |
| `SmsManager` 回流 | `grep -rn 'SmsManager\|RECEIVE_SMS\|SEND_SMS' app/src/main/` | 物理删除类文件、3 项权限、2 个 API、UI |
| `/api/shell` / `/api/system/wipe` 回流 | `grep -rn 'api/shell\|api/system/wipe' app/src/main/java/` | 物理删除 |
| `jsch` / `Java-WebSocket` 回流 | `grep -n 'jsch\|Java-WebSocket' app/build.gradle` | 物理删除依赖行 |
| `applicationId` 回退 | `grep -rn 'applicationId' app/build.gradle client/app/build.gradle tv/app/build.gradle` | 必须是 `com.androidnas.lite.*` |
| `build-all.yml` 裸 heredoc | `bash -n .github/workflows/build-all.yml` 或 `grep -n 'EOF > ' .github/workflows/build-all.yml` | 用 `printf` 或 `{ ... } > file` 块结构 |
| `strings.xml` 无障碍残留 | `grep -n 'accessibility_service_description' app/src/main/res/values/strings.xml` | 删除整行 |
| `isDefaultPwd` 静态化回归 | `grep -n 'isDefaultPwd' app/src/main/java/com/androidnas/*.java` | 必须调用 `isDefaultPassword()` |
| `while(true)` 清理线程 | `grep -n 'while (true)' app/src/main/java/com/androidnas/HttpServerService.java` | 改为 `while (running)` |
| **越界类引用残留** | `grep -rn 'RemoteDesktopManager\|AdvancedKeepAliveManager\|FreeTunnelManager\|SshTunnelManager\|MediaScraper' app/src/main/java/` | 全部清理（含 import 与调用点），否则 `cannot find symbol` |