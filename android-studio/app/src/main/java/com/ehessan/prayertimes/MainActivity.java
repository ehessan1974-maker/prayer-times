package com.ehessan.prayertimes;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlarmManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.webkit.GeolocationPermissions;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;

public class MainActivity extends Activity {

    private static final int REQ_PERMS = 100;

    private WebView webView;
    private PrayerTts tts;
    private static volatile String sLastFetchResult = "";

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // v25.2: ضروري لأجهزة Android 5.x مثل Samsung J5
        // تفعيل TLS 1.2 على HttpsURLConnection (و WebView XHR على Android 5.x)
        // بدون هذا النداء، يفشل جلب content.json و version.json على هذه الأجهزة
        try {
            Tls12Helper.enable();
            android.util.Log.i("PrayerTimes", "TLS 1.2 helper enabled");
        } catch (Throwable t) {
            android.util.Log.e("PrayerTimes", "TLS 1.2 helper failed", t);
        }

        webView = new WebView(this);
        setContentView(webView);

        WebSettings ws = webView.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setDatabaseEnabled(true);
        ws.setAllowFileAccess(true);
        ws.setAllowContentAccess(true);
        // ضروري: جلب content.json وبيانات النجوم من GitHub عبر XHR من صفحة file://
        ws.setAllowUniversalAccessFromFileURLs(true);
        ws.setAllowFileAccessFromFileURLs(true);
        // ضروري: تشغيل الأذان والإقامة تلقائياً دون لمس
        ws.setMediaPlaybackRequiresUserGesture(false);
        ws.setGeolocationEnabled(true);
        ws.setLoadWithOverviewMode(true);
        ws.setUseWideViewPort(true);

        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback cb) {
                boolean granted = permissionGranted(Manifest.permission.ACCESS_FINE_LOCATION);
                cb.invoke(origin, granted, false);
            }
        });
        webView.addJavascriptInterface(new NativeBridge(), "AndroidPrayer");
        // تحميل نسخة محدَّثة من HTML إن وُجدت في getFilesDir()، وإلا fallback للأصل في assets/
        File updatedHtml = new File(getFilesDir(), "prayer-times.html");
        if (updatedHtml.exists() && updatedHtml.length() > 1000) {
            webView.loadUrl("file://" + updatedHtml.getAbsolutePath());
        } else {
            webView.loadUrl("file:///android_asset/prayer-times.html");
        }

        tts = new PrayerTts(getApplicationContext());
        requestRuntimePermissions();
        KeepAliveService.start(this);

        // فحص تحديثات HTML من GitHub في الخلفية (يلتقط prayer-times.html الجديد)
        UpdateChecker.checkAndDownload(this, null);
    }

    @Override
    public void onBackPressed() {
        // إبقاء التطبيق في الذاكرة حتى تعمل تنبيهات الأذان والإنذارات
        moveTaskToBack(true);
    }

    @Override
    protected void onDestroy() {
        if (tts != null) tts.shutdown();
        if (webView != null) webView.destroy();
        super.onDestroy();
    }

    private boolean permissionGranted(String perm) {
        if (Build.VERSION.SDK_INT >= 23) {
            return checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED;
        }
        return PackageManager.PERMISSION_GRANTED == getPackageManager()
                .checkPermission(perm, getPackageName());
    }

    private void requestRuntimePermissions() {
        if (Build.VERSION.SDK_INT < 23) return;
        ArrayList<String> need = new ArrayList<String>();
        if (!permissionGranted(Manifest.permission.ACCESS_FINE_LOCATION)) {
            need.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }
        if (!permissionGranted(Manifest.permission.ACCESS_COARSE_LOCATION)) {
            need.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        }
        if (Build.VERSION.SDK_INT >= 33 && !permissionGranted("android.permission.POST_NOTIFICATIONS")) {
            need.add("android.permission.POST_NOTIFICATIONS");
        }
        if (need.size() > 0) {
            try {
                requestPermissions(need.toArray(new String[0]), REQ_PERMS);
            } catch (Exception e) {}
        }
    }

    private static String httpGet(String urlStr) {
        HttpURLConnection conn = null;
        try {
            URL u = new URL(urlStr);
            conn = (HttpURLConnection) u.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(30000);
            conn.setReadTimeout(30000);
            conn.setInstanceFollowRedirects(true);
            // v23: User-Agent حقيقي لتجنب رفض GitHub raw
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36");
            conn.setRequestProperty("Accept", "application/json, text/plain, */*");
            conn.setRequestProperty("Accept-Encoding", "identity");
            int code = conn.getResponseCode();
            android.util.Log.i("PrayerTimes", "httpGet " + urlStr + " → " + code);
            if (code != 200) return "";
            InputStream is = conn.getInputStream();
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) > 0) bos.write(buf, 0, n);
            is.close();
            String result = new String(bos.toByteArray(), "UTF-8");
            android.util.Log.i("PrayerTimes", "httpGot " + result.length() + " bytes");
            return result;
        } catch (Exception e) {
            android.util.Log.e("PrayerTimes", "httpGet FAILED for " + urlStr + " — " + e.getClass().getSimpleName() + ": " + e.getMessage(), e);
            return "";
        } finally {
            try { if (conn != null) conn.disconnect(); } catch (Exception e2) {}
        }
    }

    public static String cleanForTts(String s) {
        if (s == null) return "";
        String out = s.replaceAll("[^\\p{L}\\p{N} ,.:!%؟،\\-]", " ").trim();
        out = out.replaceAll(" +", " ");
        return out;
    }

    private void cancelAllNativeAlarms() {
        AlarmScheduler.cancelAll(this);
    }

    private void scheduleNativeAlarms(String json) {
        AlarmScheduler.schedule(this, json);
    }

    private class NativeBridge {

        @JavascriptInterface
        public void fetchUrl(final String url, final String callback) {
            new Thread(new Runnable() {
                public void run() {
                    final String body = httpGet(url);
                    sLastFetchResult = body;
                    if (callback != null && callback.trim().length() > 0) {
                        final String js = callback.trim() + "(" + JSONObject.quote(body) + ")";
                        runOnUiThread(new Runnable() {
                            public void run() {
                                try {
                                    if (webView != null) webView.evaluateJavascript(js, null);
                                } catch (Exception e) {}
                            }
                        });
                    }
                }
            }).start();
        }

        @JavascriptInterface
        public String getFetchResult() {
            return sLastFetchResult;
        }

        @JavascriptInterface
        public void playTtsNow(String text, String title, String subtitle) {
            if (tts != null) tts.speakNow(cleanForTts(text));
        }

        @JavascriptInterface
        public void playAudioNow(String file, String title, String subtitle) {
            PrayerAudioService.play(MainActivity.this, file,
                    title == null ? "" : title,
                    subtitle == null ? "" : subtitle, 1001);
        }

        @JavascriptInterface
        public void stopAudio() {
            PrayerAudioService.stop(MainActivity.this);
            if (tts != null) tts.stop();
        }

        @JavascriptInterface
        public void cancelAllAlarms() {
            cancelAllNativeAlarms();
        }

        @JavascriptInterface
        public void clearAlarms() {
            cancelAllNativeAlarms();
        }

        @JavascriptInterface
        public void scheduleAlarms(String json) {
            scheduleNativeAlarms(json);
        }

        @JavascriptInterface
        public void startBackgroundService() {
            KeepAliveService.start(MainActivity.this);
        }

        @JavascriptInterface
        public boolean canScheduleExactAlarms() {
            if (Build.VERSION.SDK_INT >= 31) {
                try {
                    AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
                    return am.canScheduleExactAlarms();
                } catch (Exception e) {
                    return true;
                }
            }
            return true;
        }

        @JavascriptInterface
        public void requestExactAlarmPermission() {
            if (Build.VERSION.SDK_INT >= 31) {
                try {
                    Intent it = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                            Uri.parse("package:" + getPackageName()));
                    startActivity(it);
                } catch (Exception e) {}
            }
        }

        // ===== معرّف الجهاز للرسائل الموجَّهة (يستخدم ANDROID_ID الثابت) =====
        @JavascriptInterface
        public String getDeviceId() {
            try {
                String androidId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
                if (androidId != null && androidId.length() >= 6) return androidId;
            } catch (Exception e) {}
            // fallback: معرّف عشوائي يُخزَّن في SharedPreferences
            try {
                String saved = getSharedPreferences("pt-prefs", 0).getString("fallback-device-id", "");
                if (saved != null && saved.length() > 0) return saved;
                String newId = "fallback-" + System.currentTimeMillis() + "-" + (int)(Math.random() * 100000);
                getSharedPreferences("pt-prefs", 0).edit().putString("fallback-device-id", newId).apply();
                return newId;
            } catch (Exception e2) { return "unknown"; }
        }

        // ===== فتح رابط في المتصفح الافتراضي للجهاز (يُبقي التطبيق مفتوحاً) =====
        @JavascriptInterface
        public void openExternalUrl(String url) {
            try {
                Intent it = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(it);
            } catch (Exception e) {}
        }

        // ===== v25.5: إجراء مكالمة هاتفية — يستدعيه JS عند الضغط على رقم هاتف =====
        @JavascriptInterface
        public void callPhone(String phone) {
            try {
                // تنظيف الرقم من المسافات والشرطات
                String cleanNum = phone.replaceAll("[\\s\\-().]", "");
                Intent it = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + cleanNum));
                it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(it);
            } catch (Exception e) {
                android.util.Log.e("PrayerTimes", "callPhone FAILED for " + phone + " — " + e.getMessage(), e);
            }
        }

        // ===== تنزيل تحديث HTML جديد من GitHub (يستدعيه JS عند توفر نسخة جديدة) =====
        @JavascriptInterface
        public void downloadAppUpdate(final String jsCallback) {
            new Thread(new Runnable() {
                public void run() {
                    boolean ok = UpdateChecker.checkAndDownload(MainActivity.this, null);
                    if (jsCallback != null && jsCallback.trim().length() > 0) {
                        final String call = jsCallback.trim() + "(" + (ok ? "true" : "false") + ")";
                        runOnUiThread(new Runnable() {
                            public void run() {
                                try { if (webView != null) webView.evaluateJavascript(call, null); } catch (Exception e) {}
                            }
                        });
                    }
                }
            }).start();
        }
    }
}
