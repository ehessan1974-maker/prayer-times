package com.ehessan.prayertimes;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** يستقبل إنذارات المواقيت المجدولة ويشغّل الصوت ولو كان التطبيق مغلقاً */
public class AlarmReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        try {
            if (intent == null) return;
            String action = intent.getAction();
            if (AlarmScheduler.ACTION_ALARM_FIRE.equals(action)) {
                // تشغيل الصوت + إشعار نظام بزر إيقاف — دون فتح/إحضار واجهة التطبيق (وضع الخلفية)
                PrayerAudioService.play(
                        context,
                        intent.getStringExtra("audioFile"),
                        intent.getStringExtra("title"),
                        intent.getStringExtra("text"),
                        intent.getIntExtra("notifId", 1001));
            }
        } catch (Exception e) {}
    }
}
