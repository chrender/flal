
package de.christoph_ender.flal;


import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Properties;

import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.tag.Tag;
import org.jaudiotagger.tag.FieldKey;




// "AudioGroup" is used to denote either Audiobook or Audiotheatre.

public class flal {

  private static boolean dummyProcessing = false;
  public static String outputDir = "output";
  public static String rootDir = ".";
  public static String logDir = null;
  public static Logger logger = null;
  public static int nofEncodingJobsCreated = 0;
  public static String aacEncoder = "fdkaac";
  public static boolean debugFlag = false;
  private static boolean overwriteExisting = false;
  public static boolean dontConcat = false;
  public static int numberOfParallelJobs = 4;
  public static EncodingJob[] encodingJobs = null;
  public static String outputSuffix = null;
  public static String customAudiobookGenre = null;
  public static String customAudiotheatreGenre = null;
  public static Map<String,String> customGenreNames
    = new HashMap<String,String>();

  public static Map<String, Map<String, String[]>> defaultEncoderFlags
    = Map.of(

        "fdkaac",
        Map.of(
          "music", new String[] {  "-p", "2", "-m", "5" },
          "audiobook", new String[] {  "-p", "2", "-m", "5" },
          "audio theatre", new String[] {  "-p", "2", "-m", "5" } ),

        "afconvert",
        Map.of(
          "music", new String[] {  "-s", "3", "-u", "vbrq", "64" },
          "audiobook", new String[] {  "-s", "3", "-u", "vbrq", "64" },
          "audio theatre", new String[] {  "-s", "3", "-u", "vbrq", "64" } ),

        "lame",
        Map.of(
          "music", new String[] {  "-m", "j", "-V", "2" },
          "audiobook", new String[] {  "-m", "m", "--cbr", "-b", "128" },
          "audio theatre", new String[] {  "-m", "m", "--cbr", "-b", "128" } ));

  public static Map<String,String[]> encoderFlags
    = new HashMap<String,String[]>();

  
  private static FileFilter regularFileFilter
    = new FileFilter() {
      public boolean accept(File file) {
        return file.isDirectory()
          || (!file.getName().equals(".")
            && (!file.getName().equals("..")));
      }
    };


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

      String userDir = System.getProperty("user.home");
      configFile = new File(userDir + "/." + Constants.configFilename);
      if (configFile.exists() == false) {
        String rootPath= Thread.currentThread(
            ).getContextClassLoader().getResource("").getPath();
        configFile = new File(rootPath + "/" + Constants.configFilename);
        if (configFile.exists() == false) {
          configFile = null;
        }
      }

      Properties userProps = new Properties();
      if (configFile != null) {
        userProps.load(new FileInputStream(configFile));

        if (userProps.containsKey("outputDir")) {
          outputDir = userProps.getProperty("outputDir");
        }

        if (userProps.containsKey("rootDir")) {
          rootDir = userProps.getProperty("rootDir");
        }

        if (userProps.containsKey("logDir")) {
          logDir = userProps.getProperty("logDir");
        }

        if (userProps.containsKey("debug")) {
          debugFlag = userProps.getProperty("debug").equals("true");
        }

        if (userProps.containsKey("overwriteExisting")) {
          overwriteExisting
            = userProps.getProperty("overwriteExisting").equals("true");
        }

        if (userProps.containsKey("dummyProcessing")) {
          dummyProcessing
            = userProps.getProperty("dummyProcessing").equals("true");
        }

        if (userProps.containsKey("dontConcat")) {
          dontConcat = userProps.getProperty("dontConcat").equals("true");
        }

        if (userProps.containsKey("numberOfParallelJobs")) {
          numberOfParallelJobs
            = Integer.parseInt(userProps.getProperty("numberOfParallelJobs"));
        }

        if (userProps.containsKey("customGenreAudiobook")) {
          customGenreNames.put("audiobook",
              userProps.getProperty("customGenreAudiobook"));
        }

        if (userProps.containsKey("customGenreAudiotheatre")) {
          customGenreNames.put("audio theatre",
              userProps.getProperty("customGenreAudiotheatre"));
        }

        if (userProps.containsKey("aacEncoder")) {
          aacEncoder = userProps.getProperty("aacEncoder");
          if (!aacEncoder.equals("fdkaac")
              && !aacEncoder.equals("afconvert")
              && !aacEncoder.equals("lame")) {
            throw new Exception("Invalid aac encoder: \"" + aacEncoder + "\".");
          }
        }
      }

      outputSuffix = aacEncoder.equals("lame") ? ".mp3" : ".m4a";

