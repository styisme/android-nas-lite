# Android NAS Lite 底层架构深度解析 (v4.5.0-lite.1)

本项目之所以能在极低兼容性（API 8）下实现极高的性能，并且保持极小的包体积，源于底层大量抛弃了第三方重型框架，采用了**纯手写 Socket、原生硬件调用、SQLite 数据库引擎、纯 Java TOTP 与极简设计**。

本文档将深度剖析 Lite 版（精简版）的核心架构设计，带你了解代码底层的运行原理。

> **📌 当前文档版本：v4.5.0-lite.1**

---

## 一、 核心容器调度与拦截器 (HttpServerService)

服务端以 `Foreground Service`（前台服务）的形式常驻内存，在内部开启了多个独立的线程池并发监听不同端口，互不干扰：
- **8080 (HTTP JSON API)**：基于 NanoHTTPD 二次封装。在路由分发前，底层加入了**五重安全防线拦截器**（隐藏暗号 → Fail2Ban → 局域网锁 → IP 白名单 → 算数验证码）、**2FA 身份校验**与**路径沙箱映射器**，处理前端鉴权、流媒体请求、PWA 静态资源分发（`manifest.json`, `sw.js`）、服务大盘探针、极限测速与大文件断点续传。
- **8081 (WebDAV)**：**纯 Socket 手写实现**。直接解析 HTTP 报文，处理 `PROPFIND` 的 XML 拼装、`PUT` 的流式二进制写入。**2FA 兼容性说明**：WebDAV 不支持自定义 Header，开启 2FA 后 `admin` 账号无法连接，需改用 `guest`。
- **2121 (FTP)**：**纯 Socket 手写实现**。处理 FTP 控制连接，支持 `PASV` 被动模式，管理数据通道。同样不支持 2FA。
- **8090 (UDP 协同)**：监听广播报文，用于 TV 端与手机端的局域网发现与配置下发。

> *(注：完整版中的 LocalSend 接收端、DLNA 投屏发现、8082 WebSocket 远控端口，以及 v4.5.0 新增的短信中心在 Lite 版中已被彻底移除。)*

### 后台清理线程 (v4.4.0-lite.2 修复，v4.5.0 延续)
`HttpServerService` 内置一个每 6 小时唤醒一次的临时分片清理线程，递归扫描 `/sdcard` 下深度 ≤ 5 层的 `.part` 残留文件，清理超过 24 小时的临时分片。
- **修复**：原始实现使用 `while(true)` 循环，服务停止后线程仍驻留。已改为 `while(running)`，并在 `InterruptedException` 时 `break`，确保 `stopService` 后线程随 `running` 标志一起退出。

### 电池告警初始化 (v4.5.0 新增)
`HttpServerService.onCreate()` 中，`serverHttp.start()` 之后调用 `BatteryProtector.init(this)`，注册 `ACTION_BATTERY_CHANGED` 广播监听，用于温度监控与 Webhook 告警。

---

## 二、 SQLite 架构与并发安全隔离 (v4.4.0 核心重构，v4.5.0 延续)

Lite 版保留 `DatabaseManager` 与 `AuthManager`，用于处理鉴权与秒传哈希，但剥离了复杂的面板 UI。在 v4.4.0 中，底层引入了企业级的安全防御：

### 1. 数据库设计
服务端首次启动时，会自动在应用沙盒内生成 `nas_core.db`，包含两张核心表：
- `users`：仅预置 `admin`（全盘只读）与 `guest`（锁定 `/Public`）两个账号。`users` 表含 `password`（SHA-256 加盐哈希）、`salt`（随机盐）、`pin`（敏感操作安全码）字段。
- `file_hashes`：记录文件 MD5 与路径的映射，用于大文件“秒传”比对。

