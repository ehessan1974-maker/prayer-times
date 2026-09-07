package com.ehessan.prayertimes;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

/** خدمة خلفية خفيفة تُبقي العملية حيّة لضمان دقة إنذارات المواقيت */
public class KeepAliveService extends Service {

    private static final String CHANNEL_ID = "prayer_keepalive";
    private static final int NOTIF_ID = 99;

    public static void start(Context c) {
        try {
            Intent it = new Intent(c, KeepAliveService.class);
            if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(it);
            else c.startService(it);
        } catch (Exception e) {}
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        goForeground();
        return START_STICKY;
    }

    private void goForeground() {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CHANNEL_ID, getString(R.string.app_name),
                    NotificationManager.IMPORTANCE_MIN);
            ch.setShowBadge(false);
            nm.createNotificationChannel(ch);
        }
        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= 26) {
            b = new Notification.Builder(this, CHANNEL_ID);
        } else {
            b = new Notification.Builder(this);
            b.setPriority(Notification.PRIORITY_MIN);
        }
        b.setSmallIcon(R.drawable.ic_stat)
         .setContentTitle(getString(R.string.app_name))
         .setContentText("تنبيهات المواقيت مفعّلة")
         .setOngoing(true)
         .setOnlyAlertOnce(true);
        Notification n = b.build();
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, n, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIF_ID, n);
        }
    }

    @Override
    public void onDestroy() {
        try { stopForeground(true); } catch (Exception e) {}
        super.onDestroy();
    }
}
