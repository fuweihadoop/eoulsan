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
 *      http://outils.genomique.biologie.ens.fr/eoulsan
 *
 */

package fr.ens.biologie.genomique.eoulsan.core.workflow;

import static fr.ens.biologie.genomique.eoulsan.EoulsanLogger.getLogger;
import static fr.ens.biologie.genomique.eoulsan.core.workflow.WorkflowStep.StepType.GENERATOR_STEP;
import static fr.ens.biologie.genomique.eoulsan.core.workflow.WorkflowStep.StepType.STANDARD_STEP;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;

import fr.ens.biologie.genomique.eoulsan.EoulsanException;
import fr.ens.biologie.genomique.eoulsan.EoulsanLogger;
import fr.ens.biologie.genomique.eoulsan.EoulsanRuntime;
import fr.ens.biologie.genomique.eoulsan.EoulsanRuntimeException;
import fr.ens.biologie.genomique.eoulsan.Globals;
import fr.ens.biologie.genomique.eoulsan.Settings;
import fr.ens.biologie.genomique.eoulsan.core.ExecutorArguments;
import fr.ens.biologie.genomique.eoulsan.core.Parameter;
import fr.ens.biologie.genomique.eoulsan.core.Step;
import fr.ens.biologie.genomique.eoulsan.core.workflow.CommandWorkflowModel.StepPort;
import fr.ens.biologie.genomique.eoulsan.core.workflow.WorkflowStep.StepType;
import fr.ens.biologie.genomique.eoulsan.data.DataFile;
import fr.ens.biologie.genomique.eoulsan.data.DataFormat;
import fr.ens.biologie.genomique.eoulsan.data.DataFormatRegistry;
import fr.ens.biologie.genomique.eoulsan.data.protocols.DataProtocol;
import fr.ens.biologie.genomique.eoulsan.design.Design;
import fr.ens.biologie.genomique.eoulsan.design.DesignMetadata;
import fr.ens.biologie.genomique.eoulsan.design.Sample;
import fr.ens.biologie.genomique.eoulsan.io.CompressionType;
import fr.ens.biologie.genomique.eoulsan.requirements.Requirement;
import fr.ens.biologie.genomique.eoulsan.steps.CopyInputDataStep;
import fr.ens.biologie.genomique.eoulsan.steps.CopyOutputDataStep;
import fr.ens.biologie.genomique.eoulsan.steps.RequirementInstallerStep;
import fr.ens.biologie.genomique.eoulsan.util.FileUtils;
import fr.ens.biologie.genomique.eoulsan.util.StringUtils;
import fr.ens.biologie.genomique.eoulsan.util.Utils;

/**
 * This class define a workflow based on a Command object (workflow file).
 * @author Laurent Jourdren
 * @since 2.0
 */
public class CommandWorkflow extends AbstractWorkflow {

  /** Serialization version UID. */
  private static final long serialVersionUID = 4132064673361068654L;

  private static final String LATEST_SUFFIX = "-latest";
  static final Set<Parameter> EMPTY_PARAMETERS = Collections.emptySet();

  private final List<CommandWorkflowStep> steps = new ArrayList<>();
  private final Set<String> stepsIds = new HashSet<>();

  private final CommandWorkflowModel workflowCommand;

  //
  // Add steps
  //

  /**
   * Add a step.
   * @param step step to add.
   * @throws EoulsanException if an error occurs while adding a step
   */
  private void addStep(final CommandWorkflowStep step) throws EoulsanException {

    addStep(-1, step);
  }

  /**
   * Add a step.
   * @param pos position of the step in list of steps.
   * @param step step to add.
   * @throws EoulsanException if an error occurs while adding a step
   */
  private void addStep(final int pos, final CommandWorkflowStep step)
      throws EoulsanException {

    if (step == null) {
      throw new EoulsanException("Cannot add null step");
    }

    final String stepId = step.getId();

    if (stepId == null) {
      throw new EoulsanException("Cannot add a step with null id");
    }

    if (step.getType() != GENERATOR_STEP && this.stepsIds.contains(stepId)) {
      throw new EoulsanException(
          "Cannot add step because it already had been added: " + stepId);
    }

    if (step.getType() == STANDARD_STEP || step.getType() == GENERATOR_STEP) {
      for (StepType t : StepType.values()) {
        if (t.name().equals(stepId)) {
          throw new EoulsanException(
              "Cannot add a step with a reserved id: " + stepId);
        }
      }
    }

    if (pos == -1) {
      this.steps.add(step);
    } else {
      this.steps.add(pos, step);
    }

    this.stepsIds.add(stepId);
  }