### 2. 默认密码检测 (v4.4.0-lite.2 修复，v4.5.0 延续)
前端 `/api/status` 的 `isDefaultPwd` 字段用于触发安全警告弹窗。
- **历史缺陷**：`DatabaseManager.isDefaultPwd` 为静态变量，进程重启后立即回退为默认 `true`，即使已改密也会继续弹警告。
- **修复**：新增 `DatabaseManager.isDefaultPassword()`，每次请求实际查询 SQLite 并用 `hash("admin", salt)` 比对当前 `admin` 密码哈希，杜绝误报。静态变量保留仅为兼容旧引用。

### 3. 动态沙箱映射与防逃逸 (Dynamic Path Mapping)
所有的文件操作不再直接读写 `/sdcard`。
- **`ThreadLocal` 并发隔离**：`AuthManager` 彻底废弃了全局静态变量，引入 `ThreadLocal` 存储当前请求用户身份。在 HTTP、WebDAV、FTP 请求的 `finally` 块中强制清理，杜绝了高并发下 A 用户的请求串到 B 用户沙箱的越权漏洞。
- **前导斜杠剥离**：
  - **原理**：`new File(rootDir, rel)` 中，若 `rel` 以 `/` 开头，Java 会**直接忽略 `rootDir`**，导致最终 `File` 指向绝对路径（如 `/Public` 而非 `/sdcard/Public`）。
  - **后果**：`getCanonicalPath` 校验失败 → `safeFile()` 返回 `null` → `admin` / `guest` 全站 404，且存在越权访问风险。
  - **修复**：`NasHttpServer.safeFile()` 与 `FtpServer.safeFile()` 统一改为先 `path.replaceAll("^/+", "")` 剥离前导斜杠，再 `new File(baseDir, rel)` 拼接，最后 `getCanonicalPath` 校验。**这是 lite.1 → lite.2 最核心的回归修复。**
- **秒传防投毒**：`getPathByHash(hash, userRoot)` 增加了用户路径匹配校验，杜绝 A 用户通过相同 MD5 命中 B 用户文件，导致“假秒传”数据泄露。
- **全盘 Root 模式已移除**：`SettingsManager.isGlobalMode()` 永远返回 `false`，任何人无法通过底层突破沙盒。

---

## 三、 五重高级安全防线底座 (v4.5.0 全新架构)

完整版 v4.5.0 在 `NasHttpServer` 与 `SecurityFilter` 之间构筑了一道极深的护城河，Lite 版完整保留（剔除 Root 与短信依赖）。**纯 Java 原生实现，零第三方安全框架**。

### 1. 原生 2FA 动态口令 (TotpManager)

- **算法实现**：`TotpManager` 是纯 Java 手写的 TOTP（RFC 6238），基于 `HmacSHA1` + Base32 编解码。完全无第三方依赖，兼容 Google Authenticator / Microsoft Authenticator / 1Password 等标准验证器。
- **密钥生成**：20 字节 `SecureRandom` 随机数 → Base32 编码 → 生成标准 TOTP 密钥（如 `JBSWY3DPEHPK3PXP`）。
- **校验逻辑**：`verifyCode(secret, code)` 允许 ±1 个时间窗口的容错（±30 秒），防止客户端时钟轻微漂移导致校验失败。
- **`AuthManager` 双参数校验**：
  - `verifyRole(authHeader, totpCode)`：正常鉴权入口。`admin` 账号开启 2FA 后，若 `totpCode` 为空直接 `return 0`。
  - `verifyPasswordOnly(authHeader)`：**Lite 版主动补充的方法**。只验密码不验 2FA，专门用于 `NasHttpServer` 判断「是否需要返回 `2FA_REQUIRED`」。
  - ⚠️ **关键禁令**：判断 2FA 提示时**严禁**写成 `verifyRole(auth, null) > 0`。`null` 会让 `verifyRole` 返回 0（因为空验证码），导致 `2FA_REQUIRED` 永不返回，**前端永远弹不出输入框，用户开启 2FA 后直接锁死**。这是完整版 v4.5.0 的真实 bug，Lite 版已修复。
