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

package fr.ens.biologie.genomique.eoulsan.galaxytools.elements;

import java.util.Map;

import fr.ens.biologie.genomique.eoulsan.EoulsanException;
import fr.ens.biologie.genomique.eoulsan.core.Parameter;
import fr.ens.biologie.genomique.eoulsan.data.DataFormat;

/**
 * The Interface ToolElement.
 * @author Sandrine Perrin
 * @since 2.0
 */
public interface ToolElement {

  /** The Constant SEP. */
  String SEP = ".";

  /**
   * Gets the name.
   * @return the name
   */
  String getName();

  /**
   * Gets the name which respect Eoulsan's syntax.
   * @return the name
   */
  String getValidatedName();

  /**
   * Checks if is setting.
   * @return true, if is setting
   */
  boolean isSet();

  /**
   * Gets the value.
   * @return the value
   */
  String getValue();

  /**
   * Sets the parameter eoulsan.
   */
  void setDefaultValue() throws EoulsanException;

  /**
   * Sets the parameter eoulsan.
   * @param stepParameter the new parameter eoulsan
   * @throws EoulsanException the eoulsan exception
   */
  void setValue(final Parameter stepParameter) throws EoulsanException;

  /**
   * Sets the parameter eoulsan.
   * @param stepParameters the step parameters
   * @throws EoulsanException the eoulsan exception
   */
  void setValues(final Map<String, Parameter> stepParameters)
      throws EoulsanException;

  /**
   * Checks if is file.
   * @return true, if is file
   */
  boolean isFile();

  /**
   * Gets the data format.
   * @return the data format
   */
  DataFormat getDataFormat();

  /**
   * Extract parameter by name.
   * @param stepParameters step parameters
   * @return the parameter found or null.
   */
  Parameter extractParameterByName(final Map<String, Parameter> stepParameters);

}
