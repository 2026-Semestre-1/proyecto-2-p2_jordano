package simuladorminipc.scheduler;

import simuladorminipc.model.PCB;

import java.util.List;

/**
 * Highest Response Ratio Next (HRRN) – non-preemptive.
 * <p>
 * Selects the process with the highest response ratio:
 * <pre>RR = (waitingTime + burstTime) / burstTime</pre>
 * Processes with a burst time of 0 are treated as already finished.
 * </p>
 *
 * <p>Reference: Stallings 9th Ed. 9.3 (HRRN).</p>
 */
/**
 * Highest Response Ratio Next (HRRN) — not yet implemented.
 * Will be implemented in a future iteration.
 */
public class HRRNScheduler implements SchedulingAlgorithm {

    @Override
    public PCB selectNextProcess(List<PCB> readyProcesses, PCB currentProcess) {
        // Not yet implemented
        return null;
    }

    @Override
    public String getName() { return "HRRN"; }
}
