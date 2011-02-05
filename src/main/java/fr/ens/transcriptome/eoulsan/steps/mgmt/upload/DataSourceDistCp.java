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

package fr.ens.transcriptome.eoulsan.steps.mgmt.upload;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.text.NumberFormat;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Counter;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.input.TextInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;

import com.google.common.collect.Lists;

import fr.ens.transcriptome.eoulsan.EoulsanRuntime;
import fr.ens.transcriptome.eoulsan.EoulsanRuntimeException;
import fr.ens.transcriptome.eoulsan.Globals;
import fr.ens.transcriptome.eoulsan.HadoopEoulsanRuntime;
import fr.ens.transcriptome.eoulsan.data.DataFile;
import fr.ens.transcriptome.eoulsan.data.DataFormatConverter;
import fr.ens.transcriptome.eoulsan.util.PathUtils;
import fr.ens.transcriptome.eoulsan.util.StringUtils;

/**
 * This class allow to copy and transform data in a distributed manner.
 * @author Laurent Jourdren
 */
public class DataSourceDistCp {

  /** Logger. */
  private static final Logger LOGGER = Logger.getLogger(Globals.APP_NAME);

  private final Configuration conf;
  private final Path jobPath;

  private static final long MAX_COPY_DURATION = 60 * 60 * 1000;

  /**
   * This inner class define the mapper class for DataSourceDistCp map-reduce
   * job.
   * @author Laurent Jourdren
   */
  public static final class DistCpMapper extends
      Mapper<LongWritable, Text, Text, Text> {

    private static final String COUNTER_GROUP_NAME = "DataSourceDistCp";

    /**
     * Internal class to store an exception if occurs while coping.
     * @author Laurent Jourdren
     */
    private static final class MyIOException {
      public IOException ioexception;
    }

    @Override
    protected void setup(final Context context) throws IOException,
        InterruptedException {

      if (!EoulsanRuntime.isRuntime()) {
        HadoopEoulsanRuntime.newEoulsanRuntime(context.getConfiguration());
      }

    }

    @Override
    protected void map(final LongWritable key, final Text value, Context context)
        throws IOException, InterruptedException {

      final String val = value.toString();

      final int tabPos = val.indexOf('\t');

      if (tabPos == -1)
        return;

      final Configuration conf = context.getConfiguration();

      final String srcPathname = val.substring(0, tabPos);
      final Path srcPath = new Path(srcPathname);
      final Path destPath = new Path(val.substring(tabPos + 1));

      final FileSystem srcFs = srcPath.getFileSystem(conf);
      final FileSystem destFs = destPath.getFileSystem(conf);

      // Statistic about src file
      final FileStatus fStatusSrc = srcFs.getFileStatus(srcPath);
      final long srcSize = fStatusSrc == null ? 0 : fStatusSrc.getLen();

      LOGGER.info("Start copy "
          + srcPathname + " to " + destPath + " (" + srcSize + " bytes)\n");

      final long startTime = System.currentTimeMillis();

      final DataFile src = new DataFile(srcPathname);
      final DataFile dest = new DataFile(destPath.toString());

      // Copy the file
      copyFile(src, dest, context);

      // Compute copy statistics
      final long duration = System.currentTimeMillis() - startTime;
      final FileStatus fStatusDest = destFs.getFileStatus(destPath);
      final long destSize = fStatusDest == null ? 0 : fStatusDest.getLen();
      final double speed =
          destSize == 0 ? 0 : (double) destSize / (double) duration * 1000;

      LOGGER.info("End copy "
          + srcPathname + " to " + destPath + " in "
          + StringUtils.toTimeHumanReadable(duration) + " (" + destSize
          + " bytes, " + ((int) speed) + " bytes/s)\n");

      context.getCounter(COUNTER_GROUP_NAME, "Input file size").increment(
          srcSize);
      context.getCounter(COUNTER_GROUP_NAME, "Output file size").increment(
          destSize);
    }

    /**
     * Copy the file using a Thread and inform Hadoop of the live of the copy
     * with a counter.
     * @param src source
     * @param dest destination
     * @param context context object
     * @throws InterruptedException if another thread has interrupted the
     *           current thread
     * @throws IOException if an error occurs while copying data
     */
    private static final void copyFile(final DataFile src, final DataFile dest,
        final Context context) throws InterruptedException, IOException {

      // Define a wrapper object to store exception if needed
      final MyIOException exp = new MyIOException();

      // Create the thread for copy
      final Thread t = new Thread(new Runnable() {

        @Override
        public void run() {
          try {
            new DataFormatConverter(src, dest).convert();
          } catch (IOException e) {
            exp.ioexception = e;
          }
        }
      });

      // Start thread
      t.start();

      // Create counter
      final Counter counter =
          context.getCounter(COUNTER_GROUP_NAME, "5_seconds");

      final long startTime = System.currentTimeMillis();

      // Sleep and increment counter until the end of copy
      while (t.isAlive()) {
        Thread.sleep(5000);
        counter.increment(1);

        final long duration = System.currentTimeMillis() - startTime;

        if (duration > MAX_COPY_DURATION) {
          throw new IOException("Copy timeout, copy exceed "
              + (MAX_COPY_DURATION / 1000) + " seconds.");
        }

      }

      // Throw Exception if needed
      if (exp.ioexception != null) {
        throw exp.ioexception;
      }
    }

  }

