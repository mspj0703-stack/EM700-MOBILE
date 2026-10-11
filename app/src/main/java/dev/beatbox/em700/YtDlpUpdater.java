package dev.beatbox.em700;

import android.content.Context;

import com.chaquo.python.PyObject;
import com.chaquo.python.Python;

import dev.ffmpegkit_maintained.ytdlp.YtDlp;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Installs a more recent pure-Python yt-dlp inside the app sandbox. */
final class YtDlpUpdater {
    private YtDlpUpdater() {}

    static synchronized String prepare(Context context) throws Exception {
        YtDlp.init(context); // Starts Chaquopy before injecting the Python search path.
        String desired;
        try (InputStream in = context.getAssets().open("yt_dlp_override.version")) {
            desired = readText(in).trim();
        }
        if (!desired.matches("[0-9]{4}\\.[0-9]{2}\\.[0-9]{2}(?:\\.[0-9]+)?")) {
            throw new IOException("yt-dlp 패키지 버전 정보가 올바르지 않습니다: " + desired);
        }
        File root = new File(context.getFilesDir(), "yt_dlp_update");
        File active = new File(root, "python");
        File marker = new File(active, ".bundled_version");
        if (!root.isDirectory() && !root.mkdirs()) throw new IOException("yt-dlp 업데이트 저장 공간 생성 실패");
        String current = "";
        if (marker.isFile()) {
            try (InputStream in = new FileInputStream(marker)) { current = readText(in).trim(); }
        }
        if (!desired.equals(current) || !new File(active, "yt_dlp/__init__.py").isFile()) {
            File staging = new File(root, "stage");
            deleteTree(staging);
            if (!staging.mkdirs()) throw new IOException("yt-dlp 임시 폴더 생성 실패");
            try {
                extract(context, staging);
                if (!new File(staging, "yt_dlp/__init__.py").isFile()) {
                    throw new IOException("yt-dlp 패키지 내용이 없습니다");
                }
                try (FileOutputStream out = new FileOutputStream(new File(staging, ".bundled_version"))) {
                    out.write(desired.getBytes(StandardCharsets.UTF_8));
                }
                deleteTree(active);
                if (!staging.renameTo(active)) throw new IOException("yt-dlp 버전 변경 실패");
            } catch (Exception failure) {
                deleteTree(staging);
                throw failure;
            }
        }
        Python py = Python.getInstance();
        PyObject sysPath = py.getModule("sys").get("path");
        sysPath.callAttr("insert", 0, active.getAbsolutePath());
        String actual = py.getModule("yt_dlp.version").get("__version__").toString();
        if (!desired.equals(actual)) {
            throw new IOException("yt-dlp 업데이트 로드 실패: 원함=" + desired + ", 현재=" + actual);
        }
        return actual;
    }

    private static void extract(Context context, File to) throws IOException {
        String safeRoot = to.getCanonicalPath() + File.separator;
        byte[] buf = new byte[32768];
        try (InputStream asset = context.getAssets().open("yt_dlp_override.zip");
             ZipInputStream zin = new ZipInputStream(asset)) {
            ZipEntry entry;
            int count = 0;
            long totalSize = 0;
            while ((entry = zin.getNextEntry()) != null) {
                String name = entry.getName();
                if (!name.startsWith("yt_dlp/") && !name.matches("yt_dlp-[^/]+\\.dist-info/.*")) {
                    throw new IOException("예상하지 못한 yt-dlp 패키지 파일: " + name);
                }
                File dest = new File(to, name);
                if (!dest.getCanonicalPath().startsWith(safeRoot)) throw new IOException("잘못된 패키지 경로");
                if (entry.isDirectory()) {
                    if (!dest.isDirectory() && !dest.mkdirs()) throw new IOException("디렉터리 생성 실패");
                } else {
                    File parent = dest.getParentFile();
                    if (!parent.isDirectory() && !parent.mkdirs()) throw new IOException("패키지 폴더 생성 실패");
                    try (FileOutputStream output = new FileOutputStream(dest)) {
                        int n;
                        while ((n = zin.read(buf)) != -1) {
                            output.write(buf, 0, n);
                            totalSize += n;
                            if (totalSize > 75L * 1024 * 1024) throw new IOException("yt-dlp 업데이트가 너무 큽니다");
                        }
                    }
                }
                if (++count > 10000) throw new IOException("yt-dlp 파일 수 초과");
                zin.closeEntry();
            }
            if (count < 20) throw new IOException("yt-dlp 패키지 파일이 비어 있거나 불완전합니다");
        }
    }
    private static String readText(InputStream input) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[512];
        int n;
        while ((n = input.read(buf)) != -1) {
            out.write(buf, 0, n);
            if (out.size() > 2048) throw new IOException("버전 정보가 너무 깁니다");
        }
        return out.toString("UTF-8");
    }
    private static void deleteTree(File f) {
        if (!f.exists()) return;
        if (f.isDirectory()) {
            File[] entries = f.listFiles();
            if (entries != null) for (File child : entries) deleteTree(child);
        }
        // Best effort: failed deletion will be caught by mkdir/rename checks.
        f.delete();
    }
}
