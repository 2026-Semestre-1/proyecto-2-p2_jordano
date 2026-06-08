package simuladorminipc.scheduler;

import simuladorminipc.model.PCB;

import java.util.List;

/**
 * Shortest Process Next (SPN / SJF) – non-preemptive.
 * Selects the ready process with the smallest burst time.
 *
 * <p>Tie-breaking: equal burst times are broken by earliest arrival time.</p>
 */
/**
 * Shortest Process Next (SPN / SJF) — not yet implemented.
 * Will be implemented in a future iteration.
 */
public class SPNScheduler implements SchedulingAlgorithm {

    @Override
    public PCB selectNextProcess(List<PCB> readyProcesses, PCB currentProcess) {
        // Not yet implemented
        return null;
    }

    @Override
    public String getName() { return "SPN (SJF)"; }
}
