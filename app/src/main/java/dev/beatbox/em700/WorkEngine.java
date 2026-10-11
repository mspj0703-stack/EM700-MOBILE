package dev.beatbox.em700;

import android.content.Context;
import android.net.Uri;
import android.os.StatFs;

import com.arthenica.ffmpegkit.FFmpegKit;
import com.arthenica.ffmpegkit.FFmpegSession;
import com.arthenica.ffmpegkit.FFprobeKit;
import com.arthenica.ffmpegkit.MediaInformation;
import com.arthenica.ffmpegkit.MediaInformationSession;
import com.arthenica.ffmpegkit.ReturnCode;

import dev.ffmpegkit_maintained.ytdlp.YtDlp;
import dev.ffmpegkit_maintained.ytdlp.YtDlpRequest;
import dev.ffmpegkit_maintained.ytdlp.LogCallback;
import dev.ffmpegkit_maintained.ytdlp.YtDlpResponse;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import org.json.JSONObject;
import org.json.JSONArray;
import java.util.concurrent.Future;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;

/** A single worker; only FFmpeg executes asynchronously so statistics can be displayed. */
public final class WorkEngine {
    public interface Events {
        void onProgress(MediaJob job, int percent, String detail);
        void onLog(String detail);
    }
    private final Context context;
    private final Events listener;
    private volatile boolean stopRequested;
    private volatile long sessionId = -1;
    private volatile boolean downloadActive;
    private volatile boolean ytdlpReady;

    public WorkEngine(Context context, Events listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
    }
    public void requestStop() {
        stopRequested = true;
        long id = sessionId;
        if (id >= 0) FFmpegKit.cancel(id);
        if (downloadActive) listener.onLog("현재 링크 다운로드는 즉시 중단할 수 없습니다. 완료 후 다음 작업을 중단합니다.");
    }
    public boolean stopped() { return stopRequested; }
    public void run(MediaJob job, Uri destination) throws Exception {
        File parent = context.getExternalFilesDir(null);
        if (parent == null) parent = context.getFilesDir();
        File workspace = new File(parent, "jobs/job-" + System.nanoTime());
        if (!workspace.mkdirs()) throw new IllegalStateException("임시 작업 폴더 생성 실패");
        try {
            checkStop();
            // YouTube often offers separate video and audio streams only.
            DownloadedInput downloaded = job.isLink()
                    ? download(job, workspace)
                    : new DownloadedInput(importLocal(job, workspace), null);
            File source = downloaded.main;
            checkStop();
            List<File> outputs;
            if ("mp4".equals(job.type) && downloaded.extraAudio == null
                    && source.getName().toLowerCase(Locale.ROOT).endsWith(".mp4")) {
                // Copy ordinary MP4 without reencoding; adaptive streams need audio merging.
                outputs = Arrays.asList(source);
                progress(job, 72, "MP4 원본 유지");
            } else {
                outputs = convert(job, source, downloaded.extraAudio, workspace);
            }
            checkStop();
            int index = 0;
            for (File output : outputs) {
                checkStop();
                if (output.length() <= 0) throw new IllegalStateException("출력 파일이 비어 있습니다.");
                String outName = buildOutputName(job, output, index, outputs.size());
                final int count = index++;
                listener.onLog("저장: " + outName);
                DocumentStorage.export(context, output, destination, outName, (written, total) -> {
                    if (stopRequested) throw new java.util.concurrent.CancellationException("저장 중단");
                    int percent = 82 + (int) ((count + (total > 0 ? (double)written/total : 0.0)) * 18 / outputs.size());
                    progress(job, Math.min(99, percent), "USB/선택 폴더로 저장 중");
                });
            }
            progress(job, 100, "완료 · " + outputs.size() + "개 파일 저장");
        } finally {
            deleteTree(workspace);
        }
    }
    private String buildOutputName(MediaJob job, File output, int index, int count) {
        String base = job.isLink() && job.title.equals(job.url)
                ? "download_" + Integer.toUnsignedString(job.url.hashCode()) : job.title;
        int dot = base.lastIndexOf('.');
        if (dot > 0) base = base.substring(0, dot);
        base = DocumentStorage.safeFileName(base);
        String ext = "." + job.type;
        return base + (count > 1 ? "_part" + String.format(Locale.ROOT, "%02d", index + 1) : "") + ext;
    }
    private File importLocal(MediaJob job, File workspace) throws Exception {
        progress(job, 0, "휴대폰 영상 가져오는 중");
        String ext = ".mp4";
        String name = job.title.toLowerCase(Locale.ROOT);
        int index = name.lastIndexOf('.');
        if (index >= 0 && name.length() - index < 10) ext = name.substring(index);
        // SAF content URI is copied into an ordinary file for FFmpeg native input.
        File source = new File(workspace, "source" + ext);
        DocumentStorage.copyInput(context, job.localUri, source, (written, total) -> {
            if (stopRequested) throw new java.util.concurrent.CancellationException("원본 복사 중단");
            int percent = total > 0 ? (int)(written * 20 / total) : 0;
            progress(job, Math.min(percent, 20), "원본 복사 중");
        });
        return source;
    }
    private static final class DownloadedInput {
        final File main;
        final File extraAudio;
        DownloadedInput(File main, File extraAudio) {
            this.main = main;
            this.extraAudio = extraAudio;
        }
    }

