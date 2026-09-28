package com.ehessan.prayertimes;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
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
 *
 * v25.50: إصلاحات حلقة «يوجد تحديث جديد»:
 *  1) قرار التنزيل من نسخة الملف الفعلي على القرص (لا من pref قد ينحرف) — ذاتي الإصلاح
 *  2) تحقق إلزامي أن HTML المنزّل يحتوي CURRENT_APP_VERSION = "الأحدث" قبل الحفظ
 *     (يمنع تثبيت نسخة قديمة من كاش CDN ثم "قفل" pref عليها = حلقة أبدية)
 *  3) التنفيذ في خيط خلفي — كان يُستدعى على UI thread/main looper فيرمي
 *     NetworkOnMainThreadException بصمت فلا يعمل الفحص الإقلاعي ولا الدوري أصلاً
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
                // v25.50: في خيط خلفي — كان على الـ main looper فيرمي NetworkOnMainThreadException
                // بصمت ولا يعمل الفحص الدوري أصلاً
                new Thread(new Runnable() {
                    public void run() {
                        try { checkAndDownload(ctx, null); } catch (Exception e) {}
                    }
                }).start();
                handler.postDelayed(loop, INTERVAL_MS);
            }
        };
        handler.postDelayed(loop, 30000); // أول فحص بعد 30 ثانية
    }

    /**
     * يفحص version.json؛ إن كانت النسخة أحدث من نسخة الملف الفعلي، يُنزّل HTML الجديد
     * (مع تحقق محتوى إلزامي) ويحفظه في getFilesDir()/prayer-times.html.
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

            // v25.50: القرار من الحقيقة الفعلية للملف على القرص — لا من pref قد ينحرف عنه.
            // ذاتي الإصلاح: جهاز حفظ pref=الأحدث لكن ملفه فعلاً قديم سيُنزّل من جديد بدل
            // الوقوع في حلقة «الرسالة تعود ولا يُنزّل شيء»
            File out = new File(ctx.getFilesDir(), "prayer-times.html");
            String fileVer = readHtmlVersion(out);
            if (fileVer != null && fileVer.equals(latest)) return false; // الملف فعلاً هو الأحدث

            // v25.50: نزّل مع تحقق إلزامي — يجب أن يحتوي HTML المنزّل فعلاً على رقم النسخة المتوقع
            String html = httpGetAnyVerified(HTML_URLS, "CURRENT_APP_VERSION = \"" + latest + "\"");
            if (html == null || html.length() < 1000) return false;

            File tmp = new File(ctx.getFilesDir(), "prayer-times.html.tmp");
            FileOutputStream fos = new FileOutputStream(tmp);
            fos.write(html.getBytes("UTF-8"));
            fos.flush();
            fos.close();

            // استبدال ذري
            if (out.exists()) out.delete();
            tmp.renameTo(out);

            // v25.36: نزّل أيضاً النسخة المبسطة (Lite) للأجهزة القديمة — مع تحقق مطابق
            try {
                String liteHtml = httpGetAnyVerified(LITE_HTML_URLS, "CURRENT_APP_VERSION = \"" + latest + "\"");
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

            // حفظ النسخة الجديدة (معلوماتية — القرار يعتمد على الملف)
            ctx.getSharedPreferences(PREF_NAME, 0).edit()
                    .putString(PREF_LAST_VERSION, latest)
                    .apply();

            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** v25.50: استخراج نسخة HTML المحفوظة من سطر CURRENT_APP_VERSION = "x" */
    private static String readHtmlVersion(File f) {
        try {
            if (f == null || !f.exists() || f.length() < 1000) return null;
            FileInputStream fis = new FileInputStream(f);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = fis.read(buf)) > 0) bos.write(buf, 0, n);
            fis.close();
            String s = new String(bos.toByteArray(), "UTF-8");
            String marker = "CURRENT_APP_VERSION = \"";
            int i = s.indexOf(marker);
            if (i < 0) return null;
            int j = s.indexOf("\"", i + marker.length());
            if (j < 0 || (j - i) > 40) return null;
            return s.substring(i + marker.length(), j);
        } catch (Exception e) {
            return null;
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

    /** v25.50: يجرّب المصادر ويعيد أول استجابة تحتوي فعلاً العلامة المطلوبة (تحقق محتوى) */
    private static String httpGetAnyVerified(String[] urls, String mustContain) {
        for (String u : urls) {
            String r = httpGet(u + (u.contains("?") ? "&" : "?") + "t=" + System.currentTimeMillis());
            if (r != null && r.length() > 1000 && r.contains(mustContain)) return r;
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