- **开启前验证**：`/api/2fa` POST 分支（`enable=true` 时）强制要求 `X-TOTP-Code` Header，用 `TotpManager.verifyCode` 校验通过后才真正写入 `2fa_enabled=true`。这是 Lite 版主动加固，**杜绝密钥输错导致永久锁死**。
- **密钥持久化**：`/api/2fa` GET 时若 `secret` 为空，生成新密钥后**立即调用 `SettingsManager.set2FASecret(secret)` 持久化**（完整版 v4.5.0 曾漏掉此步，导致每次 GET 生成新密钥，扫码永远不生效）。

### 2. 隐藏入口暗号 (Secret Path)

- **实现位置**：`NasHttpServer.serve()` 最前端，在 `AuthManager.verifyRole` 之前拦截。
- **规则**：若 `SettingsManager.getSecretPath()` 非空，且 `uri` 不以 `/api/` 开头、未携带 `?s=<secret>`，直接返回伪装 `404 Not Found`。
- **豁免**：`/api/*` 走 Basic Auth 鉴权，`/share/{token}` 走 Token 鉴权，均不强制暗号。
- **前端配合**：`fetch` 拦截器读取 `localStorage.nas_secret`，对所有非 API 请求自动附加 `?s=` 或 `&s=`。

### 3. 算数图形验证码 (Captcha)

- **实现位置**：`NasHttpServer` 的 `captchaMap`（`ConcurrentHashMap<String, String>`），key 为 `clientIp`，value 为答案。
- **触发时机**：`SecurityFilter.recordFail(clientIp)` 封禁 IP 时同步写入 `captchaMap`，`serve()` 入口校验。
- **校验流程**：
  1. 前端收到 `401 CAPTCHA_FAILED`，自动调用 `GET /api/captcha` 获取题目（如 `3 + 5 = ?`）。
  2. 用户算出结果，前端存入 `localStorage.nas_captcha`，请求携带 `X-Captcha` Header。
  3. 服务端从 `captchaMap` 取出答案比对，校验通过后**立即 `remove` 缓存**，防重放攻击。
- **随机源**：`Math.random()` 生成两个 0-9 的随机整数，简单但足以阻断自动化脚本。

### 4. 高危 API 局域网锁 (isLanIp)

- **实现位置**：`NasHttpServer.isLanIp(String ip)`。
- **规则**：
  ```java
  return ip.equals("::1") || ip.startsWith("127.0.0.1")
      || ip.startsWith("192.168.") || ip.startsWith("10.")
      || ip.matches("172\\.(1[6-9]|2[0-9]|3[01])\\..*");
  ```
  - **正则必要性**：`172.16.0.0/12` 是 RFC1918 的私有网段，跨越 `172.16.x.x` 到 `172.31.x.x` 共 16 个 B 类网段。**完整版历史 bug**：曾写成 `ip.startsWith("172.2")`，会误命中 `172.200.x.x` ~ `172.299.x.x` 等**公网 IP**（如 `172.217.163.46` 是 Google CDN），导致公网 IP 被误判为内网，**局域网锁完全失效**。Lite 版用正则精准修复。
- **拦截接口**：`/api/settings`、`/api/password` 等核心接口。**豁免**：`/api/list`、`/api/download`、`/api/upload` 等常规文件接口，公网穿透场景下仍可正常读写文件。

### 5. 防 CC 限流与会话防护 (SecurityFilter)

- **实现位置**：`SecurityFilter.isBlocked(String ip)`，基于 `ConcurrentHashMap<String, RateInfo>` 的滑动窗口计数器。
- **限流策略**：
  - 每个 IP 维护 `RateInfo { long windowStart; int count; }`。
  - 窗口为 1 秒，`count > 30` 时直接熔断封禁 15 分钟（与 Fail2Ban 共享 `BAN_TIME`）。
  - 可通过 `SettingsManager.isCcLimitEnabled()` 全局关闭。
