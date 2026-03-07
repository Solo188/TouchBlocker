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

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        createNotificationChannel();
        
        // Кнопка Старт в уведомлении
        Intent startIntent = new Intent(this, TouchLockService.class);
        startIntent.setAction("START_BLOCK");
        PendingIntent pendingStart = PendingIntent.getService(this, 0, startIntent, PendingIntent.FLAG_IMMUTABLE);

        Notification notification = new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("Блокировщик касаний")
                .setContentText("Нажмите Старт для активации")
                .setSmallIcon(android.R.drawable.ic_lock_lock)
                .addAction(new Notification.Action(null, "Старт", pendingStart))
                .setOngoing(true)
                .build();

        startForeground(1, notification);

        if ("START_BLOCK".equals(intent.getAction())) {
            new Handler(Looper.getMainLooper()).postDelayed(this::showOverlay, 3000);
        }

        return START_STICKY;
    }

    private void showOverlay() {
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        overlayView = new View(this);
        
        // Параметры окна: блокируем касания, но позволяем шторку
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);

        overlayView.setOnTouchListener((v, event) -> true); // Поглощаем все нажатия
        windowManager.addView(overlayView, params);
        
        Toast.makeText(this, "Экран заблокирован", Toast.LENGTH_SHORT).show();
        // Здесь можно добавить логику ввода пароля 2468 для вызова removeView
    }

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Lock Service", NotificationManager.IMPORTANCE_LOW);
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
