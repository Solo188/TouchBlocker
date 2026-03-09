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
            new Handler(Looper.getMainLooper()).postDelayed(this::showOverlay, 3000);
        } else if ("SHOW_UNLOCK_DIALOG".equals(action)) {
            showPasswordDialog();
        } else if ("BOT_UNLOCK".equals(action)) {
            unlockScreen();
        }

        updateNotification(isLocked ? "ЭКРАН ЗАБЛОКИРОВАН" : "Служба активна");
        return START_STICKY;
    }

    private void showOverlay() {
        if (isLocked) return;
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        overlayView = new View(this);
        overlayView.setBackgroundColor(0x01000000); 

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | 
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT);

        overlayView.setSystemUiVisibility(
                  View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN);

        overlayView.setOnTouchListener((v, event) -> true); 
        windowManager.addView(overlayView, params);
        isLocked = true;
        updateNotification("ЭКРАН ЗАБЛОКИРОВАН");
    }

    private void showPasswordDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Блокировка");
        final EditText input = new EditText(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        builder.setView(input);
        builder.setPositiveButton("OK", (dialog, which) -> {
            if ("2468".equals(input.getText().toString())) unlockScreen();
        });

        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);
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
        }
    }

    private void updateNotification(String text) {
        Intent startIntent = new Intent(this, TouchLockService.class).setAction("START_BLOCK");
        PendingIntent pStart = PendingIntent.getService(this, 0, startIntent, PendingIntent.FLAG_IMMUTABLE);
        Intent unlockIntent = new Intent(this, TouchLockService.class).setAction("SHOW_UNLOCK_DIALOG");
        PendingIntent pUnlock = PendingIntent.getService(this, 1, unlockIntent, PendingIntent.FLAG_IMMUTABLE);

        Notification notification = new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("Touch Blocker")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_lock_lock)
                .addAction(new Notification.Action(0, "Блок", pStart))
                .addAction(new Notification.Action(0, "Пароль", pUnlock))
                .setOngoing(true)
                .build();
        startForeground(1, notification);
    }

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Lock", NotificationManager.IMPORTANCE_LOW);
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) manager.createNotificationChannel(channel);
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
