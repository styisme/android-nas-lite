# Android NAS Lite 开发者 API 接口规范

本项目（Android NAS Lite）是 [完整版](https://github.com/styisme/android-nas) 的精简分支。Lite 版的所有端（Web、手机客户端、TV、桌面端）均通过统一的 HTTP JSON API 与局域网内的服务端通信。

由于 Lite 版移除了远程控制、多用户管理、短信中心、多媒体刮削等扩展模块，因此**仅提供完整版 API 的子集**。完整版的全部接口（包括 WebSocket 远控协议、短信 API）请参阅 [完整版 API 规范](https://github.com/styisme/android-nas/blob/master/docs/API_DOC.md)。

以下是 Lite 版保留的完整接口规范说明（基于 **v4.5.0-lite.1**）。

---

## 📡 基础约定

- **Base URL**：`http://<ip>:8080`
- **Content-Type**：
  - 常规请求：`application/x-www-form-urlencoded` 或 `application/json`
  - 文件上传：`multipart/form-data` 或 `application/octet-stream`
- **鉴权方式 (Auth)**：
  - 除分享链接、`/manifest.json`、`/sw.js` 外，所有 HTTP 请求必须携带 Header：`Authorization: Basic Base64(账号:密码)`。
  - Lite 版仅内置 `admin` 与 `guest` 双账号（默认 `admin:admin`）。
  - 鉴权失败将返回 `401 Unauthorized`。
- **并发安全与沙箱隔离 (v4.4.0 核心升级，v4.5.0 延续)**：
  - 底层使用 `ThreadLocal` 存储当前请求身份，请求结束时在 `finally` 块强制清理，彻底杜绝高并发下的越权串号。
  - `guest` 账号的路径会被底层强制映射并锁定在 `/Public` 目录内。
  - **`safeFile()` 路径映射（v4.4.0-lite.2 回归修复）**：所有路径必须先经 `replaceAll("^/+", "")` 剥离前导斜杠，再 `new File(rootDir, rel)` 拼接，最后用 `getCanonicalPath` 校验。
    - ⚠️ **禁止写法**：`new File(rootDir, "/abs/path")`。Java `File` 构造器遇到绝对路径的第二个参数时**会直接忽略第一个参数**，导致 `admin` / `guest` 全站 404，且会被 `getCanonicalPath` 判定为沙箱逃逸。`FtpServer.safeFile()` 同源同罪。
- **响应格式**：除流媒体下载、纯文本预览外，所有响应均为 JSON 格式。

### 🔐 五重安全防线 Header (v4.5.0 新增)

在原有 Basic Auth 之上，Lite 版保留完整版的五重安全防线，客户端需在特定场景下额外携带 Header：

| Header | 触发条件 | 说明 |
|---|---|---|
| `X-TOTP-Code` | 服务端开启了 2FA 且当前账号为 `admin` | 6 位动态验证码（TOTP，RFC 6238）。缺失时返回 `401 2FA_REQUIRED`。 |
| `X-Captcha` | 触发 Fail2Ban 后 | 前端先调 `/api/captcha` 拿算数题，算出结果后携带此 Header。缺失时返回 `401 CAPTCHA_FAILED`。 |

### 🕶️ 隐藏入口暗号 (v4.5.0 新增)

- 若服务端开启了「隐藏入口」，**所有非 API 请求**（`/`、`/index.html`、静态资源等）必须在 URL 后附加 `?s=你的暗号`。
- 未携带暗号的非 API 请求会统一返回伪装 `404 Not Found`，防扫站脚本探测。
- **豁免**：`/api/*` 走 Basic Auth，`/share/{token}` 走独立 Token 鉴权，均不强制暗号。

### 🚨 状态码约定 (受 NanoHTTPD 2.2.0 限制)

- `401`：鉴权失败、2FA 缺失、验证码失败。
- `403`：IP 白名单拦截、Fail2Ban 封禁、CC 限流熔断、**高危 API 局域网锁拒绝公网 IP**。
- `404`：资源不存在、隐藏暗号未携带（伪装）。
- `500`：服务端内部错误。
- ⚠️ **不支持 `429 Too Many Requests` 与 `503 Service Unavailable`**，所有限流场景统一降级为 `403`。

---

## 📁 一、 文件与目录管理接口

### 1. 获取目录列表
- **URL**: `/api/list`
- **Method**: `GET`
- **Params**: `path` (String, 必填) - 目标目录的绝对路径，如 `/` 或 `/Movies/`。
- **Response**: `200 OK`
```json
{
  "path": "/Movies/",
  "files": [
    {
      "name": "Inception.mp4",
      "dir": false,
      "size": 1024000
    }
  ]
}
```

### 2. 文件下载与流媒体分片
- **URL**: `/api/download`
- **Method**: `GET`
- **Params**: `path` (String, 必填) - 文件的绝对路径。
- **Headers**: 可携带 `Range: bytes=0-1024` 用于视频进度条拖动。
- **Response**:
  - 若携带 Range，返回 `206 Partial Content`，并带有 `Content-Range` 头。
  - 若未携带，返回 `200 OK`，并带有 `Content-Disposition` 触发下载。
  - **底层安全**：v4.4.0 引入了 `LimitedInputStream`，彻底修复了高并发下的句柄泄漏与越界崩溃。

### 3. 表单多文件上传 (Web端使用)
- **URL**: `/api/upload`
- **Method**: `POST`
- **Body**: `multipart/form-data` 格式。需包含 `path` 字段（目标目录）与一个或多个 `file` 字段。
- **Response**: `200 OK`，返回字符串 `OK`。

### 4. 客户端流式 PUT 上传 (手机端/桌面端使用)
- **URL**: `/*` (任意文件路径，如 `/Movies/new.mp4`)
- **Method**: `PUT`
- **Body**: 纯二进制流 (`application/octet-stream`)。
- **注意**：客户端必须携带准确的 `Content-Length`。如果服务端写入异常，会触发 Drain 机制并返回明确状态码。

### 5. 大文件秒传与分片上传 (v4.4.0 修复)
- **秒传校验**:
  - `POST /api/upload/check`
  - Body: `hash=文件MD5`
  - 返回: `SECONDS_UPLOAD`（秒传成功，服务端已有该文件）或 `NEED_UPLOAD`（需要继续上传）。
  - **防投毒**：服务端会校验该 Hash 的路径归属（`getPathByHash(hash, userRoot)`），防止跨用户命中。
- **分片上传**:
  - `POST /api/upload/chunk`
  - Params: `hash=文件MD5`, `index=当前分片序号`, `total=总分片数`, `name=文件名`, `path=目标目录`
  - Body: 纯二进制分片数据（通常为 2MB 块）。
  - **修复说明**：服务端已移除 `parseBody` 调用，避免二进制流被 NanoHTTPD 拦截消费。临时文件命名为 `fileName.hash.part`，彻底防止并发覆盖损坏。
  - **客户端约束**：Android 端分片上传时若需重开输入流，**禁止**使用 `new File(uri.getPath())`（对 `content://` URI 会抛 `FileNotFoundException`），必须通过 `getContentResolver().openInputStream(uri)` 重开。

### 6. 多选流式打包下载
- **URL**: `/api/download/zip`
- **Method**: `POST`
- **Body**: `multipart/form-data` 或 `x-www-form-urlencoded`。
  - 需包含 `paths` 字段，多个路径用 `|` 分隔（如 `/a.txt|/b.jpg`）。
- **Response**: `200 OK`，服务端实时压缩并返回 `application/zip` 数据流。临时 ZIP 在响应结束后自动删除。

### 7. 获取文本预览
- **URL**: `/api/preview`
- **Method**: `GET`
- **Params**: `path` (String, 必填)
- **Response**: `200 OK`，返回纯文本内容。限制 2MB 以内。

### 8. 新建文件夹 / 重命名 / 递归删除 / 在线保存
- **新建文件夹**: `POST /api/mkdir`，Body: `path=/当前目录&name=新目录名`
- **重命名**: `POST /api/rename`，Body: `path=/旧路径&newName=新名字`
- **递归删除**: `POST /api/delete`，Body: `path=/目标路径`
- **在线保存文本**: `POST /api/save`，Body: `path=/文件路径&content=新的内容`（服务端会先写入 `.bak_<时间戳>` 备份）

### 9. 在线解压 ZIP
- **URL**: `/api/unzip`
- **Method**: `POST`
- **Body**: `path=/目标zip文件路径.zip`
- **Response**: `200 OK`（内置了 `getCanonicalPath` 穿越校验，解压安全）

---

## 🔗 二、 分享接口

### 1. 生成安全分享链接
- **URL**: `/api/share`
- **Method**: `POST`
- **Body**:
  - `path`: 必填，文件路径。
  - `expire`: 选填，有效期毫秒数（如 `86400000` 表示 1 天）。缺省为 24 小时。
- **Response**: `200 OK`，返回 16 位随机 Token。
- **底层鉴权**：分享访问会绑定 Token 的所有者（Owner）身份进行沙箱路径解析。

> ⚠️ **已知限制 (v4.5.0-lite.1 仍未修)**：前端 `index.html` 的分享弹窗仍保留「提取码」输入框，但**服务端当前未实现 `pass` 参数校验**，提交的提取码会被忽略。这是精简时遗留的前后端不一致，已在 `CHANGELOG` 记录。在修复前，请勿将带提取码的分享链接视为已加密。建议客户端生成分享时使用「短有效期」（1 小时）并仅通过受信渠道分发。

### 2. 访问分享
- **URL**: `/share/{token}`
- **Method**: `GET`
- **Response**: 校验通过后，返回文件流。Token 过期或被移除时返回 `403` / `404`。

---

## 🔍 三、 搜索与工具接口

### 1. 全局递归搜索
- **URL**: `/api/search`
- **Method**: `GET`
- **Params**: `q` (String, 必填) - 搜索关键字。
- **Response**: `200 OK`，返回包含 `name`, `path`, `dir`, `size` 等属性的 JSON 数组。最多返回 200 条，递归深度上限 4 层。

### 2. 局域网剪贴板
- **获取**: `GET /api/clipboard` -> 返回纯文本内容。
- **设置**: `POST /api/clipboard`，Body: `content=要同步的文字`。

### 3. 算数图形验证码 (v4.5.0 新增)
- **URL**: `/api/captcha`
- **Method**: `GET`
- **Response**: `200 OK`。当触发防爆破（Fail2Ban）时调用，返回一道算数题。
```json
{
  "q": "3 + 5 = ?",
  "id": "8"
}
```
- **说明**：前端算出结果后，后续请求需在 `X-Captcha` Header 中携带结果，服务端校验通过后立即移除缓存，防重放攻击。
- **触发场景**：连续输错密码 5 次触发 Fail2Ban 后，前端自动弹出算数题。

---

## 📊 四、 服务大盘与测速接口 (v4.5.0 新增)

### 1. 端口探针大盘
- **URL**: `/api/ports`
- **Method**: `GET`
- **Response**: `200 OK`。多线程并发探测本机各项服务的存活状态与 TCP 连接延迟。
```json
[
  { "name": "Web 控制台", "port": 8080, "status": true, "latency": 2 },
  { "name": "WebDAV", "port": 8081, "status": true, "latency": 1 },
  { "name": "Jellyfin", "port": 8096, "status": false, "latency": 0 }
]
```
- **探测范围**：`ExecutorService` 线程池并发探测 **`127.0.0.1`（仅本机）**，300ms 超时。**不探测局域网其他设备**。若 Jellyfin / Alist 部署在另一台机器上，会显示「未监听」。
- **探测端口列表**：`8080`（Web）、`8081`（WebDAV）、`2121`（FTP）、`8090`（UDP 协同）、`445`（SMB）、`8096`（Jellyfin）、`5244`（Alist）、`3000`（AdGuard Web）、`53`（AdGuard DNS）。

### 2. 局域网极限测速
- **URL**: `/api/speedtest`
- **Method**: `GET`
- **Params**: `size` (Integer, 选填) - 数据流大小（MB），默认 **5**。
- **Response**: `200 OK`，返回 `application/octet-stream` 的 0 字节流，用于前端计算局域网下行速率。
- **底层实现**：`SpeedTestStream extends InputStream`，纯内存生成 0 字节填充流，不占用磁盘 I/O。
- **注意**：完整版 4.5.0 默认值已从 20MB 下调至 5MB，但 Lite 版**未加独立频率限制**，依赖「防 CC 限流」兜底（单 IP 每秒 30 次）。恶意刷测速会瞬时打满旧手机 WiFi，建议仅在需要时手动触发。

---

## ⚙️ 五、 系统与控制台接口

### 1. 系统状态查询
- **URL**: `/api/status`
- **Method**: `GET`
- **Response**: `200 OK`，返回运行状态 JSON。**(v4.4.0-lite.2 起 `isDefaultPwd` 改为动态判定，v4.5.0-lite.1 延续)**
```json
{
  "lowPower": false,
  "clipboardEnabled": true,
  "globalMode": false,
  "httpsEnabled": false,
  "isDefaultPwd": false,
  "fail2ban": true,
  "pinRequired": true,
  "auditEnabled": true,
  "ipWhitelistEnabled": false,
  "ipWhitelist": "192.168.1.*,127.0.0.1",
  "lanOnlyStrict": true,
  "captchaEnabled": true,
  "ccLimitEnabled": true,
  "secretPath": "",
  "sessionTimeout": 30,
  "hideHeader": true
}
```
- `isDefaultPwd`：`true` 表示 `admin` 仍在使用默认密码 `admin`，前端会弹安全警告。`false` 表示已改密。该字段每次请求都会实际查询 SQLite 并与加盐哈希比对，不再使用静态变量，重启进程不会回退。
- `lanOnlyStrict` / `captchaEnabled` / `ccLimitEnabled` / `secretPath` / `sessionTimeout` / `hideHeader`：v4.5.0 五重安全防线的实时状态。

### 2. 系统 OTA 更新检查
- **URL**: `/api/ota`
- **Method**: `GET`
- **Response**: `200 OK`，返回 `{"version":"4.5.0-lite.1"}`。
- **说明**：源码中为 `{"version":"__VERSION__"}` 占位符，由 `sync_version.py` 的 `update_ota_placeholder()` 在发版时自动注入，与 `CHANGELOG.md` 顶部版本号保持同步。

### 3. 获取/保存系统配置
- **URL**: `/api/settings`
- **Method**: `GET` / `POST`
- **权限**：**仅 `admin` 角色可调用**，且**受「高危 API 局域网锁」保护**（公网 IP 一律 403，除非关闭该锁）。
- **GET Response**: 返回包含基础配置、协议开关、Webhook 配置、五重安全防线状态的完整 JSON：
  - 基础：`alias`, `webPort`, `davPort`, `ftpPort`
  - 协议：`webdav`, `ftp`, `dlna`, `stream`, `clipboard`, `lowPower`, `globalMode`
  - 传统安全：`fail2ban`, `pinRequired`, `auditEnabled`, `ipWhitelistEnabled`, `ipWhitelist`
  - 五重防线：`lanOnlyStrict`, `captchaEnabled`, `ccLimitEnabled`, `secretPath`, `sessionTimeout`, `hideHeader`
  - Webhook：`webhookUrl`, `webhookChatId`, `webhookTemplate`（v4.5.0-lite.1 补全 GET 字段）
- **POST Body**: 包含上述字段进行更新。
  - 若开启了「敏感操作安全码」(`pinRequired`)，请求必须额外携带 `pin` 字段，否则返回 `403`。
  - 若开启了「隐藏入口暗号」，保存成功后前端需同步更新 `localStorage.nas_secret`，下次请求自动携带 `?s=`。

### 4. 2FA 两步验证 (v4.5.0 新增)
- **URL**: `/api/2fa`
- **Method**: `GET` / `POST`
- **GET Response**: `200 OK`，返回当前 2FA 状态与密钥。
```json
{
  "enabled": false,
  "secret": "JBSWY3DPEHPK3PXP"
}
```
  - **重要**：`secret` 为空时服务端会生成新密钥并**立即持久化**（v4.5.0-lite.1 修复了「每次 GET 生成新密钥但不保存」的 bug），确保扫码绑定后验证码永远一致。
- **POST Body**:
  - `enable`: `true` / `false`，开启或关闭 2FA。
  - `secret`: 密钥（选填，若为空则使用服务端当前密钥）。
  - **开启时强制要求**：必须在 Header 携带 `X-TOTP-Code`（6 位动态验证码），服务端用 `TotpManager.verifyCode(secret, code)` 校验通过后才真正写入 `2fa_enabled=true`。**这是 Lite 版主动修复的完整版遗留风险**，杜绝密钥输错导致永久锁死。
  - 失败返回：`400 Bad Request`（"Missing TOTP code" / "Invalid TOTP code" / "Invalid TOTP format"）。
- **算法**：纯 Java 手写 TOTP（RFC 6238，HMAC-SHA1），兼容 Google Authenticator / Microsoft Authenticator / 1Password。

### 5. Webhook 测试
- **URL**: `/api/webhook/test`
- **Method**: `POST`
- **Response**: `200 OK`，服务端会立即向已配置的 Webhook URL 发送一条测试通知。

### 6. 修改管理员密码
- **URL**: `/api/password`
- **Method**: `POST`
- **Body**: `password=新密码`
- **权限**：仅 `admin` 可调用，且受「高危 API 局域网锁」保护。

### 7. PWA 静态资源
- **URL**: `/manifest.json` / `/sw.js`
- **Method**: `GET`
- **Response**: 直接返回 PWA 的配置文件与 Service Worker 脚本，内容由服务端从 `assets` 目录流式读取。
- **鉴权**：**免鉴权**（与其他静态资源一致），浏览器首次加载 PWA 时无需 Basic Auth。

---

## 📡 六、 跨端协同 UDP 协议 (8090端口)

1. **发送方 (手机/桌面端)**：向局域网 `255.255.255.255:8090` 发送字符串：`DISCOVER_TV`。
2. **接收方 (TV)**：接收到该广播后，向发送方源 IP 单播回复：`TV_HERE`。
3. **配置下发**：手机向 `http://<TV_IP>:8090/sync` 发送 HTTP POST，Body 为 `ip=xxx&user=xxx&pass=xxx`。
4. **投屏下发**：手机向 `http://<TV_IP>:8090/play?<视频绝对URL>` 发送 GET 请求。

---

## 📂 七、 其他协议端口分配

- **WebDAV (8081端口)**：支持标准 WebDAV 协议，兼容 Windows 网络邻居映射。同样受 `ThreadLocal` 身份隔离与 `safeFile` 沙箱约束。
  - ⚠️ **2FA 兼容性**：WebDAV 协议不支持自定义 Header，**开启 2FA 后 `admin` 账号的 WebDAV 连接会失败**（缺少 `X-TOTP-Code`）。需改用 `guest` 账号，或临时关闭 2FA。
- **FTP (2121端口)**：支持 PASV 被动模式，兼容 Windows 资源管理器。同样受 IP 白名单与 Fail2Ban 拦截。
  - ⚠️ **2FA 兼容性**：同上，FTP 协议不支持自定义 Header，开启 2FA 后 `admin` 无法连接。

---

## 🔢 八、 版本号编码规范 (sync_version.py)

CI 与各端 `build.gradle` 的版本号均由 `sync_version.py` 注入，开发者**严禁手改**。

- **`versionName`**：保留完整 `-lite.N` 后缀，如 `4.5.0-lite.1`。
- **`versionCode`**：编码为 `major*100000 + minor*1000 + patch*10 + lite序号`。
  - 例：`4.5.0-lite.1` → `4*100000 + 5*1000 + 0*10 + 1` = **`405001`**。
- **CHANGELOG 正则**：`##\s*\[?v?(\d+\.\d+\.\d+(?:-lite\.\d+)?)\]?`。
  - ⚠️ **禁止**只匹配 `\d+\.\d+\.\d+`。会被截断成 `4.5.0`，导致注入到 `versionName` 后与 CI Release 产物文件名不一致，且 `-lite.N` 的 `N` 无法参与 `versionCode` 编码。
- **`__VERSION__` 占位符**：`/api/ota` 的响应中版本号为 `{"version":"__VERSION__"}`，`sync_version.py` 的 `update_ota_placeholder()` 会在发版时自动注入，与 `versionName` 保持同步。

---

## ⚠️ Lite 版已移除的 API（请参阅完整版）

以下接口在完整版中存在，但 **在 Lite 版中已被彻底移除**：

### 多媒体与扩展
- `/api/thumb`（图片缩略图）
- `/api/photos`（时光相册）
- `/api/media/wall`（影视海报墙刮削，依赖已剔除的 `MediaScraper`）
- `/api/monitor`（硬件状态监控，依赖已剔除的 `SystemMonitor`）
- `/api/users`（多用户管理）

### 高权限与远控
- `/api/shell`（Web Shell 执行）
- `/api/system/wipe`（Panic Wipe 自毁核按钮）
- `ws://<ip>:8082`（WebSocket 远程控制协议）
- `/api/remote/*`（远控相关接口）

### 短信与隐私
- `/api/sms/send`（发送短信）
- `/api/sms/logs`（短信日志）

### 网络穿透与协议
- `53317` 端口（LocalSend 接收端）
- `/api/tunnel/*`（内网穿透相关接口）

以上接口在 Lite 版中统一返回 `404 Not Found`（部分接口返回 `"xxx removed in Lite"` 的提示文本，便于排错）。