  /**
   * Get the index of step in the list of step.
   * @param step the step to search
   * @return the index of the step or -1 if the step is not found
   */
  private int indexOfStep(final WorkflowStep step) {

    if (step == null) {
      return -1;
    }

    return this.steps.indexOf(step);
  }

  /**
   * Create the list of steps.
   * @throws EoulsanException if an error occurs while creating the step
   */
  private void addMainSteps() throws EoulsanException {

    final CommandWorkflowModel c = this.workflowCommand;

    // Get the list of the step ids
    final List<String> stepIds = c.getStepIds();

    // Remove the last steps that are skipped
    int index = stepIds.size() - 1;
    while (index >= 0) {

      if (c.isStepSkipped(stepIds.get(index))) {
        stepIds.remove(index);
      } else {
        break;
      }

      index--;
    }

    // Add the steps
    for (String stepId : stepIds) {

      final String stepName = c.getStepName(stepId);
      final String stepVersion = c.getStepVersion(stepId);

      final Set<Parameter> stepParameters = c.getStepParameters(stepId);
      final boolean skip = c.isStepSkipped(stepId);
      final boolean copyResultsToOutput = !c.isStepDiscardOutput(stepId);
      final int requiredMemory = c.getStepRequiredMemory(stepId);
      final int requiredProcessors = c.getStepRequiredProcessors(stepId);
      final String dataProduct = c.getStepDataProduct(stepId);

      getLogger().info("Create "
          + (skip ? "skipped step" : "step ") + stepId + " (" + stepName
          + ") step.");

      addStep(new CommandWorkflowStep(this, stepId, stepName, stepVersion,
          stepParameters, skip, copyResultsToOutput, requiredMemory,
          requiredProcessors, dataProduct));
    }

    // Check if there one or more step to execute
    if (this.steps.isEmpty()) {
      throw new EoulsanException("There is no step to execute in the workflow");
    }
  }

  /**
   * Add some steps at the start of the Workflow.
   * @param firstSteps list of steps to add
   * @throws EoulsanException if an error occurs while adding a step
   */
  private void addFirstSteps(final List<Step> firstSteps)
      throws EoulsanException {

    if (firstSteps != null) {
      for (Step step : Utils.listWithoutNull(firstSteps)) {

        addStep(0, new CommandWorkflowStep(this, step));
      }
    }

    // Add the first step. Generators cannot be added after this step
    addStep(0, new CommandWorkflowStep(this, StepType.FIRST_STEP));

    // Add the checker step
    addStep(0, new CommandWorkflowStep(this, StepType.CHECKER_STEP));

    // Add the design step
    addStep(0, new CommandWorkflowStep(this, StepType.DESIGN_STEP));

    // Add the root step
    addStep(0, new CommandWorkflowStep(this, StepType.ROOT_STEP));
  }

  /**
   * Add some steps at the end of the Workflow.
   * @param endSteps list of steps to add
   * @throws EoulsanException if an error occurs while adding a step
   */
  private void addEndSteps(final List<Step> endSteps) throws EoulsanException {

    if (endSteps == null) {
      return;
    }

    for (Step step : Utils.listWithoutNull(endSteps)) {

      addStep(new CommandWorkflowStep(this, step));
    }
  }

  /**
   * Initialize the settings of the Workflow.
   */
  private void initializeSettings() {

    final CommandWorkflowModel c = this.workflowCommand;
    final Set<Parameter> globalParameters = c.getGlobalParameters();

    final Settings settings = EoulsanRuntime.getSettings();

    // Add globals parameters to Settings
    getLogger()
        .info("Init all steps with global parameters: " + globalParameters);
    for (Parameter p : globalParameters) {
      settings.setSetting(p.getName(), p.getStringValue());
    }

    // Reload the available formats because the list of the available formats
    // has already loaded at the startup when using DataFile objects
    DataFormatRegistry.getInstance().reload();
  }

