package dev.beatbox.em700;

import android.content.Context;
import android.net.Uri;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Locale;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;

/**
 * Copies bundled, previously encoded AVI test samples to a user-selected SAF folder.
 * Does NOT invoke FFmpegKit. This keeps the compatibility test usable even if the
 * device's native FFmpeg runtime fails to initialize.
 */
public final class TestAviExporter {
    public interface Progress {
        void update(int percent, String detail);
    }

    private static final int FILE_COUNT = 9;
    private TestAviExporter() {}

    public static void exportAll(Context context, Uri destination,
                                 BooleanSupplier cancelled, Progress progress) throws Exception {
        if (destination == null) throw new IllegalArgumentException("저장 폴더가 선택되지 않았습니다.");
        for (int i = 1; i <= FILE_COUNT; ++i) {
            checkCancelled(cancelled);
            final String filename = String.format(Locale.ROOT, "EM700_TEST_%02d.avi", i);
            final String assetPath = "em700_tests/" + filename;
            final int fileIndex = i - 1;
            File tmp = File.createTempFile("em700_test_", ".avi", context.getCacheDir());
            try {
                // Keep memory consumption bounded even on low-end Android devices.
                try (InputStream in = context.getAssets().open(assetPath);
                     FileOutputStream out = new FileOutputStream(tmp)) {
                    byte[] buffer = new byte[64 * 1024];
                    int n;
                    while ((n = in.read(buffer)) != -1) {
                        checkCancelled(cancelled);
                        out.write(buffer, 0, n);
                    }
                }
                if (tmp.length() < 1024) {
                    throw new IllegalStateException("내장 테스트 파일 손상: " + filename);
                }
                progress.update(fileIndex * 100 / FILE_COUNT, "테스트 " + i + "/" + FILE_COUNT + " 저장 중");
                String saved = DocumentStorage.export(context, tmp, destination, filename,
                    (written, total) -> {
                        if (cancelled != null && cancelled.getAsBoolean()) {
                            throw new CancellationException("테스트 저장 중단");
                        }
                        double partial = total > 0 ? Math.min(1.0, (double) written / total) : 0;
                        int pct = (int) Math.min(99, (fileIndex + partial) * 100 / FILE_COUNT);
                        progress.update(pct, "테스트 " + (fileIndex + 1) + "/" + FILE_COUNT + " · " + filename);
                    });
                progress.update(i * 100 / FILE_COUNT, "저장 완료: " + saved + " (" + i + "/" + FILE_COUNT + ")");
            } finally {
                if (!tmp.delete()) tmp.deleteOnExit();
            }
        }
        progress.update(100, "테스트 AVI 9개 저장 완료");
    }

    private static void checkCancelled(BooleanSupplier cancelled) throws InterruptedException {
        if (Thread.currentThread().isInterrupted() ||
            (cancelled != null && cancelled.getAsBoolean())) {
            throw new InterruptedException("테스트 저장 중단");
        }
    }
}
