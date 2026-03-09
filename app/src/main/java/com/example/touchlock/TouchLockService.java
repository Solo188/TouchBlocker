package com.example.touchlock;

import android.app.*;
import android.content.*;
import android.graphics.PixelFormat;
import android.os.*;
import android.view.*;
import android.widget.*;

public class TouchLockService extends Service {
    private WindowManager windowManager;
    private View overlayView;
    private static final String CHANNEL_ID = "TouchLockChannel";
    private boolean isLocked = false;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        createNotificationChannel();
        
        String action = (intent != null) ? intent.getAction() : null;

        if ("START_BLOCK".equals(action)) {
            Toast.makeText(this, "Блокировка через 3 секунды...", Toast.LENGTH_SHORT).show();
            // Даем время свернуть приложение и открыть видео
            new Handler(Looper.getMainLooper()).postDelayed(this::showOverlay, 3000);
        } else if ("SHOW_UNLOCK_DIALOG".equals(action)) {
            showPasswordDialog();
        } else if ("BOT_UNLOCK".equals(action)) {
            // Команда от будущего Telegram бота
            unlockScreen();
        }

        updateNotification(isLocked ? "ЭКРАН ЗАБЛОКИРОВАН" : "Служба готова");
        return START_STICKY;
    }

    private void showOverlay() {
        if (isLocked) return;

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        overlayView = new View(this);
        
        // Цвет: почти прозрачный черный (0x01 позволяет видеть видео, но ловит касания)
        overlayView.setBackgroundColor(0x01000000); 

        // Настройка параметров окна для блокировки статус-бара и навигации
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                // Убираем NOT_FOCUSABLE, чтобы шторка НЕ открывалась
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | 
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT);

        // Магия для скрытия кнопок навигации и жестов (Immersive Mode)
        overlayView.setSystemUiVisibility(
                  View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN);

        overlayView.setOnTouchListener((v, event) -> true); 
        
        try {
            windowManager.addView(overlayView, params);
            isLocked = true;
            updateNotification("ЭКРАН ЗАБЛОКИРОВАН");
            Toast.makeText(this, "Защита активна", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void showPasswordDialog() {
        if (!isLocked) return;

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Контроль доступа");
        builder.setMessage("Введите код разблокировки:");

        final EditText input = new EditText(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        builder.setView(input);

        builder.setPositiveButton("OK", (dialog, which) -> {
            if ("2468".equals(input.getText().toString())) {
                unlockScreen();
            } else {
                Toast.makeText(this, "Доступ запрещен!", Toast.LENGTH_SHORT).show();
            }
        });

        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);
            // Позволяем клавиатуре появиться поверх блокировщика
            dialog.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
        }
        dialog.show();
    }

    private void unlockScreen() {
        if (isLocked && overlayView != null) {
            windowManager.removeView(overlayView);
            overlayView = null;
            isLocked = false;
            updateNotification("Экран разблокирован");
            Toast.makeText(this, "Доступ восстановлен", Toast.LENGTH_SHORT).show();
        }
    }

    private void updateNotification(String text) {
        Intent startIntent = new Intent(this, TouchLockService.class).setAction("START_BLOCK");
        PendingIntent pStart = PendingIntent.getService(this, 0, startIntent, PendingIntent.FLAG_IMMUTABLE);

        // Для разблокировки теперь нужно нажать кнопку (так как шторку тянуть нельзя, 
        // это сработает только если зайти в приложение или через внешнюю команду)
        Intent unlockIntent = new Intent(this, TouchLockService.class).setAction("SHOW_UNLOCK_DIALOG");
        PendingIntent pUnlock = PendingIntent.getService(this, 1, unlockIntent, PendingIntent.FLAG_IMMUTABLE);

        Notification notification = new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("Родительский контроль")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_lock_lock)
                .addAction(new Notification.Action(0, "Заблокировать", pStart))
                .addAction(new Notification.Action(0, "Разблокировать", pUnlock))
                .setOngoing(true)
                .build();

        startForeground(1, notification);
    }

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Lock Service", NotificationManager.IMPORTANCE_LOW);
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.createNotificationChannel(channel);
        }
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
