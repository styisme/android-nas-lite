package com.androidnas;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.InputType;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import java.io.File;
import java.util.*;
public class MainActivity extends Activity {
    private static final String ROOT = Environment.getExternalStorageDirectory().getAbsolutePath();
    private File currentDir;
    private ListView listView;
    private TextView pathView, statusView, ipView;
    private Button toggleBtn;
    private CheckBox cbAuth;
    private FileAdapter adapter;
    private final List<File> items = new ArrayList<File>();
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        SettingsManager.init(this); AuthManager.init(this);
        currentDir = new File(ROOT);
        pathView = (TextView) findViewById(R.id.pathView); statusView = (TextView) findViewById(R.id.statusView); ipView = (TextView) findViewById(R.id.ipView); toggleBtn = (Button) findViewById(R.id.toggleBtn); listView = (ListView) findViewById(R.id.listView); cbAuth = (CheckBox) findViewById(R.id.cbAuth);
        SharedPreferences sp = getSharedPreferences("nas_auth", Context.MODE_PRIVATE);
        cbAuth.setChecked(sp.getBoolean("auth_enabled", true));
        cbAuth.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() { @Override public void onCheckedChanged(CompoundButton b, boolean isChecked) { AuthManager.setAuthEnabled(MainActivity.this, isChecked); if (HttpServerService.isRunning()) { stopService(new Intent(MainActivity.this, HttpServerService.class)); new Handler().postDelayed(new Runnable() { @Override public void run() { startService(new Intent(MainActivity.this, HttpServerService.class)); } }, 500); Toast.makeText(MainActivity.this, "账号验证已切换，服务正在重启…", Toast.LENGTH_SHORT).show(); } updateServiceStatus(); } });
        adapter = new FileAdapter(); listView.setAdapter(adapter);
        listView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() { @Override public boolean onItemLongClick(AdapterView<?> p, View v, int pos, long id) { showFileMenu(items.get(pos)); return true; } });
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() { @Override public void onItemClick(AdapterView<?> p, View v, int pos, long id) { File f = items.get(pos); if (f.isDirectory()) { currentDir = f; refresh(); } else showFileMenu(f); } });
        toggleBtn.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { if (HttpServerService.isRunning()) { stopService(new Intent(MainActivity.this, HttpServerService.class)); Toast.makeText(MainActivity.this, "正在停止...", Toast.LENGTH_SHORT).show(); new Handler().postDelayed(new Runnable() { @Override public void run() { updateServiceStatus(); } }, 1000); } else { startService(new Intent(MainActivity.this, HttpServerService.class)); Toast.makeText(MainActivity.this, "正在启动...", Toast.LENGTH_SHORT).show(); new Handler().postDelayed(new Runnable() { @Override public void run() { updateServiceStatus(); } }, 500); } } });
        findViewById(R.id.upBtn).setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { File p = currentDir.getParentFile(); if (p != null && p.getAbsolutePath().startsWith(ROOT)) { currentDir = p; refresh(); } } });
        findViewById(R.id.refreshBtn).setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { refresh(); } });
        if (!HttpServerService.isRunning()) { startService(new Intent(this, HttpServerService.class)); checkAllFilesPermission(); checkBatteryOptimization(); }
    }
    private void checkAllFilesPermission() { if (Build.VERSION.SDK_INT >= 30) { if (!android.os.Environment.isExternalStorageManager()) { new AlertDialog.Builder(this).setTitle("全盘权限提示").setMessage("你需要开启“所有文件访问权限”才能正常读取手机存储。").setPositiveButton("去开启", new DialogInterface.OnClickListener() { @Override public void onClick(DialogInterface d, int w) { try { Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION); intent.setData(Uri.parse("package:" + getPackageName())); startActivity(intent); } catch (Exception ignored) {} } }).setNegativeButton("暂不", null).show(); } } }
    private void checkBatteryOptimization() { if (Build.VERSION.SDK_INT >= 23) { PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE); if (!pm.isIgnoringBatteryOptimizations(getPackageName())) { new AlertDialog.Builder(this).setTitle("后台保活核心设置").setMessage("为保证 NAS 息屏不断网，必须进行以下设置：\n\n1. 忽略电池优化\n2. 允许自启动/后台运行").setPositiveButton("去忽略电池优化", new DialogInterface.OnClickListener() { @Override public void onClick(DialogInterface d, int w) { try { Intent intent = new Intent(); intent.setAction(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS); intent.setData(Uri.parse("package:" + getPackageName())); startActivity(intent); } catch (Exception e) { e.printStackTrace(); } } }).setNegativeButton("暂不", null).show(); } } }
    @Override public boolean onCreateOptionsMenu(Menu menu) { menu.add(0, 1, 0, "新建文件夹"); menu.add(0, 6, 0, "NAS 驾驶舱"); menu.add(0, 4, 0, "运行日志"); menu.add(0, 7, 0, "关于"); return true; }
    @Override public boolean onOptionsItemSelected(MenuItem item) { int id = item.getItemId(); if (id == 1) { final EditText input = new EditText(this); new AlertDialog.Builder(this).setTitle("新建文件夹").setView(input).setPositiveButton("创建", new DialogInterface.OnClickListener() { @Override public void onClick(DialogInterface d, int w) { String name = input.getText().toString().trim(); if (name.length() == 0) return; if (name.contains("/") || name.contains("\\") || name.equals(".") || name.equals("..")) { Toast.makeText(MainActivity.this, "名称含非法字符", Toast.LENGTH_SHORT).show(); return; } if (new File(currentDir, name).mkdirs()) refresh(); } }).setNegativeButton("取消", null).show(); return true; } else if (id == 6) { showControlCenter(); return true; } else if (id == 4) { new AlertDialog.Builder(this).setTitle("最近日志").setMessage(LogManager.getAllLogs()).setPositiveButton("关闭", null).show(); return true; } else if (id == 7) { new AlertDialog.Builder(this).setTitle("关于 Android NAS Lite").setMessage("版本: v4.4.0-lite.1\n\n精简版：专注核心文件流转。\n\n⚠️ 大量代码由 AI 辅助生成。").setPositiveButton("确定", null).show(); return true; } return super.onOptionsItemSelected(item); }
    private static Integer parsePortSafe(String s) { try { int p = Integer.parseInt(s.trim()); if (p < 1 || p > 65535) return null; return p; } catch (Exception e) { return null; } }
    private void showControlCenter() {
        ScrollView sv = new ScrollView(this); LinearLayout layout = new LinearLayout(this); layout.setOrientation(LinearLayout.VERTICAL); layout.setPadding(48, 24, 48, 24); sv.addView(layout);
        EditText etAlias = new EditText(this); etAlias.setHint("设备名称"); etAlias.setText(SettingsManager.getDeviceAlias()); layout.addView(etAlias);
        RadioGroup tabGroup = new RadioGroup(this); tabGroup.setOrientation(RadioGroup.HORIZONTAL); RadioButton rbBasic = new RadioButton(this); rbBasic.setText("基础"); rbBasic.setChecked(true); RadioButton rbServices = new RadioButton(this); rbServices.setText("服务"); RadioButton rbAdvanced = new RadioButton(this); rbAdvanced.setText("高级"); tabGroup.addView(rbBasic); tabGroup.addView(rbServices); tabGroup.addView(rbAdvanced); layout.addView(tabGroup);
        LinearLayout groupBasic = new LinearLayout(this); groupBasic.setOrientation(LinearLayout.VERTICAL);
        EditText etWebPort = new EditText(this); etWebPort.setHint("Web 端口"); etWebPort.setInputType(InputType.TYPE_CLASS_NUMBER); etWebPort.setText(String.valueOf(SettingsManager.getWebPort())); groupBasic.addView(etWebPort);
        EditText etDavPort = new EditText(this); etDavPort.setHint("WebDAV 端口"); etDavPort.setInputType(InputType.TYPE_CLASS_NUMBER); etDavPort.setText(String.valueOf(SettingsManager.getWebDavPort())); groupBasic.addView(etDavPort);
        EditText etFtpPort = new EditText(this); etFtpPort.setHint("FTP 端口"); etFtpPort.setInputType(InputType.TYPE_CLASS_NUMBER); etFtpPort.setText(String.valueOf(SettingsManager.getFtpPort())); groupBasic.addView(etFtpPort); layout.addView(groupBasic);
        LinearLayout groupServices = new LinearLayout(this); groupServices.setOrientation(LinearLayout.VERTICAL); groupServices.setVisibility(View.GONE);
        CheckBox cbWebDav = new CheckBox(this); cbWebDav.setText("开启 WebDAV"); cbWebDav.setChecked(SettingsManager.isWebDavEnabled()); groupServices.addView(cbWebDav);
        CheckBox cbFtp = new CheckBox(this); cbFtp.setText("开启 FTP 服务"); cbFtp.setChecked(SettingsManager.isFtpEnabled()); groupServices.addView(cbFtp);
        CheckBox cbStream = new CheckBox(this); cbStream.setText("允许网页流媒体"); cbStream.setChecked(SettingsManager.isStreamEnabled()); groupServices.addView(cbStream);
        CheckBox cbClipboard = new CheckBox(this); cbClipboard.setText("开启剪贴板"); cbClipboard.setChecked(SettingsManager.isClipboardEnabled()); groupServices.addView(cbClipboard); layout.addView(groupServices);
        LinearLayout groupAdvanced = new LinearLayout(this); groupAdvanced.setOrientation(LinearLayout.VERTICAL); groupAdvanced.setVisibility(View.GONE);
        CheckBox cbLowPower = new CheckBox(this); cbLowPower.setText("极寒省电模式"); cbLowPower.setChecked(SettingsManager.isLowPowerMode()); groupAdvanced.addView(cbLowPower); layout.addView(groupAdvanced);
        tabGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() { @Override public void onCheckedChanged(RadioGroup group, int checkedId) { View v = group.findViewById(checkedId); if (v == null) return; String text = ((RadioButton) v).getText().toString(); groupBasic.setVisibility(text.equals("基础") ? View.VISIBLE : View.GONE); groupServices.setVisibility(text.equals("服务") ? View.VISIBLE : View.GONE); groupAdvanced.setVisibility(text.equals("高级") ? View.VISIBLE : View.GONE); } });
        new AlertDialog.Builder(this).setTitle("NAS 驾驶舱").setView(sv).setPositiveButton("保存并重启", new DialogInterface.OnClickListener() { @Override public void onClick(DialogInterface dialog, int which) { Integer webPort = parsePortSafe(etWebPort.getText().toString()); Integer davPort = parsePortSafe(etDavPort.getText().toString()); Integer ftpPort = parsePortSafe(etFtpPort.getText().toString()); if (webPort == null || davPort == null || ftpPort == null) { Toast.makeText(MainActivity.this, "端口必须为 1-65535 的数字", Toast.LENGTH_LONG).show(); return; } SettingsManager.setDeviceAlias(etAlias.getText().toString().trim()); SettingsManager.setWebPort(webPort); SettingsManager.setWebDavPort(davPort); SettingsManager.setFtpPort(ftpPort); SettingsManager.setWebDavEnabled(cbWebDav.isChecked()); SettingsManager.setFtpEnabled(cbFtp.isChecked()); SettingsManager.setStreamEnabled(cbStream.isChecked()); SettingsManager.setClipboardEnabled(cbClipboard.isChecked()); SettingsManager.setLowPowerMode(cbLowPower.isChecked()); if (HttpServerService.isRunning()) { stopService(new Intent(MainActivity.this, HttpServerService.class)); Toast.makeText(MainActivity.this, "正在重启服务...", Toast.LENGTH_SHORT).show(); new Handler().postDelayed(new Runnable() { @Override public void run() { startService(new Intent(MainActivity.this, HttpServerService.class)); new Handler().postDelayed(new Runnable() { @Override public void run() { updateServiceStatus(); } }, 500); } }, 1000); } updateServiceStatus(); } }).setNegativeButton("取消", null).show();
    }
    private void showFileMenu(final File f) { final String[] actions = f.isDirectory() ? new String[]{"重命名", "删除", "属性"} : new String[]{"删除", "属性"}; new AlertDialog.Builder(this).setTitle(f.getName()).setItems(actions, new DialogInterface.OnClickListener() { @Override public void onClick(DialogInterface d, int w) { String a = actions[w]; if (a.equals("重命名")) { final EditText input = new EditText(MainActivity.this); input.setText(f.getName()); new AlertDialog.Builder(MainActivity.this).setTitle("重命名").setView(input).setPositiveButton("确定", new DialogInterface.OnClickListener() { @Override public void onClick(DialogInterface d2, int w2) { String n = input.getText().toString().trim(); if (n.length() == 0) return; if (n.contains("/") || n.contains("\\") || n.equals(".") || n.equals("..")) { Toast.makeText(MainActivity.this, "名称含非法字符", Toast.LENGTH_SHORT).show(); return; } if (f.renameTo(new File(f.getParentFile(), n))) refresh(); } }).setNegativeButton("取消", null).show(); } else if (a.equals("删除")) { new AlertDialog.Builder(MainActivity.this).setTitle("删除").setMessage("确定删除 " + f.getName() + "？").setPositiveButton("删除", new DialogInterface.OnClickListener() { @Override public void onClick(DialogInterface d2, int w2) { if (deleteRecursive(f)) refresh(); } }).setNegativeButton("取消", null).show(); } else { String info = "路径：" + f.getAbsolutePath() + "\n大小：" + (f.length() / 1024) + " KB"; new AlertDialog.Builder(MainActivity.this).setTitle("属性").setMessage(info).setPositiveButton("确定", null).show(); } } }).show(); }
    private boolean deleteRecursive(File f) { if (f.isDirectory()) { File[] c = f.listFiles(); if (c != null) for (File x : c) if (!deleteRecursive(x)) return false; } return f.delete(); }
    @Override protected void onResume() { super.onResume(); updateServiceStatus(); refresh(); }
    private void updateServiceStatus() { boolean running = HttpServerService.isRunning(); statusView.setText(running ? "服务状态：运行中" : "服务状态：已停止"); toggleBtn.setText(running ? "停止服务" : "启动服务"); toggleBtn.setEnabled(true); String ip = getIpAddress(); String text = "核心访问: http://" + ip + ":" + SettingsManager.getWebPort(); if (SettingsManager.isLowPowerMode()) text += "\n❄️ 极寒模式运行中"; if (!HttpServerService.lastError.isEmpty()) text += "\n⚠️ " + HttpServerService.lastError; ipView.setText(text); }
    private String getIpAddress() { try { WifiManager wm = (WifiManager) getSystemService(WIFI_SERVICE); int ip = wm.getConnectionInfo().getIpAddress(); if (ip == 0) return "127.0.0.1"; return String.format(Locale.US, "%d.%d.%d.%d", (ip & 0xff), (ip >> 8 & 0xff), (ip >> 16 & 0xff), (ip >> 24 & 0xff)); } catch (Throwable t) { return "127.0.0.1"; } }
    private void refresh() { if (!currentDir.exists() || !currentDir.isDirectory()) currentDir = new File(ROOT); pathView.setText(currentDir.getAbsolutePath().replace(ROOT, "/")); items.clear(); File[] files = currentDir.listFiles(); if (files != null) Collections.addAll(items, files); Collections.sort(items, new Comparator<File>() { @Override public int compare(File a, File b) { if (a.isDirectory() && !b.isDirectory()) return -1; if (!a.isDirectory() && b.isDirectory()) return 1; return a.getName().compareToIgnoreCase(b.getName()); } }); adapter.notifyDataSetChanged(); }
    private class FileAdapter extends BaseAdapter { @Override public int getCount() { return items.size(); } @Override public Object getItem(int p) { return items.get(p); } @Override public long getItemId(int p) { return p; } @Override public View getView(int pos, View cv, ViewGroup parent) { TextView tv; if (cv == null) { tv = new TextView(MainActivity.this); tv.setPadding(16, 24, 16, 24); tv.setTextSize(16); tv.setTextColor(0xFF1D1B20); tv.setBackgroundColor(0xFFFFFFFF); } else tv = (TextView) cv; File f = items.get(pos); tv.setText((f.isDirectory() ? "📁 " : "📄 ") + f.getName()); return tv; } }
}
