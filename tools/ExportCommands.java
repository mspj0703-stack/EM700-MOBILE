import dev.beatbox.em700.ConversionProfile;
public class ExportCommands {
 public static void main(String[] args) {
  for (int i=0;i<ConversionProfile.LABELS.length;i++) {
   System.out.print(i+1);
   for (String a:ConversionProfile.testCommand("/mnt/data/EM700_Android/tools/test"+(i+1)+".avi",i))
     System.out.print("\u0001"+a);
   System.out.println();
  }
 }
}