- **会话超时**：前端 fetch 拦截器读取 `localStorage.nas_session_ts`，超过 `sessionTimeout` 分钟无操作自动清空 Token。
- **隐藏 Server 头**：`NasHttpServer` 对 `/` 响应注入 `Server: SecureNAS` 伪装，防指纹识别。

---

## 四、 硬件哨兵：电池温度告警 (v4.5.0 新增)

### BatteryProtector.java

- **触发源**：`HttpServerService.onCreate()` 调用 `BatteryProtector.init(this)`，注册 `ACTION_BATTERY_CHANGED` 广播监听器。
- **温度读取**：从 `BroadcastReceiver` 的 `Intent` 中读取 `BatteryManager.EXTRA_TEMPERATURE`（单位：0.1℃），除以 10 得到摄氏度。
- **告警逻辑**：
  - 温度 ≥ 45℃ 且未处于 `isCoolingDown` 状态 → 触发 Webhook：`⚠️ 电池高温警告，当前温度 XX℃`。
  - 温度 < 42℃ 且处于 `isCoolingDown` 状态 → 触发 Webhook：`✅ 电池温度恢复，当前温度 XX℃`。
  - 42℃ - 45℃ 之间为缓冲带，不触发告警，防止临界点抖动刷屏。
- **Root 停充已物理剔除**：完整版 `BatteryProtector` 中有 `AdvancedKeepAliveManager.hasRoot()` 与 `RootManager.execCommand("...charging_enabled")` 分支，用于自动切断充电。Lite 版这两个类已物理删除，精简时已剥离相关 if 分支，**仅保留温度告警**。用户需根据 Webhook 告警**手动拔电**。

---

## 五、 服务大盘与网络中枢 (v4.5.0 新增)

### 1. 端口探针大盘 (`/api/ports`)

- **实现原理**：`ExecutorService` 线程池并发对 `127.0.0.1` 发起 `Socket.connect(port, 300ms)`，超时或拒绝连接视为「未监听」。
- **探测端口**：`8080`（Web）、`8081`（WebDAV）、`2121`（FTP）、`8090`（UDP 协同）、`445`（SMB）、`8096`（Jellyfin）、`5244`（Alist）、`3000`（AdGuard Web）、`53`（AdGuard DNS）。
- **⚠️ 已知边界**：**仅探测本机**（`127.0.0.1`），不探测局域网其他设备。若 Jellyfin / Alist 部署在另一台机器上，会显示「未监听」。这是当前实现的诚实边界，Lite 版文档中已明确说明。

### 2. 极限测速引擎 (`/api/speedtest`)

- **实现原理**：`SpeedTestStream extends InputStream`，纯内存生成 0 字节填充流，不占用任何磁盘 I/O。
- **核心代码**：
  ```java
  @Override public int read(byte[] b, int off, int len) {
      if (remaining <= 0) return -1;
      int toRead = (int) Math.min(len, remaining);
      Arrays.fill(b, off, off + toRead, (byte)0);
      remaining -= toRead; return toRead;
  }
  ```
- **默认大小**：5MB（完整版 v4.5.0 已从 20MB 下调，Lite 版同步）。
- **⚠️ 限频策略**：**未加独立频率限制**，依赖「防 CC 限流」兜底（单 IP 每秒 30 次）。恶意刷测速会瞬时打满旧手机 WiFi，建议仅在需要时手动触发。这是 Lite 版保留的诚实边界。

---

## 六、 核心文件处理引擎

