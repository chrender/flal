
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.Process;
import java.lang.ProcessBuilder;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.audio.AudioHeader;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.images.Artwork;
import org.jaudiotagger.tag.Tag;
import org.jaudiotagger.tag.TagField;




// TODO: Integrity Check: When group encompasses multiple directories,
// check if they all actually belong to this group, e.g. no music files
// in a directory which has several DISCTOTAL>1 subdirs.

// TODO: Implement lame/mp3.

// TODO: Implement afconvert.

// TODO: Implement <targetFile.suffix>.lock for parallel processing.



public class EncodingJob {

  private static final Pattern encPattern = Pattern.compile("FLAC (.*) bits");
  private String jobName;
  private String rootDir;
  private List<File> sourceFiles;
  private File outputFile;
  private String[] encoderParameters = Constants.fdkaacEncoderDefaults;
  private boolean overwriteExisting = false;
  private boolean dummyProcessing = false;
  private Logger logger = null;


  /**
   * Creates a new encoding job.
   *
   * @param logger Logger to write encoder job's output to.
   * @param sourceFiles List of source files. Files are processed in
   *          the supplied list's order.
   * @param outputFile File to write to. 
   */
  public EncodingJob(Logger logger, String jobName, String rootDir,
      List<File> sourceFiles, File outputFile) {
    this.logger = logger;
    this.jobName = jobName;
    this.rootDir = rootDir;
    this.sourceFiles = sourceFiles;
    this.outputFile = outputFile;
  }


  public EncodingJob(Logger logger, String jobName, String rootDir,
      List<File> sourceFiles, File outputFile, String[] encoderParameters) {
    this(logger, jobName, rootDir, sourceFiles, outputFile);
    this.encoderParameters = encoderParameters;
  }


  public EncodingJob(Logger logger, String jobName, String rootDir,
      List<File> sourceFiles, File outputFile, String[] encoderParameters,
      boolean overwriteExisting) {
    this(logger, jobName, rootDir, sourceFiles, outputFile, encoderParameters);
    this.overwriteExisting = overwriteExisting;
  }


  public EncodingJob(Logger logger, String jobName, String rootDir,
      List<File> sourceFiles, File outputFile, String[] encoderParameters,
      boolean overwriteExisting, boolean dummyProcessing) {
    this(logger, jobName, rootDir, sourceFiles, outputFile,
       encoderParameters, overwriteExisting);
    this.dummyProcessing = dummyProcessing;
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

    long bitrate = header.getBitRateAsNumber();
    int sampleRate = header.getSampleRateAsNumber();
    int channels = Integer.parseInt(header.getChannels());
    String format = header.getFormat();
    String encType = header.getEncodingType();
    Matcher m = encPattern.matcher(format);
    m.find();
    String bits = m.group(1);

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

    if (targetLastModified < latestModified) {
      TagField binaryField = tag.getFirstField(FieldKey.COVER_ART);
      List<Artwork> existingArtworkList = tag.getArtworkList();

      File tmpOutputFile = File.createTempFile(
          outputFile.getName() + "-", ".m4a");
      tmpOutputFile.deleteOnExit();

      if (dummyProcessing == true) {
	tmpOutputFile.createNewFile(); 
      }
      else {
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

        if (title != null && title.length()>1) {
          parameters.add("--title");
          parameters.add(title);
        }

        if (artist != null && artist.length()>1) {
          parameters.add("--artist");
          parameters.add(artist);
        }

        if (album != null && album.length()>1) {
          parameters.add("--album");
          parameters.add(album);
        }

        if (composer != null && composer.length()>1) {
          parameters.add("--composer");
          parameters.add(composer);
        }

        if (genre != null && genre.length()>1) {
          parameters.add("--genre");
          parameters.add(genre);
        }

        if (this.encoderParameters != null) {
          parameters.addAll(Arrays.asList(this.encoderParameters));
        }

        if (sourceFiles.size() == 1) {
          String trackNumber = getTagOrEmpty(tag, FieldKey.TRACK);
          String trackTotal = getTagOrEmpty(tag, FieldKey.TRACK_TOTAL);
          String discNumber = getTagOrEmpty(tag, FieldKey.DISC_NO);
          String discTotal = getTagOrEmpty(tag, FieldKey.DISC_TOTAL);
          String isCompilationAsString = getTagOrEmpty(tag,
              FieldKey.IS_COMPILATION);

          boolean isCompilation
            = (isCompilationAsString != null
                && isCompilationAsString.equals("1"));

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

        parameters.add("-");

	Process process = new ProcessBuilder(
                parameters.toArray(new String[0])).start();
	OutputStream os = process.getOutputStream();
	InputStream oes = process.getErrorStream();
	InputStreamReader oer = new InputStreamReader(oes);

	for (int sourceIndex=0; sourceIndex<sourceFiles.size(); sourceIndex++) {
	  File sourceFile = sourceFiles.get(sourceIndex);
	  f = AudioFileIO.read(sourceFile);
          header = f.getAudioHeader();
          int trackLength = header.getTrackLength();
          String relativePath = sourceFile.getParentFile().getAbsolutePath(
              ).substring(rootDir.length()+1);
          int minutes = trackLength / 60;
          int seconds = trackLength - (minutes * 60);
          logMessage((sourceIndex+1) + "/" + sourceFiles.size() + ": "
              + relativePath + "/" + sourceFile.getName()
              + " [" + minutes + ":" + (seconds < 10 ? "0" : "")
              + seconds + "]");

	  Process inProc = new ProcessBuilder(
              "flac",
              "--silent",
              "--decode",
              "--sign=signed",
              "--endian=little",
              "--force-raw-format", sourceFile.getAbsolutePath(),
              "-c").start();
	  InputStream is = inProc.getInputStream();
	  InputStream es = inProc.getErrorStream();
	  InputStreamReader er = new InputStreamReader(es);
	  byte[] data = new byte[1024];

	  int nRead;
	  while ((nRead = is.read(data, 0, data.length)) != -1) {
	    os.write(data, 0, nRead);
	    dumpStream("out", oer);
	    dumpStream("in", er);
	  }
	  dumpStream("out", oer);
	  dumpStream("in", er);
	  er.close();
	  is.close();
	  int inExit = inProc.waitFor();
	  if (inExit != 0) {
	    logMessage("inexitcode:" + inExit);
	  }
	}
	os.close();
	dumpStream("out", oer);
	oer.close();
	int outExit = process.waitFor();
	if (outExit != 0) {
	  logMessage("outexitcode:" + outExit);
	}

	f = AudioFileIO.read(tmpOutputFile);
	tag = f.getTag();
	tag.addField(existingArtworkList.get(0));
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
      String prefix, InputStreamReader er) throws Exception {
    StringBuffer output = new StringBuffer();
    boolean atLineStart = true;
    if (er.ready()) {
      do {
	int erData = er.read();
	if (erData != -1) {
	  if (atLineStart) {
	    output.append("[" + prefix + "] ");
	    atLineStart = false;
	  }
	  char theChar = (char) erData;
	  if (theChar == '\r') {
	    theChar = '\n';
	  }
	  if (theChar == '\n') {
	    atLineStart = true;
	  }
	  output.append(theChar);
	}
      }
      while (er.ready());
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

