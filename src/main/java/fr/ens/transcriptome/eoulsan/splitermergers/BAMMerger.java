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
 * of the Institut de Biologie de l'École Normale Supérieure and
 * the individual authors. These should be listed in @author doc
 * comments.
 *
 * For more information on the Eoulsan project and its aims,
 * or to join the Eoulsan Google group, visit the home page
 * at:
 *
 *      http://www.transcriptome.ens.fr/eoulsan
 *
 */

package fr.ens.transcriptome.eoulsan.splitermergers;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.Set;

import net.sf.samtools.BAMFileWriter;
import net.sf.samtools.SAMFileReader;
import net.sf.samtools.SAMRecord;
import fr.ens.transcriptome.eoulsan.EoulsanException;
import fr.ens.transcriptome.eoulsan.core.Parameter;
import fr.ens.transcriptome.eoulsan.data.DataFile;
import fr.ens.transcriptome.eoulsan.data.DataFormat;
import fr.ens.transcriptome.eoulsan.data.DataFormats;

/**
 * This class define a merger class for SAM files.
 * @author Laurent Jourdren
 * @since 2.0
 */
public class BAMMerger implements Merger {

  @Override
  public DataFormat getFormat() {

    return DataFormats.MAPPER_RESULTS_BAM;
  }

  @Override
  public void configure(final Set<Parameter> conf) throws EoulsanException {

    // The merge does not need any parameter
    for (Parameter p : conf) {
      throw new EoulsanException("Unknown parameter for "
          + getFormat().getName() + " merger: " + p.getName());
    }
  }

  @Override
  public void merge(final Iterator<DataFile> inFileIterator,
      final DataFile outFile) throws IOException {

    BAMFileWriter outputSam = null;

    while (inFileIterator.hasNext()) {

      // Get reader
      final SAMFileReader inputSam =
          new SAMFileReader(inFileIterator.next().open());

      // Get Writer
      if (outputSam == null) {

        outputSam =
            new BAMFileWriter(outFile.create(), new File(outFile.getName()));
        outputSam.setHeader(inputSam.getFileHeader());
      }

      // Write all the entries of the input file to the output file
      for (SAMRecord samRecord : inputSam) {
        outputSam.addAlignment(samRecord);
      }

      inputSam.close();
    }

    outputSam.close();
  }

}