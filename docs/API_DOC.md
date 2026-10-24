# Android NAS Lite 开发者 API 接口规范

本项目（Android NAS Lite）是 [完整版](https://github.com/styisme/android-nas) 的精简分支。Lite 版的所有端（Web、手机客户端、TV、桌面端）均通过统一的 HTTP JSON API 与局域网内的服务端通信。

由于 Lite 版移除了远程控制、多用户管理、多媒体刮削等扩展模块，因此**仅提供完整版 API 的子集**。完整版的全部接口（包括 WebSocket 远控协议）请参阅 [完整版 API 规范](https://github.com/styisme/android-nas/blob/master/docs/API_DOC.md)。

以下是 Lite 版保留的完整接口规范说明（基于 v4.2.0-lite）。

---

## 📡 基础约定

- **Base URL**：`http://<ip>:8080`
- **Content-Type**：
  - 常规请求：`application/x-www-form-urlencoded` 或 `application/json`
  - 文件上传：`multipart/form-data` 或 `application/octet-stream`
- **鉴权方式 (Auth)**：
  - 除分享链接外，所有 HTTP 请求必须携带 Header：`Authorization: Basic Base64(账号:密码)`。
  - Lite 版仅内置 `admin` 与 `guest` 双账号（默认 `admin:admin`）。
  - 鉴权失败将返回 `401 Unauthorized`。
- **沙箱隔离**：`guest` 账号的路径会被底层强制映射并锁定在 `/Public` 目录内，无法越权访问。
- **响应格式**：除流媒体下载、纯文本预览外，所有响应均为 JSON 格式。

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

### 3. 表单多文件上传 (Web端使用)
- **URL**: `/api/upload`
- **Method**: `POST`
- **Body**: `multipart/form-data` 格式。需包含 `path` 字段（目标目录）与一个或多个 `file` 字段。
- **Response**: `200 OK`，返回字符串 `OK: filename.ext`。

### 4. 客户端流式 PUT 上传 (手机端/桌面端使用)
- **URL**: `/*` (任意文件路径，如 `/Movies/new.mp4`)
- **Method**: `PUT`
- **Body**: 纯二进制流 (`application/octet-stream`)。
- **注意**：客户端必须携带准确的 `Content-Length`。如果服务端写入异常，会触发 Drain 机制并返回明确状态码。

### 5. 大文件秒传与分片上传 (v4.2.0 实装)
- **秒传校验**:
  - `POST /api/upload/check`
  - Body: `hash=文件MD5`
  - 返回: `SECONDS_UPLOAD:/path/to/file`（秒传成功，服务端已有该文件）或 `NEED_UPLOAD`（需要继续上传）。
- **分片上传**:
  - `POST /api/upload/chunk`
  - Params: `hash=文件MD5`, `index=当前分片序号`, `total=总分片数`, `name=文件名`, `path=目标目录`
  - Body: 纯二进制分片数据（通常为 2MB 块）。
  - 服务端底层使用 `FileOutputStream(..., true)` 将分片追加至 `.part` 临时文件。当 `index == total - 1` 时，自动合并重命名为正式文件，并触发 Webhook 通知。

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
- **在线保存文本**: `POST /api/save`，Body: `path=/文件路径&content=新的内容`

### 9. 在线解压 ZIP
- **URL**: `/api/unzip`
- **Method**: `POST`
- **Body**: `path=/目标zip文件路径.zip`
- **Response**: `200 OK` (内置了路径穿越校验，解压安全)

---

## 🔗 二、 分享接口

### 1. 生成安全分享链接
- **URL**: `/api/share`
- **Method**: `POST`
- **Body**:
  - `path`: 必填，文件路径。
  - `pass`: 选填，提取码（如 `1234`）。
  - `expire`: 选填，有效期毫秒数（如 `86400000` 表示 1 天）。
- **Response**: `200 OK`，返回 8 位随机 Token (如 `abcd1234`)。

### 2. 访问分享
- **URL**: `/share/{token}`
- **Method**: `GET`
- **Params**: 如果有提取码，需携带 `?pass=1234`。
- **Response**: 校验通过后，返回文件流。

---

## 🔍 三、 搜索与工具接口

### 1. 全局递归搜索
- **URL**: `/api/search`
- **Method**: `GET`
- **Params**: `q` (String, 必填) - 搜索关键字。
- **Response**: `200 OK`，返回包含 `name`, `path`, `dir`, `size` 等属性的 JSON 数组。

### 2. 局域网剪贴板
- **获取**: `GET /api/clipboard` -> 返回纯文本内容。
- **设置**: `POST /api/clipboard`，Body: `content=要同步的文字`。

---

## ⚙️ 四、 系统与控制台接口

### 1. 系统 OTA 更新检查
- **URL**: `/api/ota`
- **Method**: `GET`
- **Response**: `200 OK`，返回最新版本信息，供客户端比对。

### 2. 获取/保存系统配置 (v4.2.0 扩展)
- **URL**: `/api/settings`
- **Method**: `GET` / `POST`
- **GET Response**: 返回包含 `alias`, `webPort`, `davPort`, `ftpPort`, `webdav`, `ftp`, `stream`, `clipboard`, `lowPower`, 以及 **`webhookUrl`, `webhookChatId`** 的 JSON。*(注：Lite 版移除了 `dlna` 和 `globalMode` 字段)*
- **POST Body**: 包含上述字段进行更新。

### 3. Webhook 测试 (v4.2.0 新增)
- **URL**: `/api/webhook/test`
- **Method**: `POST`
- **Response**: `200 OK`，服务端会立即向已配置的 Webhook URL 发送一条测试通知。

### 4. 修改管理员密码
- **URL**: `/api/password`
- **Method**: `POST`
- **Body**: `password=新密码`

### 5. PWA 静态资源 (v4.2.0 新增)
- **URL**: `/manifest.json` / `/sw.js`
- **Method**: `GET`
- **Response**: 直接返回 PWA 的配置文件与 Service Worker 脚本，内容由服务端从 `assets` 目录流式读取。

---

## 📡 五、 跨端协同 UDP 协议 (8090端口)

1. **发送方 (手机/桌面端)**：向局域网 `255.255.255.255:8090` 发送字符串：`DISCOVER_TV`。
2. **接收方 (TV)**：接收到该广播后，向发送方源 IP 单播回复：`TV_HERE`。
3. **配置下发**：手机向 `http://<TV_IP>:8090/sync` 发送 HTTP POST，Body 为 `ip=xxx&user=xxx&pass=xxx`。
4. **投屏下发**：手机向 `http://<TV_IP>:8090/play?<视频绝对URL>` 发送 GET 请求。

---

## 📂 六、 其他协议端口分配

- **WebDAV (8081端口)**：支持标准 WebDAV 协议，兼容 Windows 网络邻居映射。
- **FTP (2121端口)**：支持 PASV 被动模式，兼容 Windows 资源管理器。

---

## ⚠️ Lite 版已移除的 API（请参阅完整版）

以下接口在完整版中存在，但 **在 Lite 版中已被彻底移除**：
- `/api/thumb`（图片缩略图）
- `/api/photos`（时光相册）
- `/api/media/wall`（影视海报墙刮削）
- `/api/monitor`（硬件状态监控）
- `/api/users`（多用户管理）
- `/api/shell`（Web Shell 执行）
- `ws://<ip>:8082`（WebSocket 远程控制协议）
- `53317` 端口（LocalSend 接收端）