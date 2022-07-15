
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.Process;
import java.lang.ProcessBuilder;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.audio.AudioHeader;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.images.Artwork;
import org.jaudiotagger.tag.Tag;
import org.jaudiotagger.tag.TagField;

import de.christoph_ender.audio.RF64FileOutputStream;



// TODO: Use fifo/named pipe for afconvert when available.

// TODO: Integrity Check: When group encompasses multiple directories,
// check if they all actually belong to this group, e.g. no music files
// in a directory which has several DISCTOTAL>1 subdirs.

// TODO: Implement lame/mp3.

// TODO: Implement <targetFile.suffix>.lock for parallel processing.


// afconvert flags / settings:
// https://ss64.com/osx/afconvert.html


public class EncodingJob {

  private static final Pattern encPattern = Pattern.compile("FLAC (.*) bits");
  private String jobName;
  private String rootDir;
  private List<File> sourceFiles;
  private File outputFile;
  private String encoderName;
  private boolean debugFlag = false;
  private String[] encoderParameters = null;
  private boolean overwriteExisting = false;
  private boolean dummyProcessing = false;
  private Logger logger = null;


  @SuppressWarnings("unchecked")
  static <T> T getParm(Map<String, Object> map, String key, T defaultValue) {
    return (map.containsKey(key)) ? (T) map.get(key) : defaultValue;
  }

  @SuppressWarnings("unchecked")
  static <T> T getParm(Map<String, Object> map, String key) {
    return (map.containsKey(key)) ? (T) map.get(key) : null;
  }

  public EncodingJob(Map<String,Object> parameters) {
    this.logger = getParm(parameters, "logger");
    this.jobName = getParm(parameters, "jobName");
    this.rootDir = getParm(parameters, "rootDir");
    this.sourceFiles = getParm(parameters, "sourceFiles");
    this.outputFile = getParm(parameters, "outputFile");
    this.encoderName = getParm(parameters, "encoderName");
    this.debugFlag = getParm(parameters, "debugFlag");
    this.overwriteExisting = getParm(parameters, "overwriteExisting");
    this.dummyProcessing = getParm(parameters, "dummyProcessing");
    this.encoderParameters = getParm(parameters, "encoderParameters");
  }

  /**
   * Creates a new encoding job.
   *
   * @param logger Logger to write encoder job's output to.
   * @param sourceFiles List of source files. Files are processed in
   *          the supplied list's order.
   * @param outputFile File to write to.
   */
  public EncodingJob(Logger logger, String jobName, String rootDir,
      List<File> sourceFiles, File outputFile, String encoderName) {
    this.logger = logger;
    this.jobName = jobName;
    this.rootDir = rootDir;
    this.sourceFiles = sourceFiles;
    this.outputFile = outputFile;
    this.encoderName = encoderName;
  }