    private DownloadedInput download(MediaJob job, File workspace) throws Exception {
        progress(job, 0, "yt-dlp 초기화");
        listener.onLog("EM700 Mobile v0.1.8 · 실시간 형식 조회 후 다운로드");
        if (!ytdlpReady) {
            listener.onLog("yt-dlp 엔진: " + YtDlpUpdater.prepare(context));
            ytdlpReady = true;
        }
        long available = new StatFs(workspace.getAbsolutePath()).getAvailableBytes();
        if (available < 512L * 1024 * 1024)
            throw new IllegalStateException("저장 공간이 최소 512MB 이상 필요합니다.");
        downloadActive = true;
        try {
            int height = "avi".equals(job.type) ? 360 : 720;
            listener.onLog("형식 조회 중 · JS/EJS 및 제공 스트림 확인");
            JSONObject report;
            try {
                String raw = com.chaquo.python.Python.getInstance()
                        .getModule("em700_format_diagnostic")
                        .callAttr("plan", job.url, height).toString();
                report = new JSONObject(raw);
            } catch (Exception ex) {
                throw new IllegalStateException("영상 형식 조회 실패: " + shortError(ex.getMessage()), ex);
            }
            listener.onLog("엔진 상태: yt-dlp " + report.optString("yt_dlp") +
                    " · EJS " + report.optString("ejs") +
                    " · QuickJS " + report.optString("quickjs"));
            JSONArray warnings = report.optJSONArray("warnings");
            if (warnings != null) {
                for (int i = 0; i < warnings.length(); i++)
                    listener.onLog("추출 진단: " + warnings.optString(i));
            }
            JSONObject choices = report.getJSONObject("choices");
            JSONArray combined = choices.getJSONArray("combined");
            JSONArray video = choices.getJSONArray("video");
            JSONArray audio = choices.getJSONArray("audio");
            listener.onLog("확인된 형식 " + report.optInt("formats_seen") + "개 · " +
                    "통합 " + combined.length() + " / 영상 " + video.length() + " / 음성 " + audio.length());
            String reason = report.optString("error");
            if (!reason.isEmpty()) throw new IllegalStateException("유튜브 형식 조회 오류: " + shortError(reason));
            if ("mp3".equals(job.type)) {
                File audioFile = tryCandidates(job, workspace, "source", audio, 0, 50);
                if (audioFile == null && combined.length() > 0)
                    audioFile = tryCandidates(job, workspace, "source", combined, 0, 50);
                if (audioFile == null)
                    throw new IllegalStateException("다운로드 가능한 음성 형식이 없습니다. 위 추출 진단 로그를 확인하세요.");
                return new DownloadedInput(audioFile, null);
            }
            // Prefer single-file video; otherwise fetch audio and video separately.
            File direct = tryCandidates(job, workspace, "source", combined, 0, 50);
            if (direct != null) {
                listener.onLog("음성 포함 영상 다운로드 완료");
                return new DownloadedInput(direct, null);
            }
            listener.onLog("음성 포함 스트림이 없거나 실패 · 영상/음성 각각 다운로드");
            File videoFile = tryCandidates(job, workspace, "source_video", video, 0, 28);
            if (videoFile == null)
                throw new IllegalStateException("사용할 수 있는 영상 스트림이 없습니다. 형식 목록과 접근 제한을 확인하세요.");
            File audioFile = tryCandidates(job, workspace, "source_audio", audio, 28, 50);
            if (audioFile == null)
                throw new IllegalStateException("영상은 받았지만 음성 스트림이 없습니다. 위 로그를 확인하세요.");
            listener.onLog("영상/음성 다운로드 성공 · FFmpeg로 병합합니다.");
            return new DownloadedInput(videoFile, audioFile);
        } finally {
            downloadActive = false;
        }
    }

