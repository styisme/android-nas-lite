# AI 生成声明

本项目（Android NAS Lite）是 [完整版](https://github.com/styisme/android-nas) 的精简分支。为了开源协作的透明度，特此详细披露 AI 在本项目及上游完整版中的参与程度。

**核心声明：Lite 版 100% 的业务代码（Java、Python、HTML、YAML 等）直接来自完整版；而完整版 99% 以上的代码由 AI 直接输出。此外，Lite 版仓库自带的全部专属文档、CI 矩阵配置以及精简过程中的修复脚本，均由 AI 根据人类指令直接生成。**

人类开发者在此过程中没有手写过任何一行核心业务代码，也没有手写过任何一篇文档。

> **📌 当前文档版本：v4.5.0-lite.1**

---

## AI 参与的具体部分（Lite 版及完整版）

### 1. 完整版核心业务代码（Lite 版完整继承）

- **服务端核心安全与架构 (v4.5.0 升级)**：
  - `NasHttpServer.java`：NanoHTTPD 路由设计、Range 分片视频流、ZIP 流式解压、剪贴板缓存、大文件分片上传与秒传合并、PWA 静态路由拦截、多用户路径拦截、**五重安全防线拦截器**（隐藏暗号 → Fail2Ban → 局域网锁 → IP 白名单 → 算数验证码）、**2FA 动态口令拦截**、**服务大盘探针 API**、**网络测速流引擎**，以及 v4.5.0 新增的 `BatteryProtector` 温度告警 `Webhook` 触发。
  - `AuthManager.java`：基于 `ThreadLocal` 的并发身份隔离，**并新增了与 TOTP 算法结合的双参数 2FA 校验逻辑**。`verifyPasswordOnly()` 为 Lite 版主动补充的方法。
  - `TotpManager.java`（**v4.5.0 新增**）：基于纯 Java 手写实现的 HMAC-SHA1 TOTP 算法（RFC 6238），Base32 编解码，兼容 Google Authenticator 等标准验证器。**零第三方依赖**。
  - `BatteryProtector.java`（**v4.5.0 新增**）：广播监听电池物理温度，超阈值触发 Webhook 报警。
  - `SecurityFilter.java`：防爆破（Fail2Ban）、**单 IP QPS 滑动窗口防 CC 限流**、IP 白名单校验与在线设备审计引擎。
  - `DatabaseManager.java`：SQLite 数据库引擎，实现多用户鉴权（SHA-256 加盐哈希）、独立根目录隔离、文件秒传哈希记录与紧急安全码存储。
  - `WebDavServer.java` / `FtpServer.java`：纯 Socket 实现的协议栈，支持突破全盘权限与 IP 白名单拦截，彻底修复了历史遗留的沙箱逃逸缺陷。
  - `CertificateUtils.java`：原生 HTTPS / WSS 自签证书动态生成器。
  - `WebhookManager.java`：轻量级 HTTP 推送中枢，支持适配 Telegram 与企业微信机器人。
  - `index.html`：全套 MD3 CSS 变量系统、响应式 UI、Bottom Sheet 交互逻辑、SparkMD5 分片计算、**五重安全防线配置面板**、**2FA 扫码绑定弹窗**、**服务状态大盘卡片**、**全量 MD3 SVG 矢量图标**、PWA 注册逻辑。
- **跨平台客户端**：
  - 手机端（相册自动备份、局域网扫描与投屏、`content://` URI 分片上传）、TV 端（Leanback 卡片流）、桌面端（Tkinter UI、在线编辑、系统托盘）。
- **CI/CD 自动化**：
  - 基于 GitHub Actions 的矩阵编译、基于 Secrets 的 Keystore 签名注入、`sync_version.py` 版本号提取与注入（支持 `-lite.N` 后缀、`versionCode` 编码与 `__VERSION__` 占位符）。
  - **废弃易导致 YAML/Bash 语法崩溃的 Here-Document，全面改用 `printf` 追加流生成 Release Notes**。

### 2. Lite 版专属精简与修复工作

- **Python 覆盖/精简脚本**：从完整版剥离高危依赖（`jsch`、`Java-WebSocket`）、删除冗余模块（远控、DLNA、刮削、Watch/CLI/Apple/Harmony 端）、清理残留代码引用。
  - **`4.5.0-lite.1` 精简脚本**：新增「残留引用扫描器」，在精简完成后自动扫描所有 `.java` 文件，检查是否仍引用了已物理删除的类（`RemoteDesktopManager` / `AdvancedKeepAliveManager` / `FreeTunnelManager` / `SshTunnelManager` / `MediaScraper` / `SmsManager` 等）。**本次共发现 6 处越界引用，全部精准清理。**
  - **`4.4.0-lite.2` 精简脚本**：覆盖 `safeFile` 沙箱、CI YAML、包名隔离、越界依赖、`content://` 上传、版本号正则、清理线程泄漏、默认密码检测、分享有效期与无障碍描述残留等 11 处回归修复。
- **正则与 CI 修复**：重写 `build-all.yml` 中的 `grep` 正则和 `sync_version.py` 的解析逻辑，使其能够正确提取带 `-lite.N` 后缀的版本号，同时修复 `versionCode` 转换时的崩溃问题，并新增 `__VERSION__` 占位符注入。
- **全套专属文档**：重写 `README.md`、`SECURITY.md`、`CONTRIBUTING.md`、`CHANGELOG.md`、`USAGE.md`、`ARCHITECTURE.md`、`API_DOC.md`、`DEVELOPMENT_CONSTRAINTS.md` 等，确保其准确反映 Lite 版的真实边界与安全策略。

### 3. Lite 版主动修复完整版遗留 Bug（v4.5.0-lite.1 新增）

这些不是精简回归，而是**完整版 v4.5.0 自身存在的 bug**，AI 在 Lite 版精简过程中主动发现并修复：

- **2FA 开启前 TOTP 验证（防锁死）**：完整版 `toggle2FA` 允许用户直接点确定开启 2FA，**不验证密钥是否正确**。密钥输错一个字符，用户就会永久锁死。Lite 版在前端 `toggle2FA` 加一次「输入当前 6 位验证码」校验，服务端 `/api/2fa` POST 分支强制 `X-TOTP-Code` 头，`TotpManager.verifyCode()` 校验通过才写入 `2fa_enabled=true`。
- **`showSessions` 空列表提示失效**：完整版 `msg || "暂无在线设备"` 永远为 truthy（`msg` 初始值为 `"在线设备:\n"`），空列表时提示永远不显示。Lite 版改为 `data.length === 0 ? "暂无在线设备" : msg`。
- **`webhookTemplate` GET 字段缺失**：完整版 `/api/settings` GET Response 未输出 `webhookTemplate` 字段，用户配置的自定义模板在重开设置面板时会被静默清空。Lite 版补全字段并做 JSON 转义。

---

## 开发者（人类）负责的核心工作

- **产品定义与需求决策**：决定剥离哪些高风险或低使用率的功能（如远控、Root 全盘、短信中心、Watch/Apple 端），维持 Lite 版“稳定、干净、可长期挂机”的定位。
- **编译与执行**：在无电脑的纯 Android 环境下，使用 Pydroid 运行 AI 生成的 Python 覆盖脚本，使用 Mgit 提交代码并推送至 GitHub。
- **真机测试与反馈**：把编译好的 APK 安装到废旧手机、电视盒子上，测试文件流转、WebDAV 映射、相册备份、**2FA 绑定与登录**、**隐藏暗号生效**、**电池温度告警**等核心功能是否正常，并将错误日志反馈给 AI，由 AI 输出修复补丁。
- **兼容性与编译排雷**：
  - 发现并反馈了因精简未彻底导致 CI 报 `cannot find symbol`（如 `FreeTunnelManager` 残留）的编译崩溃。
  - 敏锐捕捉并反馈了 `sync_version.py` 在计算 `4.1.1-lite.1` 的 `versionCode` 时触发 `ValueError` 的致命拦截。
  - 发现并反馈了 Markdown 中的绝对链接使用了错误的默认分支名（如 `main` 应为 `master`）。
  - 发现并反馈了根目录 `.git` 被覆盖脚本误删，并配合 AI 完成了 `unrelated histories` 的历史桥接。
  - **发现并反馈了 `lite.1` 的沙箱回归**（4.4.0 时期）：`safeFile()` 被精简脚本回退为 `new File(rootDir, path)`，导致 `admin` / `guest` 全站 404，直接促成 `lite.2` 的整套回归修复。
  - **确认了完整版 v4.5.0 修复 6 项致命 Bug 后，才允许 Lite 版精简**：2FA 死锁、Secret 未持久化、`showSettings` 读错接口、`ccLimitEnabled` 前端漏 append、`isLanIp` 172.2 前缀误判、`BatteryProtector.init` 未调用。
  - **完整版仍未修但 Lite 版主动修复**：`toggle2FA` 开启前未验证（锁死风险）、`showSessions` 空列表提示、`webhookTemplate` GET 缺失。
- **最终验收与发版**：决定某个 Lite 版本是否达到发布标准，并手动打 Tag 发版。

---

## 已知的局限性

1. **边缘场景依赖人类测试**：AI 无法真机运行，必须依靠人类反复测试并反馈 Bug（例如 H264 推流在不同 SOC 上的兼容性、2FA 在不同 Authenticator 应用上的表现、SQLite 多线程并发锁表现）。
2. **精简继承的 Bug 是最大的风险源**：Lite 版对完整版的“字符串裁剪”本质上是**不可靠的**。
   - `4.4.0-lite.1` → `4.4.0-lite.2` 的回归事故：`safeFile()` 路径拼接回退、`applicationId` 回退、`jsch` / `Java-WebSocket` 加回来、`build-all.yml` heredoc 破坏成裸残片。
   - `4.5.0-lite.1` 精简过程中同样出现 **6 处越界类引用**（`RemoteDesktopManager` / `AdvancedKeepAliveManager` / `FreeTunnelManager` / `SshTunnelManager` / `MediaScraper`），需要靠 `4.5.0-lite.1` 新增的「残留扫描器」才被发现。
   - **这些回归全部通过了 CI 编译**，只有靠真机测试或自动化扫描才能发现。因此每次重新精简后，都必须执行 `CONTRIBUTING.md` 的「精简高危雷区速查」17 条 grep 清单，并进行完整的编译和真机验证。
3. **CI 编译通过 ≠ 功能正确**：沙箱回归、CI YAML 崩坏、包名回退、越界依赖回流全部是“编译通过、运行崩溃或行为错误”的缺陷。CI 只能拦截语法与依赖，无法拦截精简引入的语义回归。
4. **保活能力受限**：Lite 版移除了所有的激进保活策略，仅保留前台服务和基础锁。在严苛的国产 ROM 上，后台服务被系统杀死的概率显著高于完整版。
5. **明文传输**：保留了 NanoHTTPD 的明文 HTTP 传输。若在公共网络使用，请自建 SSH 穿透进行加密。
6. **文档排版与链接的盲区**：AI 生成 Markdown 时容易写错仓库分支名或路径，需要人类在预览时进行肉眼排雷与截图反馈。
7. **前后端不一致的静默残留**：精简过程中，前端 UI 的改动（例如分享弹窗的提取码输入框）可能未被后端同步实现（`/api/share` 的 `pass` 参数）。这类“看起来能用、实际未生效”的缺陷不会被任何自动化流程拦截，只能靠文档诚实披露和后续版本补齐。
8. **完整版仍有未修 bug 被 Lite 版主动修复**：2FA 锁死风险、`showSessions` 空列表提示、`webhookTemplate` GET 缺失，这 3 项在完整版 v4.5.0 中仍存在。**如果完整版用户也遇到这些 bug，应引导他们升级到 Lite 版，或等待完整版后续修复。**（这是 Lite 版“更安全”的具体体现之一，也是用户选择 Lite 版的真实理由。）

---

## 从两次精简回归中得到的教训

### 第一次：`4.4.0-lite.1` → `4.4.0-lite.2`

`4.4.0-lite.2` 是一次纯粹的回归修复版本，完整版并未发布新大版本。这次事故暴露了三个真实的结构性问题：

1. **精简脚本天然不稳定**：字符串匹配替换对代码缩进、注释、上下文极度敏感。完整版一旦改动 `safeFile()` 的实现细节，精简脚本就可能“匹配失败 → 静默跳过 → 原代码保留”，而如果原代码本身就与 Lite 版约束冲突，就会形成回归。
2. **AI 会“重新引入”被移除的东西**：完整版 `app/build.gradle` 中重新加了 `jsch` / `Java-WebSocket`，精简脚本没有精确覆盖，依赖就被原样带过来了。这就是为什么 `CONTRIBUTING.md` 里必须有 `grep` 复验清单——**AI 不会记得上一条规则，人类必须把规则写成机器可执行的检查**。
3. **诚实披露比“看起来完美”更重要**：`lite.2` 的 `CHANGELOG.md` 完整列出了 11 项回归修复，而不是悄悄打补丁发布。这种“把伤疤写进文档”的做法，是极限约束开发下唯一可持续的协作方式。

### 第二次：`4.5.0-lite.1` 精简过程中

`4.5.0-lite.1` 是完整版大版本升级后的首次精简，本次暴露的问题：

1. **大版本升级会带来“新引入”的高危功能**：完整版 v4.5.0 新增了短信中心（`SmsManager` + 3 项权限 + 2 个 API + UI）、电池 Root 自动停充、`/api/system/wipe` 自毁接口。这些都需要在精简时逐项物理删除，否则会悄悄流入 Lite 版。
2. **越界引用靠人眼完全无法识别**：`RemoteDesktopManager.init(this)` 这种单行调用，在几百行的 `MainActivity` 里肉眼扫不到。必须靠自动化扫描器（如 `4.5.0-lite.1` 新增的「残留引用扫描器」）才能发现。**AI 生成的精简脚本，也必须是「AI 生成 + 自动化验证」的闭环。**
3. **完整版本身的 bug 需要人类先修才能精简**：完整版 v4.5.0 自身带着 6 项致命 Bug（2FA 死锁、Secret 未持久化等）。人类在完整版修完并推 commit 后，才能基于修复后的版本做 Lite 精简。**这要求人类既是产品经理，也是完整版的 beta 测试员。**
4. **AI 主动修复完整版遗留 bug 是双刃剑**：Lite 版修了完整版没修的 3 个 bug（`toggle2FA` 验证、`showSessions` 空列表、`webhookTemplate` GET），这提高了 Lite 版的安全性，但也意味着 Lite 版与完整版的代码分叉在加大。**下一轮完整版发大版本时，如果完整版自己也修了这 3 个 bug，合并可能产生冲突。**

---

## 协议

本项目遵循 MIT 协议。