  /**
   * Configure the steps of the Workflow.
   * @throws EoulsanException if an error occurs while creating the step
   */
  private void configureSteps() throws EoulsanException {

    // Configure all the steps
    for (CommandWorkflowStep step : this.steps) {
      step.configure();
    }

    Multimap<CommandWorkflowStep, Requirement> requirements =
        ArrayListMultimap.create();

    // Get the requiement of all steps
    for (CommandWorkflowStep step : this.steps) {

      Set<Requirement> stepRequirements = step.getStep().getRequirements();

      if (stepRequirements != null && !stepRequirements.isEmpty()) {
        requirements.putAll(step, stepRequirements);
      }
    }

    int installerCount = 0;
    for (Map.Entry<CommandWorkflowStep, Requirement> e : requirements
        .entries()) {

      final Requirement r = e.getValue();

      if (r.isAvailable()) {
        continue;
      }

      if (!r.isInstallable()) {

        if (r.isOptional()) {
          continue;
        } else {
          throw new EoulsanException("Requirement for step \""
              + e.getKey().getId() + "\" is not available: " + r.getName());
        }
      }

      installerCount++;

      // Create an installer step
      final CommandWorkflowStep step = new CommandWorkflowStep(this,
          r.getName() + "install" + installerCount,
          RequirementInstallerStep.STEP_NAME, Globals.APP_VERSION.toString(),
          r.getParameters(), false, false, -1, -1, "");

      // Configure the installer step
      step.configure();

      // Add the new step to the workflow
      addStep(indexOfStep(getFirstStep()), step);
    }
  }

  /**
   * Add a dependency. Add an additional step that copy/(un)compress data if
   * necessary.
   * @param inputPort input port of the step
   * @param dependencyOutputPort output port the dependency
   * @throws EoulsanException if an error occurs while adding the dependency
   */
  private void addDependency(final WorkflowInputPort inputPort,
      final WorkflowOutputPort dependencyOutputPort) throws EoulsanException {

    try {

      final AbstractWorkflowStep step = inputPort.getStep();
      final AbstractWorkflowStep dependencyStep =
          dependencyOutputPort.getStep();

      final DataFile stepDir = inputPort.getStep().getStepOutputDirectory();
      final DataFile depDir =
          dependencyOutputPort.getStep().getStepOutputDirectory();

      final DataProtocol stepProtocol = stepDir.getProtocol();
      final DataProtocol depProtocol = depDir.getProtocol();

      final EnumSet<CompressionType> stepCompressionsAllowed =
          inputPort.getCompressionsAccepted();

      final CompressionType depOutputCompression =
          dependencyOutputPort.getCompression();

      CommandWorkflowStep newStep = null;

      // Check if copy is needed in the working directory
      if ((step.getType() == StepType.STANDARD_STEP
          || step.getType() == StepType.GENERATOR_STEP)
          && !step.isSkip() && stepProtocol != depProtocol
          && inputPort.isRequiredInWorkingDirectory()) {
        newStep = newInputFormatCopyStep(this, inputPort, dependencyOutputPort,
            depOutputCompression, stepCompressionsAllowed);
      }

      // Check if (un)compression is needed
      if (newStep == null
          && (step.getType() == StepType.STANDARD_STEP
              || step.getType() == StepType.GENERATOR_STEP)
          && !step.isSkip() && !inputPort.getCompressionsAccepted()
              .contains(depOutputCompression)) {
        newStep = newInputFormatCopyStep(this, inputPort, dependencyOutputPort,
            depOutputCompression, stepCompressionsAllowed);
      }

      // If the dependency if design step and step does not allow all the
      // compression types as input, (un)compress data
      if (newStep == null
          && (step.getType() == StepType.STANDARD_STEP
              || step.getType() == StepType.GENERATOR_STEP)
          && !step.isSkip() && dependencyStep == this.getDesignStep()
          && !EnumSet.allOf(CompressionType.class)
              .containsAll(stepCompressionsAllowed)) {
        newStep = newInputFormatCopyStep(this, inputPort, dependencyOutputPort,
            depOutputCompression, stepCompressionsAllowed);
      }

      // Set the dependencies
      if (newStep != null) {

        // Add the copy step in the list of steps just before the step given as
        // method argument
        // addStep(indexOfStep(dependencyStep), newStep);
        addStep(indexOfStep(step), newStep);

        // Add the copy dependency
        newStep.addDependency(newStep.getWorkflowInputPorts().getFirstPort(),
            dependencyOutputPort);

        // Add the step dependency
        step.addDependency(inputPort,
            newStep.getWorkflowOutputPorts().getFirstPort());

      } else {

        // Add the step dependency
        step.addDependency(inputPort, dependencyOutputPort);
      }
    } catch (IOException e) {
      throw new EoulsanException(e);
    }
  }

