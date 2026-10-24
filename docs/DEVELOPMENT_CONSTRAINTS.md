# 开发环境约束说明

本文档说明本仓库（Android NAS Lite）的实际开发条件与责任边界。

---

## 一、开发环境

- [ ] 有本地 IDE / SDK，可本地编译
- [ ] 有本地 diff 工具，可逐行审阅
- [ ] 可跑 lint / 单元测试
- [ ] 走标准 PR 流程（fork → PR → review）
- [x] **仅靠云端 CI 编译（无本地工具链）**
- [x] **仅靠移动端开发（Termux + Pydroid + Mgit）**
- [x] **走直推 master（无 PR 流程）**

---

## 二、我们实际执行的机制

### 披露

- [x] 提供 `docs/AI_DISCLOSURE.md`，说明 AI 参与范围与比例
- [x] README 顶部链接本文件
- [x] Commit message 标注 `[ai-assisted]` / `[lite]`

### 高危功能

- [x] 高危功能默认关闭（本仓库已**物理删除**，非文档层面）
- [x] `SECURITY.md` 明确列出与完整版的安全边界差异
- [x] 敏感操作（修改密码、修改端口）需二次确认弹窗

### 门禁

- [x] **CI 编译通过才发版**（这是本项目唯一的自动化门禁）
- [x] 发布产物有版本号与 tag（格式为 `v<版本>-lite.<次数>`）
- [x] `CHANGELOG.md` 记录每次变更

### 测试

- [x] **真机测试**（物理设备：旧 Android 手机 + Windows 桌面端 + TV 盒子）
- [ ] 模拟器测试（无可用模拟器环境）
- [ ] 单元测试（项目未引入测试框架）

---

## 三、诚实声明

- 本仓库代码由 AI 生成，人类开发者负责需求、真机验证与发布决策。
- CI 编译通过仅代表语法与依赖正确，**不代表所有功能已在所有机型上可用**。
- 本仓库从完整版精简而来，**精简过程本身是脚本化、可重现的**，但精简后的代码仍需每次发版前在物理设备上验证。

---

## 四、敏感模块清单

以下模块涉及安全、权限、数据等高危操作。按 Lite 版实际存在的模块列出：

| 模块 | 验证状态 |
|---|---|
| `AuthManager.java`（ThreadLocal 并发隔离） | ✅ 真机测试 |
| `NasHttpServer.safeFile()`（路径沙箱） | ✅ 真机测试 |
| `WebDavServer` / `FtpServer`（协议解析） | ✅ 真机测试 |
| `DatabaseManager.hash()`（账号鉴权） | ✅ 真机测试 |
| `.github/workflows/*.yml`（签名与发布） | ✅ CI 验证通过 |

> 已移除模块（不在本仓库中）：`RemoteDesktopManager`、`AdvancedKeepAliveManager`、`MediaScraper`、`ThumbnailManager`、`SystemMonitor`、`DlnaServer`、`LocalSendServer`、`FreeTunnelManager`、`SshTunnelManager`、`SilentPlayer`、`AlarmReceiver`、`OnePixelActivity`。
> 如需上述模块的验证状态，请参阅完整版仓库。

---

## 五、我们承认这不完美

形式化的 PR 流程、逐行代码审阅、本地静态分析，这些在本仓库中**全部缺位**。

我们用「**披露 + CI 门禁 + 物理删除高危功能 + 真机验证**」承担了实质责任，而不是假装做到了所有形式责任。

这是约束下的选择，不是对标准的否定。

---

## 六、给其他极限约束开发者的建议

如果你也在极端约束下开发（无本地工具链 / 移动端 / 走直推），这套最小责任清单供参考：

1. **诚实披露 AI 参与度**（不要美化比例）
2. **高危功能默认关闭**（能删就删）
3. **能做的门禁不绕过**（有 CI 就卡 CI，没有就靠人工）
4. **测试状态如实标注**（做了就勾，没做就写"未测试"）
5. **敏感模块清单公开**（让外部能核对你测过什么）

五件事做到了，你就已经承担了人类责任。