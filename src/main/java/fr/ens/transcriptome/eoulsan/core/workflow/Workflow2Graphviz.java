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

package fr.ens.transcriptome.eoulsan.core.workflow;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.HashSet;
import java.util.Set;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;

import fr.ens.transcriptome.eoulsan.Globals;
import fr.ens.transcriptome.eoulsan.core.Parameter;
import fr.ens.transcriptome.eoulsan.data.DataFile;
import fr.ens.transcriptome.eoulsan.data.DataFormat;

/**
 * Convert a Workflow to Graphviz
 * @author Laurent Jourdren
 * @since 2.0
 */
public class Workflow2Graphviz {

  private final AbstractWorkflow workflow;

  private void addRow(final StringBuilder sb, final String s) {

    sb.append("<tr><td bgcolor=\"white\" align=\"center\" colspan=\"2\"><font color=\"black\">");
    sb.append(s);
    sb.append("</font></td></tr>");
  }

  /**
   * Convert the workflow to Graphviz format
   * @return a string with the workflow converted to Graphviz format
   */
  private String convert() {

    final StringBuilder sb = new StringBuilder();

    sb.append("## Generated by "
        + Globals.APP_NAME + " " + Globals.APP_VERSION_STRING);
    sb.append('\n');

    sb.append("digraph g {\n  graph [fontsize=30 labelloc=\"t\" label=\"\" "
        + "splines=true overlap=false rankdir = \"LR\"]\n"
        + "  ratio = auto;\n");

    // Create nodes
    for (WorkflowStep step : this.workflow.getSteps()) {

      if (step == this.workflow.getFirstStep()
          || step == this.workflow.getCheckerStep()) {
        continue;
      }

      sb.append("  \"step");
      sb.append(step.getNumber());
      sb.append("\" [ style = \"filled\" penwidth = 1 fillcolor = \"white\" fontname = \"Courier New\" shape = \"Mrecord\" label =");

      sb.append("<<table border=\"0\" cellborder=\"0\" cellpadding=\"3\" bgcolor=\"white\">");

      sb.append("<tr><td bgcolor=\"black\" align=\"center\" colspan=\"2\"><font color=\"white\">");
      sb.append(step.getId());
      sb.append("</font></td></tr>");

      addRow(sb, step.getStepName() + " " + step.getStepVersion());
      for (Parameter p : step.getParameters()) {
        addRow(sb, p.getName() + " = " + p.getValue());
      }

      sb.append("</table>> ] ;\n");
    }

    sb.append('\n');

    final Multimap<AbstractWorkflowStep, AbstractWorkflowStep> linkedSteps =
        HashMultimap.create();

    // Create links from output ports
    for (WorkflowStep step : this.workflow.getSteps()) {

      // Do not handle first and check step
      if (step == this.workflow.getFirstStep()
          || step == this.workflow.getCheckerStep()) {
        continue;
      }

      final int stepNumber = step.getNumber();
      AbstractWorkflowStep abstractStep = (AbstractWorkflowStep) step;

      // For each port
      for (WorkflowOutputPort outputPort : abstractStep
          .getWorkflowOutputPorts()) {

        // For each port link
        for (WorkflowInputPort link : outputPort.getLinks()) {

          final AbstractWorkflowStep linkedStep = link.getStep();

          // Do not handle first and check step
          if (linkedStep == this.workflow.getFirstStep()
              || linkedStep == this.workflow.getCheckerStep()) {
            continue;
          }

          linkedSteps.put(linkedStep, abstractStep);

          sb.append("  step");
          sb.append(stepNumber);
          sb.append(" -> step");
          sb.append(linkedStep.getNumber());
          sb.append(" [ penwidth = 5 fontsize = 28 fontcolor = \"black\" label = \"");

          final DataFormat format = outputPort.getFormat();

          String formatName =
              format.getAlias() == null || "".equals(format.getAlias())
                  ? format.getName() : format.getAlias();

          sb.append(formatName);
          sb.append("\" ];\n");
        }
      }
    }

    // Create other links between steps
    for (WorkflowStep step : this.workflow.getSteps()) {

      // Do not handle first and check step
      if (step == this.workflow.getFirstStep()
          || step == this.workflow.getCheckerStep()) {
        continue;
      }

      AbstractWorkflowStep abstractStep = (AbstractWorkflowStep) step;

      WorkflowStepStateObserver observer =
          ((AbstractWorkflowStep) step).getStepStateObserver();
      Set<AbstractWorkflowStep> requiredSteps =
          new HashSet<>(observer.getRequiredSteps());

      requiredSteps.removeAll(linkedSteps.get(abstractStep));

      // Do not handle first and check step
      requiredSteps.remove(this.workflow.getFirstStep());
      requiredSteps.remove(this.workflow.getCheckerStep());

      for (AbstractWorkflowStep requiredStep : requiredSteps) {

        sb.append("  step");
        sb.append(requiredStep.getNumber());
        sb.append(" -> step");
        sb.append(step.getNumber());
        sb.append(" [ penwidth = 5 fontsize = 28 fontcolor = \"green\"");
        sb.append(" ];\n");

      }
    }

    sb.append("}\n");

    return sb.toString();
  }

  /**
   * Convert and save the workflow as a Graphviz file.
   * @param outputFile output file
   * @throws IOException if an error occurs while creating the output file
   */
  public void save(final DataFile outputFile) throws IOException {

    checkNotNull(outputFile, "outputFile parameter cannot be null");

    final Writer writer = new OutputStreamWriter(outputFile.create());
    writer.write(convert());
    writer.close();
  }

  //
  // Constructor
  //

  /**
   * Public constructor.
   * @param workflow the workflow
   */
  public Workflow2Graphviz(final AbstractWorkflow workflow) {

    checkNotNull(workflow, "workflow parameter cannot be null");

    this.workflow = workflow;
  }

}