      if (userProps.containsKey(aacEncoder + "FlagsForMusic")) {
        encoderFlags.put(
            "music",
            userProps.getProperty(
              aacEncoder + "FlagsForMusic").split(","));
      }
      else {
        encoderFlags.put("music",
            (defaultEncoderFlags.get(aacEncoder)).get("music"));
      }

      if (userProps.containsKey(aacEncoder + "FlagsForAudiobooks")) {
        encoderFlags.put(
            "audiobook",
            userProps.getProperty(
              aacEncoder + "FlagsForAudiobooks").split(","));
      }
      else {
        encoderFlags.put("audiobook",
            (defaultEncoderFlags.get(aacEncoder)).get("audiobook"));
      }

      if (userProps.containsKey(aacEncoder + "FlagsForAudiotheatre")) {
        encoderFlags.put(
            "audio theatre",
            userProps.getProperty(
              aacEncoder + "FlagsForAudiotheatre").split(","));
      }
      else {
        encoderFlags.put("audio theatre",
            (defaultEncoderFlags.get(aacEncoder)).get("audio theatre"));
      }

      encodingJobs = new EncodingJob[numberOfParallelJobs];

      logger = new Logger(logDir);

      File outputRootDir = new File(outputDir);
      List<String> oldOutputFilenames = new ArrayList<String>();
      listOldOutputFiles(outputRootDir, oldOutputFilenames);