  /**
   * Create a new step that copy/(un)compress input data of a step.
   * @param workflow workflow where adding the step
   * @param inputPort input port
   * @param outputPort output port
   * @param inputCompression compression format of the data to read
   * @param outputCompressionsAllowed compression formats allowed by the step
   * @return a new step
   * @throws EoulsanException if an error occurs while creating the step
   */
  private static CommandWorkflowStep newInputFormatCopyStep(
      final CommandWorkflow workflow, final WorkflowInputPort inputPort,
      final WorkflowOutputPort outputPort,
      final CompressionType inputCompression,
      final EnumSet<CompressionType> outputCompressionsAllowed)
          throws EoulsanException {

    // Set the step name
    final String stepName = CopyInputDataStep.STEP_NAME;

    // Search a non used step id
    final Set<String> stepsIds = new HashSet<>();
    for (WorkflowStep s : workflow.getSteps()) {
      stepsIds.add(s.getId());
    }
    int i = 1;
    String stepId;
    do {

      stepId = inputPort.getStep().getId() + "prepare" + i;
      i++;

    } while (stepsIds.contains(stepId));

    final boolean designOutputport =
        outputPort.getStep().getType() == StepType.DESIGN_STEP;

    // Find output compression
    final CompressionType comp;
    if (outputCompressionsAllowed.contains(inputCompression)) {
      comp = inputCompression;
    } else if (outputCompressionsAllowed.contains(CompressionType.NONE)) {
      comp = CompressionType.NONE;
    } else {
      comp = outputCompressionsAllowed.iterator().next();
    }

    // Set parameters
    final Set<Parameter> parameters = new HashSet<>();
    parameters.add(new Parameter(CopyInputDataStep.FORMAT_PARAMETER,
        inputPort.getFormat().getName()));
    parameters.add(new Parameter(CopyInputDataStep.OUTPUT_COMPRESSION_PARAMETER,
        comp.name()));
    parameters.add(new Parameter(CopyInputDataStep.DESIGN_INPUT_PARAMETER,
        "" + designOutputport));
    parameters.add(
        new Parameter(CopyInputDataStep.OUTPUT_COMPRESSIONS_ALLOWED_PARAMETER,
            CopyInputDataStep.encodeAllowedCompressionsParameterValue(
                outputCompressionsAllowed)));

    // Create step
    CommandWorkflowStep step = new CommandWorkflowStep(workflow, stepId,
        stepName, null, parameters, false, false, -1, -1, "");

    // Configure step
    step.configure();

    return step;
  }

  /**
   * Create a new step that copy output data of a step.
   * @param workflow workflow where adding the step
   * @param outputPorts output ports where data must be copied
   * @return a new step
   * @throws EoulsanException if an error occurs while creating the step
   */
  private static List<CommandWorkflowStep> newOutputFormatCopyStep(
      final CommandWorkflow workflow, final WorkflowOutputPorts outputPorts)
          throws EoulsanException {

    final List<CommandWorkflowStep> result = new ArrayList<>();

    // Set the step name
    final String stepName = CopyOutputDataStep.STEP_NAME;

    for (WorkflowOutputPort outputPort : outputPorts) {

      // Search a non used step id
      final Set<String> stepsIds = new HashSet<>();
      for (WorkflowStep s : workflow.getSteps()) {
        stepsIds.add(s.getId());
      }
      int i = 1;
      String stepId;
      do {

        stepId = outputPorts.getFirstPort().getStep().getId() + "finalize" + i;
        i++;

      } while (stepsIds.contains(stepId));

      // Set parameters
      final Set<Parameter> parameters = new HashSet<>();
      parameters.add(new Parameter(CopyOutputDataStep.PORTS_PARAMETER,
          outputPort.getName()));
      parameters.add(new Parameter(CopyOutputDataStep.FORMATS_PARAMETER,
          outputPort.getFormat().getName()));

      // Create step
      CommandWorkflowStep step = new CommandWorkflowStep(workflow, stepId,
          stepName, null, parameters, false, true, -1, -1, "");

      // Configure step
      step.configure();

      result.add(step);
    }

    return result;
  }

