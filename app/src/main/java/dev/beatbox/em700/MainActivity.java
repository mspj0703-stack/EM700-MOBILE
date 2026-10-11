package dev.beatbox.em700;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.text.InputType;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** On-device download, FFmpeg transcode, and SAF USB export. No broad storage permission. */
public final class MainActivity extends Activity {
    private static final int PICK_MEDIA = 101;
    private static final int PICK_FOLDER = 102;
    private final List<MediaJob> jobs = new ArrayList<>();
    private Spinner typeSelector, profileSelector, splitSelector;
    private TextView folderLabel, queueLabel, stateLabel, logLabel;
    private EditText urlField;
    private ProgressBar progressBar;
    private Button startButton, stopButton;
    private Uri outputTree;
    private volatile WorkEngine engine;
    private volatile boolean busy;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String last = getPreferences(MODE_PRIVATE).getString("output_uri", "");
        if (!last.isEmpty()) outputTree = Uri.parse(last);
        buildUI();
        refreshQueue();
    }
    private LinearLayout vertical() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }
    private int dp(int v) { return (int)(v * getResources().getDisplayMetrics().density + 0.5f); }
    private TextView label(String txt, int size) {
        TextView v = new TextView(this);
        v.setText(txt); v.setTextSize(size); v.setTextColor(Color.rgb(30, 39, 55));
        v.setPadding(0, dp(7), 0, dp(7)); return v;
    }
    private Button action(String text, View.OnClickListener click) {
        Button b = new Button(this); b.setAllCaps(false); b.setText(text); b.setOnClickListener(click); return b;
    }
    private Spinner chooser(String[] values) {
        Spinner spin = new Spinner(this);
        ArrayAdapter<String> a = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, values);
        a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spin.setAdapter(a); return spin;
    }
    private void buildUI() {
        LinearLayout page = vertical();
        page.setPadding(dp(16), dp(8), dp(16), dp(12));
        page.setBackgroundColor(Color.rgb(247, 249, 252));
        page.setFitsSystemWindows(true);
        ScrollView scroll = new ScrollView(this);
        LinearLayout body = vertical();
        scroll.setFillViewport(true); scroll.addView(body);
        page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(page);

        TextView title = label("EM700 Converter", 24);
        title.setTextColor(Color.rgb(34, 86, 181)); title.setTypeface(null, 1); body.addView(title);
        body.addView(label("휴대폰에서 변환 → USB C-to-C로 EM700에 저장", 13));
        body.addView(label("① 영상 링크 입력 (한 줄에 하나)", 16));
        urlField = new EditText(this);
        urlField.setMinLines(2); urlField.setMaxLines(4);
        urlField.setGravity(android.view.Gravity.TOP);
        urlField.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        urlField.setHint("https://www.youtube.com/watch?v=...");
        body.addView(urlField, new LinearLayout.LayoutParams(-1, dp(100)));
        body.addView(action("링크를 대기열에 추가", v -> addLinks()));
        body.addView(action("휴대폰 영상/음악 파일 선택 (여러 개 가능)", v -> pickFiles()));

        body.addView(label("② 출력 형식", 16));
        typeSelector = chooser(new String[] { "AVI · EM700 전용", "MP3 · 음악", "MP4 · 영상" });
        body.addView(typeSelector);
        body.addView(label("AVI 호환성 프리셋 (재생되는 설정을 찾기 전까지 실험값)", 13));
        profileSelector = chooser(ConversionProfile.LABELS);
        body.addView(profileSelector);
        body.addView(label("AVI 긴 영상 분할", 13));
        splitSelector = chooser(new String[] { "30분마다 (권장 시험)", "분할 안 함", "10분마다", "60분마다", "90분마다", "120분마다" });
        body.addView(splitSelector);

        body.addView(label("③ 저장 위치 (USB 플레이어의 VIDEO 폴더도 선택 가능)", 16));
        folderLabel = label(outputTree == null ? "선택 안 됨" : "마지막으로 선택한 폴더: " + outputTree, 12);
        body.addView(folderLabel);
        body.addView(action("저장 폴더 선택 / USB VIDEO 폴더", v -> pickFolder()));
        body.addView(action("8초 호환성 테스트 AVI 6개 저장 (인코딩 없음)", v -> startTests()));

        body.addView(label("④ 대기열", 16));
        queueLabel = label("대기열 비어 있음", 13);
        body.addView(queueLabel);
        body.addView(action("완료/실패 항목 정리", v -> {
            if (busy) return;
            jobs.removeIf(j -> !j.status.equals("대기")); refreshQueue();
        }));

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        startButton = action("▶ 변환 시작", v -> startJobs());
        stopButton = action("■ 중단", v -> {
            WorkEngine w = engine;
            if (w != null) w.requestStop();
            stateLabel.setText("중단 요청됨 · 진행 중 다운로드는 완료 후 중단");
        });
        buttons.addView(startButton, new LinearLayout.LayoutParams(0, dp(55), 1));
        buttons.addView(stopButton, new LinearLayout.LayoutParams(0, dp(55), 1));
        page.addView(buttons);
        stopButton.setEnabled(false);
        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100); page.addView(progressBar);
        stateLabel = label("준비 완료", 12); page.addView(stateLabel);
        logLabel = label("", 11); page.addView(logLabel);
    }
    private String currentKind() {
        int p=typeSelector.getSelectedItemPosition();
        return p==0 ? "avi" : p==1 ? "mp3" : "mp4";
    }
    private int currentSplit() {
        switch (splitSelector.getSelectedItemPosition()) {
            case 0: return 30;
            case 2: return 10;
            case 3: return 60;
            case 4: return 90;
            case 5: return 120;
            default: return 0;
        }
    }
    private void addLinks() {
        String raw = urlField.getText().toString(); int count=0;
        for (String part : raw.split("\\s+")) {
            String trimmed=part.trim();
            if (trimmed.isEmpty()) continue;
            if (!trimmed.startsWith("https://") && !trimmed.startsWith("http://")) {
                toast("https:// 링크만 추가할 수 있습니다."); continue;
            }
            jobs.add(new MediaJob(trimmed, trimmed, null, currentKind(),
                    profileSelector.getSelectedItemPosition(), currentSplit())); count++;
        }
        urlField.setText("");
        log("대기열에 URL " + count + "개 추가"); refreshQueue();
    }
    private void pickFiles() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE); i.setType("*/*");
        i.putExtra(Intent.EXTRA_MIME_TYPES, new String[] {"video/*", "audio/*", "application/octet-stream"});
        i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        startActivityForResult(i, PICK_MEDIA);
    }
    private void pickFolder() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION |
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        if (outputTree != null) i.putExtra(DocumentsContract.EXTRA_INITIAL_URI, outputTree);
        startActivityForResult(i, PICK_FOLDER);
    }
    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) return;
        if (requestCode == PICK_FOLDER) {
            Uri tree = data.getData();
            if (tree == null) return;
            try {
                getContentResolver().takePersistableUriPermission(tree,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                outputTree=tree;
                getPreferences(MODE_PRIVATE).edit().putString("output_uri",tree.toString()).apply();
                folderLabel.setText("출력 폴더: " + tree.toString());
            } catch (Exception ex) { toast("폴더 권한 실패: " + ex.getMessage()); }
        } else if (requestCode == PICK_MEDIA) {
            ArrayList<Uri> files = new ArrayList<>();
            if (data.getClipData() != null) {
                for (int i=0;i<data.getClipData().getItemCount();i++) files.add(data.getClipData().getItemAt(i).getUri());
            } else if (data.getData() != null) files.add(data.getData());
            for (Uri uri: files) {
                try { getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION); }
                catch (SecurityException ignored) { /* Temporary picker grant still works this session. */ }
                String title = DocumentStorage.getName(this, uri);
                jobs.add(new MediaJob(title, null, uri, currentKind(),
                        profileSelector.getSelectedItemPosition(), currentSplit()));
            }
            log("기기 파일 " + files.size() + "개 추가"); refreshQueue();
        }
    }
    private void startJobs() {
        if (busy) return;
        if (outputTree == null) { toast("먼저 저장 폴더를 선택하세요."); return; }
        List<MediaJob> todo=new ArrayList<>();
        for (MediaJob j: jobs) if (j.status.equals("대기")) todo.add(j);
        if (todo.isEmpty()) { toast("대기 중인 영상이 없습니다."); return; }
        startWork(() -> {
            int done = 0;
            for (MediaJob j: todo) {
                if (engine.stopped()) break;
                j.status = "진행 중"; refreshQueueThreadsafe();
                try {
                    engine.run(j, outputTree);
                    j.status="완료";
                    done++;
                    logAsync("완료: " + j.title);
                } catch (InterruptedException e) {
                    j.status="대기"; logAsync("작업 중단: " + j.title); break;
                } catch (Exception e) {
                    if (engine.stopped()) {
                        j.status="대기"; logAsync("작업 중단: " + j.title); break;
                    }
                    j.status="실패"; logAsync("실패: " + j.title + " → " + e.getMessage());
                }
                refreshQueueThreadsafe();
            }
            final int success = done;
            runOnUiThread(() -> stateLabel.setText("처리 종료 · 성공 " + success + "개"));
        });
    }
    private void startTests() {
        if (busy) return;
        if (outputTree == null) { toast("먼저 저장 폴더를 선택하세요."); return; }
        final Uri selectedFolder = outputTree;
        startWork(() -> {
            try {
                // Pre-generated, verified AVI samples. No FFmpegKit/native-code execution.
                TestAviExporter.exportAll(this, selectedFolder,
                        () -> engine != null && engine.stopped(),
                        (percent, detail) -> runOnUiThread(() -> {
                            progressBar.setProgress(percent);
                            stateLabel.setText(detail);
                        }));
                logAsync("호환성 테스트 AVI 6개 저장 완료");
                runOnUiThread(() -> stateLabel.setText("8초 테스트 AVI 6개 저장 완료"));
            } catch (InterruptedException | java.util.concurrent.CancellationException e) {
                logAsync("테스트 파일 저장 중단됨");
                runOnUiThread(() -> stateLabel.setText("테스트 저장 중단됨"));
            } catch (Exception e) {
                final String reason = e.getClass().getSimpleName() + ": " + e.getMessage();
                logAsync("테스트 파일 저장 실패: " + reason);
                runOnUiThread(() -> {
                    stateLabel.setText("저장 실패: " + reason);
                    toast("테스트 저장 실패. 아래 오류 로그를 확인하세요.");
                });
            }
        });
    }
    private interface Work { void run(); }
    private void startWork(Work task) {
        busy=true;
        engine = new WorkEngine(this, new WorkEngine.Events() {
            @Override public void onProgress(MediaJob job, int p, String msg) {
                runOnUiThread(() -> { progressBar.setProgress(p); stateLabel.setText(msg + " · " + p + "%"); refreshQueue(); });
            }
            @Override public void onLog(String s) { logAsync(s); }
        });
        startButton.setEnabled(false); stopButton.setEnabled(true);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        log("작업 중에는 앱을 화면에 유지하세요. 화면이 꺼지면 Android가 작업을 제한할 수 있습니다.");
        Thread worker = new Thread(() -> {
            try { task.run(); }
            finally {
                runOnUiThread(() -> {
                    busy=false; engine=null;
                    getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                    startButton.setEnabled(true); stopButton.setEnabled(false);
                    refreshQueue();
                });
            }
        }, "EM700-work");
        worker.start();
    }
    private void refreshQueueThreadsafe() { runOnUiThread(this::refreshQueue); }
    private void refreshQueue() {
        StringBuilder b=new StringBuilder();
        int n=0;
        for (MediaJob j: jobs) {
            b.append(++n).append(". ").append(j.title.length()>80 ? j.title.substring(0,80) + "..." : j.title)
                    .append("\n   ").append(j.type.toUpperCase(Locale.ROOT)).append(" · ")
                    .append(j.status).append(j.status.equals("진행 중") ? " " + j.percent + "%" : "")
                    .append("\n");
        }
        queueLabel.setText(n==0 ? "대기열 비어 있음" : b.toString());
    }
    private void log(String s) {
        String t=logLabel.getText().toString() + "\n" + s;
        logLabel.setText(t.length()>1800 ? t.substring(t.length()-1800) : t);
    }
    private void logAsync(String s) { runOnUiThread(() -> log(s)); }
    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_LONG).show(); }
}
