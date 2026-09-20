package com.ehessan.prayertimes;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * يعيد جدولة منبهات المواقيت تلقائياً بعد:
 *  - إعادة تشغيل الهاتف (BOOT_COMPLETED)
 *  - تغيير وقت النظام يدوياً أو تلقائياً (TIME_SET)
 *  - تغيير المنطقة الزمنية (TIMEZONE_CHANGED)
 *  - تحديث التطبيق (MY_PACKAGE_REPLACED)
 * كانت المنبهات تضيع نهائياً بعد إعادة التشغيل حتى يُفتح التطبيق مجدداً.
 */
public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        try {
            String action = (intent == null) ? "" : intent.getAction();
            boolean hit =
                Intent.ACTION_BOOT_COMPLETED.equals(action) ||
                Intent.ACTION_TIME_CHANGED.equals(action) ||
                Intent.ACTION_TIMEZONE_CHANGED.equals(action) ||
                Intent.ACTION_MY_PACKAGE_REPLACED.equals(action);
            if (hit) {
                // وضع الخلفية: إعادة تسليح المنبهات + إبقاء العملية حيّة — دون فتح واجهة التطبيق
                AlarmScheduler.rearmStored(context);
                KeepAliveService.start(context);
            }
        } catch (Exception e) {}
    }
}
