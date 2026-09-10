package com.ehessan.prayertimes;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import java.io.File;
import java.util.Locale;

/**
 * خدمة أمامية لتشغيل صوت الأذان/الإقامة/التنبيهات من أصول التطبيق
 * أو من مجلد المستخدم الخارجي، مع بديل نطق تلقائي عند غياب الملف.
 */
public class PrayerAudioService extends Service {

    public static final String ACTION_PLAY = "com.ehessan.prayertimes.ACTION_PLAY";
    public static final String ACTION_STOP = "com.ehessan.prayertimes.ACTION_STOP";
    private static final String CHANNEL_ID = "prayer_alerts";

    private MediaPlayer player;
    private TextToSpeech tts;
    private volatile boolean ttsReady = false;
    private volatile String pendingTts = null;
    private PowerManager.WakeLock wakeLock;
    // مانع التكرار: تجاهل طلب تشغيل نفس الملف إذا كان الصوت يعمل فعلاً
    // (يمنع تشويش إعادة التشغيل عند تزامن الإنذار الأصلي مع نداء الصفحة المتزامن)
    private static volatile String sLastFile = "";
    private static volatile long sLastPlayAt = 0;
    private AudioManager audioManager;
    private AudioFocusRequest focusRequest;

    /** مستمع تركيز الصوت — الأذان/الإقامة لهما الأولوية: نستمر بالتشغيل ولا نخفض */
    private final AudioManager.OnAudioFocusChangeListener focusListener =
            new AudioManager.OnAudioFocusChangeListener() {
                @Override
                public void onAudioFocusChange(int focusChange) {
                    // نتجاهل فقدان التركيز المؤقت — تنبيهات المواقيت ذات أولوية
                }
            };

    /** المطالبة بأولوية الصوت: توقف تطبيقات الموسيقى/الفيديو الأخرى أثناء الأذان */
    private void requestAudioPriority() {
        try {
            audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            if (audioManager == null) return;
            AudioAttributes attrs = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build();
            if (Build.VERSION.SDK_INT >= 26) {
                focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                        .setAudioAttributes(attrs)
                        .setOnAudioFocusChangeListener(focusListener)
                        .build();
                audioManager.requestAudioFocus(focusRequest);
            } else {
                audioManager.requestAudioFocus(focusListener, AudioManager.STREAM_ALARM,
                        AudioManager.AUDIOFOCUS_GAIN);
            }
        } catch (Exception e) {}
    }

    /** إعادة التركيز بعد انتهاء التشغيل حتى تعود التطبيقات الأخرى لعملها الطبيعي */
    private void abandonAudioPriority() {
        try {
            if (audioManager == null) return;
            if (focusRequest != null && Build.VERSION.SDK_INT >= 26) {
                audioManager.abandonAudioFocusRequest(focusRequest);
            } else {
                audioManager.abandonAudioFocus(focusListener);
            }
        } catch (Exception e) {}
        focusRequest = null;
        audioManager = null;
    }

    public static void play(Context c, String file, String title, String text, int notifId) {
        Intent it = new Intent(c, PrayerAudioService.class);
        it.setAction(ACTION_PLAY);
        it.putExtra("audioFile", file == null ? "" : file);
        it.putExtra("title", title == null ? "" : title);
        it.putExtra("text", text == null ? "" : text);
        it.putExtra("notifId", notifId);
        if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(it);
        else c.startService(it);
    }

    public static void stop(Context c) {
        Intent it = new Intent(c, PrayerAudioService.class);
        it.setAction(ACTION_STOP);
        if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(it);
        else c.startService(it);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : ACTION_STOP;
        if (ACTION_STOP.equals(action)) {
            cleanup();
            stopSelf();
            return START_NOT_STICKY;
        }
        if (!ACTION_PLAY.equals(action) || intent == null) {
            stopSelf();
            return START_NOT_STICKY;
        }
        String audioFile = intent.getStringExtra("audioFile");
        String title = intent.getStringExtra("title");
        String text = intent.getStringExtra("text");
        int notifId = intent.getIntExtra("notifId", 1001);
        goForeground(notifId, title, text);
        acquireLock();
        requestAudioPriority();
        resolveAndPlay(audioFile, title);
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        cleanup();
        super.onDestroy();
    }

