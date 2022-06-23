
import java.io.OutputStream;
import java.io.IOException;
import java.io.FilterOutputStream;


public class WavOutputStream extends FilterOutputStream {

  public static final long maxUInt32Value = 1L << 32 -1;

  private int bitsPerSample;
  private int numberOfChannels;
  private int sampleRate;
  private long dataLength;
  private boolean headerWritter = false;


  public WavOutputStream(OutputStream out, int bitsPerSample,
      int numberOfChannels, int sampleRate, long dataLength) {
    super(out);
    this.bitsPerSample = bitsPerSample;
    this.numberOfChannels = numberOfChannels;
    this.sampleRate = sampleRate;
    this.dataLength = dataLength;
  }


  public WavOutputStream(OutputStream out, int bitsPerSample,
      int numberOfChannels, int sampleRate) {
    this(out, bitsPerSample, numberOfChannels, sampleRate, Integer.MAX_VALUE);
  }


  public void close() throws IOException {
    writeHeaderIfRequired();
    out.close();
  }


  public void flush()  throws IOException {
    out.flush();
  }


  public void write(byte[] b) throws IOException {
    writeHeaderIfRequired();
    out.write(b);
  }


  public void write(byte[] b, int off, int len) throws IOException {
    writeHeaderIfRequired();
    out.write(b);
  }


  private void writeHeaderIfRequired() throws IOException {
    if (headerWritter == false) {
      long bitRate
        = sampleRate * numberOfChannels * bitsPerSample;
      long totalDataLength
        = dataLength > maxUInt32Value-36 ? maxUInt32Value : dataLength + 36;

      byte[] header = new byte[44];

      header[ 0] = 'R'; 
      header[ 1] = 'I';
      header[ 2] = 'F';
      header[ 3] = 'F';
      header[ 4] = (byte) ( totalDataLength & 0xff);
      header[ 5] = (byte) ((totalDataLength >> 8) & 0xff);
      header[ 6] = (byte) ((totalDataLength >> 16) & 0xff);
      header[ 7] = (byte) ((totalDataLength >> 24) & 0xff);
      header[ 8] = 'W';
      header[ 9] = 'A';
      header[10] = 'V';
      header[11] = 'E';
      header[12] = 'f'; 
      header[13] = 'm';
      header[14] = 't';
      header[15] = ' ';
      header[16] = (byte) bitsPerSample; 
      header[17] = 0;
      header[18] = 0;
      header[19] = 0;
      header[20] = 1; 
      header[21] = 0;
      header[22] = (byte) numberOfChannels; 
      header[23] = 0;
      header[24] = (byte) ( sampleRate & 0xff);
      header[25] = (byte) ((sampleRate >> 8) & 0xff);
      header[26] = (byte) ((sampleRate >> 16) & 0xff);
      header[27] = (byte) ((sampleRate >> 24) & 0xff);
      header[28] = (byte) (( bitRate / 8) & 0xff);
      header[29] = (byte) (((bitRate / 8) >> 8) & 0xff);
      header[30] = (byte) (((bitRate / 8) >> 16) & 0xff);
      header[31] = (byte) (((bitRate / 8) >> 24) & 0xff);
      header[32] = (byte) ((numberOfChannels * bitsPerSample) / 8); 
      header[33] = 0;
      header[34] = 16; 
      header[35] = 0;
      header[36] = 'd';
      header[37] = 'a';
      header[38] = 't';
      header[39] = 'a';
      header[40] = (byte) ( dataLength  & 0xff);
      header[41] = (byte) ((dataLength >> 8) & 0xff);
      header[42] = (byte) ((dataLength >> 16) & 0xff);
      header[43] = (byte) ((dataLength >> 24) & 0xff);

      out.write(header);
      headerWritter = true;
    }
  }
}

