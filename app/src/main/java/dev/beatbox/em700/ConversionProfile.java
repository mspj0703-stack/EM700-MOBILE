package dev.beatbox.em700;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Pure Java, independently testable FFmpeg argument builder. */
public final class ConversionProfile {
    public static final String[] LABELS = {
        "25fps XVID · 400kbps · 스테레오 (비교 06)",
        "25fps XVID · 300kbps · 모노 (비교 07)",
        "15fps XVID · 320x240 · 모노 (안전 시험)",
        "24fps XVID · 320x240 · 스테레오 (PC 구버전)",
        "15fps DIVX · 160x120 · 모노 (저사양 시험)",
        "15fps MJPEG · 320x240 · 모노 (대체 시험)"
    };
    private ConversionProfile() {}

    public static List<String> command(String source, String dest, String kind, int profile, int splitMinutes) {
        ArrayList<String> cmd = new ArrayList<>(Arrays.asList(
                "-hide_banner", "-nostdin", "-y", "-i", source));
        if ("mp3".equals(kind)) {
            cmd.addAll(Arrays.asList("-map", "0:a:0", "-vn", "-c:a", "libmp3lame", "-b:a", "192k", "-id3v2_version", "3"));
        } else if ("mp4".equals(kind)) {
            // GPL-free MPEG-4 Part 2 fallback; original compatible MP4s are copied without encoding.
            cmd.addAll(Arrays.asList("-map", "0:v:0", "-map", "0:a:0?",
                    "-vf", "scale='min(1280,iw)':-2,format=yuv420p", "-c:v", "mpeg4", "-q:v", "4",
                    "-c:a", "aac", "-b:a", "128k", "-movflags", "+faststart"));
        } else if ("avi".equals(kind)) {
            int p = Math.max(0, Math.min(profile, LABELS.length - 1));
            int w = p == 4 ? 160 : 320;
            int h = p == 4 ? 120 : 240;
            int fps = (p == 0 || p == 1) ? 25 : (p == 3 ? 24 : 15);
            String codec = p == 5 ? "mjpeg" : "mpeg4";
            String tag = p == 4 ? "DIVX" : (p == 5 ? "MJPG" : "XVID");
            String br = p == 1 ? "300k" : (p == 0 ? "400k" : "320k");
            String rate = (p == 0 || p == 3) ? "44100" : "22050";
            String channels = (p == 0 || p == 3) ? "2" : "1";
            cmd.addAll(Arrays.asList("-map", "0:v:0", "-map", "0:a:0?",
                    "-vf", "scale=" + w + ":" + h + ":force_original_aspect_ratio=decrease," +
                            "pad=" + w + ":" + h + ":(ow-iw)/2:(oh-ih)/2,setsar=1,format=yuv420p",
                    "-r", String.valueOf(fps), "-c:v", codec, "-vtag", tag,
                    "-pix_fmt", p == 5 ? "yuvj420p" : "yuv420p"));
            if (p != 5) {
                cmd.addAll(Arrays.asList("-bf", "0", "-g", "150", "-b:v", br,
                        "-maxrate", br, "-bufsize", "800k"));
            } else {
                cmd.addAll(Arrays.asList("-q:v", "5"));
            }
            cmd.addAll(Arrays.asList("-c:a", "libmp3lame", "-b:a", "128k", "-ar", rate,
                    "-ac", channels, "-af", "aresample=async=1:first_pts=0"));
            if (splitMinutes > 0) {
                int seconds = splitMinutes * 60;
                cmd.addAll(Arrays.asList("-force_key_frames", "expr:gte(t,n_forced*" + seconds + ")",
                        "-f", "segment", "-segment_time", String.valueOf(seconds),
                        "-reset_timestamps", "1", "-segment_format", "avi"));
            }
        } else {
            throw new IllegalArgumentException("Unknown output type: " + kind);
        }
        cmd.add(dest);
        return cmd;
    }

    /** Self-contained, short known-length clip; selects same encoding options as user files. */
    public static List<String> testCommand(String dest, int profile) {
        List<String> c = command("_input_", dest, "avi", profile, 0);
        c.remove(4); // remove placeholder input; keep -i, replace with lavfi source
        c.remove(3);
        c.addAll(3, Arrays.asList(
            "-f", "lavfi", "-i", "testsrc2=size=320x240:rate=25",
            "-f", "lavfi", "-i", "sine=frequency=440:sample_rate=44100",
            "-t", "8"));
        return c;
    }
}
