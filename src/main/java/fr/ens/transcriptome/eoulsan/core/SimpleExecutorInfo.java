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

package fr.ens.transcriptome.eoulsan.core;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.logging.Logger;

import fr.ens.transcriptome.eoulsan.EoulsanRuntime;
import fr.ens.transcriptome.eoulsan.Globals;
import fr.ens.transcriptome.eoulsan.datatypes.DataType;
import fr.ens.transcriptome.eoulsan.datatypes.DataTypes;
import fr.ens.transcriptome.eoulsan.design.Sample;

/**
 * This class define an simple ExecutorInfo.
 * @author jourdren
 */
public class SimpleExecutorInfo implements ExecutorInfo {

  protected static final Logger logger = Logger.getLogger(Globals.APP_NAME);

  private String basePathname;
  private String logPathname;
  private String outputPathname;
  private String executionName;
  private String designPathname;
  private String paramPathname;
  private String commandName = "";
  private String commandDescription = "";
  private String commandAuthor = "";

  //
  // Getters
  //

  /**
   * Get the base path
   * @return Returns the basePath
   */
  @Override
  public String getBasePathname() {
    return this.basePathname;
  }

  /**
   * Get the log path
   * @return Returns the log Path
   */
  @Override
  public String getLogPathname() {
    return this.logPathname;
  }

  /**
   * Get the output path
   * @return Returns the output Path
   */
  @Override
  public String getOutputPathname() {
    return this.outputPathname;
  }

  /**
   * Get the execution name.
   * @return the execution name
   */
  @Override
  public String getExecutionName() {
    return this.executionName;
  }

  /**
   * Get the design path.
   * @return the design path
   */
  @Override
  public String getDesignPathname() {
    return this.designPathname;
  }

  /**
   * Get the parameter path.
   * @return the parameter path
   */
  @Override
  public String getParameterPathname() {
    return this.paramPathname;
  }

  /**
   * Get the command name.
   * @return the command name
   */
  @Override
  public String getCommandName() {
    return this.commandName;
  }

  /**
   * Get command description.
   * @return the command description
   */
  @Override
  public String getCommandDescription() {
    return this.commandDescription;
  }

  /**
   * Get the command author.
   * @return the command author
   */
  @Override
  public String getCommandAuthor() {
    return this.commandAuthor;
  }

  //
  // Setters
  //

  /**
   * Set the base path
   * @param basePath The basePath to set
   */
  public void setBasePathname(final String basePath) {

    logger.info("Base path: " + basePath);
    this.basePathname = basePath;
  }

  /**
   * Set the log path
   * @param logPath The log Path to set
   */
  public void setLogPathname(final String logPath) {

    logger.info("Log path: " + logPath);
    this.logPathname = logPath;
  }

  /**
   * Set the output path
   * @param logPathname The log Path to set
   */
  public void setOutputPathname(final String outputPath) {

    logger.info("Output path: " + outputPath);
    this.outputPathname = outputPath;
  }

  /**
   * Set the design path
   * @param designPathname The log Path to set
   */
  public void setDesignPathname(final String designPathname) {

    logger.info("Design path: " + designPathname);
    this.designPathname = designPathname;
  }

  /**
   * Set the parameter path
   * @param paramPathname The log Path to set
   */
  public void setParameterPathname(final String paramPathname) {

    logger.info("Parameter path: " + paramPathname);
    this.paramPathname = paramPathname;
  }

  /**
   * Set command name
   * @param commandName the command name
   */
  public void setCommandName(final String commandName) {

    logger.info("Command name: " + commandName);
    this.commandName = commandName;
  }

  /**
   * Set command description
   * @param commandDescription the command name
   */
  public void setCommandDescription(final String commandDescription) {

    logger.info("Command description: " + commandDescription);
    this.commandDescription = commandDescription;
  }

  /**
   * Set command author
   * @param commandAuthor the command name
   */
  public void setCommandAuthor(final String commandAuthor) {

    logger.info("Command author: " + commandAuthor);
    this.commandAuthor = commandAuthor;
  }

  /**
   * Add information from command object.
   * @param command the command object
   */
  public void addCommandInfo(final Command command) {

    if (command == null)
      throw new NullPointerException("The command is null");

    setCommandName(command.getName());
    setCommandDescription(command.getDescription());
    setCommandAuthor(command.getAuthor());
  }

  //
  // Other methods
  //

  private void createExecutionName() {

    final Calendar cal = Calendar.getInstance(Locale.ENGLISH);
    cal.setTime(new Date(System.currentTimeMillis()));

    this.executionName =
        Globals.APP_NAME_LOWER_CASE
            + "-"
            + String.format("%04d%02d%02d-%02d%02d%02d",
                cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal
                    .get(Calendar.DAY_OF_MONTH), cal.get(Calendar.HOUR_OF_DAY),
                cal.get(Calendar.MINUTE), cal.get(Calendar.SECOND));
  }



  /**
   * Add executor information to log.
   */
  @Override
  public void logInfo() {

    logger.info("EXECINFO Design path: " + this.getDesignPathname());
    logger.info("EXECINFO Parameter path: " + this.getParameterPathname());

    logger.info("EXECINFO Author: " + this.getCommandAuthor());
    logger.info("EXECINFO Description: " + this.getCommandDescription());
    logger.info("EXECINFO Command name: " + this.getCommandName());
    logger.info("EXECINFO Exection name: " + this.getExecutionName());

    logger.info("EXECINFO Base path: " + this.getBasePathname());
    logger.info("EXECINFO Output path: " + this.getOutputPathname());
    logger.info("EXECINFO Log path: " + this.getLogPathname());
  }

  @Override
  public String getPathname(final DataType dt, final Sample sample) {

    if (dt == null || sample == null)
      return null;

    if (dt == DataTypes.READS)
      return sample.getSource();

    if (dt == DataTypes.GENOME)
      return sample.getMetadata().getGenome();

    if (dt == DataTypes.ANNOTATION)
      return sample.getMetadata().getAnnotation();

    return this.getBasePathname()
        + "/"
        + dt.getPrefix()
        + (dt.isOneFilePerAnalysis() ? "1" : sample.getId()
            + dt.getDefaultExtention());
  }

  @Override
  public InputStream getInputStream(final DataType dt, final Sample sample)
      throws IOException {

    final String src = getPathname(dt, sample);

    return EoulsanRuntime.getRuntime().getInputStream(src);
  }

  @Override
  public InputStream getRawInputStream(final DataType dt, final Sample sample)
      throws IOException {

    final String src = getPathname(dt, sample);

    return EoulsanRuntime.getRuntime().getRawInputStream(src);
  }

  @Override
  public OutputStream getOutputStream(final DataType dt, final Sample sample)
      throws IOException {

    return EoulsanRuntime.getRuntime().getOutputStream(
        getPathname(dt, sample));
  }

  //
  // Constructor
  //

  /**
   * Public constructor.
   */
  public SimpleExecutorInfo() {

    createExecutionName();
  }

}