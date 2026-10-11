package dev.beatbox.em700;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;
import android.os.StatFs;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashSet;
import java.util.Set;

public final class DocumentStorage {
    public interface Progress { void onBytes(long current, long total); }
    private DocumentStorage() {}

    public static String getName(Context ctx, Uri uri) {
        try (Cursor c = ctx.getContentResolver().query(uri,
                new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) return c.getString(0);
        } catch (Exception ignored) { }
        String name = uri.getLastPathSegment();
        return name == null ? "video" : name;
    }
    public static long getSize(Context ctx, Uri uri) {
        try (Cursor c = ctx.getContentResolver().query(uri,
                new String[]{OpenableColumns.SIZE}, null, null, null)) {
            if (c != null && c.moveToFirst() && !c.isNull(0)) return c.getLong(0);
        } catch (Exception ignored) { }
        return -1;
    }
    public static String safeFileName(String name) {
        String s = name.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").trim();
        if (s.length() > 100) s = s.substring(0, 100);
        return s.isEmpty() ? "video" : s;
    }
    public static void copyInput(Context ctx, Uri uri, File file, Progress listener) throws Exception {
        long expected = getSize(ctx, uri);
        long available = new StatFs(file.getParentFile().getAbsolutePath()).getAvailableBytes();
        if (expected > 0 && available < expected + 150L * 1024 * 1024) {
            throw new IllegalStateException("휴대폰 저장 공간이 부족합니다 (원본 임시 저장 공간 필요).");
        }
        try (InputStream in = ctx.getContentResolver().openInputStream(uri);
             OutputStream out = new FileOutputStream(file)) {
            if (in == null) throw new IllegalStateException("선택한 파일을 읽을 수 없습니다.");
            byte[] buf = new byte[128 * 1024]; long total = 0; int n;
            while ((n = in.read(buf)) != -1) {
                if (Thread.currentThread().isInterrupted()) throw new InterruptedException("복사가 중단되었습니다.");
                out.write(buf, 0, n); total += n;
                if (listener != null) listener.onBytes(total, expected);
            }
        }
    }
    private static String mime(String name) {
        String n = name.toLowerCase(java.util.Locale.ROOT);
        if (n.endsWith(".avi")) return "video/x-msvideo";
        if (n.endsWith(".mp4")) return "video/mp4";
        if (n.endsWith(".mp3")) return "audio/mpeg";
        return "application/octet-stream";
    }
    private static String uniqueName(ContentResolver cr, Uri tree, String requested) throws Exception {
        String treeId = DocumentsContract.getTreeDocumentId(tree);
        Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, treeId);
        Set<String> used = new HashSet<>();
        try (Cursor c = cr.query(children,
                new String[]{DocumentsContract.Document.COLUMN_DISPLAY_NAME}, null, null, null)) {
            if (c != null) while (c.moveToNext()) used.add(c.getString(0).toLowerCase(java.util.Locale.ROOT));
        }
        if (!used.contains(requested.toLowerCase(java.util.Locale.ROOT))) return requested;
        int dot = requested.lastIndexOf('.');
        String base = dot > 0 ? requested.substring(0,dot) : requested;
        String ext = dot > 0 ? requested.substring(dot) : "";
        for (int i=2;i<10000;i++) {
            String test = base + " (" + i + ")" + ext;
            if (!used.contains(test.toLowerCase(java.util.Locale.ROOT))) return test;
        }
        throw new IllegalStateException("같은 이름의 파일이 너무 많습니다.");
    }
    public static String export(Context ctx, File file, Uri tree, String fileName, Progress progress) throws Exception {
        ContentResolver cr = ctx.getContentResolver();
        String name = uniqueName(cr, tree, safeFileName(fileName));
        Uri dir = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree));
        Uri target = DocumentsContract.createDocument(cr, dir, mime(name), name);
        if (target == null) throw new IllegalStateException("저장 위치에 파일을 만들 수 없습니다. USB 권한을 확인하세요.");
        boolean committed = false;
        try (ParcelFileDescriptor fd = cr.openFileDescriptor(target, "w")) {
            if (fd == null) throw new IllegalStateException("출력 저장소를 열 수 없습니다.");
            try (FileInputStream in = new FileInputStream(file);
                 FileOutputStream out = new FileOutputStream(fd.getFileDescriptor())) {
                byte[] buf = new byte[128 * 1024]; long done=0; int n;
                while ((n = in.read(buf)) != -1) {
                    out.write(buf, 0, n); done+=n;
                    if (progress != null) progress.onBytes(done, file.length());
                }
                // Some SAF/USB providers do not support fsync on their descriptors.
                try { out.getFD().sync(); } catch (java.io.IOException ignored) { }
            }
            committed = true;
        } finally {
            if (!committed) {
                try { DocumentsContract.deleteDocument(cr, target); } catch (Exception ignored) { }
            }
        }
        return name;
    }
}