  public void copy(final Map<DataFile, DataFile> entries) throws IOException {

    if (entries == null || entries.size() == 0)
      return;

    final Configuration conf = this.conf;
    final Path tmpInputDir =
        PathUtils.createTempPath(jobPath, "distcp-in-", "", conf);
    final Path tmpOutputDir =
        PathUtils.createTempPath(jobPath, "distcp-out-", "", conf);

    //
    // Create entries for distcp
    //

    final FileSystem fs = tmpInputDir.getFileSystem(conf);
    fs.mkdirs(tmpInputDir);

    // Sort files by size
    final List<DataFile> inFiles = Lists.newArrayList(entries.keySet());
    sortInFilesByDescSize(inFiles);

    // Set the format for the id of the copy task
    final NumberFormat nf = NumberFormat.getInstance();
    nf.setMinimumIntegerDigits(Integer.toString(inFiles.size()).length());
    nf.setGroupingUsed(false);

    int count = 0;
    for (DataFile inFile : inFiles) {

      count++;

      final DataFile outFile = entries.get(inFile);

      final Path f =
          new Path(tmpInputDir, "distcp-" + nf.format(count) + ".cp");

      LOGGER.info("Task copy " + inFile + " in " + f.toString());

      BufferedWriter bw =
          new BufferedWriter(new OutputStreamWriter(fs.create(f)));

      bw.write(inFile.getSource() + "\t" + outFile.getSource() + "\n");
      bw.close();
    }

    final Job job = createJobConf(conf, tmpInputDir, tmpOutputDir);

    try {
      job.waitForCompletion(false);
    } catch (InterruptedException e) {
      throw new EoulsanRuntimeException("Error while distcp: " + e.getMessage());
    } catch (ClassNotFoundException e) {
      throw new EoulsanRuntimeException("Error while distcp: " + e.getMessage());
    }

    // Remove tmp directory
    PathUtils.fullyDelete(tmpInputDir, conf);
    PathUtils.fullyDelete(tmpOutputDir, conf);

    if (!job.isSuccessful())
      throw new IOException("Unable to copy files using DataSourceDistCp.");
  }

  /**
   * Sort a list of DataFile by dissident order.
   * @param inFiles list of DataFile to sort
   */
  private void sortInFilesByDescSize(final List<DataFile> inFiles) {

    Collections.sort(inFiles, new Comparator<DataFile>() {

      @Override
      public int compare(final DataFile f1, DataFile f2) {

        long size1;

        try {
          size1 = f1.getMetaData().getContentLength();
        } catch (IOException e) {
          size1 = -1;
        }

        long size2;
        try {
          size2 = f2.getMetaData().getContentLength();
        } catch (IOException e) {
          size2 = -1;
        }

        return ((Long) size1).compareTo(size2) * -1;
      }

    });

  }

  private static Job createJobConf(final Configuration parentConf,
      final Path cpEntriesPath, final Path outputPath) throws IOException {

    final Configuration jobConf = new Configuration(parentConf);

    // timeout
    jobConf.set("mapred.task.timeout", "" + MAX_COPY_DURATION);

    // Create the job and its name
    final Job job = new Job(jobConf, "Distcp");

    // Set the jar
    job.setJarByClass(DataSourceDistCp.class);

    // Add input path
    FileInputFormat.addInputPath(job, cpEntriesPath);

    // Set the input format
    job.setInputFormatClass(TextInputFormat.class);

    // Set the Mapper class
    job.setMapperClass(DistCpMapper.class);

    // Set the reducer class
    // job.setReducerClass(IdentityReducer.class);

    // Set the output key class
    job.setOutputKeyClass(Text.class);

    // Set the output value class
    job.setOutputValueClass(Text.class);

    // Set the number of reducers
    job.setNumReduceTasks(1);

    // Set the output Path
    FileOutputFormat.setOutputPath(job, outputPath);

    return job;
  }

  //
  // Constructor
  //

  /**
   * Public constructor.
   * @param conf Configuration object
   * @param jobPath the path where create job temporary file
   */
  public DataSourceDistCp(final Configuration conf, final Path jobPath) {

    if (conf == null)
      throw new NullPointerException("The configuration is null");

    if (jobPath == null)
      throw new NullPointerException("The job Path is null");

    this.conf = conf;
    this.jobPath = jobPath;

  }

}
