package com.zynremote.agent;

import android.content.Context;
import android.os.Build;
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
        sb.append("\"device\":\"").append(esc(Build.DEVICE)).append("\",");
        sb.append("\"product\":\"").append(esc(Build.PRODUCT)).append("\",");
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

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
