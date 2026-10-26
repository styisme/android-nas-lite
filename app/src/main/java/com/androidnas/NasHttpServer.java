package com.androidnas;

import android.content.Context;
import android.os.Environment;
import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.*;
import org.json.JSONObject;
import fi.iki.elonen.NanoHTTPD;

public class NasHttpServer extends NanoHTTPD {
    private static final String ROOT = Environment.getExternalStorageDirectory().getAbsolutePath();
    private static class ShareInfo { String owner; String path; long expire; }
    private Map<String, ShareInfo> sharedLinks = new ConcurrentHashMap<String, ShareInfo>();
    private static volatile String clipboardContent = "";
    private Context context;
    private static Map<String, String> captchaMap = new ConcurrentHashMap<String, String>();

    public NasHttpServer(int port, Context context) { 
        super(port); 
        this.context = context;
    }

    private File safeFile(String path) throws IOException {
        if (path == null || path.trim().isEmpty()) path = "/";
        String user = AuthManager.getCurrentUser();
        String userRoot = DatabaseManager.getInstance().getUserRoot(user);
        
        if (user.equals("admin") && SettingsManager.isGlobalMode()) {
            return new File(path);
        }
        
        File rootDir = new File(ROOT, userRoot);
        if (!rootDir.exists()) rootDir.mkdirs();
        
        String relativePath = path.replaceAll("^/+", "");
        File f = new File(rootDir, relativePath);
        
        String c = f.getCanonicalPath();
        String r = rootDir.getCanonicalPath();
        if (!c.equals(r) && !c.startsWith(r + File.separator)) return null;
        return f;
    }

    private String getSafeName(String name) {
        if (name == null || name.trim().isEmpty() || name.equals(".") || name.equals("..")) return null;
        return new File(name).getName().replace("\\", "_"); 
    }

    private boolean deleteRec(File f) {
        if (!f.exists()) return true;
        if (f.isDirectory()) {
            File[] ch = f.listFiles();
            if (ch != null) for (File c : ch) if (!deleteRec(c)) return false;
        }
        return f.delete();
    }
    
