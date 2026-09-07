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
import android.provider.Settings;
import android.webkit.GeolocationPermissions;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
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
        webView.loadUrl("file:///android_asset/prayer-times.html");

        tts = new PrayerTts(getApplicationContext());
        requestRuntimePermissions();
        KeepAliveService.start(this);
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
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);
            conn.setInstanceFollowRedirects(true);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) PrayerTimes/1.0");
            int code = conn.getResponseCode();
            if (code != 200) return "";
            InputStream is = conn.getInputStream();
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) > 0) bos.write(buf, 0, n);
            is.close();
            return new String(bos.toByteArray(), "UTF-8");
        } catch (Exception e) {
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
    }
}
