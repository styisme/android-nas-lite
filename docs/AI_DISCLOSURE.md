# AI 生成声明

本项目（Android NAS Lite）是 [完整版](https://github.com/styisme/android-nas) 的精简分支。为了开源协作的透明度，特此详细披露 AI 在本项目及上游完整版中的参与程度。

**核心声明：Lite 版 100% 的业务代码（Java、Python、HTML、YAML 等）直接来自完整版；而完整版 99% 以上的代码由 AI 直接输出。此外，Lite 版仓库自带的全部专属文档、CI 矩阵配置以及精简过程中的修复脚本，均由 AI 根据人类指令直接生成。**

人类开发者在此过程中没有手写过任何一行核心业务代码，也没有手写过任何一篇文档。

> **📌 当前文档版本：v4.4.0-lite.2**

---

## AI 参与的具体部分（Lite 版及完整版）

### 1. 完整版核心业务代码（Lite 版完整继承）
- **服务端核心安全与架构 (v4.4.0 核心重构)**：
  - `AuthManager.java`：引入 `ThreadLocal` 存储当前请求身份，废弃全局静态变量，在 `finally` 块强制清理，彻底解决并发越权漏洞。
  - `DatabaseManager.java`：强制剥离 `root_dir` 前导斜杠，配合 `getCanonicalPath` 封死沙箱逃逸；新增秒传路径校验，防止跨用户投毒；`isDefaultPassword()` 动态判定默认密码，取代静态变量。
  - `NasHttpServer.java`：引入 `LimitedInputStream` 修复视频 Range 请求的句柄泄漏；移除分片上传错误的 `parseBody` 调用；实现短效 Share Token 鉴权与 `expire` 有效期控制。
  - `WebDavServer.java` / `FtpServer.java`：纯 Socket 协议栈，并在连接关闭时强制执行身份清理。
  - `WebhookManager.java`：轻量级事件推送中枢。
- **跨平台客户端**：
  - 手机端（相册自动备份、局域网扫描与投屏、`content://` URI 分片上传）、TV 端（Leanback 卡片流）、桌面端（Tkinter UI、在线编辑、系统托盘）。
- **Web 前端**：
  - `index.html` 的全套 MD3 变量系统、SparkMD5 分片计算、PWA 路由注册，以及修复 401 无限弹窗死锁和媒体标签 401 黑屏的逻辑。
- **CI/CD 自动化**：
  - 基于 GitHub Actions 的矩阵编译、基于 Secrets 的 Keystore 签名注入、`sync_version.py` 版本号提取与注入（支持 `-lite.N` 后缀与 `versionCode` 编码）。

### 2. Lite 版专属精简与修复工作
- **Python 覆盖/精简脚本**：用于从完整版剥离高危依赖（`jsch`、`Java-WebSocket`）、删除冗余模块（远控、DLNA、刮削、Watch/CLI/Apple/Harmony 端）、清理残留代码引用。
- **正则与 CI 修复**：重写了 `build-all.yml` 中的 `grep` 正则和 `sync_version.py` 的解析逻辑，使其能够正确提取带 `-lite.N` 后缀的版本号，同时修复了 `versionCode` 转换时的崩溃问题。
- **全套专属文档**：重写了 `README.md`、`SECURITY.md`、`CONTRIBUTING.md`、`CHANGELOG.md`、`USAGE.md`、`ARCHITECTURE.md` 等，确保其准确反映 Lite 版的真实边界与安全策略。
- **精简回归修复 (`lite.1` → `lite.2`)**：针对上一轮精简产生的回归缺陷，AI 输出了完整的 Python 修复脚本，覆盖 `safeFile` 沙箱、CI YAML、包名隔离、越界依赖、`content://` 上传、版本号正则、清理线程泄漏、默认密码检测、分享有效期与无障碍描述残留等 11 处。

---

## 开发者（人类）负责的核心工作

- **产品定义与需求决策**：决定剥离哪些高风险或低使用率的功能（如远控、Root 全盘、Watch/Apple 端），维持 Lite 版“稳定、干净、可长期挂机”的定位。
- **编译与执行**：在无电脑的纯 Android 环境下，使用 Pydroid 运行 AI 生成的 Python 覆盖脚本，使用 Mgit 提交代码并推送至 GitHub。
- **真机测试与反馈**：把编译好的 APK 安装到废旧手机、电视盒子上，测试文件流转、WebDAV 映射、相册备份等核心功能是否正常，并将错误日志反馈给 AI，由 AI 输出修复补丁。
- **兼容性与编译排雷**：
  - 发现并反馈了因精简未彻底导致 CI 报 `cannot find symbol`（如 `FreeTunnelManager` 残留）的编译崩溃。
  - 敏锐捕捉并反馈了 `sync_version.py` 在计算 `4.1.1-lite.1` 的 `versionCode` 时触发 `ValueError` 的致命拦截，促使 AI 给出剥离后缀的修复代码。
  - 发现并反馈了 Markdown 中的绝对链接使用了错误的默认分支名（如 `main` 应为 `master`），导致点击出现 404 页面。
  - 发现并反馈了根目录 `.git` 被覆盖脚本误删，并配合 AI 完成了 `unrelated histories` 的历史桥接。
  - **发现并反馈了 `lite.1` 的沙箱回归**：`safeFile()` 被精简脚本回退为 `new File(rootDir, path)`，导致 `admin` / `guest` 全站 404，直接促成 `lite.2` 的整套回归修复。
- **最终验收与发版**：决定某个 Lite 版本是否达到发布标准，并手动打 Tag 发版。

---

## 已知的局限性

1. **边缘场景依赖人类测试**：AI 无法真机运行，必须依靠人类反复测试并反馈 Bug（例如不同路由器下 WebDAV 的兼容性、Android 各厂商 ROM 的后台保活表现）。
2. **精简继承的 Bug 是最大的风险源**：Lite 版对完整版的“字符串裁剪”本质上是**不可靠的**。`lite.1` → `lite.2` 就是一次真实的回归事故：AI 在精简时把 `safeFile()` 的路径拼接写回了 `new File(rootDir, path)` 的致命反模式，导致全站 404 + 越权风险；同时把 `applicationId` 回退、把 `jsch` / `Java-WebSocket` 加回来、把 `build-all.yml` 的 heredoc 破坏成裸残片。**这些回归全部通过了 CI 编译**，只有靠人类真机测试才能发现。因此每次重新精简后，都必须执行 `CONTRIBUTING.md` 的「精简高危雷区速查」grep 清单，并进行完整的编译和真机验证。
3. **CI 编译通过 ≠ 功能正确**：`lite.1` 的沙箱回归、CI YAML 崩坏、包名回退、越界依赖回流全部是“编译通过、运行崩溃或行为错误”的缺陷。CI 只能拦截语法与依赖，无法拦截精简引入的语义回归。
4. **保活能力受限**：Lite 版移除了所有的激进保活策略，仅保留前台服务和基础锁。在严苛的国产 ROM 上，后台服务被系统杀死的概率显著高于完整版。
5. **明文传输**：保留了 NanoHTTPD 的明文 HTTP 传输。若在公共网络使用，请自建 SSH 穿透进行加密。
6. **文档排版与链接的盲区**：AI 生成 Markdown 时容易写错仓库分支名或路径，需要人类在预览时进行肉眼排雷与截图反馈。
7. **前后端不一致的静默残留**：精简过程中，前端 UI 的改动（例如分享弹窗的提取码输入框）可能未被后端同步实现（`/api/share` 的 `pass` 参数）。这类“看起来能用、实际未生效”的缺陷不会被任何自动化流程拦截，只能靠文档诚实披露和后续版本补齐。

---

## 从 lite.1 → lite.2 事故中得到的教训

`4.4.0-lite.2` 是一次纯粹的回归修复版本，完整版并未发布新大版本。这次事故暴露了三个真实的结构性问题：

1. **精简脚本天然不稳定**：字符串匹配替换对代码缩进、注释、上下文极度敏感。完整版一旦改动 `safeFile()` 的实现细节，精简脚本就可能“匹配失败 → 静默跳过 → 原代码保留”，而如果原代码本身就与 Lite 版约束冲突，就会形成回归。
2. **AI 会“重新引入”被移除的东西**：完整版 `app/build.gradle` 中重新加了 `jsch` / `Java-WebSocket`，精简脚本没有精确覆盖，依赖就被原样带过来了。这就是为什么 `CONTRIBUTING.md` 里必须有 `grep` 复验清单——**AI 不会记得上一条规则，人类必须把规则写成机器可执行的检查**。
3. **诚实披露比“看起来完美”更重要**：`lite.2` 的 `CHANGELOG.md` 完整列出了 11 项回归修复，而不是悄悄打补丁发布。这种“把伤疤写进文档”的做法，是极限约束开发下唯一可持续的协作方式。

---

## 协议

本项目遵循 MIT 协议。