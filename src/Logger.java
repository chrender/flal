
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;


public class Logger {

  private String logFilename = null;
  private BufferedOutputStream outputStream = null;


  public Logger(String logDirectory) {
    if (logDirectory != null) {
      logFilename
        = logDirectory
        + "/flal-" 
        + new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss").format(new Date())
        + ".txt";
    }
  }

  private String getTimestamp() {
    return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
  }

  public void log(String message) {
    log(message, null);
  }

  public void log(String message, String jobName) {
    System.out.println("[" + getTimestamp() + (jobName != null ? "|" + jobName : "") + "] " + message);
  }


  @Override
  public void finalize() {
    try {
      if (outputStream != null) {
        outputStream.close();
      }
    } catch (IOException e) {
      e.printStackTrace();
    }
  }
}

