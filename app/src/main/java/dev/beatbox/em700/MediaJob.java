package dev.beatbox.em700;

import android.net.Uri;

public final class MediaJob {
    public final String title;
    public final String url;
    public final Uri localUri;
    public final String type;
    public final int profile;
    public final int splitMinutes;
    public final boolean playlist;
    public volatile String status = "대기";
    public volatile int percent = 0;

    public MediaJob(String title, String url, Uri localUri, String type, int profile, int splitMinutes) {
        this(title, url, localUri, type, profile, splitMinutes, false);
    }
    public MediaJob(String title, String url, Uri localUri, String type, int profile, int splitMinutes,
                    boolean playlist) {
        this.title = title; this.url = url; this.localUri = localUri;
        this.type = type; this.profile = profile; this.splitMinutes = splitMinutes;
        this.playlist = playlist;
    }
    public boolean isLink() { return url != null; }
}
