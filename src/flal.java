
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.audio.exceptions.CannotReadException;
import org.jaudiotagger.tag.images.Artwork;
import org.jaudiotagger.tag.Tag;
import org.jaudiotagger.tag.FieldKey;




// "AudioGroup" is used to denote either Audiobook or Audiotheathre.

public class flal {

  private static boolean dummyProcessing = false;
  public static String outputDir = "output";
  public static String rootDir = ".";
  public static String logDir = null;
  public static Logger logger = null;


  private static FileFilter flacFileFilter
    = new FileFilter() {
      public boolean accept(File file) {
        return file.isDirectory()
          || (!file.getName().startsWith(".")
            && file.getName().toLowerCase().endsWith(
            Constants.FLAC_SUFFIX.toLowerCase()));
      }
    };


  public static void main(String... args) {
    try {
      File configFile = null;

      String userDir = System. getProperty("user.home");
      configFile = new File(userDir + "/." + Constants.configFilename);
      if (configFile.exists() == false) {
        String rootPath= Thread.currentThread(
            ).getContextClassLoader().getResource("").getPath();
        configFile = new File(rootPath + "/" + Constants.configFilename);
        if (configFile.exists() == false) {
          configFile = null;
        }
      }

      if (configFile != null) {
        Properties appProps = new Properties();
        appProps.load(new FileInputStream(configFile));

        if (appProps.containsKey("outputDir")) {
          outputDir = appProps.getProperty("outputDir");
        }

        if (appProps.containsKey("rootDir")) {
          rootDir = appProps.getProperty("rootDir");
        }

        if (appProps.containsKey("logDir")) {
          logDir = appProps.getProperty("logDir");
        }
      }

      logger = new Logger(logDir);

      File root = new File(rootDir);
      if (root.isDirectory()) {
        processFlalDirectory(root);
      }
      else {
        logger.log("root is not a directory.");
      }
    }
    catch (Exception e) {
      e.printStackTrace();
    }
  }


  public static boolean processFlalDirectory(File dir) throws Exception {
    File[] files = dir.listFiles(flacFileFilter);
    Arrays.sort(files);
    for (File file : files) {
      if (file.isDirectory()) {
        boolean skipRestOfThisDir = processFlalDirectory(file);
        if (skipRestOfThisDir) {
          break;
        }
      }
      else {
        String relativePath = file.getParentFile().getAbsolutePath().substring(
            rootDir.length()+1);
        logger.log(
            "Processing \"" + relativePath + "/" + file.getName() + "\".");
        AudioFile f = AudioFileIO.read(file);
        Tag tag = f.getTag();
        String album = tag.getFirst(FieldKey.ALBUM);
        String genre = tag.getFirst(FieldKey.GENRE).toLowerCase();
        String concat = getAndVerifyConcat(tag);
        if ((Constants.audioGroupGenres.contains(genre))
            && (!concat.equals(Constants.CONCAT_FALSE))) {
          // audio group
          boolean parentDirBelongsToGroup = processAudioGroup(dir);
          return parentDirBelongsToGroup;
            }
        else {
          // single file
          String outputFilename
            = outputDir + "/"
            + file.getParentFile().getAbsolutePath().substring(
                rootDir.length()+1)
            + "/"
            + file.getName().substring(0,
                file.getName().length()
                - Constants.FLAC_SUFFIX.length() - 1) + ".m4a";
          EncodingJob job = new EncodingJob(
              logger,
              Arrays.asList(file),
              new File(outputFilename));
          job.run();
        }
      }
    }
    return false;
  }


  public static boolean processAudioGroup(File dir) throws Exception {
    String groupType;
    int myDiscTotal;
    String myConcat;

    File[] files = dir.listFiles(flacFileFilter);

    File file = files[0];
    AudioFile f = AudioFileIO.read(file);
    Tag tag = f.getTag();
    String genre = tag.getFirst(FieldKey.GENRE);
    String discTotalAsString = tag.getFirst(FieldKey.DISC_TOTAL);
    myConcat = getAndVerifyConcat(tag);

    myDiscTotal = (discTotalAsString == null || discTotalAsString.isEmpty()
		    ? 1 : Integer.parseInt(discTotalAsString));
    if (genre.equals("Audiobook") || genre.equals("Audiotheathre")) {
	    groupType = genre;
    }
    else {
      throw new Exception("Invalid group type.");
    }

    File groupRootDir = (myDiscTotal > 1 ? dir.getParentFile() : dir);
    String relativeGroupRoot
      = groupRootDir.getAbsolutePath().substring(
                rootDir.length()+1,
                (int)(groupRootDir.getAbsolutePath().length()
                  - groupRootDir.getName().length()));

    List<File> sourceFiles = new ArrayList<File>();
    File[] rootFiles = groupRootDir.listFiles(flacFileFilter);
    Arrays.sort(rootFiles);
    for (File rootFile : rootFiles) {
      if (rootFile.isDirectory()) {
	File[] subFiles = rootFile.listFiles(flacFileFilter);
	Arrays.sort(subFiles);
	sourceFiles.addAll(Arrays.asList(subFiles));
      }
      else {
	sourceFiles.add(rootFile);
      }
    }

    String outputFilename = outputDir + "/" + relativeGroupRoot
      + "/" + groupRootDir.getName() + ".m4a";
    File outputFile = new File(outputFilename);

    EncodingJob job = new EncodingJob(logger, sourceFiles, outputFile);
    job.run();

    return myDiscTotal > 1;
  }


  public static String getAndVerifyConcat(Tag tag) throws Exception {
    String concat = tag.getFirst("CONCAT").toLowerCase();
    if (concat == null || concat.isEmpty()) {
      return Constants.CONCAT_FALSE;
    }
    else {
      concat = concat.toLowerCase();
      if (concat.equals(Constants.CONCAT_FALSE)
          || concat.equals(Constants.CONCAT_DIRONLY)
          || concat.equals(Constants.CONCAT_ALLDISCS)) {
        return concat;
          }
      else {
        throw new Exception("Invalid concat type.");
      }
    }
  }
}

