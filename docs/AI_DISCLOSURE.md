# AI 生成声明

本项目（Android NAS Lite）是 [完整版](https://github.com/styisme/android-nas) 的精简分支。为了开源协作的透明度，特此详细披露 AI 在本项目及上游完整版中的参与程度。

**核心声明：Lite 版 100% 的业务代码（Java、Python、HTML、YAML 等）直接来自完整版；而完整版 99% 以上的代码由 AI 直接输出。此外，Lite 版仓库自带的全部专属文档、CI 矩阵配置以及精简过程中的修复脚本，均由 AI 根据人类指令直接生成。**

人类开发者在此过程中没有手写过任何一行核心业务代码，也没有手写过任何一篇文档。

---

## AI 参与的具体部分（Lite 版及完整版）

### 1. 完整版核心业务代码（Lite 版完整继承）
- **服务端核心**：
  - `NasHttpServer.java`：路由设计、Range 分片、ZIP 流式解压、大文件分片与秒传合并（`/api/upload/chunk`）、PWA 静态资源路由、路径沙箱拦截。
  - `WebDavServer.java` / `FtpServer.java`：纯 Socket 协议栈。
  - `DatabaseManager.java` / `AuthManager.java`：SQLite 鉴权与文件哈希记录。
  - `WebhookManager.java`：轻量级事件推送中枢（支持 TG / 企微）。
- **跨平台客户端**：
  - 手机端（相册自动备份、局域网扫描与投屏）、TV 端（Leanback 卡片流）、桌面端（Tkinter UI 与在线编辑）。
- **Web 前端**：`index.html` 的全套 MD3 变量系统、SparkMD5 分片计算、PWA `manifest.json` 与 `sw.js`。
- **CI/CD 自动化**：基于 GitHub Actions 的矩阵编译、基于 Secrets 的 Keystore 签名注入、`sync_version.py` 版本号提取与注入。

### 2. Lite 版专属精简与修复工作
- **Python 覆盖/精简脚本**：用于从完整版剥离高危依赖（`jsch`、`Java-WebSocket`）、删除冗余模块（远控、DLNA、刮削、Watch/CLI/Apple/Harmony 端）、清理残留代码引用（如 `FreeTunnelManager`）。
- **正则与 CI 修复**：重写了 `build-all.yml` 中的 `grep` 正则和 `sync_version.py` 的解析逻辑，使其能够正确提取带 `-lite.N` 后缀的版本号，同时修复了 `versionCode` 转换时的崩溃问题。
- **全套专属文档**：重写了 `README.md`、`SECURITY.md`、`CONTRIBUTING.md`、`CHANGELOG.md`、`USAGE.md` 等，确保其准确反映 Lite 版的真实边界与安全策略。

---

## 开发者（人类）负责的核心工作

- **产品定义与需求决策**：决定剥离哪些高风险或低使用率的功能（如远控、Root 全盘、Watch/Apple 端），维持 Lite 版“稳定、干净、可长期挂机”的定位。
- **编译与执行**：在无电脑的纯 Android 环境下，使用 Pydroid 运行 AI 生成的 Python 覆盖脚本，使用 Mgit 提交代码并推送至 GitHub。
- **真机测试与反馈**：把编译好的 APK 安装到废旧手机、电视盒子上，测试文件流转、WebDAV 映射、相册备份等核心功能是否正常，并将错误日志反馈给 AI，由 AI 输出修复补丁。
- **兼容性与编译排雷**：
  - 发现并反馈了因精简未彻底导致 CI 报 `cannot find symbol`（如 `FreeTunnelManager` 残留）的编译崩溃。
  - 敏锐捕捉并反馈了 `sync_version.py` 在计算 `4.1.1-lite.1` 的 `versionCode` 时触发 `ValueError` 的致命拦截，促使 AI 给出剥离后缀的修复代码。
  - 发现并反馈了根目录 `.git` 被覆盖脚本误删，并配合 AI 完成了 `unrelated histories` 的历史桥接。
- **最终验收与发版**：决定某个 Lite 版本是否达到发布标准，并手动打 Tag 发版。

---

## 已知的局限性

1. **边缘场景依赖人类测试**：AI 无法真机运行，必须依靠人类反复测试并反馈 Bug（例如不同路由器下 WebDAV 的兼容性、Android 各厂商 ROM 的后台保活表现）。
2. **精简继承的 Bug**：由于 Lite 版是对完整版的字符串裁剪，如果完整版更新了某段代码的缩进或注释，可能导致精简脚本匹配失败，进而产生残留调用。因此每次重新精简后，都必须进行完整的编译和真机验证。
3. **保活能力受限**：Lite 版移除了所有的激进保活策略，仅保留前台服务和基础锁。在严苛的国产 ROM 上，后台服务被系统杀死的概率显著高于完整版。
4. **明文传输**：保留了 NanoHTTPD 的明文 HTTP 传输。若在公共网络使用，请自建 SSH 穿透进行加密。
5. **文档排版与链接的盲区**：AI 生成 Markdown 时容易写错仓库分支名或路径，需要人类在预览时进行肉眼排雷与截图反馈。

---

## 协议

本项目遵循 MIT 协议。