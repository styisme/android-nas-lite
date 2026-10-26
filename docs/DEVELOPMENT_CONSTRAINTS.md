# 开发环境约束说明

本文档说明本仓库（Android NAS Lite）的实际开发条件与责任边界。
按实际情况勾选，不强迫所有项目都声称完成了全部流程。

> **📌 本文件在根目录与 `docs/` 目录各存一份，内容完全一致。修改时请同步两份。**

---

## 一、开发环境（按实际勾选）

- [ ] 有本地 IDE / SDK，可本地编译
- [ ] 有本地 diff 工具，可逐行审阅
- [ ] 可跑 lint / 单元测试
- [ ] 走标准 PR 流程（fork → PR → review）
- [x] **仅靠云端 CI 编译（无本地工具链）**
- [x] **仅靠移动端开发（Termux + Pydroid + Mgit）**
- [x] **走直推 master（无 PR 流程）**

> 未勾选的条目，代表**本仓库物理上无法执行**，不是"忽略了"。

---

## 二、实际执行的机制（做了什么就勾什么）

### 披露
- [x] 提供 `docs/AI_DISCLOSURE.md`，说明 AI 参与范围与比例
- [x] README 顶部链接本文件
- [x] Commit message 标注 `[ai-assisted]` / `[lite]`

### 高危功能
- [x] 高危功能默认关闭（本仓库已**物理删除**，非文档层面）
- [x] `SECURITY.md` 明确列出与完整版的安全边界差异
- [x] 敏感操作（修改密码、修改端口）需二次确认弹窗
- [x] **2FA 开启前强制验证一次 TOTP 码**（Lite 版主动加固，防锁死）

### 门禁
- [x] **CI 编译通过才发版**（这是本项目唯一的自动化门禁）
- [x] 发布产物有版本号与 tag（格式为 `v<版本>-lite.<次数>`）
- [x] `CHANGELOG.md` 记录每次变更

### 测试
- [x] **真机测试**（物理设备：旧 Android 手机 + Windows 桌面端 + TV 盒子）
- [x] **精简后 grep 复验**（按 `CONTRIBUTING.md` 的「精简高危雷区速查」17 条逐条扫描回归）
- [ ] 模拟器测试（无可用模拟器环境）
- [ ] 单元测试（项目未引入测试框架）

> 未勾选的条目，代表**本仓库没有条件做**，不是"忘了做"。

---

## 三、诚实声明

- 本仓库代码由 AI 生成，人类开发者负责需求、真机验证与发布决策。
- CI 编译通过仅代表语法与依赖正确，**不代表所有功能已在所有机型上可用**。
- 本仓库从完整版精简而来，精简过程本身是脚本化、可重现的，但精简后的代码仍需每次发版前在物理设备上验证。
- **精简回归是真实存在的风险**：`4.4.0-lite.2` 是一次典型的回归修复版本——`lite.1` 在精简时把沙箱路径拼接写回了 `new File(rootDir, path)` 的致命反模式，导致 `admin` / `guest` 全站 404 并伴随路径穿越风险。这类回归**不会被 CI 编译拦截**，只能靠真机测试发现。
- **`4.5.0-lite.1` 是第二次精简回归清理**：本次精简后，扫描发现 `MainActivity` / `HttpServerService` / `NasHttpServer` 中遗留了 6 处越界类引用（`RemoteDesktopManager` / `AdvancedKeepAliveManager` / `FreeTunnelManager` / `SshTunnelManager` / `MediaScraper`），全部通过了自动化脚本的「残留扫描」才被发现。**CI 编译前必须清零残留**，否则 `cannot find symbol` 会让整个构建崩溃。

---

## 四、敏感模块清单（按实际填写）

以下模块涉及安全、权限、数据等高危操作。按 Lite 版实际存在的模块列出：