    private File tryCandidates(MediaJob job, File workspace, String prefix,
                               JSONArray formats, int from, int to) throws Exception {
        for (int i = 0; i < formats.length() && i < 3; i++) {
            checkStop();
            JSONObject item = formats.getJSONObject(i);
            String id = item.getString("id");
            listener.onLog(prefix + " 시도 " + (i + 1) + ": " + id +
                    " (" + item.optString("ext") + ", " + item.optInt("height") + "p)");
            try {
                return downloadTrack(job, workspace, prefix, id, from, to);
            } catch (InterruptedException stop) {
                throw stop;
            } catch (Exception ex) {
                listener.onLog(prefix + " 다운로드 실패: " + shortError(ex.getMessage()));
                // A failed attempt must not contaminate the following format attempt.
                deleteMatching(workspace, prefix + ".");
                checkStop();
            }
        }
        return null;
    }

    private static String shortError(String message) {
        if (message == null || message.isEmpty()) return "상세 오류 없음";
        return message.length() > 320 ? message.substring(message.length() - 320) : message;
    }

    private static void deleteMatching(File folder, String prefix) {
        File[] files = folder.listFiles((d, n) -> n.startsWith(prefix));
        if (files != null) for (File file : files) if (file.isFile()) file.delete();
    }

    private void diagnoseFormats(MediaJob job) {
        listener.onLog("진단: 실제 접근 가능한 영상/음성 형식 확인 중");
        try {
            String report = com.chaquo.python.Python.getInstance()
                    .getModule("em700_format_diagnostic")
                    .callAttr("describe", job.url).toString();
            for (String line : report.split("\n")) {
                if (!line.trim().isEmpty()) listener.onLog("형식 진단: " + shortError(line));
            }
        } catch (Exception ex) {
            listener.onLog("형식 목록 조회 실패: " + shortError(ex.getMessage()));
        }
    }