  /**
   * Add user defined dependencies.
   * @throws EoulsanException if an error occurs while setting dependencies
   */
  private void addManualDependencies() throws EoulsanException {

    // Create a map with the name of the steps
    final Map<String, CommandWorkflowStep> stepsMap = new HashMap<>();
    for (CommandWorkflowStep step : this.steps) {
      stepsMap.put(step.getId(), step);
    }

    // Use a copy of this.step as new new steps can be added
    for (CommandWorkflowStep toStep : Lists.newArrayList(this.steps)) {

      final Map<String, StepPort> inputs =
          this.workflowCommand.getStepInputs(toStep.getId());

      for (Map.Entry<String, StepPort> e : inputs.entrySet()) {

        final String toPortName = e.getKey();
        final String fromStepId = e.getValue().stepId;
        final String fromPortName = e.getValue().portName;

        // final DataFormat inputFormat = e.getKey();
        final CommandWorkflowStep fromStep = stepsMap.get(fromStepId);

        // Check if fromStep step exists
        if (fromStep == null) {
          throw new EoulsanException(
              "No workflow step found with id: " + fromStepId);
        }

        // Check if the fromPort exists
        if (!fromStep.getWorkflowOutputPorts().contains(fromPortName)) {
          throw new EoulsanException("No port with name \""
              + fromPortName + "\" found for step with id: "
              + fromStep.getId());
        }

        // Check if the toPort exists
        if (!toStep.getWorkflowInputPorts().contains(toPortName)) {
          throw new EoulsanException("No port with name \""
              + toPortName + "\" found for step with id: " + toStep.getId());
        }

        final WorkflowOutputPort fromPort =
            fromStep.getWorkflowOutputPorts().getPort(fromPortName);
        final WorkflowInputPort toPort =
            toStep.getWorkflowInputPorts().getPort(toPortName);

        addDependency(toPort, fromPort);
      }
    }
  }

  /**
   * Search dependency between steps.
   * @throws EoulsanException if an error occurs while search dependencies
   */
  private void searchDependencies() throws EoulsanException {

    final Map<DataFormat, CommandWorkflowStep> generatorAdded = new HashMap<>();
    searchDependencies(generatorAdded, null);
  }