      File root = new File(rootDir);
      if (root.isDirectory()) {
        List<String> newOutputFilenames = new ArrayList<String>();

        processFlalDirectory(root, newOutputFilenames);

        for (EncodingJob encodingJob : encodingJobs) {
          if (encodingJob != null) {
            if (debugFlag) {
              logger.log("Waiting for \""
                  + encodingJob.getJobName() + "\" to finish.");
            }
            joinAndTestEncodingJob(encodingJob);
          }
        }

        for (String newOutputFilename : newOutputFilenames) {
          if (oldOutputFilenames.contains(newOutputFilename)) {
            oldOutputFilenames.remove(newOutputFilename);
          }
        }

        System.out.println(oldOutputFilenames.size() + " old files");
        System.out.println(newOutputFilenames.size() + " new files");
        System.out.println("Superfluous:");
        for (String oldOutputFilename : oldOutputFilenames) {
          System.out.println(oldOutputFilename);
        }
      }
      else {
        logger.log("root is not a directory.");
      }
    }
    catch (Exception e) {
      e.printStackTrace();
    }
  }


  public static void listOldOutputFiles(
      File dir,
      List<String> oldOutputFilenames
      ) throws Exception {
    File[] files = dir.listFiles(regularFileFilter);
    for (File file : files) {
      if (file.isDirectory()) {
        listOldOutputFiles(file, oldOutputFilenames);
      }
      else {
        oldOutputFilenames.add(file.getAbsolutePath().substring(outputDir.length()+1));
      }
    }
  }


  public static boolean processFlalDirectory(
      File dir,
      List<String> newOutputFilenames
      ) throws Exception {
    File[] files = dir.listFiles(flacFileFilter);
    Arrays.sort(files);
    for (File file : files) {
      if (file.isDirectory()) {
        boolean skipRestOfThisDir = processFlalDirectory(
            file, newOutputFilenames);
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
        if (debugFlag) {
          logger.log("Detected genre \"" + genre + "\".");
        }
        if ((Constants.audioGroupGenres.contains(genre))
            && (!concat.equals(Constants.CONCAT_FALSE))
            && dontConcat == false) {
          // audio group
          logger.log("Detected group, genre: \"" + genre + "\", concat: \""
              + concat + "\".");
          boolean parentDirBelongsToGroup
            = processAudioGroup(dir, newOutputFilenames);
          return parentDirBelongsToGroup;
        }
        else {
          // single file
          String relativeOutputFilename
            = file.getParentFile().getAbsolutePath().substring(
                rootDir.length()+1)
            + "/"
            + file.getName().substring(0,
                file.getName().length()
                - Constants.FLAC_SUFFIX.length()) + outputSuffix;
          newOutputFilenames.add(relativeOutputFilename);
          String outputFilename
            = outputDir + "/" + relativeOutputFilename;
          String[] genresEncoderFlags
            = encoderFlags.containsKey(genre)
            ? encoderFlags.get(genre)
            : encoderFlags.get("music");
          File outputFile = new File(outputFilename);
          EncodingJob job = new EncodingJob(
              Map.ofEntries(
                Map.entry("logger", logger),
                Map.entry("jobName", getNextEncodingJobName()),
                Map.entry("rootDir", rootDir),
                Map.entry("sourceFiles", Arrays.asList(file)),
                Map.entry("outputFile", outputFile),
                Map.entry("encoderName", aacEncoder),
                Map.entry("debugFlag", debugFlag),
                Map.entry("overwriteExisting", overwriteExisting),
                Map.entry("dummyProcessing", dummyProcessing),
                Map.entry("encoderParameters", genresEncoderFlags),
                Map.entry("customGenreName",
                  getCustomGenreName(genre.toLowerCase()))));
          runEncodingJob(job);
        }
      }
    }
    return false;
  }


  /**
   * Processes the supplied audio group.
   *
   * @param dir One of the directories containing one of the group's files.
   * @returns Whether the supplied dir's parent directory belongs to the
   *  group, which is the case when the group consists of multiple directories.
   */
  public static boolean processAudioGroup(
      File dir,
      List<String> newOutputFilenames
      ) throws Exception {
    String groupType;
    int myDiscTotal;
    String myConcat;

    File[] files = dir.listFiles(flacFileFilter);

    File file = files[0];
    AudioFile f = AudioFileIO.read(file);
    Tag tag = f.getTag();
    String genre = tag.getFirst(FieldKey.GENRE).toLowerCase();
    String discTotalAsString = tag.getFirst(FieldKey.DISC_TOTAL);
    myConcat = getAndVerifyConcat(tag);

    myDiscTotal = (discTotalAsString == null || discTotalAsString.isEmpty()
        ? 1 : Integer.parseInt(discTotalAsString));
    if (Constants.audioGroupGenres.contains(genre)) {
      groupType = genre;
    }
    else {
      throw new Exception("Invalid group type.");
    }

    boolean isMultiDirGroup
      = myDiscTotal > 1 && !myConcat.equals(Constants.CONCAT_DIRONLY);
    File groupRootDir = isMultiDirGroup ? dir.getParentFile() : dir;
    String relativeGroupRoot
      = groupRootDir.getAbsolutePath().substring(rootDir.length()+1);
    logger.log("Group root directory: \"" + relativeGroupRoot + "\".");

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

    String relativeOutputFilename = relativeGroupRoot + outputSuffix;
    newOutputFilenames.add(relativeOutputFilename);
    String outputFilename = outputDir + "/" + relativeOutputFilename;
    File outputFile = new File(outputFilename);

    String[] genresEncoderFlags
      = encoderFlags.containsKey(genre)
      ? encoderFlags.get(genre)
      : encoderFlags.get("music");

    EncodingJob job = new EncodingJob(
        Map.ofEntries(
          Map.entry("logger", logger),
          Map.entry("jobName", getNextEncodingJobName()),
          Map.entry("rootDir", rootDir),
          Map.entry("sourceFiles", sourceFiles),
          Map.entry("outputFile", outputFile),
          Map.entry("encoderName", aacEncoder),
          Map.entry("debugFlag", debugFlag),
          Map.entry("overwriteExisting", overwriteExisting),
          Map.entry("dummyProcessing", dummyProcessing),
          Map.entry("encoderParameters", genresEncoderFlags),
          Map.entry("customGenreName",
            getCustomGenreName(genre.toLowerCase()))));
    runEncodingJob(job);

    return isMultiDirGroup;
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


  public static String getNextEncodingJobName() {
    nofEncodingJobsCreated++;
    return "job" + nofEncodingJobsCreated;
  }


  public static void runEncodingJob(EncodingJob newJob) throws Exception {
    int index = 0;
findUnusedSlot:
    while (true) {
      while (index < encodingJobs.length) {
        EncodingJob job = encodingJobs[index];
        if (job == null || job.isAlive() == false) {
          if (job != null) {
            if (debugFlag) {
              logger.log("Waiting for \"" + job.getJobName() + "\" to finish.");
              joinAndTestEncodingJob(job);
            }
          }
          break findUnusedSlot;
        }
        index++;
      }
      index = 0;
      Thread.sleep(1);
    }

    if (debugFlag) {
      logger.log("Using slot " + index + " for new job.");
    }
    encodingJobs[index] = newJob;
    newJob.start();
  }


  public static void joinAndTestEncodingJob(
      EncodingJob encodingJob) throws Exception {
    encodingJob.join();
    Exception jobsLastException = encodingJob.getLastException();
    if (jobsLastException != null) {
      throw jobsLastException;
    }
  }


  private static String getCustomGenreName(String genre) {
    if (customGenreNames.containsKey(genre)) {
      System.out.println(customGenreNames.get(genre));
      return customGenreNames.get(genre);
    }
    else {
      return genre;
    }
  }
}