  public void run() throws Exception {
    File file = sourceFiles.get(0);
    AudioFile f = AudioFileIO.read(file);
    Tag tag = f.getTag();
    AudioHeader header = f.getAudioHeader();

    String album = tag.getFirst(FieldKey.ALBUM);
    String title = sourceFiles.size()>1 ? album : tag.getFirst(FieldKey.TITLE);
    String artist = tag.getFirst(FieldKey.ARTIST);
    String genre = tag.getFirst(FieldKey.GENRE);
    String composer = tag.getFirst(FieldKey.COMPOSER);
    String releaseDate = tag.getFirst("DATE");

    String trackNumber = null;
    String trackTotal = null;
    String discNumber = null;
    String discTotal = null;
    boolean isCompilation = false;

    if (sourceFiles.size() == 1) {
      trackNumber = getTagOrEmpty(tag, FieldKey.TRACK);
      trackTotal = getTagOrEmpty(tag, FieldKey.TRACK_TOTAL);
      discNumber = getTagOrEmpty(tag, FieldKey.DISC_NO);
      discTotal = getTagOrEmpty(tag, FieldKey.DISC_TOTAL);
      String isCompilationAsString
        = getTagOrEmpty(tag, FieldKey.IS_COMPILATION);
      isCompilation = (isCompilationAsString != null
          && isCompilationAsString.equals("1"));
    }

    long bitrate = header.getBitRateAsNumber();
    int sampleRate = header.getSampleRateAsNumber();
    int channels = Integer.parseInt(header.getChannels());
    String format = header.getFormat();
    String encType = header.getEncodingType();
    Matcher m = encPattern.matcher(format);
    m.find();
    int bits = Integer.parseInt(m.group(1));

    long latestModified = 0;
    for (File sourceFile : sourceFiles) {
      if (sourceFile.lastModified() > latestModified) {
        latestModified = sourceFile.lastModified();
      }
    }

    long targetLastModified = 0;
    if (outputFile.exists()) {
      targetLastModified = outputFile.lastModified();
    }
    else if (outputFile.getParentFile().exists() == false) {
      outputFile.getParentFile().mkdirs();
    }

    if (targetLastModified < latestModified || overwriteExisting) {
      TagField binaryField = tag.getFirstField(FieldKey.COVER_ART);
      List<Artwork> existingArtworkList = tag.getArtworkList();

      File tmpOutputFile = File.createTempFile(
          outputFile.getName() + "-", ".m4a");
      tmpOutputFile.deleteOnExit();

      if (dummyProcessing == true) {
	tmpOutputFile.createNewFile();
      }
      else {
        Process outProc;
        OutputStream os;
        InputStreamReader oer;
        File tmpWavFile;
        List<String> flacParameters = new ArrayList<String>();

        if (encoderName.equals("fdkaac")) {
          List<String> parameters = new ArrayList<String>();
          parameters.addAll(Arrays.asList( new String[]
                { "fdkaac",
                  "--silent",
                  "--raw",
                  "--raw-channels", Integer.toString(channels),
                  "--raw-rate", Integer.toString(sampleRate),
                  "--raw-format" , "S"+bits+"L",
                  "-o", tmpOutputFile.getAbsolutePath()
                }));
          if (this.encoderParameters != null) {
            parameters.addAll(Arrays.asList(this.encoderParameters));
          }

          /*
          if (title != null && title.length() > 0) {
            parameters.add("--title");
            parameters.add(title);
          }

          if (artist != null && artist.length() > 0) {
            parameters.add("--artist");
            parameters.add(artist);
          }

          if (album != null && album.length() > 0) {
            parameters.add("--album");
            parameters.add(album);
          }

          if (composer != null && composer.length() > 0) {
            parameters.add("--composer");
            parameters.add(composer);
          }

          if (genre != null && genre.length() > 0) {
            parameters.add("--genre");
            parameters.add(genre);
          }

          if (this.encoderParameters != null) {
            parameters.addAll(Arrays.asList(this.encoderParameters));
          }

          if (sourceFiles.size() == 1) {
            if (!trackNumber.isEmpty() || !trackTotal.isEmpty()) {
              parameters.addAll(Arrays.asList( new String[]
                    { "--track", trackNumber + "/" + trackTotal }));
            }

            if (!discNumber.isEmpty() || !discTotal.isEmpty()) {
              parameters.addAll(Arrays.asList( new String[]
                    { "--disk", discNumber + "/" + discTotal }));
            }

            if (isCompilation) {
              parameters.addAll(Arrays.asList( new String[]
                    { "--tag", "cpil:1" }));
            }
          }
          */

          parameters.add("-");

          outProc = new ProcessBuilder(
              parameters.toArray(new String[0])).start();
          os = outProc.getOutputStream();
          InputStream oes = outProc.getErrorStream();
          oer = new InputStreamReader(oes);

          flacParameters.add("--sign=signed");
          flacParameters.add("--endian=little");
          flacParameters.add("--force-raw-format");

          tmpWavFile = null;
        }
        else if (encoderName.equals("afconvert")) {
          outProc = null;
          tmpWavFile = File.createTempFile(outputFile.getName() + "-", ".wav");
          tmpWavFile.deleteOnExit();
          os = new RF64FileOutputStream(tmpWavFile,
              bits, channels, sampleRate);
          logMessage(tmpWavFile.getAbsolutePath());
          oer = null;
          if (bits == 8) {
            flacParameters.add("--sign=unsigned");
          }
          else {
            flacParameters.add("--sign=signed");
          }
          //  RIFF header requires little endian byte order, RIFX
          //  big endian order.
          flacParameters.add("--endian=little");

          flacParameters.add("--force-raw-format");
        }
        else {
          throw new Exception ("Unknown encoder \"" + encoderName + "\".");
        }

        List<String> processParameters = new ArrayList<String>();
        processParameters.addAll(
            Arrays.asList( new String[]
              { "flac",
                "--silent",
                "--decode",
                "-c" }));
        processParameters.addAll(
            flacParameters);
        for (File sourceFile : sourceFiles) {
          processParameters.add(sourceFile.getAbsolutePath());
        }
        if (debugFlag) {
          for (int i=0; i<processParameters.size(); i++) {
            logMessage(
                "Param " + (i+1) + ": \"" + processParameters.get(i) + "\"");
          }
        }
        Process inProc = new ProcessBuilder(
            processParameters.toArray(new String[0])
            ).start();

        // flac's decoded output will be available from "is".
        InputStream is = inProc.getInputStream();
        InputStream ies = inProc.getErrorStream();
        InputStreamReader ier = new InputStreamReader(ies);
        byte[] data = new byte[1024];

        int nRead;
        while ((nRead = is.read(data, 0, data.length)) != -1) {
          os.write(data, 0, nRead);
          if (oer != null) {
            dumpStream("out", oer, false);
          }
          dumpStream("in", ier, false);
        }
        if (oer != null) {
          dumpStream("out", oer, false);
        }
        dumpStream("in", ier, true);
        ier.close();
        is.close();
        int inExit = inProc.waitFor();
        if (inExit != 0) {
          logMessage("inexitcode:" + inExit);
        }
        os.close();
        if (oer != null) {
          dumpStream("out", oer, true);
          oer.close();
        }
        if (outProc != null) {
          int outExit = outProc.waitFor();
          if (outExit != 0) {
            logMessage("outexitcode:" + outExit);
          }
        }

        if (encoderName.equals("afconvert")) {
          List<String> outProcessParams = new ArrayList<String>();
          outProcessParams.addAll(
              Arrays.asList( new String[]
                { "afconvert",
                  "-d", "aac",
                  "-f", "m4af" }));
          outProcessParams.addAll(Arrays.asList(encoderParameters));
          outProcessParams.add(tmpWavFile.getAbsolutePath());
          outProcessParams.add(tmpOutputFile.getAbsolutePath());

          if (debugFlag) {
            for (int i=0; i<outProcessParams.size(); i++) {
              logMessage(
                  "Param " + (i+1) + ": \"" + outProcessParams.get(i) + "\"");
            }
          }

          outProc = new ProcessBuilder(
              outProcessParams.toArray(new String[0])).start();
          os = outProc.getOutputStream();
          InputStream oes = outProc.getErrorStream();
          oer = new InputStreamReader(oes);
          dumpStream("out", oer, true);
          oer.close();
          int outExit = outProc.waitFor();
          if (outExit != 0) {
            logMessage("outexitcode:" + outExit);
          }
          tmpWavFile.delete();
        }

        f = AudioFileIO.read(tmpOutputFile);
        tag = f.getTag();

        if (title != null && title.length() > 0) {
          tag.setField(FieldKey.TITLE, title);
        }

        if (artist != null && artist.length() > 0) {
          tag.setField(FieldKey.ARTIST, artist);
        }

        if (album != null && album.length() > 0) {
          tag.setField(FieldKey.ALBUM, album);
        }

        if (composer != null && composer.length() > 0) {
          tag.setField(FieldKey.COMPOSER, composer);
        }

        if (genre != null && genre.length() > 0) {
          tag.setField(FieldKey.GENRE, genre);
        }

        if (releaseDate != null && releaseDate.length() > 0) {
          tag.setField(FieldKey.YEAR, releaseDate);
        }

        if (sourceFiles.size() == 1) {
          if (trackNumber != null && trackNumber.length() > 0) {
            tag.setField(FieldKey.TRACK, trackNumber);
          }

          if (trackTotal != null && trackTotal.length() > 0) {
            tag.setField(FieldKey.TRACK_TOTAL, trackTotal);
          }

          if (discNumber != null && discNumber.length() > 0) {
            tag.setField(FieldKey.DISC_NO, discNumber);
          }

          if (discTotal != null && discTotal.length() > 0) {
            tag.setField(FieldKey.DISC_TOTAL, discTotal);
          }

          if (isCompilation) {
            tag.setField(FieldKey.IS_COMPILATION, "1");
          }
        }

        if (existingArtworkList != null && existingArtworkList.size() > 0) {
          tag.addField(existingArtworkList.get(0));
        }

        StringBuffer comment = new StringBuffer();
        comment.append("Encoder: \"");
        comment.append(encoderName);
        comment.append("\", Parameters:");
        boolean firstIteration = true;
        for (String parameter : encoderParameters) {
          if (firstIteration) {
            firstIteration = false;
          }
          else {
            comment.append(", ");
          }
          comment.append("\"");
          comment.append(parameter);
          comment.append("\"");
        }
        tag.addField(FieldKey.COMMENT, comment.toString());

        f.commit();
      }

      Files.move(tmpOutputFile.toPath(), outputFile.toPath(),
          StandardCopyOption.REPLACE_EXISTING);
    }
    else {
      logMessage("Existing is newer.");
    }
  }


  public void dumpStream(
      String prefix, InputStreamReader isr, boolean waitForEOF
      ) throws Exception {
    StringBuffer output = new StringBuffer();
    boolean atLineStart = true;
    if (isr.ready() || waitForEOF) {
      do {
	int data = isr.read();
	if (data != -1) {
	  if (atLineStart) {
	    output.append("[" + prefix + "] ");
	    atLineStart = false;
	  }
	  char theChar = (char)data;
	  if (theChar == '\n') {
	    atLineStart = true;
	  }
	  output.append(theChar);
        }
        else if (waitForEOF) {
          return;
        }
      }
      while (isr.ready() | waitForEOF);
      logMessage(output.toString());
      output = new StringBuffer();
    }
  }


  private void logMessage(String message) {
    logger.log(message, jobName);
  }


  private String getTagOrEmpty(Tag tag, FieldKey fieldKey) {
    String result = tag.getFirst(fieldKey);
    return result == null ? "" : result;
  }
}

