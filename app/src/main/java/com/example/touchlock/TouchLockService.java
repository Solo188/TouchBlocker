package com.example.touchlock;

import android.app.*;
import android.content.*;
import android.graphics.PixelFormat;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.Toast;
import okhttp3.*;
import org.json.*;
import java.io.IOException;

public class TouchLockService extends Service {
    private WindowManager windowManager;
    private View overlayView;
    private static final String CHANNEL_ID = "TouchLockChannel";
    private boolean isLocked = false;
    
    private static final String BOT_TOKEN = "8388799545:AAGPwGKOTs47C29s6PUDFsqZbAjNh9wdrgE";
    private final OkHttpClient client = new OkHttpClient();
    private long lastUpdateId = 0;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        createNotificationChannel();
        startForeground(1, getLockNotification("Ожидание команд бота..."));
        startTelegramPolling();
        return START_STICKY;
    }

    private void startTelegramPolling() {
        new Thread(() -> {
            while (true) {
                try {
                    String url = "https://api.telegram.org/bot" + BOT_TOKEN + "/getUpdates?offset=" + (lastUpdateId + 1);
                    Request request = new Request.Builder().url(url).build();
                    try (Response response = client.newCall(request).execute()) {
                        if (response.isSuccessful() && response.body() != null) {
                            String jsonData = response.body().string();
                            JSONObject jsonObject = new JSONObject(jsonData);
                            JSONArray result = jsonObject.getJSONArray("result");

                            for (int i = 0; i < result.length(); i++) {
                                JSONObject update = result.getJSONObject(i);
                                lastUpdateId = update.getLong("update_id");
                                
                                if (update.has("message")) {
                                    String text = update.getJSONObject("message").getString("text");
                                    if (text.equals("/block")) {
                                        new Handler(Looper.getMainLooper()).post(this::showOverlay);
                                    } else if (text.equals("/stop")) {
                                        new Handler(Looper.getMainLooper()).post(this::unlockScreen);
                                    }
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                try { Thread.sleep(2000); } catch (InterruptedException e) {}
            }
        }).start();
    }

    private void showOverlay() {
        if (isLocked) return;

        // ПРОВЕРКА: Если разрешения нет, не пытаемся блокировать (чтобы не вылетело)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            return;
        }

        try {
            windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
            overlayView = new View(this);
            overlayView.setBackgroundColor(0x02000000); 

            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS |
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL |
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                    PixelFormat.TRANSLUCENT);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            }

            overlayView.setSystemUiVisibility(
                      View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_FULLSCREEN);

            overlayView.setOnTouchListener((v, event) -> {
                sendBroadcast(new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS));
                return true; 
            }); 

            windowManager.addView(overlayView, params);
            isLocked = true;
            updateNotification("ЭКРАН ЗАБЛОКИРОВАН");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void unlockScreen() {
        if (isLocked && overlayView != null) {
            try {
                windowManager.removeView(overlayView);
                overlayView = null;
                isLocked = false;
                updateNotification("Доступ разрешен");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private Notification getLockNotification(String text) {
        return new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("Родительский контроль")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_secure)
                .setOngoing(true)
                .build();
    }

    private void updateNotification(String text) {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(1, getLockNotification(text));
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Lock", NotificationManager.IMPORTANCE_LOW);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
