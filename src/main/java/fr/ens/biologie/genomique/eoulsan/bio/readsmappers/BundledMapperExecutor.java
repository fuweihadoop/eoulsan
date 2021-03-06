/*
 *                  Eoulsan development code
 *
 * This code may be freely distributed and modified under the
 * terms of the GNU Lesser General Public License version 2.1 or
 * later and CeCILL-C. This should be distributed with the code.
 * If you do not have a copy, see:
 *
 *      http://www.gnu.org/licenses/lgpl-2.1.txt
 *      http://www.cecill.info/licences/Licence_CeCILL-C_V1-en.txt
 *
 * Copyright for this code is held jointly by the Genomic platform
 * of the Institut de Biologie de l'École normale supérieure and
 * the individual authors. These should be listed in @author doc
 * comments.
 *
 * For more information on the Eoulsan project and its aims,
 * or to join the Eoulsan Google group, visit the home page
 * at:
 *
 *      http://outils.genomique.biologie.ens.fr/eoulsan
 *
 */

package fr.ens.biologie.genomique.eoulsan.bio.readsmappers;

import static com.google.common.base.Preconditions.checkNotNull;
import static fr.ens.biologie.genomique.eoulsan.EoulsanLogger.getLogger;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import com.google.common.base.Joiner;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;

import fr.ens.biologie.genomique.eoulsan.util.BinariesInstaller;

/**
 * This class define a mapper executor that executes process bundled in Eoulsan
 * jar file.
 * @author Laurent Jourdren
 * @since 2.0
 */
public class BundledMapperExecutor implements MapperExecutor {

  private final String softwarePackage;
  private final String version;
  private final File executablesTemporaryDirectory;

  /**
   * This class define an executor result for BundledMapperExecutor and
   * PathMapperExecutor.
   * @author Laurent Jourdren
   */
  static class ProcessResult implements Result {

    private final Process process;

    @Override
    public InputStream getInputStream() {

      return process.getInputStream();
    }

    @Override
    public int waitFor() throws IOException {

      try {
        return process.waitFor();
      } catch (InterruptedException e) {
        throw new IOException(e);
      }
    }

    //
    // Constructor
    //

    /**
     * Constructor.
     * @param process Java Process object
     */
    ProcessResult(final Process process) {

      Preconditions.checkNotNull(process, "process argument cannot be null");

      this.process = process;
    }
  }

  @Override
  public String install(final String executable) throws IOException {

    checkNotNull(executable, "executable argument cannot be null");

    return BinariesInstaller.install(this.softwarePackage, this.version,
        executable, this.executablesTemporaryDirectory.getAbsolutePath());
  }

  @Override
  public boolean isExecutable(final String executable) {

    checkNotNull(executable, "executable argument cannot be null");

    return BinariesInstaller.check(this.softwarePackage, this.version,
        executable);
  }

  @Override
  public Result execute(final List<String> command,
      final File executionDirectory, final boolean stdout,
      final boolean redirectStderr, final File... fileUsed) throws IOException {

    checkNotNull(command, "command argument cannot be null");

    final ProcessBuilder builder = new ProcessBuilder(command);
    builder.redirectErrorStream(redirectStderr);

    // Set execution directory if exists
    if (executionDirectory != null) {
      builder.directory(executionDirectory);
    }

    getLogger()
        .info("Process command: " + Joiner.on(' ').join(builder.command()));
    getLogger().info("Process directory: " + builder.directory());

    return new ProcessResult(builder.start());
  }

  //
  // Object methods
  //

  @Override
  public String toString() {

    return Objects.toStringHelper(this).add("softwarePackage", softwarePackage)
        .add("version", version)
        .add("executablesTemporaryDirectory", executablesTemporaryDirectory)
        .toString();
  }

  //
  // Constructor
  //

  /**
   * Constructor.
   * @param softwarePackage software package of the mapper
   * @param version version of the mapper
   * @param executablesTemporaryDirectory temporary directory for executables
   */
  BundledMapperExecutor(final String softwarePackage, final String version,
      final File executablesTemporaryDirectory) {

    checkNotNull(softwarePackage, "dockerConnection argument cannot be null");
    checkNotNull(version, "dockerConnection argument cannot be null");
    checkNotNull(executablesTemporaryDirectory,
        "dockerConnection argument cannot be null");

    this.softwarePackage = softwarePackage;
    this.version = version;
    this.executablesTemporaryDirectory = executablesTemporaryDirectory;
  }

}
