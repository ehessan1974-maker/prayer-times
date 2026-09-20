package com.ehessan.prayertimes;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;

/**
 * جدولة إنذارات المواقيت — تُستخدم من MainActivity (عبر جسر JavaScript)
 * ومن BootReceiver (إعادة الجدولة بعد إعادة تشغيل الهاتف أو تغيّر الوقت/المنطقة الزمنية).
 * يحفظ آخر جدولة JSON في SharedPreferences لتتمكن إعادة التسليح دون فتح التطبيق.
 */
public final class AlarmScheduler {

    public static final String ACTION_ALARM_FIRE = "com.ehessan.prayertimes.ALARM_FIRE";
    private static final String PREFS = "pt-prefs";
    private static final String PREF_ALARM_CODES = "alarm-codes";
    private static final String PREF_ALARM_DATA = "alarm-data-json";

    private AlarmScheduler() {}

    /** إلغاء كل الإنذارات المخزنة (بالرموز المحفوظة) */
    public static void cancelAll(Context ctx) {
        try {
            AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
            SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            String saved = sp.getString(PREF_ALARM_CODES, "");
            if (saved != null && saved.length() > 0) {
                String[] toks = saved.split(",");
                for (int i = 0; i < toks.length; i++) {
                    String t = toks[i].trim();
                    if (t.length() == 0) continue;
                    try {
                        int rc = Integer.parseInt(t);
                        Intent it = new Intent(ctx, AlarmReceiver.class);
                        it.setAction(ACTION_ALARM_FIRE);
                        int flags = PendingIntent.FLAG_NO_CREATE;
                        if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
                        PendingIntent pi = PendingIntent.getBroadcast(ctx, rc, it, flags);
                        if (pi != null) am.cancel(pi);
                    } catch (Exception e2) {}
                }
            }
            sp.edit().remove(PREF_ALARM_CODES).apply();
        } catch (Exception e) {}
    }

    /** جدولة دفعة إنذارات جديدة (JSON من صفحة الويب) + حفظها لإعادة التسليح لاحقاً */
    public static void schedule(Context ctx, String json) {
        try {
            cancelAll(ctx);
            if (json == null || json.length() == 0) return;
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
               .edit().putString(PREF_ALARM_DATA, json).apply();
            armAll(ctx, json);
        } catch (Exception e) {}
    }

    /** إعادة تسليح كل المنبهات المخزنة التي لم يحِن وقتها بعد (بعد إعادة التشغيل/تغيير الوقت) */
    public static void rearmStored(Context ctx) {
        try {
            String j = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                          .getString(PREF_ALARM_DATA, "");
            if (j == null || j.length() == 0) return;
            cancelAll(ctx);
            armAll(ctx, j);
        } catch (Exception e) {}
    }

    private static void armAll(Context ctx, String json) {
        try {
            AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
            JSONArray arr = new JSONArray(json);
            ArrayList<Integer> codes = new ArrayList<Integer>();
            for (int i = 0; i < arr.length(); i++) {
                try {
                    JSONObject o = arr.getJSONObject(i);
                    long triggerAtMs = o.getLong("triggerAtMs");
                    if (triggerAtMs <= System.currentTimeMillis()) continue;
                    String audioFile = o.optString("audioFile", "");
                    String title = o.optString("title", "");
                    String text = o.optString("text", "");
                    int notifId = o.optInt("notifId", 1000 + i);
                    int requestCode = o.optInt("requestCode", i);

                    Intent it = new Intent(ctx, AlarmReceiver.class);
                    it.setAction(ACTION_ALARM_FIRE);
                    it.putExtra("audioFile", audioFile);
                    it.putExtra("title", title);
                    it.putExtra("text", text);
                    it.putExtra("notifId", notifId);

                    int flags = PendingIntent.FLAG_UPDATE_CURRENT;
                    if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
                    PendingIntent pi = PendingIntent.getBroadcast(ctx, requestCode, it, flags);

                    boolean canExact = true;
                    if (Build.VERSION.SDK_INT >= 31) canExact = am.canScheduleExactAlarms();
                    if (!canExact) {
                        // بلا صلاحية المنبه الدقيق: نافذة 60 ثانية (تتأخر في Doze — لذلك نطلب USE_EXACT_ALARM في الـManifest)
                        am.setWindow(AlarmManager.RTC_WAKEUP, triggerAtMs, 60000, pi);
                    } else if (Build.VERSION.SDK_INT >= 23) {
                        // يعمل بدقة حتى في وضع Doze العميق (فجراً)
                        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMs, pi);
                    } else {
                        am.setExact(AlarmManager.RTC_WAKEUP, triggerAtMs, pi);
                    }
                    codes.add(requestCode);
                } catch (Exception inner) {}
            }
            SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < codes.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append(codes.get(i).intValue());
            }
            sp.edit().putString(PREF_ALARM_CODES, sb.toString()).apply();
        } catch (Exception e) {}
    }
}
