package simuladorminipc.scheduler;

import simuladorminipc.model.PCB;

import java.util.List;

/**
 * Priority Scheduling – non-preemptive.
 * Selects the ready process with the lowest priority number
 * (convention: lower number = higher priority, per project spec).
 *
 * <p>Tie-breaking: equal priorities are broken by earliest arrival time.</p>
 */
/**
 * Priority Scheduling — not yet implemented.
 * Will be implemented in a future iteration.
 */
public class PriorityScheduler implements SchedulingAlgorithm {

    @Override
    public PCB selectNextProcess(List<PCB> readyProcesses, PCB currentProcess) {
        // Not yet implemented
        return null;
    }

    @Override
    public String getName() { return "Prioridad"; }
}
