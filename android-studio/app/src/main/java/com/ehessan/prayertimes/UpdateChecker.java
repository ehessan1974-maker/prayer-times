package com.ehessan.prayertimes;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * UpdateChecker — يفحص version.json على GitHub دورياً ويُنزّل prayer-times.html
 * الجديد إلى getFilesDir()/prayer-times.html. الـ MainActivity يحمّل هذا الملف
 * بدلاً من assets/prayer-times.html عند الإقلاع، فيتحقق التحديث دون إعادة تثبيت APK.
 *
 * يُستدعى أيضاً من NativeBridge.downloadAppUpdate() عند الضغط على toast "تحديث متوفر".
 */
public class UpdateChecker {

    // v25.43: مصادر متعددة — إذا فشل jsDelivr (حجب/تعثر شبكة) نجرّب GitHub Pages ثم raw مباشرة
    private static final String[] VERSION_URLS = {
            "https://cdn.jsdelivr.net/gh/ehessan1974-maker/prayer-times@main/version.json",
            "https://ehessan1974-maker.github.io/prayer-times/version.json",
            "https://raw.githubusercontent.com/ehessan1974-maker/prayer-times/main/version.json"
    };
    private static final String[] HTML_URLS = {
            "https://cdn.jsdelivr.net/gh/ehessan1974-maker/prayer-times@main/prayer-times.html",
            "https://ehessan1974-maker.github.io/prayer-times/prayer-times.html",
            "https://raw.githubusercontent.com/ehessan1974-maker/prayer-times/main/prayer-times.html"
    };
    // v25.36: نسخة مبسطة للأجهزة القديمة (Android 4.2.2+)
    private static final String[] LITE_HTML_URLS = {
            "https://cdn.jsdelivr.net/gh/ehessan1974-maker/prayer-times@main/prayer-times-lite.html",
            "https://ehessan1974-maker.github.io/prayer-times/prayer-times-lite.html",
            "https://raw.githubusercontent.com/ehessan1974-maker/prayer-times/main/prayer-times-lite.html"
    };
    private static final long INTERVAL_MS = 5 * 60 * 1000; // 5 دقائق
    private static final String PREF_NAME = "pt-prefs";
    private static final String PREF_LAST_VERSION = "last-known-app-version";

    private static Handler handler;
    private static Runnable loop;

    /** يبدأ حلقة الفحص الدورية. يجب استدعاؤها مرة واحدة (مثلاً من KeepAliveService). */
    public static void startPeriodicCheck(final Context ctx) {
        if (handler != null) return;
        handler = new Handler(Looper.getMainLooper());
        loop = new Runnable() {
            public void run() {
                try { checkAndDownload(ctx, null); } catch (Exception e) {}
                handler.postDelayed(loop, INTERVAL_MS);
            }
        };
        handler.postDelayed(loop, 30000); // أول فحص بعد 30 ثانية
    }

    /**
     * يفحص version.json؛ إن كانت النسخة أحدث من المحفوظة، يُنزّل HTML الجديد
     * ويحفظه في getFilesDir()/prayer-times.html.
     * @param jsToastCallback اسم دالة JS (اختياري) تُستدعى بعد اكتمال التنزيل
     * @return true إن نُزّل تحديث، false إن لا توجد تحديثات أو فشل التنزيل
     */
    public static boolean checkAndDownload(Context ctx, String jsToastCallback) {
        try {
            String versionJson = httpGetAny(VERSION_URLS);
            if (versionJson == null || versionJson.length() == 0) return false;

            JSONObject info = new JSONObject(versionJson);
            String latest = info.optString("appVersion", "");
            if (latest.length() == 0) return false;

            String known = ctx.getSharedPreferences(PREF_NAME, 0)
                    .getString(PREF_LAST_VERSION, "16.0");

            if (latest.equals(known)) return false; // لا تحديث جديد

            // نزّل HTML الجديد (يجرّب المصادر بالترتيب حتى ينجح واحد)
            String html = httpGetAny(HTML_URLS);
            if (html == null || html.length() < 1000) return false;

            File out = new File(ctx.getFilesDir(), "prayer-times.html");
            File tmp = new File(ctx.getFilesDir(), "prayer-times.html.tmp");
            FileOutputStream fos = new FileOutputStream(tmp);
            fos.write(html.getBytes("UTF-8"));
            fos.flush();
            fos.close();

            // استبدال ذري
            if (out.exists()) out.delete();
            tmp.renameTo(out);

            // v25.36: نزّل أيضاً النسخة المبسطة (Lite) للأجهزة القديمة
            try {
                String liteHtml = httpGetAny(LITE_HTML_URLS);
                if (liteHtml != null && liteHtml.length() > 1000) {
                    File liteOut = new File(ctx.getFilesDir(), "prayer-times-lite.html");
                    File liteTmp = new File(ctx.getFilesDir(), "prayer-times-lite.html.tmp");
                    FileOutputStream liteFos = new FileOutputStream(liteTmp);
                    liteFos.write(liteHtml.getBytes("UTF-8"));
                    liteFos.flush();
                    liteFos.close();
                    if (liteOut.exists()) liteOut.delete();
                    liteTmp.renameTo(liteOut);
                }
            } catch(Exception e2) {
                // ليس ضرورياً — النسخة المبسطة اختيارية
            }

            // حفظ النسخة الجديدة
            ctx.getSharedPreferences(PREF_NAME, 0).edit()
                    .putString(PREF_LAST_VERSION, latest)
                    .apply();

            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** v25.43: يجرّب المصادر بالترتيب ويعيد أول استجابة غير فارغة — cache-busting لكل محاولة */
    private static String httpGetAny(String[] urls) {
        for (String u : urls) {
            String r = httpGet(u + (u.contains("?") ? "&" : "?") + "t=" + System.currentTimeMillis());
            if (r != null && r.length() > 0) return r;
        }
        return "";
    }

    private static String httpGet(String urlStr) {
        HttpURLConnection conn = null;
        try {
            URL u = new URL(urlStr);
            conn = (HttpURLConnection) u.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(20000);
            conn.setInstanceFollowRedirects(true);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) PrayerTimes/Updater");
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
}
