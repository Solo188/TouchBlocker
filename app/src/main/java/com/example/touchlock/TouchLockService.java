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
            new Handler(Looper.getMainLooper()).postDelayed(this::showOverlay, 3000);
        } else if ("SHOW_UNLOCK_DIALOG".equals(action)) {
            showPasswordDialog();
        }

        updateNotification("Служба активна");
        return START_STICKY;
    }

    private void showOverlay() {
        if (isLocked) return;

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        overlayView = new View(this);
        
        // FLAG_NOT_FOCUSABLE позволяет шторке уведомлений работать поверх блокировки
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);

        overlayView.setOnTouchListener((v, event) -> true); // Поглощаем все нажатия на экран
        windowManager.addView(overlayView, params);
        
        isLocked = true;
        updateNotification("ЭКРАН ЗАБЛОКИРОВАН");
        Toast.makeText(this, "Блокировка включена", Toast.LENGTH_SHORT).show();
    }

    private void showPasswordDialog() {
        if (!isLocked) return;

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Разблокировка");
        builder.setMessage("Введите пароль 2468:");

        final EditText input = new EditText(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        builder.setView(input);

        builder.setPositiveButton("OK", (dialog, which) -> {
            if ("2468".equals(input.getText().toString())) {
                unlockScreen();
            } else {
                Toast.makeText(this, "Неверный пароль!", Toast.LENGTH_SHORT).show();
            }
        });

        AlertDialog dialog = builder.create();
        // Устанавливаем тип окна для диалога, чтобы он был виден поверх блокировщика
        if (dialog.getWindow() != null) {
            dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);
        }
        dialog.show();
    }

    private void unlockScreen() {
        if (isLocked && overlayView != null) {
            windowManager.removeView(overlayView);
            overlayView = null;
            isLocked = false;
            updateNotification("Экран доступен");
            Toast.makeText(this, "Доступ восстановлен", Toast.LENGTH_SHORT).show();
        }
    }

    private void updateNotification(String text) {
        Intent startIntent = new Intent(this, TouchLockService.class).setAction("START_BLOCK");
        PendingIntent pStart = PendingIntent.getService(this, 0, startIntent, PendingIntent.FLAG_IMMUTABLE);

        Intent unlockIntent = new Intent(this, TouchLockService.class).setAction("SHOW_UNLOCK_DIALOG");
        PendingIntent pUnlock = PendingIntent.getService(this, 1, unlockIntent, PendingIntent.FLAG_IMMUTABLE);

        // Используем 0 вместо null для иконок Action, чтобы избежать ошибок компиляции
        Notification notification = new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("Touch Blocker")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_lock_lock)
                .addAction(new Notification.Action(0, "Старт", pStart))
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
