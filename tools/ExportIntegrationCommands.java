import dev.beatbox.em700.ConversionProfile;
public class ExportIntegrationCommands {
 public static void main(String[] args){
    for (String k:new String[] {"avi","mp3","mp4"}) {
        String out="/mnt/data/EM700_Android/tools/integration_output";
        if (k.equals("avi")) out+="_%02d.avi";
        else out+="."+k;
        System.out.print(k);
        for (String a: ConversionProfile.command("/mnt/data/EM700_Android/tools/source.mp4",out,k,0,10)) System.out.print("\u0001"+a);
        System.out.println();
    }
 }
}
