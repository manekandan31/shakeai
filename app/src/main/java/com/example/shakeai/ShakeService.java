package com.example.shakeai;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import androidx.core.app.NotificationCompat;

public class ShakeService extends Service implements SensorEventListener {

    private SensorManager sensorManager;
    private Sensor accelerometer;
    private int shakeCount = 0;
    private long lastShakeTime = 0;
    private long firstShakeTime = 0;
    private boolean volumePressed = false;
    private long volumePressTime = 0;

    // Volume window — 10 seconds after pressing volume
    private static final long VOLUME_WINDOW_MS = 10000;

    // 3 shakes must happen within 2 seconds
    private static final int SHAKE_COUNT_REQUIRED = 3;
    private static final long SHAKE_WINDOW_MS = 2000;

    @Override
    public void onCreate() {
        super.onCreate();

        String channelId = "ShakeAI";

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    channelId,
                    "ShakeAI",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager =
                    getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
        }

        Notification notification = new NotificationCompat.Builder(this, channelId)
                .setContentTitle("ShakeAI Running")
                .setContentText("Hidden background active")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .build();

        startForeground(1, notification);

        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        sensorManager.registerListener(
                this,
                accelerometer,
                SensorManager.SENSOR_DELAY_NORMAL
        );

        startVolumeMonitor();
    }

    private void startVolumeMonitor() {
        final AudioManager audioManager =
                (AudioManager) getSystemService(Context.AUDIO_SERVICE);

        final int[] lastVolume = {
                audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        };

        Handler handler = new Handler(Looper.getMainLooper());

        Runnable volumeChecker = new Runnable() {
            @Override
            public void run() {
                int currentVolume =
                        audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);

                if (currentVolume != lastVolume[0]) {
                    // Volume button pressed — start listening for shakes
                    volumePressed = true;
                    volumePressTime = System.currentTimeMillis();
                    shakeCount = 0;
                    firstShakeTime = 0;
                    lastVolume[0] = currentVolume;
                }

                // Reset after volume window expires
                if (volumePressed &&
                        System.currentTimeMillis() - volumePressTime > VOLUME_WINDOW_MS) {
                    volumePressed = false;
                    shakeCount = 0;
                    firstShakeTime = 0;
                }

                handler.postDelayed(this, 200);
            }
        };

        handler.post(volumeChecker);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {

        float x = event.values[0];
        float y = event.values[1];
        float z = event.values[2];

        double force = Math.sqrt(x * x + y * y + z * z);

        long now = System.currentTimeMillis();

        if (force > 12 && now - lastShakeTime > 400) {

            // Only count shake if volume was pressed
            if (volumePressed) {

                // Record time of first shake
                if (shakeCount == 0) {
                    firstShakeTime = now;
                }

                shakeCount++;
                lastShakeTime = now;

                // Check if 3 shakes happened within 2 seconds
                if (shakeCount >= SHAKE_COUNT_REQUIRED) {

                    if (now - firstShakeTime <= SHAKE_WINDOW_MS) {

                        // Launch AssistantActivity — no vibration
                        new Handler(Looper.getMainLooper()).postDelayed(() -> {
                            Intent intent = new Intent(
                                    ShakeService.this,
                                    AssistantActivity.class
                            );
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(intent);
                        }, 300);
                    }

                    // Reset everything
                    shakeCount = 0;
                    firstShakeTime = 0;
                    volumePressed = false;
                }
            }
        }

        // Reset shake count if too much time passed
        if (shakeCount > 0 && now - firstShakeTime > SHAKE_WINDOW_MS) {
            shakeCount = 0;
            firstShakeTime = 0;
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}