    private void deleteDir(File dir) {
        if (dir == null || !dir.exists()) return;
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isDirectory()) deleteDir(f);
                else f.delete();
            }
        }
        if (!dir.getAbsolutePath().equals(Environment.getExternalStorageDirectory().getAbsolutePath())) {
            dir.delete();
        }
    }

    private boolean isHighRiskApi(String uri) {
        return uri.startsWith("/api/shell") || uri.startsWith("/api/settings") || uri.startsWith("/api/system/wipe") || uri.startsWith("/api/password");
    }

    private boolean isLanIp(String ip) {
        if (ip == null) return false;
        return ip.equals("::1") || ip.startsWith("127.0.0.1") 
            || ip.startsWith("192.168.") || ip.startsWith("10.") 
            || ip.matches("172\\.(1[6-9]|2[0-9]|3[01])\\..*");
    }

    @Override public Response serve(IHTTPSession session) {
        String uri = session.getUri();
        try {
            if (uri.startsWith("/share/")) return handleSecureShare(session, uri.substring(7));
            if ("/manifest.json".equals(uri)) return newFixedLengthResponse(Response.Status.OK, "application/json", getClass().getClassLoader().getResourceAsStream("assets/manifest.json"), -1);
            if ("/sw.js".equals(uri)) return newFixedLengthResponse(Response.Status.OK, "application/javascript", getClass().getClassLoader().getResourceAsStream("assets/sw.js"), -1);

            String clientIp = session.getHeaders().get("x-forwarded-for");
            if (clientIp == null) clientIp = session.getHeaders().get("remote-addr");
            if (clientIp == null) clientIp = "127.0.0.1";

            String secretPath = SettingsManager.getSecretPath();
            if (secretPath != null && !secretPath.trim().isEmpty()) {
                String reqSecret = session.getParms().get("s");
                if (reqSecret == null || !reqSecret.equals(secretPath.trim())) {
                    if (!uri.startsWith("/api/")) {
                        return text(Response.Status.NOT_FOUND, "404 Not Found");
                    }
                }
            }

            if (SettingsManager.isFail2BanEnabled() && SecurityFilter.isBlocked(clientIp)) {
                return text(Response.Status.FORBIDDEN, "Too many requests. IP Blocked.");
            }
            
            if (SettingsManager.isLanOnlyStrict() && isHighRiskApi(uri)) {
                if (!isLanIp(clientIp)) {
                    LogManager.audit("拦截公网高危请求: " + clientIp + " 访问 " + uri);
                    return text(Response.Status.FORBIDDEN, "Forbidden: High-risk API only allowed from LAN.");
                }
            }
            
            if (SettingsManager.isIpWhitelistEnabled()) {
                String list = SettingsManager.getIpWhitelist();
                boolean allowed = false;
                if (list != null) {
                    for (String rule : list.split(",")) {
                        rule = rule.trim();
                        if (rule.endsWith("*") && clientIp.startsWith(rule.substring(0, rule.length() - 1))) { allowed = true; break; }
                        if (rule.equals(clientIp)) { allowed = true; break; }
                    }
                }
                if (!allowed) return text(Response.Status.FORBIDDEN, "IP Blocked by Whitelist");
            }

            int role = 1;
            if (!Method.OPTIONS.equals(session.getMethod())) {
                if (SettingsManager.isCaptchaEnabled()) {
                    String reqCap = session.getHeaders().get("x-captcha");
                    String expected = captchaMap.get(clientIp);
                    if (expected != null) {
                        if (reqCap == null || !reqCap.equals(expected)) {
                            SecurityFilter.recordFail(clientIp);
                            return text(Response.Status.UNAUTHORIZED, "CAPTCHA_FAILED");
                        }
                        captchaMap.remove(clientIp);
                    }
                }
                
                String totpCode = session.getHeaders().get("x-totp-code");
                role = AuthManager.verifyRole(session.getHeaders().get("authorization"), totpCode);
                if (role == 0) {
                    // 🔥 修复死锁：如果密码正确但 2FA 失败，提示需要验证码
                    if (SettingsManager.is2FAEnabled() && AuthManager.verifyPasswordOnly(session.getHeaders().get("authorization")) > 0) {
                        return text(Response.Status.UNAUTHORIZED, "2FA_REQUIRED");
                    }
                    SecurityFilter.recordFail(clientIp);
                    LogManager.audit("登录失败: " + clientIp + " 尝试账号 " + AuthManager.getCurrentUser());
                    Response r = newFixedLengthResponse(Response.Status.UNAUTHORIZED, "text/plain", "401");
                    r.addHeader("WWW-Authenticate", "Basic realm=\"Android NAS\"");
                    return r;
                }
                SecurityFilter.recordSuccess(clientIp, AuthManager.getCurrentUser());
            }

            if ("/".equals(uri) || "/index.html".equals(uri)) {
                Response r = newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", getClass().getClassLoader().getResourceAsStream("assets/index.html"), -1);
                if (SettingsManager.isHideHeaderEnabled()) r.addHeader("Server", "SecureNAS");
                return r;
            }

            if ("/api/status".equals(uri)) {
                return newFixedLengthResponse(Response.Status.OK, "application/json", "{\"lowPower\":" + SettingsManager.isLowPowerMode() + ",\"clipboardEnabled\":" + SettingsManager.isClipboardEnabled() + ",\"globalMode\":" + SettingsManager.isGlobalMode() + ",\"httpsEnabled\":" + SettingsManager.isHttpsEnabled() + ",\"isDefaultPwd\":" + DatabaseManager.isDefaultPassword() + ",\"fail2ban\":" + SettingsManager.isFail2BanEnabled() + ",\"pinRequired\":" + SettingsManager.isPinRequired() + ",\"auditEnabled\":" + SettingsManager.isAuditEnabled() + ",\"ipWhitelistEnabled\":" + SettingsManager.isIpWhitelistEnabled() + ",\"ipWhitelist\":\"" + SettingsManager.getIpWhitelist() + "\",\"lanOnlyStrict\":" + SettingsManager.isLanOnlyStrict() + ",\"captchaEnabled\":" + SettingsManager.isCaptchaEnabled() + ",\"ccLimitEnabled\":" + SettingsManager.isCcLimitEnabled() + ",\"secretPath\":\"" + SettingsManager.getSecretPath() + "\",\"sessionTimeout\":" + SettingsManager.getSessionTimeout() + ",\"hideHeader\":" + SettingsManager.isHideHeaderEnabled() + "}");
            }
            
            if ("/api/captcha".equals(uri)) {
                int a = (int)(Math.random() * 10);
                int b = (int)(Math.random() * 10);
                String ans = String.valueOf(a + b);
                captchaMap.put(clientIp, ans);
                return newFixedLengthResponse(Response.Status.OK, "application/json", "{\"q\":\"" + a + " + " + b + " = ?\",\"id\":\"" + ans + "\"}");
            }
            
            if ("/api/ports".equals(uri)) {
                final int[] ports = {8080, 8081, 2121, 8082, 53317, 8090, 445, 8096, 5244, 3000, 53};
                final String[] names = {"Web 控制台", "WebDAV", "FTP 服务", "WebSocket 远控", "LocalSend", "协同发现", "SMB", "Jellyfin", "Alist", "AdGuard Web", "AdGuard DNS"};
                java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(10);
                java.util.List<java.util.concurrent.Future<String>> futures = new java.util.ArrayList<>();
                for (int i = 0; i < ports.length; i++) {
                    final int p = ports[i];
                    final String n = names[i];
                    futures.add(pool.submit(new java.util.concurrent.Callable<String>() {
                        @Override public String call() {
                            long start = System.currentTimeMillis();
                            boolean open = false;
                            java.net.Socket socket = new java.net.Socket();
                            try {
                                socket.connect(new java.net.InetSocketAddress("127.0.0.1", p), 300);
                                open = true;
                            } catch (Exception ignored) {} finally {
                                try { socket.close(); } catch (Exception ignored) {}
                            }
                            long latency = System.currentTimeMillis() - start;
                            return "{\"name\":\"" + n + "\",\"port\":" + p + ",\"status\":" + open + ",\"latency\":" + (open ? latency : 0) + "}";
                        }
                    }));
                }
                StringBuilder resJson = new StringBuilder("[");
                for (int i = 0; i < futures.size(); i++) {
                    if (i > 0) resJson.append(",");
                    try { resJson.append(futures.get(i).get()); } catch (Exception ignored) {}
                }
                resJson.append("]");
                pool.shutdown();
                return newFixedLengthResponse(Response.Status.OK, "application/json", resJson.toString());
            }

            if ("/api/speedtest".equals(uri)) {
                long bytesToSend = 5 * 1024 * 1024L;
                try { bytesToSend = Long.parseLong(session.getParms().get("size")) * 1024 * 1024; } catch (Exception ignored) {}
                return newChunkedResponse(Response.Status.OK, "application/octet-stream", new SpeedTestStream(bytesToSend));
            }
            if ("/api/sms/send".equals(uri) && Method.POST.equals(session.getMethod())) {
                String phone = session.getParms().get("phone");
                String content = session.getParms().get("content");
                if (phone != null && content != null) {
                    // Lite: SmsManager removed, no-op for security
                    return text(Response.Status.OK, "OK");
                }
                return text(Response.Status.BAD_REQUEST, "Missing params");
            }
            if ("/api/sms/logs".equals(uri)) return text(Response.Status.NOT_FOUND, "SMS logs removed in Lite");
            if ("/api/2fa".equals(uri)) {
                if (Method.GET.equals(session.getMethod())) {
                    String secret = SettingsManager.get2FASecret();
                    if (secret.isEmpty()) {
                        secret = TotpManager.generateSecret();
                        SettingsManager.set2FASecret(secret); // 🔥 修复：生成后立即持久化
                    }
                    return newFixedLengthResponse(Response.Status.OK, "application/json", "{\"enabled\":" + SettingsManager.is2FAEnabled() + ",\"secret\":\"" + secret + "\"}");
                } else if (Method.POST.equals(session.getMethod())) {
                    String enable = session.getParms().get("enable");
                    String secret = session.getParms().get("secret");
                    if (secret != null) SettingsManager.set2FASecret(secret);
                    boolean willEnable = "true".equals(enable);
                    if (willEnable) {
                        String totpCode = session.getHeaders().get("x-totp-code");
                        if (totpCode == null || totpCode.trim().isEmpty()) {
                            return text(Response.Status.BAD_REQUEST, "Missing TOTP code");
                        }
                        try {
                            int code = Integer.parseInt(totpCode.trim());
                            if (!TotpManager.verifyCode(secret, code)) {
                                return text(Response.Status.BAD_REQUEST, "Invalid TOTP code");
                            }
                        } catch (Exception e) {
                            return text(Response.Status.BAD_REQUEST, "Invalid TOTP format");
                        }
                    }
                    SettingsManager.set2FAEnabled(willEnable);
                    return text(Response.Status.OK, "OK");
                }
            }

            if ("/api/monitor".equals(uri)) return text(Response.Status.NOT_FOUND, "Monitor removed in Lite");
        } catch (Exception e) {
            return text(Response.Status.INTERNAL_ERROR, "Error: " + e.getMessage());
        } finally {
            AuthManager.clearCurrentUser();
        }
        return text(Response.Status.NOT_FOUND, "Not found");
    }

    private Response handleSecureShare(IHTTPSession session, String token) throws Exception {
        ShareInfo info = sharedLinks.get(token);
        if (info == null) return text(Response.Status.NOT_FOUND, "失效");
        if (System.currentTimeMillis() > info.expire) { sharedLinks.remove(token); return text(Response.Status.FORBIDDEN, "已过期"); }
        AuthManager.setCurrentUser(info.owner);
        try {
            File f = safeFile(info.path);
            if (f != null && f.exists() && !f.isDirectory()) {
                Response r = newChunkedResponse(Response.Status.OK, "application/octet-stream", new FileInputStream(f));
                r.addHeader("Content-Disposition", "attachment; filename=\"" + f.getName().replace("\r", "").replace("\n", "").replace("\"", "") + "\"");
                return r;
            }
        } finally {
            AuthManager.clearCurrentUser();
        }
        return text(Response.Status.NOT_FOUND, "丢失");
    }

    private void scanMedia(File dir, List<File> list, int depth, String type) {
        if (dir == null || !dir.exists() || depth > 4) return;
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) { if (!f.getName().startsWith(".")) scanMedia(f, list, depth + 1, type); }
            else {
                String n = f.getName().toLowerCase();
                if (type.equals("photo") && (n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png"))) list.add(f);
                if (type.equals("video") && (n.endsWith(".mp4") || n.endsWith(".mkv") || n.endsWith(".avi"))) list.add(f);
            }
        }
    }

    private void searchFiles(File dir, String q, List<File> res, int depth) {
        if (dir == null || !dir.exists() || depth > 4 || res.size() > 200) return;
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.getName().toLowerCase().contains(q.toLowerCase())) res.add(f);
            if (f.isDirectory() && !f.getName().startsWith(".")) searchFiles(f, q, res, depth + 1);
        }
    }

    private boolean unzip(File zipFile) {
        try {
            ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile));
            ZipEntry entry;
            File destDir = zipFile.getParentFile();
            byte[] buf = new byte[8192];
            while ((entry = zis.getNextEntry()) != null) {
                File f = new File(destDir, entry.getName());
                if (!f.getCanonicalPath().startsWith(destDir.getCanonicalPath())) continue;
                if (entry.isDirectory()) f.mkdirs();
                else {
                    f.getParentFile().mkdirs();
                    FileOutputStream fos = new FileOutputStream(f);
                    int len; while ((len = zis.read(buf)) > 0) fos.write(buf, 0, len);
                    fos.close();
                }
            }
            zis.close();
            return true;
        } catch (Exception e) { return false; }
    }

    private String mime(String n) {
        if (n.endsWith(".mp4")) return "video/mp4"; 
        if (n.endsWith(".jpg") || n.endsWith(".jpeg")) return "image/jpeg";
        if (n.endsWith(".png")) return "image/png";
        return "application/octet-stream"; 
    }
    private Response text(Response.Status s, String m) { return newFixedLengthResponse(s, "text/plain; charset=utf-8", m); }
    
    private String buildJson(File dir, String path) {
        File[] files = dir.listFiles();
        if (files == null) files = new File[0];
        Arrays.sort(files, new Comparator<File>() {
            @Override public int compare(File a, File b) {
                if (a.isDirectory() && !b.isDirectory()) return -1;
                if (!a.isDirectory() && b.isDirectory()) return 1;
                return a.getName().compareToIgnoreCase(b.getName());
            }
        });
        StringBuilder sb = new StringBuilder("{\"path\":\"" + esc(path) + "\",\"files\":[");
        for (int i = 0; i < files.length; i++) {
            File f = files[i];
            if (i > 0) sb.append(",");
            sb.append("{\"name\":\"").append(esc(f.getName())).append("\",\"dir\":").append(f.isDirectory()).append(",\"size\":").append(f.length()).append("}");
        }
        sb.append("]}");
        return sb.toString();
    }
    
    private void copy(File src, File dst) throws IOException {
        InputStream in = new FileInputStream(src); OutputStream out = new FileOutputStream(dst);
        byte[] buf = new byte[8192]; int len; while ((len = in.read(buf)) > 0) out.write(buf, 0, len);
        in.close(); out.close();
    }
    
    private String esc(String s) { 
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }
    
    private static class SpeedTestStream extends InputStream {
        private long remaining;
        public SpeedTestStream(long size) { this.remaining = size; }
        @Override public int read() throws java.io.IOException {
            if (remaining <= 0) return -1;
            remaining--; return 0;
        }
        @Override public int read(byte[] b, int off, int len) throws java.io.IOException {
            if (remaining <= 0) return -1;
            int toRead = (int) Math.min(len, remaining);
            java.util.Arrays.fill(b, off, off + toRead, (byte)0);
            remaining -= toRead; return toRead;
        }
    }

    private static class LimitedInputStream extends FilterInputStream {
        private long limit;
        protected LimitedInputStream(InputStream in, long limit) { super(in); this.limit = limit; }
        @Override public int read() throws IOException {
            if (limit <= 0) return -1;
            int res = super.read(); if (res != -1) limit--; return res;
        }
        @Override public int read(byte[] b, int off, int len) throws IOException {
            if (limit <= 0) return -1;
            int toRead = (int) Math.min(len, limit);
            int res = super.read(b, off, toRead);
            if (res != -1) limit -= res;
            return res;
        }
    }
}