  /**
   * Search dependency between steps.
   * @param generatorAdded generator added
   * @param lastStepWithoutOutput last step without output
   * @throws EoulsanException if an error occurs while search dependencies
   */
  private void searchDependencies(
      final Map<DataFormat, CommandWorkflowStep> generatorAdded,
      final CommandWorkflowStep lastStepWithoutOutput) throws EoulsanException {

    final List<CommandWorkflowStep> steps = this.steps;
    CommandWorkflowStep currentLastStepWithoutOutput = lastStepWithoutOutput;

    for (int i = steps.size() - 1; i >= 0; i--) {

      final CommandWorkflowStep step = steps.get(i);

      // If step is a generator, move the step just after the checker
      if (step.getType() == StepType.GENERATOR_STEP
          && !generatorAdded.containsValue(step)) {

        // Move step just after the checker step
        final int generatorIndex = indexOfStep(step);
        this.steps.remove(generatorIndex);
        this.steps.add(indexOfStep(getCheckerStep()) + 1, step);

        if (step.getOutputPorts().isEmpty()) {
          throw new EoulsanException("Step \""
              + step.getId() + "\" is a generator but not generate anything.");
        }

        if (step.getOutputPorts().size() > 1) {
          throw new EoulsanException("Step \""
              + step.getId()
              + "\" is a generator but generate more than one format.");
        }

        generatorAdded.put(step.getOutputPorts().getFirstPort().getFormat(),
            step);

        searchDependencies(generatorAdded, currentLastStepWithoutOutput);
        return;
      }

      // If step need no data, the step depends from the previous step
      if (step.getWorkflowInputPorts().isEmpty() && i > 0) {
        step.addDependency(steps.get(i - 1));
      }

      // If step has no output, all the next step(s) depend on it
      if (step.getWorkflowOutputPorts().isEmpty()) {

        for (int j = i + 1; j < steps.size(); j++) {

          final CommandWorkflowStep s = steps.get(j);

          s.addDependency(step);

          // If s has no output, there is no need to go further
          if (s == currentLastStepWithoutOutput) {
            break;
          }
        }
        currentLastStepWithoutOutput = step;
      }

      for (WorkflowInputPort inputPort : step.getWorkflowInputPorts()) {

        // Do not search dependency for the format if already has been manually
        // set
        if (inputPort.isLinked()) {
          continue;
        }

        final DataFormat format = inputPort.getFormat();

        // Check if more than one input have the same type
        int formatCount = 0;
        for (WorkflowInputPort p : step.getWorkflowInputPorts()
            .getPortsWithDataFormat(format)) {
          if (!p.isLinked()) {
            formatCount++;
          }
        }
        if (formatCount > 1) {
          throw new EoulsanException("Step \""
              + step.getId()
              + "\" contains more than one of port of the same format ("
              + format
              + "). Please manually define all inputs for this ports.");
        }

        boolean found = false;

        for (int j = i - 1; j >= 0; j--) {

          // For each step before current step
          final CommandWorkflowStep stepTested = steps.get(j);

          // Test each port
          for (WorkflowOutputPort outputPort : stepTested
              .getWorkflowOutputPorts().getPortsWithDataFormat(format)) {

            // The tested step is a standard/generator step
            if (stepTested.getType() == StepType.STANDARD_STEP
                || stepTested.getType() == StepType.GENERATOR_STEP
                || stepTested.getType() == StepType.DESIGN_STEP) {

              // Add the dependency
              addDependency(inputPort, outputPort);

              // New dependency may has been added so change the current index
              j = indexOfStep(stepTested);

              found = true;
              break;
            }
          }

          // Dependency found, do not search in other steps
          if (found) {
            break;
          }
        }

        // New dependency may has been added so change the current index
        i = indexOfStep(step);

        if (!found) {

          // A generator is available for the DataType
          if (format.isGenerator()) {

            if (!generatorAdded.containsKey(format)) {

              final CommandWorkflowStep generatorStep =
                  new CommandWorkflowStep(this, format);

              generatorStep.configure();

              // Add after checker
              addStep(indexOfStep(getCheckerStep()) + 1, generatorStep);
              generatorAdded.put(format, generatorStep);

              // Rerun search dependencies
              searchDependencies(generatorAdded, currentLastStepWithoutOutput);
              return;
            }

            if (step.getType() == StepType.GENERATOR_STEP) {

              // Swap generators order
              Collections.swap(this.steps, indexOfStep(step),
                  indexOfStep(generatorAdded.get(format)));
              searchDependencies(generatorAdded, currentLastStepWithoutOutput);
              return;
            }

            throw new EoulsanException("Cannot found input data format \""
                + format.getName() + "\" for step " + step.getId() + ".");
          }
        }
      }
    }

    // Add dependencies for terminal steps
    final List<CommandWorkflowStep> terminalSteps = new ArrayList<>();
    for (CommandWorkflowStep step : this.steps) {

      for (CommandWorkflowStep terminalStep : terminalSteps) {
        step.addDependency(terminalStep);
      }

      if (step.isTerminalStep()) {
        terminalSteps.add(step);
      }
    }

    // Add steps to copy output data from steps to output directory if
    // necessary
    for (CommandWorkflowStep step : Lists.newArrayList(this.steps)) {

      if (step.isCopyResultsToOutput()
          && !step.getStepOutputDirectory().equals(getOutputDirectory())
          && !step.getWorkflowOutputPorts().isEmpty()) {

        final List<CommandWorkflowStep> newSteps =
            newOutputFormatCopyStep(this, step.getWorkflowOutputPorts());

        for (CommandWorkflowStep newStep : newSteps) {

          // Add the copy step in the list of steps just before the step given
          // as method argument
          addStep(indexOfStep(step) + 1, newStep);

          // Add the copy dependencies
          final WorkflowInputPort newStepInputPort =
              newStep.getWorkflowInputPorts().getFirstPort();
          newStep.addDependency(newStepInputPort, step.getWorkflowOutputPorts()
              .getPort(newStepInputPort.getName()));

        }
      }
    }

    // Check if all input port are linked
    for (CommandWorkflowStep step : this.steps) {
      for (WorkflowInputPort inputPort : step.getWorkflowInputPorts()) {
        if (!inputPort.isLinked()) {
          throw new EoulsanException("The \""
              + inputPort.getName() + "\" port ("
              + inputPort.getFormat().getName() + " format) of step \""
              + inputPort.getStep().getId() + "\" is not linked");
        }
      }
    }
  }

