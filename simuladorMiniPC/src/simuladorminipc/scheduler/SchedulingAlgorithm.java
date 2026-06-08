package simuladorminipc.scheduler;

import simuladorminipc.model.PCB;

import java.util.List;

/**
 * Strategy interface for CPU scheduling algorithms.
 * Each implementation contains only its own selection logic.
 *
 * <p>Implementations must be stateless (all state lives in the PCB or the
 * Kernel), allowing the active algorithm to be swapped at runtime via
 * {@link SchedulingPolicyManager#setPolicy}.</p>
 *
 * @see SchedulingPolicyManager
 * @see simuladorminipc.model.PCB
 */
public interface SchedulingAlgorithm {

    /**
     * Selects the next process to run.
     *
     * @param readyProcesses     snapshot of the current ready queue (unmodifiable view)
     * @param currentProcess     the process currently running on the CPU (may be null)
     * @return the PCB that should run next, or null if the CPU should be idle
     */
    PCB selectNextProcess(List<PCB> readyProcesses, PCB currentProcess);

    /** Human-readable name of the algorithm (shown in the UI). */
    String getName();
}
