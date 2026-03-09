package com.example.touchlock;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Просто запускаем сервис и закрываем окно
        Intent intent = new Intent(this, TouchLockService.class);
        startForegroundService(intent);
        
        finish(); // Приложение закроется, а сервис останется в фоне
    }
}
