package simuladorminipc.scheduler;

import simuladorminipc.model.PCB;

import java.util.List;

/**
 * Shortest Remaining Time (SRT) – preemptive version of SPN.
 * Always runs the ready process with the least remaining burst time.
 * The current process is preempted if a shorter job arrives.
 *
 * <p>Tie-breaking: equal remaining times favour the process with the earliest arrival.</p>
 */
/**
 * Shortest Remaining Time (SRT) — not yet implemented.
 * Will be implemented in a future iteration.
 */
public class SRTScheduler implements SchedulingAlgorithm {

    @Override
    public PCB selectNextProcess(List<PCB> readyProcesses, PCB currentProcess) {
        // Not yet implemented
        return null;
    }

    @Override
    public String getName() { return "SRT"; }
}
