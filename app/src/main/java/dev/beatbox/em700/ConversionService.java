package dev.beatbox.em700;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.os.SystemClock;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Keeps conversions running when MainActivity is backgrounded or its screen is off.
 * The OS can still end a foreground service for resource or time limits.
 * Running jobs live in this service, NEVER in an Activity-owned worker.
 */
public final class ConversionService extends Service {
    static final String ACTION_START = "dev.beatbox.em700.CONVERT";
    static final String ACTION_STOP = "dev.beatbox.em700.STOP";
    static final String EXTRA_JOBS = "jobs";
    static final String EXTRA_DESTINATION = "destination";
    private static final String CHANNEL = "em700_conversion";
    private static final int NOTIFICATION_ID = 7300;

    static final class Snapshot {
        final List<MediaJob> jobs;
        final boolean running;
        final int percent;
        final String status;
        final String log;
        Snapshot(List<MediaJob> jobs, boolean running, int percent, String status, String log) {
            this.jobs = jobs; this.running = running; this.percent = percent;
            this.status = status; this.log = log;
        }
    }
    private static volatile Snapshot latest;
    private final CopyOnWriteArrayList<MediaJob> jobs = new CopyOnWriteArrayList<>();
    private volatile WorkEngine engine;
    private volatile boolean running;
    private volatile boolean stopping;
    private volatile String status = "대기 중";
    private volatile int percent;
    private final StringBuilder logs = new StringBuilder();
    private long lastNotification;

    static Snapshot snapshot() { return latest; }
    static boolean isRunning() { Snapshot s = latest; return s != null && s.running; }

    @Override public void onCreate() {
        super.onCreate();
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        manager.createNotificationChannel(new NotificationChannel(CHANNEL, "EM700 다운로드·변환",
                NotificationManager.IMPORTANCE_LOW));
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_NOT_STICKY;
        if (ACTION_STOP.equals(intent.getAction())) {
            stopping = true;
            status = "중단 중…";
            WorkEngine e = engine;
            if (e != null) e.requestStop();
            publish(true);
            if (!running) stopSelf();
            return START_NOT_STICKY;
        }
        if (!ACTION_START.equals(intent.getAction()) || running) return START_NOT_STICKY;
        final String json = intent.getStringExtra(EXTRA_JOBS);
        final String destination = intent.getStringExtra(EXTRA_DESTINATION);
        if (json == null || destination == null) { stopSelf(); return START_NOT_STICKY; }
        try {
            jobs.clear();
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                String link = obj.optString("url", "");
                String local = obj.optString("localUri", "");
                jobs.add(new MediaJob(obj.getString("title"), link.isEmpty() ? null : link,
                        local.isEmpty() ? null : Uri.parse(local), obj.getString("type"),
                        obj.getInt("profile"), obj.getInt("splitMinutes"), obj.optBoolean("playlist")));
            }
            if (jobs.isEmpty()) throw new IllegalArgumentException("비어 있는 대기열");
        } catch (Exception e) {
            log("대기열 로드 실패: " + e.getMessage()); stopSelf(); return START_NOT_STICKY;
        }
        stopping = false;
        running = true;
        status = "작업 시작";
        percent = 0;
        Notification n = buildNotification();
        if (Build.VERSION.SDK_INT >= 35) {
            startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING);
        } else if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            startForeground(NOTIFICATION_ID, n);
        }
        publish(true);
        Thread thread = new Thread(() -> process(Uri.parse(destination)), "EM700-foreground-worker");
        thread.start();
        return START_NOT_STICKY;
    }

    private void process(Uri destination) {
        int completed = 0, failed = 0;
        engine = new WorkEngine(getApplicationContext(), new WorkEngine.Events() {
            @Override public void onProgress(MediaJob job, int p, String detail) {
                percent = Math.min(100, Math.max(0, p));
                status = detail + " · " + percent + "%";
                publish(false);
            }
            @Override public void onLog(String message) { log(message); }
        });
        if (stopping) engine.requestStop();
        int next = 0;
        try {
            while (next < jobs.size() && !stopping) {
                MediaJob job = jobs.get(next++);
                job.status = "진행 중";
                publish(false);
                if (job.playlist) {
                    try {
                        status = "재생목록 확인 중";
                        publish(false);
                        List<MediaJob> expanded = PlaylistResolver.resolve(this, job);
                        if (stopping) { job.status = "대기"; break; }
                        if (expanded.isEmpty()) throw new IllegalStateException("재생목록이 비어 있습니다");
                        jobs.remove(next - 1);
                        next--;
                        jobs.addAll(next, expanded);
                        log("재생목록 " + expanded.size() + "개 추가");
                    } catch (Exception e) {
                        if (stopping) { job.status = "대기"; break; }
                        job.status = "실패"; failed++;
                        log("재생목록 확인 실패: " + e.getMessage());
                    }
                    publish(false);
                    continue;
                }
                try {
                    engine.run(job, destination);
                    job.status = "완료"; completed++;
                    log("완료: " + job.title);
                } catch (InterruptedException e) {
                    job.status = "대기";
                    log("작업 중단됨: " + job.title);
                    break;
                } catch (Exception e) {
                    if (stopping || engine.stopped()) {
                        job.status = "대기"; break;
                    }
                    job.status = "실패"; failed++;
                    log("실패: " + job.title + " → " + e.getMessage());
                }
                publish(false);
            }
        } catch (Throwable ex) {
            log("작업 엔진 오류: " + ex.getClass().getSimpleName() + ": " + ex.getMessage());
        } finally {
            engine = null;
            status = (stopping ? "중단됨" : "처리 종료") + " · 성공 " + completed + "개, 실패 " + failed + "개";
            running = false;
            percent = 100;
            publish(true);
            stopForeground(STOP_FOREGROUND_DETACH);
            stopSelf();
        }
    }

    private void log(String line) {
        synchronized (logs) {
            logs.append('\n').append(line);
            if (logs.length() > 6500) logs.delete(0, logs.length() - 6500);
        }
        publish(false);
    }

    private void publish(boolean force) {
        final String logCopy;
        synchronized (logs) { logCopy = logs.toString(); }
        latest = new Snapshot(new ArrayList<>(jobs), running, percent, status, logCopy);
        long now = SystemClock.elapsedRealtime();
        if (!force && now - lastNotification < 900) return;
        lastNotification = now;
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        nm.notify(NOTIFICATION_ID, buildNotification());
    }

    private Notification buildNotification() {
        Intent open = new Intent(this, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent openPi = PendingIntent.getActivity(this, 0, open,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification.Builder b = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle(running ? "EM700 변환 중" : "EM700 변환 완료")
                .setContentText(status)
                .setContentIntent(openPi)
                .setOnlyAlertOnce(true)
                .setOngoing(running)
                .setProgress(100, Math.min(100, percent), running && percent == 0);
        if (running) {
            Intent stop = new Intent(this, ConversionService.class).setAction(ACTION_STOP);
            PendingIntent stopPi = PendingIntent.getService(this, 1, stop,
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            b.addAction(android.R.drawable.ic_media_pause, "중단", stopPi);
        }
        return b.build();
    }

    @Override public void onDestroy() {
        stopping = true;
        WorkEngine e = engine;
        if (e != null) e.requestStop();
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent) { return null; }
}
