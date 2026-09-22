package com.ehessan.prayertimes;

import android.os.Build;
import android.util.Log;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Tls12Helper — يعالج مشكلة Samsung J5 (Android 5.x) التي تمنع جلب content.json
 *
 * المشكلة:
 *   على Android 5.x (API 16-22)، HttpsURLConnection يفعّل TLS 1.0 فقط افتراضياً.
 *   raw.githubusercontent.com يتطلب TLS 1.2+، فيفشل مصافحة SSL بصمت.
 *   النتيجة: AndroidPrayer.fetchUrl() يرجع ""، و XHR في WebView يفشل أيضاً
 *   لأن WebView على Android 5.x يستخدم نفس Java SSL stack.
 *
 * الحل:
 *   تركيب SSLSocketFactory مخصّصة على HttpsURLConnection.setDefaultSSLSocketFactory()
 *   تُفعّل TLS 1.1 و 1.2 على كل socket. هذا يصلح:
 *     - AndroidPrayer.fetchUrl() (يستخدم HttpsURLConnection في MainActivity.httpGet)
 *     - UpdateChecker.httpGet()
 *     - WebView XHR على Android 5.x (يستخدم Java SSL stack)
 *
 * يجب استدعاء Tls12Helper.enable() مرة واحدة في بداية MainActivity.onCreate()
 * قبل أي اتصال HTTPS.
 */
public class Tls12Helper {

    private static final String TAG = "Tls12Helper";
    private static boolean sEnabled = false;

    public static synchronized void enable() {
        if (sEnabled) return;

        // على Android 5.0+ (API 21+), TLS 1.2 مدعوم لكن غير مُفعّل افتراضياً في HttpsURLConnection
        // على Android 6+ (API 23+)، TLS 1.2 مُفعّل افتراضياً
        if (Build.VERSION.SDK_INT >= 23) {
            sEnabled = true;
            return;
        }

        if (Build.VERSION.SDK_INT < 16) {
            // Honeycomb وأقدم — لا يدعم TLS 1.2 إطلاقاً
            Log.w(TAG, "Android API < 16 — TLS 1.2 غير مدعوم على هذا الجهاز");
            sEnabled = true;
            return;
        }

        try {
            SSLContext sc = SSLContext.getInstance("TLSv1.2");
            sc.init(null, null, null);
            final SSLSocketFactory baseFactory = sc.getSocketFactory();

            HttpsURLConnection.setDefaultSSLSocketFactory(new SSLSocketFactory() {
                @Override
                public String[] getDefaultCipherSuites() {
                    return baseFactory.getDefaultCipherSuites();
                }

                @Override
                public String[] getSupportedCipherSuites() {
                    return baseFactory.getSupportedCipherSuites();
                }

                @Override
                public Socket createSocket(Socket s, String host, int port, boolean autoClose) throws IOException {
                    return enableTls(baseFactory.createSocket(s, host, port, autoClose));
                }

                @Override
                public Socket createSocket(String host, int port) throws IOException {
                    return enableTls(baseFactory.createSocket(host, port));
                }

                @Override
                public Socket createSocket(String host, int port, InetAddress localHost, int localPort) throws IOException {
                    return enableTls(baseFactory.createSocket(host, port, localHost, localPort));
                }

                @Override
                public Socket createSocket(InetAddress host, int port) throws IOException {
                    return enableTls(baseFactory.createSocket(host, port));
                }

                @Override
                public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort) throws IOException {
                    return enableTls(baseFactory.createSocket(address, port, localAddress, localPort));
                }
            });

            sEnabled = true;
            Log.i(TAG, "✅ تم تفعيل TLS 1.2 بنجاح (Android API " + Build.VERSION.SDK_INT + ")");
        } catch (Exception e) {
            Log.e(TAG, "❌ فشل تفعيل TLS 1.2: " + e.getMessage(), e);
            sEnabled = true; // لمنع إعادة المحاولة دون جدوى
        }
    }

    /** يُفعّل TLS 1.1 و 1.2 على الـ socket إن كان مدعوماً */
    private static Socket enableTls(Socket socket) {
        if (socket instanceof SSLSocket) {
            SSLSocket ssl = (SSLSocket) socket;
            List<String> supported = Arrays.asList(ssl.getSupportedProtocols());
            List<String> enabled = new ArrayList<>();

            // الأولوية لـ TLS 1.2 ثم 1.1 ثم 1.0 (احتياط)
            if (supported.contains("TLSv1.2")) enabled.add("TLSv1.2");
            if (supported.contains("TLSv1.1")) enabled.add("TLSv1.1");
            if (supported.contains("TLSv1"))   enabled.add("TLSv1");

            if (enabled.size() > 0) {
                try {
                    ssl.setEnabledProtocols(enabled.toArray(new String[0]));
                } catch (Exception e) {
                    Log.w(TAG, "تعذر تعيين بروتوكولات SSL: " + e.getMessage());
                }
            }
        }
        return socket;
    }
}
