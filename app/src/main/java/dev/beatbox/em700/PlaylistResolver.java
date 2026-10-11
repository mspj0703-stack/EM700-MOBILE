package dev.beatbox.em700;

import android.content.Context;
import android.net.Uri;

import com.chaquo.python.Python;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Expands YouTube playlist metadata into individual MediaJobs without downloading any media. */
final class PlaylistResolver {
    private PlaylistResolver() {}

    static boolean isPlaylistUrl(String url) {
        try {
            Uri uri = Uri.parse(url);
            String host = uri.getHost();
            if (host == null) return false;
            host = host.toLowerCase(Locale.ROOT);
            boolean youtube = host.equals("youtube.com") || host.endsWith(".youtube.com") ||
                    host.equals("youtu.be") || host.equals("youtube-nocookie.com") ||
                    host.endsWith(".youtube-nocookie.com");
            String list = uri.getQueryParameter("list");
            return youtube && list != null && !list.trim().isEmpty();
        } catch (RuntimeException ignored) { return false; }
    }

    static boolean isPlaylistOnlyUrl(String url) {
        if (!isPlaylistUrl(url)) return false;
        String path = Uri.parse(url).getPath();
        return path != null && path.matches("/playlist/?");
    }

    static List<MediaJob> resolve(Context context, MediaJob parent) throws Exception {
        if (!parent.playlist || !isPlaylistUrl(parent.url))
            throw new IllegalArgumentException("유효한 YouTube 재생목록 링크가 아닙니다.");
        YtDlpUpdater.prepare(context);
        String json = Python.getInstance().getModule("em700_playlist")
                .callAttr("resolve", parent.url).toString();
        JSONObject doc = new JSONObject(json);
        JSONArray items = doc.getJSONArray("entries");
        List<MediaJob> result = new ArrayList<>();
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.getJSONObject(i);
            String videoUrl = item.getString("url");
            String title = item.optString("title", "영상 " + (i + 1));
            int position = item.optInt("position", i + 1);
            String named = String.format(Locale.ROOT, "%04d_%s", position, title);
            result.add(new MediaJob(named, videoUrl, null,
                    parent.type, parent.profile, parent.splitMinutes));
        }
        return result;
    }
}