  //
  // Convert S3 URL methods
  //

  /**
   * Convert the S3 URLs to S3N URLs in source, genome and annotation fields of
   * the design.
   */
  private void convertDesignS3URLs() {

    final Design design = getDesign();

    for (Sample s : design.getSamples()) {

      // Convert read file URL
      final List<String> readsSources =
          Lists.newArrayList(s.getMetadata().getReads());
      for (int i = 0; i < readsSources.size(); i++) {
        readsSources.set(i, convertS3URL(readsSources.get(i)));
      }
      s.getMetadata().setReads(readsSources);

    }

    final DesignMetadata dmd = design.getMetadata();

    // Convert genome file URL
    if (dmd.containsGenomeFile()) {
      dmd.setGenomeFile(convertS3URL(dmd.getGenomeFile()));
    }

    // Convert GFF file URL
    if (dmd.containsGffFile()) {
      dmd.setGffFile(convertS3URL(dmd.getGffFile()));
    }

    // Convert additional annotation file URL
    if (dmd.containsAdditionnalAnnotationFile()) {
      dmd.setAdditionnalAnnotationFile(
          convertS3URL(dmd.getAdditionnalAnnotationFile()));
    }

  }

  /**
   * Convert a s3:// URL to a s3n:// URL
   * @param url input URL
   * @return converted URL
   */
  private String convertS3URL(final String url) {

    return StringUtils.replacePrefix(url, "s3:/", "s3n:/");
  }

  /**
   * List the files (input files, output files and resused files) of the
   * workflow from a given step.
   * @param originStep origin step from which files must be searched
   * @return a WorkflowFile object
   */
  private WorkflowFiles listStepsFiles(final WorkflowStep originStep) {

    // final Set<WorkflowStepOutputDataFile> inFiles = new HashSet<>();
    // final Set<WorkflowStepOutputDataFile> reusedFiles = new HashSet<>();
    // final Set<WorkflowStepOutputDataFile> outFiles = new HashSet<>();
    //
    // boolean firstStepFound = false;
    //
    // for (CommandWorkflowStep step : this.steps) {
    //
    // if (!firstStepFound) {
    //
    // if (step == originStep)
    // firstStepFound = true;
    // else
    // continue;
    // }
    //
    // if (step.getType() == STANDARD_STEP && !step.isSkip()) {
    //
    // // If a terminal step exist don't go further
    // if (step.getStep().isTerminalStep())
    // return new WorkflowFiles(inFiles, reusedFiles, outFiles);
    // }
    //
    // for (Sample sample : getDesign().getSamples()) {
    // for (WorkflowInputPort inputPort : step.getWorkflowInputPorts()) {
    //
    // // Nothing to do if port is not linked
    // if (!inputPort.isLinked())
    // continue;
    //
    // final DataFormat format = inputPort.getFormat();
    //
    // final List<WorkflowStepOutputDataFile> files;
    //
    // if (format.getMaxFilesCount() == 1) {
    //
    // WorkflowStepOutputDataFile f =
    // new WorkflowStepOutputDataFile(inputPort.getLink(), sample);
    //
    // files = Collections.singletonList(f);
    // } else {
    // files = new ArrayList<>();
    // final int count =
    // step.getInputPortData(inputPort.getName()).getDataFileCount(false);
    //
    // for (int i = 0; i < count; i++) {
    //
    // WorkflowStepOutputDataFile f =
    // new WorkflowStepOutputDataFile(inputPort.getLink(), sample, i);
    //
    // files.add(f);
    // }
    // }
    //
    // for (WorkflowStepOutputDataFile file : files) {
    //
    // if (reusedFiles.contains(file))
    // continue;
    //
    // if (outFiles.contains(file)) {
    // outFiles.remove(file);
    // reusedFiles.add(file);
    // continue;
    // }
    //
    // inFiles.add(file);
    // }
    // }
    // }
    //
    // // Add output files of the step
    // if (!step.isSkip()) {
    // for (Sample sample : getDesign().getSamples()) {
    // for (WorkflowOutputPort outputPort : step.getWorkflowOutputPorts()) {
    // // for (DataFormat format : step.getWorkflowOutputPorts()) {
    //
    // final DataFormat format = outputPort.getFormat();
    //
    // if (format.getMaxFilesCount() == 1) {
    //
    // WorkflowStepOutputDataFile f =
    // new WorkflowStepOutputDataFile(outputPort, sample);
    //
    // outFiles.add(f);
    // } else {
    // final int count =
    // WorkflowStepOutputDataFile.dataFileCount(outputPort, sample,
    // false);
    //
    // for (int i = 0; i < count; i++) {
    // WorkflowStepOutputDataFile f =
    // new WorkflowStepOutputDataFile(outputPort, sample, i);
    // outFiles.add(f);
    // }
    // }
    // }
    // }
    // }
    // }
    //
    // return new WorkflowFiles(inFiles, reusedFiles, outFiles);

    return null;
  }

