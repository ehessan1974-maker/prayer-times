package com.ehessan.prayertimes;

import android.content.Context;
import android.media.AudioAttributes;
import android.speech.tts.TextToSpeech;
import java.util.Locale;

/** محرك النطق العربي المشترك بين التطبيق والخدمة */
public class PrayerTts {

    private TextToSpeech tts;
    private volatile boolean ready = false;
    private volatile String pending = null;

    public PrayerTts(final Context ctx) {
        try {
            tts = new TextToSpeech(ctx.getApplicationContext(), new TextToSpeech.OnInitListener() {
                @Override
                public void onInit(int status) {
                    if (status == TextToSpeech.SUCCESS && tts != null) {
                        try { tts.setLanguage(new Locale("ar", "SA")); } catch (Exception e) {}
                        try {
                            tts.setSpeechRate(0.82f);
                            tts.setPitch(0.85f);
                        } catch (Exception e2) {}
                        try {
                            tts.setAudioAttributes(new AudioAttributes.Builder()
                                    .setUsage(AudioAttributes.USAGE_MEDIA)
                                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                    .build());
                        } catch (Exception e3) {}
                        ready = true;
                        if (pending != null) {
                            String p = pending;
                            pending = null;
                            speakNow(p);
                        }
                    }
                }
            });
        } catch (Exception e) {
            tts = null;
        }
    }

    public void speakNow(String text) {
        if (text == null || text.trim().length() == 0) return;
        if (tts == null) return;
        if (!ready) {
            pending = text;
            return;
        }
        try {
            tts.stop();
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "pt-tts-" + System.currentTimeMillis());
        } catch (Exception e) {}
    }

    public void stop() {
        try {
            if (tts != null) tts.stop();
        } catch (Exception e) {}
    }

    public void shutdown() {
        try {
            if (tts != null) {
                tts.stop();
                tts.shutdown();
                tts = null;
            }
        } catch (Exception e) {}
    }
}