    private File downloadTrack(MediaJob job, File workspace, String prefix, String format,
                               int fromPercent, int toPercent) throws Exception {
        checkStop();
        String target = new File(workspace, prefix + ".%(ext)s").getAbsolutePath();
        YtDlpRequest request = new YtDlpRequest(job.url)
                .setOutputTemplate(target)
                .addOption("--no-playlist")
                .addOption("-f", format);
        AtomicReference<String> lastError = new AtomicReference<>("");
        Future<YtDlpResponse> future = YtDlp.executeDebug(request, (level, line) -> {
            if ("ERROR".equals(level)) {
                lastError.set(line);
                listener.onLog("yt-dlp 오류: " + shortError(line));
            } else if ("WARNING".equals(level)) {
                listener.onLog("yt-dlp 경고: " + shortError(line));
            } else if ("DEBUG".equals(level) && line != null) {
                String lower = line.toLowerCase(Locale.ROOT);
                if (lower.contains("challenge") || lower.contains("javascript") ||
                        lower.contains("quickjs") || lower.contains("format") ||
                        lower.contains("ejs") || lower.contains("nsig")) {
                    listener.onLog("yt-dlp 진단: " + shortError(line));
                }
            }
        }, (p, eta, line) -> {
            int progress = fromPercent + (int) ((toPercent - fromPercent) *
                    Math.max(0, Math.min(100, p)) / 100.0);
            progress(job, progress, String.format(Locale.ROOT, "%s %.0f%% · ETA %ds",
                    prefix.equals("source_audio") ? "음성" : "영상", p, eta));
        });
        YtDlpResponse response;
        try {
            response = future.get();
        } catch (ExecutionException ex) {
            Throwable root = ex.getCause() != null ? ex.getCause() : ex;
            throw new IllegalStateException(root.getMessage(), root);
        }
        if (!response.isSuccess()) {
            throw new IllegalStateException(lastError.get().isEmpty()
                    ? "다운로드 실패 (code=" + response.getExitCode() + ")"
                    : lastError.get());
        }
        File[] results = workspace.listFiles((dir, name) -> name.startsWith(prefix + ".") &&
                !name.endsWith(".part") && !name.endsWith(".ytdl") && !name.endsWith(".temp"));
        if (results == null || results.length != 1)
            throw new IllegalStateException("다운로드한 " + prefix + " 파일을 찾지 못했습니다.");
        progress(job, toPercent, "다운로드 완료: " + prefix);
        return results[0];
    }
    private List<File> convert(MediaJob job, File source, File extraAudio, File workspace) throws Exception {
        int start = job.isLink() ? 50 : 20;
        String ext = "." + job.type;
        String pattern = "avi".equals(job.type) && job.splitMinutes > 0 ? "output_%02d.avi" : "output" + ext;
        File target = new File(workspace, pattern);
        List<String> cmd = ConversionProfile.command(source.getAbsolutePath(), target.getAbsolutePath(),
                job.type, job.profile, job.splitMinutes);
        if (extraAudio != null)
            cmd = ConversionProfile.withExternalAudio(cmd, extraAudio.getAbsolutePath());
        return convertInternal(job, workspace, pattern, cmd, start);
    }
    public void runTest(Uri folder) throws Exception {
        File parent = context.getExternalFilesDir(null);
        if (parent == null) parent = context.getFilesDir();
        File workspace = new File(parent, "jobs/test-" + System.nanoTime());
        if (!workspace.mkdirs()) throw new IllegalStateException("임시 작업 폴더 생성 실패");
        try {
            for (int i=0;i<ConversionProfile.LABELS.length;i++) {
                checkStop();
                String name = String.format(Locale.ROOT, "EM700_TEST_%02d.avi", i+1);
                File output = new File(workspace, name);
                List<String> command = ConversionProfile.testCommand(output.getAbsolutePath(), i);
                listener.onLog("테스트 " + (i+1) + "/" + ConversionProfile.LABELS.length + " · " + ConversionProfile.LABELS[i]);
                final CountDownLatch latch = new CountDownLatch(1);
                AtomicReference<FFmpegSession> done = new AtomicReference<>();
                FFmpegSession session = FFmpegKit.executeWithArgumentsAsync(command.toArray(new String[0]), s -> { done.set(s); latch.countDown(); });
                sessionId = session.getSessionId();
                latch.await();
                sessionId = -1;
                checkStop();
                if (!ReturnCode.isSuccess(done.get().getReturnCode()))
                    throw new IllegalStateException("테스트 영상 인코딩 실패 " + (i+1) + ": " + brief(done.get().getAllLogsAsString()));
                validate(output);
                DocumentStorage.export(context, output, folder, name, null);
                if (!output.delete()) listener.onLog("임시 파일 정리 지연: " + name);
            }
        } finally { deleteTree(workspace); }
    }
    private List<File> convertInternal(MediaJob job, File workspace, String fileName,
                                       List<String> cmd, int start) throws Exception {
        // Read the actual source file extension from the generated FFmpeg command.
        int idx = cmd.indexOf("-i");
        double duration = idx >= 0 && idx + 1 < cmd.size()
                ? durationSeconds(new File(cmd.get(idx + 1))) : -1;
        final double durationFinal = duration;
        final CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<FFmpegSession> result = new AtomicReference<>();
        AtomicReference<String> lastError = new AtomicReference<>("");
        progress(job, start, "변환 시작");
        FFmpegSession session = FFmpegKit.executeWithArgumentsAsync(cmd.toArray(new String[0]),
            s -> { result.set(s); latch.countDown(); },
            log -> { String line = log.getMessage(); if (line != null && line.toLowerCase(Locale.ROOT).contains("error")) lastError.set(line); },
            stats -> {
                if (durationFinal > 0) {
                    int pct = start + (int)Math.min(31, Math.max(0, stats.getTime() * 32.0 / (durationFinal * 1000.0)));
                    progress(job, Math.min(pct, 81), "AVI/음성 변환 " + Math.max(0, (pct - start) * 100 / Math.max(1, 82 - start)) + "%");
                }
            });
        sessionId = session.getSessionId();
        latch.await();
        sessionId = -1;
        checkStop();
        if (result.get() == null || !ReturnCode.isSuccess(result.get().getReturnCode())) {
            String detail = lastError.get();
            if (detail.isEmpty() && result.get() != null) detail = brief(result.get().getAllLogsAsString());
            throw new IllegalStateException("FFmpeg 변환 실패: " + detail);
        }
        File[] outputs;
        if (fileName.contains("%02d")) outputs = workspace.listFiles((dir,name) -> name.matches("output_[0-9]+\\.avi"));
        else outputs = new File[]{new File(workspace, fileName)};
        if (outputs == null || outputs.length == 0) throw new IllegalStateException("출력 파일 없음");
        Arrays.sort(outputs, Comparator.comparing(File::getName));
        for (File file : outputs) validate(file);
        progress(job, 82, "변환 및 파일 검사 완료");
        return Arrays.asList(outputs);
    }
    private static String brief(String s) {
        if (s == null) return "FFmpeg 오류";
        return s.length() > 500 ? s.substring(s.length() - 500) : s;
    }
    private static double durationSeconds(File file) {
        try {
            MediaInformationSession info = FFprobeKit.getMediaInformation(file.getAbsolutePath());
            MediaInformation media = info.getMediaInformation();
            return media == null ? -1 : Double.parseDouble(media.getDuration());
        } catch (Exception e) { return -1; }
    }
    private static void validate(File file) throws Exception {
        if (!file.exists() || file.length() < 1024) throw new IllegalStateException("인코딩 파일이 너무 작습니다: " + file.getName());
        MediaInformationSession probe = FFprobeKit.getMediaInformation(file.getAbsolutePath());
        if (probe.getMediaInformation() == null) throw new IllegalStateException("FFprobe로 영상을 읽을 수 없습니다: " + file.getName());
    }
    private void progress(MediaJob job, int pct, String detail) {
        job.percent = pct;
        listener.onProgress(job, pct, detail);
    }
    private void checkStop() throws InterruptedException {
        if (stopRequested) throw new InterruptedException("사용자가 중단했습니다.");
    }
    private static void deleteTree(File file) {
        if (file.isDirectory()) {
            File[] files=file.listFiles();
            if (files != null) for (File child: files) deleteTree(child);
        }
        if (!file.delete()) { /* External storage can remove temporary files later. */ }
    }
}
