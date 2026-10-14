package com.zynremote.agent;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;
import android.telephony.TelephonyManager;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class DeviceInfo {

    public static String getInfoJson(Context ctx) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"manufacturer\":\"").append(esc(Build.MANUFACTURER)).append("\",");
        sb.append("\"model\":\"").append(esc(Build.MODEL)).append("\",");
        sb.append("\"brand\":\"").append(esc(Build.BRAND)).append("\",");
        sb.append("\"android\":\"").append(esc(Build.VERSION.RELEASE)).append("\",");
        sb.append("\"sdk\":").append(Build.VERSION.SDK_INT).append(",");
        sb.append("\"board\":\"").append(esc(Build.BOARD)).append("\",");
        sb.append("\"hardware\":\"").append(esc(Build.HARDWARE)).append("\",");
        sb.append("\"id\":\"").append(esc(Build.ID)).append("\"");
        sb.append("}");
        return sb.toString();
    }

    public static String getPublicIp() {
        try {
            URL u = new URL("https://api.ipify.org");
            HttpURLConnection c = (HttpURLConnection) u.openConnection();
            c.setConnectTimeout(5000);
            c.setReadTimeout(5000);
            BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream()));
            String ip = r.readLine();
            r.close();
            return ip;
        } catch (Throwable t) {
            return "unknown";
        }
    }

    public static String getBattery(Context ctx) {
        try {
            IntentFilter f = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
            Intent b = ctx.registerReceiver(null, f);
            if (b == null) return "no info";
            int level = b.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int status = b.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            int temp = b.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0);
            int volt = b.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0);
            boolean charging = status == BatteryManager.BATTERY_STATUS_CHARGING
                || status == BatteryManager.BATTERY_STATUS_FULL;
            return "Level: " + level + "%\nCharging: " + (charging ? "yes" : "no")
                + "\nTemperature: " + (temp / 10.0) + "C\nVoltage: " + volt + " mV";
        } catch (Throwable t) {
            return "error battery: " + t.getMessage();
        }
    }

    public static String getWifi(Context ctx) {
        try {
            WifiManager wm = (WifiManager) ctx.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wm == null) return "no wifi";
            WifiInfo info = wm.getConnectionInfo();
            if (info == null) return "no info";
            int ip = info.getIpAddress();
            String ipStr = (ip & 0xFF) + "." + ((ip >> 8) & 0xFF) + "."
                + ((ip >> 16) & 0xFF) + "." + ((ip >> 24) & 0xFF);
            return "SSID: " + info.getSSID() + "\nBSSID: " + info.getBSSID()
                + "\nIP: " + ipStr + "\nSpeed: " + info.getLinkSpeed() + " Mbps";
        } catch (Throwable t) {
            return "error wifi: " + t.getMessage();
        }
    }

    public static String getSim(Context ctx) {
        try {
            TelephonyManager tm = (TelephonyManager) ctx.getSystemService(Context.TELEPHONY_SERVICE);
            if (tm == null) return "no telephony";
            String operator = tm.getNetworkOperatorName();
            String country = tm.getNetworkCountryIso();
            String simState = "state_" + tm.getSimState();
            return "Operator: " + operator + "\nCountry: " + country + "\nSIM: " + simState;
        } catch (Throwable t) {
            return "error sim: " + t.getMessage();
        }
    }

    public static String getStorage() {
        try {
            StatFs stat = new StatFs(Environment.getExternalStorageDirectory().getPath());
            long total = stat.getBlockCountLong() * stat.getBlockSizeLong();
            long free = stat.getAvailableBlocksLong() * stat.getBlockSizeLong();
            long used = total - free;
            return "Total: " + human(total) + "\nFree: " + human(free) + "\nUsed: " + human(used);
        } catch (Throwable t) {
            return "error storage: " + t.getMessage();
        }
    }

    public static String getUptime() {
        long ms = android.os.SystemClock.elapsedRealtime();
        long sec = ms / 1000;
        long h = sec / 3600;
        long m = (sec % 3600) / 60;
        return "Uptime: " + h + "h " + m + "m";
    }

    public static String getNetwork(Context ctx) {
        try {
            android.net.ConnectivityManager cm = (android.net.ConnectivityManager)
                ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return "no manager";
            android.net.NetworkInfo ni = cm.getActiveNetworkInfo();
            if (ni == null) return "no network";
            return "Type: " + ni.getTypeName() + "\nState: " + ni.getState()
                + "\nRoaming: " + ni.isRoaming();
        } catch (Throwable t) {
            return "error network: " + t.getMessage();
        }
    }

    private static String human(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return (bytes / 1024) + " KB";
        if (bytes < 1024L * 1024 * 1024) return (bytes / 1024 / 1024) + " MB";
        return String.format("%.1f GB", bytes / 1024.0 / 1024.0 / 1024.0);
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
