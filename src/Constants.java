
import java.util.Arrays;
import java.util.List;


public class Constants {

  public static final String FLAC_SUFFIX = ".flac";

  public static final String CONCAT_FALSE = "false";
  public static final String CONCAT_DIRONLY = "dir-only";
  public static final String CONCAT_ALLDISCS = "all-discs";

  public static final List<String> audioGroupGenres
    = Arrays.asList("audiobook", "audio theatre");

  public static final String configFilename = "flal.properties";

  public static final String[] fdkaacEncoderDefaults
    = new String[] { "-p", "2", "-m", "5" };
}

