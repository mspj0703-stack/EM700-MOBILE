import dev.beatbox.em700.ConversionProfile;
import java.util.List;

public class TestProfiles {
    public static void main(String[] args) {
        for (int i = 0; i < ConversionProfile.LABELS.length; i++) {
            List<String> a = ConversionProfile.command("input.mp4", "output.avi", "avi", i, 0);
            if (!a.contains("-vtag") || !a.contains("-bf") && i != 5) throw new AssertionError("bad profile: " + i);
            if (!a.get(a.indexOf("-r") + 1).equals(i < 2 ? "25" : i == 3 ? "24" : "15")) throw new AssertionError("fps: " + i);
            List<String> test = ConversionProfile.testCommand("sample.avi", i);
            if (!test.contains("lavfi") || test.contains("_input_") || !test.get(test.size()-1).equals("sample.avi")) throw new AssertionError("test command");
        }
        for (String kind : new String[] {"mp3", "mp4", "avi"}) {
            List<String> cmd = ConversionProfile.command("in.mkv", "out." + kind, kind, 0, 30);
            if (!cmd.get(cmd.size()-1).equals("out." + kind)) throw new AssertionError(kind);
            if ((kind.equals("avi")) != cmd.contains("-segment_time")) throw new AssertionError(kind);
        }
        System.out.println("Profiles + ffmpeg commands: PASS");
    }
}
