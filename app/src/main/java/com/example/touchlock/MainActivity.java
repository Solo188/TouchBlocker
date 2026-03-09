package com.example.touchlock;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            // Если разрешения нет, отправляем пользователя в настройки
            Toast.makeText(this, "Дайте разрешение 'Поверх других окон'", Toast.LENGTH_LONG).show();
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, 101);
        } else {
            // Если разрешение уже есть, запускаем сервис
            startServiceNow();
        }
    }

    private void startServiceNow() {
        Intent intent = new Intent(this, TouchLockService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
        finish(); // Закрываем окно, сервис работает в фоне
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 101) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(this)) {
                startServiceNow();
            } else {
                Toast.makeText(this, "Без разрешения приложение не сможет работать!", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