| 模块 | 验证状态 | 说明 |
|---|---|---|
| `AuthManager.java`（ThreadLocal 并发隔离 + 双参数 2FA 校验） | ✅ 真机测试 | 包含 `verifyPasswordOnly()` 方法（Lite 版主动补充） |
| `NasHttpServer.safeFile()`（路径沙箱，`4.4.0-lite.2` 回归修复后复验） | ✅ 真机测试 | 前导斜杠剥离 + `getCanonicalPath` 校验 |
| `FtpServer.safeFile()`（路径沙箱，`4.4.0-lite.2` 回归修复后复验） | ✅ 真机测试 | 与 HTTP 端逻辑对齐 |
| `WebDavServer` / `FtpServer`（协议解析） | ✅ 真机测试 | 纯 Socket 手写协议栈 |
| `DatabaseManager.hash()`（账号鉴权与加盐） | ✅ 真机测试 | SHA-256 + 随机盐 |
| `DatabaseManager.isDefaultPassword()`（`4.4.0-lite.2` 动态判定替换静态变量） | ✅ 真机测试 | 解决进程重启后误报 |
| `SecurityFilter.java`（防爆破 + 防 CC 限流 + 白名单） | ✅ 真机测试 | v4.5.0 新增滑动窗口限流 |
| **`TotpManager.java`（v4.5.0 纯 Java TOTP 算法）** | ✅ 真机测试 | HMAC-SHA1 + Base32，RFC 6238 兼容 Google Authenticator |
| **`BatteryProtector.java`（v4.5.0 电池温度告警）** | ✅ 真机测试 | **仅保留温度告警，Root 停充已物理剔除** |
| `NasHttpServer` 五重防线拦截（隐藏暗号 / 局域网锁 / 算数验证码） | ✅ 真机测试 | `isLanIp()` 用正则精准覆盖 RFC1918 |
| `NasHttpServer` 2FA 开启前验证（`/api/2fa` POST） | ✅ 真机测试 | **Lite 版主动加固，杜绝锁死** |
| `NasHttpServer` 分享接口（`expire` 参数支持） | ✅ 真机测试 | 缺省 24 小时 |
| `.github/workflows/build-all.yml`（签名、发布、Release Notes 生成） | ✅ CI 验证通过 | `printf` 替代 heredoc，彻底杜绝语法崩坏 |

> **已移除模块（不在本仓库中）**：
> - v4.1.x~v4.4.x 移除：`RemoteDesktopManager`、`AdvancedKeepAliveManager`、`MediaScraper`、`ThumbnailManager`、`SystemMonitor`、`DlnaServer`、`LocalSendServer`、`FreeTunnelManager`、`SshTunnelManager`、`SilentPlayer`、`AlarmReceiver`、`OnePixelActivity`、`RootManager`、`NasAccessibilityService`、`RemoteService`。
> - **v4.5.0-lite.1 新增移除**：`SmsManager`（短信中心，含 `RECEIVE_SMS` / `READ_SMS` / `SEND_SMS` 三项权限与 `/api/sms/send` / `/api/sms/logs` 接口）、`BatteryProtector` 中的 Root 停充分支、`/api/system/wipe`（自毁接口）、`/api/shell`（Web Shell）。
>
> 如需上述模块的验证状态，请参阅完整版仓库。

---

## 五、我们承认这不完美

形式化的 PR 流程、逐行代码审阅、本地静态分析，这些在本仓库中**全部缺位**。
我们用「**披露 + CI 门禁 + 物理删除高危功能 + 真机验证 + 精简后 grep 复验**」承担了实质责任，而不是假装做到了所有形式责任。
这是约束下的选择，不是对标准的否定。

---

## 六、给其他极限约束开发者的建议

如果你也在极端约束下开发（无本地工具链 / 移动端 / 走直推），这套最小责任清单供参考：

1. **诚实披露 AI 参与度**（不要美化比例）
2. **高危功能默认关闭**（能删就删）
3. **能做的门禁不绕过**（有 CI 就卡 CI，没有就靠人工）
4. **测试状态如实标注**（做了就勾，没做就写"未测试"）
5. **敏感模块清单公开**（让外部能核对你测过什么）
6. **精简式维护必须有复验清单**（字符串裁剪最大的敌人是"看起来没坏"）
7. **越界引用必须自动化扫描**（4.5.0-lite.1 的 6 处残留证明：光靠人眼看不出 `RemoteDesktopManager.init(this)` 这种单行遗留）

七件事做到了，你就已经承担了人类责任。