### 1. 大文件秒传与分片断点续传
- **秒传机制**：前端计算文件 MD5 后请求 `/api/upload/check`。服务端通过查询 SQLite 的 `file_hashes` 表（**按用户 root_dir 隔离**），若已存在则直接返回 `SECONDS_UPLOAD`，实现 0 秒极速上传。
- **分片合并**：前端将大文件切为 2MB 分片。服务端通过 `FileOutputStream(file, true)` 将分片流式追加至 `.part` 临时文件，并在文件名中加入 Hash 后缀（`fileName.hash.part`）防止并发覆盖损坏。
- **流处理修复**：移除了分片上传接口中错误的 `parseBody` 调用，避免二进制流被 NanoHTTPD 拦截消费。
- **线程安全**：在最后一片到达触发合并重命名时，使用 `synchronized` 加锁，防止并发写入导致文件损坏。
- **客户端 `content://` 陷阱**：Android 端分片上传时对 `content://` URI 曾使用 `new File(uri.getPath())` 二次打开，会抛 `FileNotFoundException`。已改为通过 `getContentResolver().openInputStream(uri)` 重开输入流。

### 2. 流式打包与视频切片
- **流式打包下载**：底层利用 `ZipOutputStream`，将多选文件分块读取并直接写入 HTTP 响应流，实现“边压边传”，杜绝手机本地临时空间的占用。
- **`LimitedInputStream` 句柄防泄漏**：重写了 Range 视频拖拽播放逻辑，彻底解决了 `RandomAccessFile` 导致的高并发句柄泄漏与视频越界崩溃。

---

## 七、 Webhook、PWA 与分享

### 1. Webhook 事件通知中枢 (WebhookManager)
- **轻量化实现**：放弃第三方 HTTP 库，使用 `HttpURLConnection` 异步发送 JSON 格式的事件通知。
- **协议适配**：底层内置对 Telegram (Form 表单) 与企业微信 (JSON 文本) 的格式适配。
- **触发场景**：文件上传完成、温度报警、异常登录、分享生成（v4.5.0 扩展）。

### 2. PWA 渐进式 Web 应用
- **Service Worker**：前端注入 `sw.js`，在用户“添加到主屏幕”后，实现对静态资源的缓存加速。
- **路由拦截**：服务端 `NasHttpServer` 补全了对 `/manifest.json` 与 `/sw.js` 的拦截，从 `assets` 目录流式读取并返回，防止 404。
- **MD3 SVG 图标**：PWA 桌面图标与网页端菜单、顶部导航全部替换为 Material Symbols 矢量图标。

### 3. 安全分享 (ShareInfo)
- **底层鉴权**：分享访问会绑定 Token 的所有者（Owner）身份，由 `AuthManager.setCurrentUser(owner)` 临时切换身份后再走 `safeFile()` 沙箱解析，确保分享不会绕过用户目录隔离。
- **有效期**：`/api/share` 支持 `expire` 参数（前端可选 1 小时 / 1 天 / 7 天 / 永久），缺省 24 小时。
- **⚠️ 已知缺口**：`ShareInfo` 无 `pass` 字段，服务端未实现提取码校验。前端分享弹窗的「提取码」输入框提交后会被忽略。这是从 lite.2 延续至 lite.1 的已知缺口，建议后续版本补齐字段与 `/share/{token}` 校验，或下掉前端输入框。

---

## 八、 极限兼容与适配策略

- **API 8 的妥协**：为了能在十年前的设备上跑，服务端 `minSdkVersion` 锁死在 8。代码中全面废弃了 Lambda 表达式，改用传统匿名内部类（避免 AGP 4.2.2 编译报错）。
- **NanoHTTPD 状态码降级**：`NanoHTTPD 2.2.0` 不包含 `429 Too Many Requests` 与 `503 Service Unavailable`，所有限流场景统一降级返回 `403 FORBIDDEN`，完美兼容且语义通顺。
- **高版本适配守卫**：针对 Android 11+ 强制接入 `MANAGE_EXTERNAL_STORAGE`；针对 Android 13+ 引入相册媒体权限；针对电池读取 API 引入 21 版本守卫。
- **Lint 致命拦截修复**：`build.gradle` 全局关闭阻塞式 Lint 检查（`lintOptions { checkReleaseBuilds false; abortOnError false }`），解决 `targetSdkVersion 22` 在 `assembleRelease` 时报错导致编译中断。

---

## 九、 基础保活策略 (Keep-Alive)