    private void goForeground(int notifId, String title, String text) {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CHANNEL_ID, getString(R.string.app_name),
                    NotificationManager.IMPORTANCE_HIGH);
            ch.setDescription("تنبيهات الأذان والإقامة");
            nm.createNotificationChannel(ch);
        }
        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= 26) {
            b = new Notification.Builder(this, CHANNEL_ID);
        } else {
            b = new Notification.Builder(this);
            b.setPriority(Notification.PRIORITY_MAX);
        }
        b.setSmallIcon(R.drawable.ic_stat)
         .setContentTitle(title == null || title.length() == 0 ? getString(R.string.app_name) : title)
         .setContentText(text == null ? "" : text)
         .setOngoing(true)
         .setOnlyAlertOnce(true)
         .setAutoCancel(false)
         // إشعار الحدث يظهر كمنبه وعلى شاشة القفل — زر الإيقاف موجود أدناه
         .setCategory(Notification.CATEGORY_ALARM)
         .setVisibility(Notification.VISIBILITY_PUBLIC);
        try {
            Intent open = new Intent(this, MainActivity.class);
            int piFlags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= 23) piFlags |= PendingIntent.FLAG_IMMUTABLE;
            b.setContentIntent(PendingIntent.getActivity(this, 77, open, piFlags));
        } catch (Exception e) {}
        try {
            Intent stopIt = new Intent(this, PrayerAudioService.class);
            stopIt.setAction(ACTION_STOP);
            int piFlags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= 23) piFlags |= PendingIntent.FLAG_IMMUTABLE;
            b.addAction(new Notification.Action(0, getString(R.string.stop_action),
                    PendingIntent.getService(this, 78, stopIt, piFlags)));
        } catch (Exception e2) {}
        Notification n = b.build();
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(notifId, n, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        } else {
            startForeground(notifId, n);
        }
    }

    private void acquireLock() {
        try {
            if (wakeLock != null && wakeLock.isHeld()) return;
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "PrayerTimes:AudioAlert");
            wakeLock.setReferenceCounted(false);
            wakeLock.acquire(20 * 60 * 1000L);
        } catch (Exception e) {}
    }

    private void resolveAndPlay(String file, String title) {
        String f = (file == null) ? "" : file.trim();
        // 0) تجاهل التكرار: نفس الملف خلال 15 ثانية بينما الصوت يعمل الآن
        if (f.length() > 0 && f.equals(sLastFile)
                && (System.currentTimeMillis() - sLastPlayAt) < 15000
                && player != null && player.isPlaying()) {
            return;
        }
        // تحرير أي مشغّل سابق قبل إنشاء جديد (منع تسريب MediaPlayer)
        releasePlayer();
        sLastFile = f;
        sLastPlayAt = System.currentTimeMillis();
        // 1) ملف وضعه المستخدم في مجلد التطبيق الخارجي (أولوية أولى)
        try {
            File extDir = getExternalFilesDir(null);
            if (f.length() > 0 && extDir != null) {
                File ext = new File(extDir, f);
                if (ext.exists() && ext.length() > 0) {
                    playFromPath(ext.getAbsolutePath(), title);
                    return;
                }
            }
        } catch (Exception e) {}
        // 2) أصل مضمّن داخل APK
        if (f.length() > 0) {
            try {
                AssetFileDescriptor afd = getAssets().openFd(f);
                playFromAsset(afd, title);
                return;
            } catch (Exception e2) {}
        }
        // 3) احتياط: نطق العنوان
        speakFallback(title);
    }

    private void playFromPath(String path, String title) {
        try {
            player = new MediaPlayer();
            AudioAttributes attrs = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build();
            player.setAudioAttributes(attrs);
            player.setDataSource(path);
            finishPlayerSetup(title);
        } catch (Exception e) {
            releasePlayer();
            speakFallback(title);
        }
    }

    private void playFromAsset(AssetFileDescriptor afd, String title) {
        try {
            player = new MediaPlayer();
            AudioAttributes attrs = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build();
            player.setAudioAttributes(attrs);
            player.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
            try { afd.close(); } catch (Exception e0) {}
            finishPlayerSetup(title);
        } catch (Exception e) {
            releasePlayer();
            speakFallback(title);
        }
    }

    private void finishPlayerSetup(final String title) {
        player.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                cleanup();
                stopSelf();
            }
        });
        player.setOnErrorListener(new MediaPlayer.OnErrorListener() {
            @Override
            public boolean onError(MediaPlayer mp, int what, int extra) {
                releasePlayer();
                speakFallback(title);
                return true;
            }
        });
        try {
            player.prepare();
            player.start();
        } catch (Exception e) {
            releasePlayer();
            speakFallback(title);
        }
    }

    private void speakFallback(String title) {
        String say = MainActivity.cleanForTts(title);
        if (say == null || say.length() == 0) {
            cleanup();
            stopSelf();
            return;
        }
        try {
            tts = new TextToSpeech(getApplicationContext(), new TextToSpeech.OnInitListener() {
                @Override
                public void onInit(int status) {
                    if (status == TextToSpeech.SUCCESS && tts != null) {
                        ttsReady = true;
                        try { tts.setLanguage(new Locale("ar", "SA")); } catch (Exception e) {}
                        try {
                            tts.setSpeechRate(0.82f);
                            tts.setPitch(0.85f);
                        } catch (Exception e2) {}
                        try {
                            tts.setAudioAttributes(new AudioAttributes.Builder()
                                    .setUsage(AudioAttributes.USAGE_ALARM)
                                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                    .build());
                        } catch (Exception e3) {}
                        flushPendingTts();
                    }
                }
            });
            pendingTts = say;
            flushPendingTts();
        } catch (Exception e) {
            cleanup();
            stopSelf();
        }
    }

    private void flushPendingTts() {
        String say = pendingTts;
        if (!ttsReady || say == null || tts == null) return;
        pendingTts = null;
        try {
            tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override public void onStart(String utteranceId) {}
                @Override public void onDone(String utteranceId) {
                    cleanup();
                    stopSelf();
                }
                @Override public void onError(String utteranceId) {
                    cleanup();
                    stopSelf();
                }
            });
            tts.speak(say, TextToSpeech.QUEUE_FLUSH, null, "pt-alarm-" + System.currentTimeMillis());
        } catch (Exception e) {
            cleanup();
            stopSelf();
        }
    }

    private void releasePlayer() {
        try {
            if (player != null) {
                try { player.stop(); } catch (Exception e1) {}
                player.release();
                player = null;
            }
        } catch (Exception e) {
            player = null;
        }
    }

    private void cleanup() {
        releasePlayer();
        try {
            if (tts != null) {
                tts.stop();
                tts.shutdown();
                tts = null;
                ttsReady = false;
                pendingTts = null;
            }
        } catch (Exception e) {}
        try {
            if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        } catch (Exception e2) {}
        abandonAudioPriority();
        sLastFile = "";
        sLastPlayAt = 0;
        try {
            stopForeground(true);
        } catch (Exception e3) {}
    }
}
