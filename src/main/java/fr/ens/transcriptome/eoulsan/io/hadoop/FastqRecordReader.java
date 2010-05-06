/*
 *                      Nividic development code
 *
 * This code may be freely distributed and modified under the
 * terms of the GNU Lesser General Public Licence.  This should
 * be distributed with the code.  If you do not have a copy,
 * see:
 *
 *      http://www.gnu.org/copyleft/lesser.html
 *
 * Copyright for this code is held jointly by the microarray platform
 * of the École Normale Supérieure and the individual authors.
 * These should be listed in @author doc comments.
 *
 * For more information on the Nividic project and its aims,
 * or to join the Nividic mailing list, visit the home page
 * at:
 *
 *      http://www.transcriptome.ens.fr/nividic
 *
 */

package fr.ens.transcriptome.eoulsan.io.hadoop;

import java.io.IOException;
import java.util.regex.Pattern;

import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.DataOutputBuffer;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapred.FileSplit;
import org.apache.hadoop.mapred.JobConf;
import org.apache.hadoop.mapred.RecordReader;

@SuppressWarnings("deprecation")
public class FastqRecordReader implements RecordReader<LongWritable, Text> {

  private long end;
  private boolean stillInChunk = true;

  private FSDataInputStream fsin;
  private DataOutputBuffer buffer = new DataOutputBuffer();

  private byte[] endTag = "\n@".getBytes();
  private static final Pattern PATTERN = Pattern.compile("\n");
  private static final StringBuilder sb = new StringBuilder();

  public FastqRecordReader(JobConf job, FileSplit split) throws IOException {

    Path path = split.getPath();
    FileSystem fs = path.getFileSystem(job);

    fsin = fs.open(path);
    long start = split.getStart();
    end = split.getStart() + split.getLength();
    fsin.seek(start);

    if (start != 0) {
      readUntilMatch(endTag, false);
    }
  }

  @Override
  public boolean next(final LongWritable key, final Text value)
      throws IOException {

    if (!stillInChunk)
      return false;

    final long startPos = fsin.getPos();

    boolean status = readUntilMatch(endTag, true);

    final String data;

    // If start of the file, ignore first '@'
    if (startPos == 0)
      data = new String(buffer.getData(), 1, buffer.getLength());
    else
      data = new String(buffer.getData(), 0, buffer.getLength());

    final String[] lines = PATTERN.split(data);

    String id = "";
    int count = 0;

    for (int i = 0; i < lines.length; i++) {

      final String line = lines[i].trim();

      if ("".equals(line))
        continue;

      if (count == 0)
        id = line;

      if (count == 2 && !id.equals(line.substring(1)))
        throw new IOException("Invalid record: id not equals "
            + id + " " + line.substring(1));

      if (count != 2 && count < 4) {
        if (count > 0)
          sb.append("\t");
        sb.append(line);
      }
      count++;
    }

    key.set(fsin.getPos());
    value.set(sb.toString());

    sb.setLength(0);
    buffer.reset();

    if (!status)
      stillInChunk = false;

    return true;
  }

  @Override
  public long getPos() throws IOException {

    return fsin.getPos();
  }

  @Override
  public LongWritable createKey() {
    return new LongWritable();
  }

  @Override
  public Text createValue() {

    return new Text();
  }

  @Override
  public float getProgress() {
    return 0;
  }

  @Override
  public void close() throws IOException {
    fsin.close();
  }

  private boolean readUntilMatch(byte[] match, boolean withinBlock)
      throws IOException {
    int i = 0;
    while (true) {
      int b = fsin.read();
      if (b == -1)
        return false;
      if (withinBlock)
        buffer.write(b);
      if (b == match[i]) {
        i++;
        if (i >= match.length) {
          return fsin.getPos() < end;
        }
      } else
        i = 0;
    }
  }
}
