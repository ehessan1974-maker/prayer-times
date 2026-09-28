package com.ehessan.prayertimes;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlarmManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class MainActivity extends Activity {

    private static final int REQ_PERMS = 100;

    private WebView webView;
    private PrayerTts tts;
    // v25.54: انتظار رد WebChromeClient بعد طلب إذن الموقع (حوار النظام)
    private GeolocationPermissions.Callback pendingGeoCb;
    private String pendingGeoOrigin;
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
                // v25.54: الإذن ممنوح مسبقاً → اسمح فوراً. غير ذلك اطلبه فعلياً (حوار النظام)
                // بدل الرفض الصامت الذي كان يجعل «تلقائي» فاشلاً بلا أي رسالة على J5 —
                // طلب الإذن الوحيد كان عند الإقلاع فقط، ومن فوّت الحوار فيها حُرم أبداً.
                if (permissionGranted(Manifest.permission.ACCESS_FINE_LOCATION)
                        || permissionGranted(Manifest.permission.ACCESS_COARSE_LOCATION)) {
                    cb.invoke(origin, true, false);
                    return;
                }
                pendingGeoOrigin = origin;
                pendingGeoCb = cb;
                requestRuntimePermissions();
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
        // v25.50: في خيط خلفي — كان على UI thread فيرمي NetworkOnMainThreadException
        // بصمت ولا يعمل فحص الإقلاع أصلاً
        new Thread(new Runnable() {
            public void run() {
                try { UpdateChecker.checkAndDownload(MainActivity.this, null); } catch (Exception e) {}
            }
        }).start();
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

    // v25.54: عند اكتمال حوار إذن النظام — أجب استدعاء WebView المعلق (onGeolocationPermissionsShowPrompt)
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == REQ_PERMS && pendingGeoCb != null) {
            boolean ok = permissionGranted(Manifest.permission.ACCESS_FINE_LOCATION)
                    || permissionGranted(Manifest.permission.ACCESS_COARSE_LOCATION);
            GeolocationPermissions.Callback cb = pendingGeoCb;
            String origin = pendingGeoOrigin;
            pendingGeoCb = null;
            pendingGeoOrigin = null;
            try { cb.invoke(origin, ok, false); } catch (Exception e) {}
        }
    }

    private static String httpGet(String urlStr) {
        return httpGet(urlStr, 30000);
    }

    // v25.47: overload بمهلة قصيرة لعمليات الواجهة التفاعلية (البحث عن المدن) —
    // 30 ثانية كانت تجعل المستخدم ينتظر قبل أن يسقط الـ JS إلى المصدر البديل
    private static String httpGet(String urlStr, int timeoutMs) {
        HttpURLConnection conn = null;
        try {
            URL u = new URL(urlStr);
            conn = (HttpURLConnection) u.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(timeoutMs);
            conn.setReadTimeout(timeoutMs);
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

    // v25.54: منطق تحديد الموقع الأصلي — يعمل حتى بلا WebView (LocationManager مباشرة)
    private String doNativeLocation(int timeoutMs) {
        try {
            boolean fine = permissionGranted(Manifest.permission.ACCESS_FINE_LOCATION);
            boolean coarse = permissionGranted(Manifest.permission.ACCESS_COARSE_LOCATION);
            if (!fine && !coarse) return "{\"ok\":false,\"reason\":\"permission\"}";
            LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            if (lm == null) return "{\"ok\":false,\"reason\":\"error\"}";
            boolean netOn = false, gpsOn = false;
            try { netOn = lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER); } catch (Exception e) {}
            try { gpsOn = lm.isProviderEnabled(LocationManager.GPS_PROVIDER); } catch (Exception e) {}
            if (!netOn && !gpsOn) return "{\"ok\":false,\"reason\":\"disabled\"}";
            // 1) آخر موقع معروف — إجابة فورية إن كانت طازجة (≤10 دقائق تكفي لدقة مواقيت الصلاة)
            Location best = null;
            String[] provs = {LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER};
            for (int i = 0; i < provs.length; i++) {
                try {
                    Location l = lm.getLastKnownLocation(provs[i]);
                    if (l != null && (best == null || l.getTime() > best.getTime())) best = l;
                } catch (Exception e) {}
            }
            if (best != null && System.currentTimeMillis() - best.getTime() <= 600000) return locToJson(best);
            // 2) إصلاح واحد سريع: الشبكة (أسرع داخلياً) ثم GPS
            final CountDownLatch latch = new CountDownLatch(1);
            final Location[] fresh = new Location[1];
            LocationListener ll = new LocationListener() {
                public void onLocationChanged(Location l) {
                    if (l == null) return;
                    synchronized (fresh) { if (fresh[0] == null) fresh[0] = l; }
                    latch.countDown();
                }
                public void onStatusChanged(String p, int s, android.os.Bundle b) {}
                public void onProviderEnabled(String p) {}
                public void onProviderDisabled(String p) {}
            };
            try {
                if (netOn) lm.requestSingleUpdate(LocationManager.NETWORK_PROVIDER, ll, getMainLooper());
                if (gpsOn) lm.requestSingleUpdate(LocationManager.GPS_PROVIDER, ll, getMainLooper());
            } catch (Exception e) {}
            try { latch.await(timeoutMs, TimeUnit.MILLISECONDS); } catch (InterruptedException ie) {}
            try { lm.removeUpdates(ll); } catch (Exception e) {}
            Location chosen = (fresh[0] != null) ? fresh[0] : best; // طازج إن وصل، وإلا آخر معروف قديم
            if (chosen != null) return locToJson(chosen);
            return "{\"ok\":false,\"reason\":\"timeout\"}";
        } catch (Throwable t) {
            return "{\"ok\":false,\"reason\":\"error\"}";
        }
    }

    private String locToJson(Location l) {
        StringBuilder sb = new StringBuilder("{\"ok\":true");
        sb.append(",\"lat\":").append(String.format(java.util.Locale.US, "%.6f", l.getLatitude()));
        sb.append(",\"lng\":").append(String.format(java.util.Locale.US, "%.6f", l.getLongitude()));
        sb.append(",\"provider\":").append(JSONObject.quote(l.getProvider() == null ? "" : l.getProvider()));
        sb.append(",\"ageMs\":").append(System.currentTimeMillis() - l.getTime());
        try { if (l.hasAccuracy()) sb.append(",\"acc\":").append(l.getAccuracy()); } catch (Exception e) {}
        sb.append("}");
        return sb.toString();
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

        // v25.47: نفس fetchUrl بمهلة قصيرة يحددها الـ JS — للبحث عن المدن والعمليات التفاعلية.
        // غياب هذه الدالة على APK قديم آمن: الـ JS يفحص وجودها قبل الاستخدام ويسقط لـ fetchUrl
        @JavascriptInterface
        public void fetchUrlT(final String url, final String callback, final int timeoutMs) {
            new Thread(new Runnable() {
                public void run() {
                    final String body = httpGet(url, timeoutMs > 0 ? timeoutMs : 10000);
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

        // ===== v25.54: تحديد الموقع عبر القناة الأصلية — أكثر موثوقية من WebView القديم (J5) =====
        // يعيد فوراً آخر موقع معروف إن كان طازجاً (≤10 دقائق)، وإلا يطلب إصلاحاً واحداً
        // من مزوّد الشبكة/GPS خلال المهلة، وإلا يرجع بآخر موقع معروف قديم، وإلا سبب الفشل.
        @JavascriptInterface
        public void nativeLocation(final int timeoutMs, final String callback) {
            new Thread(new Runnable() {
                public void run() {
                    final String json = doNativeLocation(timeoutMs > 0 ? Math.min(timeoutMs, 15000) : 8000);
                    if (callback != null && callback.trim().length() > 0) {
                        final String js = callback.trim() + "(" + JSONObject.quote(json) + ")";
                        runOnUiThread(new Runnable() {
                            public void run() {
                                try { if (webView != null) webView.evaluateJavascript(js, null); } catch (Exception e) {}
                            }
                        });
                    }
                }
            }).start();
        }

        // v25.54: إعادة طلب إذن الموقع من الواجهة (يفتح حوار النظام إن كان ممكناً)
        @JavascriptInterface
        public void requestLocationPermission() {
            runOnUiThread(new Runnable() {
                public void run() { requestRuntimePermissions(); }
            });
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

        // v25.33: إعادة تحميل WebView من الملف المحدّث في getFilesDir
        // بدلاً من إعادة تحميل الصفحة الحالية (القديمة)
        @JavascriptInterface
        public void reloadUpdatedHtml() {
            runOnUiThread(new Runnable() {
                public void run() {
                    try {
                        File updatedHtml = new File(getFilesDir(), "prayer-times.html");
                        if (updatedHtml.exists() && updatedHtml.length() > 1000) {
                            // حمّل الملف المحدّث
                            webView.loadUrl("file://" + updatedHtml.getAbsolutePath() + "?t=" + System.currentTimeMillis());
                            android.util.Log.i("PrayerTimes", "✓ Reloaded updated HTML from getFilesDir");
                        } else {
                            // لا يوجد ملف محدّث — أعد تحميل الصفحة الحالية
                            webView.reload();
                            android.util.Log.i("PrayerTimes", "No updated HTML found, reloaded current page");
                        }
                    } catch (Exception e) {
                        android.util.Log.e("PrayerTimes", "reloadUpdatedHtml failed: " + e.getMessage(), e);
                        try { webView.reload(); } catch(Exception e2) {}
                    }
                }
            });
        }
    }
}