Lite 版移除了与系统对抗的流氓保活，仅保留最基础的保活三件套：
1. **前台服务与锁**：`START_STICKY` + 通知栏常驻，辅以 `WakeLock`（CPU 锁）和 `WifiLock`（Wi-Fi 高性能锁）。
2. **开机自启**：监听 `BOOT_COMPLETED` 广播。
*(注：完整版中的静音音频欺骗、1像素 Activity、AlarmManager 心跳在 Lite 版中均已被移除。)*

---

## 十、 跨端协同协议 (UDP Discovery)

设备之间通过 `255.255.255.255:8090` 发送 `DISCOVER_TV` 广播。接收方（TV）回复自身 IP。发送方拿到 IP 后，通过 HTTP POST 将密码与视频流地址直接推送到目标设备，实现秒级联动。

---

## 十一、 自动化构建与全量发版矩阵 (CI/CD)

`sync_version.py` 是整个 DevOps 流程的核心枢纽。在 Lite 版中，它经过特殊适配，能够解析带后缀的版本号（如 `4.5.0-lite.1`）。

### 1. 版本号编码规范
- **`versionName`**：保留完整 `-lite.N` 后缀，如 `4.5.0-lite.1`。
- **`versionCode`**：编码为 `major*100000 + minor*1000 + patch*10 + lite序号`。
  - 例：`4.5.0-lite.1` → `4*100000 + 5*1000 + 0*10 + 1` = **`405001`**。
  - 历史参考：`4.4.0-lite.2` → `4*100000 + 4*1000 + 0*10 + 2` = **`404002`**（**不是 `440002`**，这是早期文档误写，已更正）。
- **CHANGELOG 正则**：`##\s*\[?v?(\d+\.\d+\.\d+(?:-lite\.\d+)?)\]?`。
  - ⚠️ **禁止**只匹配 `\d+\.\d+\.\d+`。会被截断成 `4.5.0`，导致 `versionName` 与 CI Release 产物文件名不一致，且 `-lite.N` 的 `N` 无法参与 `versionCode` 编码。
- **`__VERSION__` 占位符**：`/api/ota` 的响应中版本号为 `{"version":"__VERSION__"}`，`sync_version.py` 的 `update_ota_placeholder()` 在发版时自动注入，与 `versionName` 保持同步。

### 2. 双轨制构建与签名机制
- **日常调试**：只触发对应端的独立编译（`assembleDebug`），包名自动追加 `.debug` 后缀，防止覆盖正式版。
- **全量发版**：修改 `CHANGELOG.md` 触发矩阵（`assembleRelease`）。通过 GitHub Secrets 注入 `release.keystore` 完成正式签名。未配置密钥时自动降级为 Debug 签名，避免 `Keystore file not found` 崩溃。

### 3. 高容错与平铺直传
- 脚本使用 `find -type f` 和 `|| true` 提取产物，提取后的文件会按 `AndroidNAS-*-v${VERSION}` 规范重命名。
- 所有产物直接平铺展示在 GitHub Release 页面，供用户自由下载。
- **`Extract Release Notes` 段**：**严禁使用 heredoc (`<<EOF`)**，必须用 `printf` 或 `{ ... } > file` 块结构。完整版 v4.5.0 曾因 heredoc 在 YAML 中缩进错乱导致 CI 直接报 `command not found`，Lite 版已对齐完整版的 `printf` 方案。

### 4. 应用标识隔离
为避免与完整版在手机上安装冲突，Lite 版的所有 Android 端包名统一迁移至 `com.androidnas.lite.*`（如 `com.androidnas.lite.server`）。
- **强制复查**：每次精简后必须 `grep -rn 'applicationId' app/build.gradle client/app/build.gradle tv/app/build.gradle`，确保不存在 `com.androidnas.all.*` 残留。
- **历史教训**：lite.1 精简时曾误改为 `com.androidnas.all.*`，lite.2 已恢复。**这是本轮 lite.1 精简的重点复查项**。