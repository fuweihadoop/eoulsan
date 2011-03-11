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

package fr.ens.transcriptome.eoulsan.bio.readsfilters;

import java.util.regex.Pattern;

import fr.ens.transcriptome.eoulsan.bio.ReadSequence;

/**
 * Define a filter that remove terminal polyN sequences of a read and check the
 * length of the resulting read.
 * @author Maria Bernard
 * @author Laurent Jourdren
 */
public class TrimReadFilter extends AbstractReadFilter {

  private static final Pattern PATTERN = Pattern.compile("NN+$");

  private int lengthThreshold;

  /**
   * Trim the read sequence and quality if ends with polyN.
   * @param read Read to trim
   */
  public static final void trim(final ReadSequence read) {

    if (read == null || !read.isFastQValid())
      return;

    final String[] splitResult = PATTERN.split(read.getSequence());

    // Test if the sequence contains only N nucleotides
    if (splitResult == null || splitResult.length == 0) {
      read.setSequence("");
      read.setQuality("");

      return;
    }

    final String sequence = splitResult[0];
    final int len = sequence.length();

    // Trim read sequence and quality if needed
    if (len != read.length()) {
      read.setSequence(sequence);
      read.setQuality(read.getQuality().substring(0, len));
    }

  }

  @Override
  public boolean accept(final ReadSequence read) {

    if (read == null)
      throw new NullPointerException("The read is null");

    trim(read);

    return read.length() > this.lengthThreshold;
  }

  @Override
  public String getName() {

    return "Trim ReadFilter";
  }
  
  //
  // Constructor
  //

  /**
   * Public constructor.
   * @param lengthThreshold minimal length of the reads after read trimming
   */
  public TrimReadFilter(final int lengthThreshold) {

    if (lengthThreshold < 1)
      throw new IllegalArgumentException("Invalid lengthThreshold: "
          + lengthThreshold);

    this.lengthThreshold = lengthThreshold;
  }

}