  @Override
  protected void saveConfigurationFiles() throws EoulsanException {

    // Save design file
    super.saveConfigurationFiles();

    try {
      DataFile jobDir = getWorkflowContext().getJobDirectory();

      if (!jobDir.exists()) {
        jobDir.mkdirs();
      }

      // Create a shortcut link to the current job directory
      final DataFile latest = new DataFile(jobDir.getParent(),
          Globals.APP_NAME_LOWER_CASE + LATEST_SUFFIX);
      try {

        if (latest.exists()) {
          latest.delete();
        }
        new DataFile(jobDir.getName()).symlink(latest);
      } catch (IOException e) {
        EoulsanLogger.getLogger().severe(
            "Cannot create the new shortcut to the jod directory: " + latest);
      }

      // Save workflow file
      BufferedWriter writer = FileUtils.createBufferedWriter(
          new DataFile(jobDir, WORKFLOW_COPY_FILENAME).create());
      writer.write(this.workflowCommand.toXML());
      writer.close();

    } catch (IOException | EoulsanRuntimeException e) {
      throw new EoulsanException(
          "Error while writing workflow file: " + e.getMessage(), e);
    }

  }

  //
  // Workflow methods
  //

  @Override
  public WorkflowFiles getWorkflowFilesAtRootStep() {

    return listStepsFiles(getRootStep());
  }

  @Override
  public WorkflowFiles getWorkflowFilesAtFirstStep() {

    return listStepsFiles(getFirstStep());
  }

  //
  // Constructor
  //

  /**
   * Public constructor.
   * @param executionArguments execution arguments
   * @param workflowCommand Command object with the content of the parameter
   *          file
   * @param firstSteps optional steps to add at the beginning of the workflow
   * @param endSteps optional steps to add at the end of the workflow
   * @param design Design to use with the workflow
   * @throws EoulsanException
   */
  public CommandWorkflow(final ExecutorArguments executionArguments,
      final CommandWorkflowModel workflowCommand, final List<Step> firstSteps,
      final List<Step> endSteps, final Design design) throws EoulsanException {

    super(executionArguments, design);

    if (workflowCommand == null) {
      throw new NullPointerException("The command is null.");
    }

    this.workflowCommand = workflowCommand;

    // Set command information in context
    final WorkflowContext context = getWorkflowContext();
    context.setCommandName(workflowCommand.getName());
    context.setCommandDescription(workflowCommand.getDescription());
    context.setCommandAuthor(workflowCommand.getAuthor());

    // Set the globals parameter in the Eoulsan settings
    initializeSettings();

    // Convert s3:// urls to s3n:// urls
    convertDesignS3URLs();

    // Create the basic steps
    addMainSteps();

    // Add first steps
    addFirstSteps(firstSteps);

    // Add end steps
    addEndSteps(endSteps);

    // Initialize steps
    configureSteps();

    // Set manually defined input format source
    addManualDependencies();

    // Search others input format sources
    searchDependencies();
  }

}