package simuladorminipc.scheduler;

import simuladorminipc.model.PCB;

import java.util.List;

/**
 * Round-Robin (RR) scheduler with configurable quantum.
 * <p>
 * Each process receives up to {@code quantum} CPU ticks before being
 * preempted and returned to the back of the ready queue.
 * The Kernel manages the quantum counter on the PCB; this class merely
 * selects the front of the ready queue when the current process should be
 * replaced.
 * </p>
 */
/**
 * Round-Robin (RR) scheduler — not yet implemented.
 * Will be implemented in a future iteration.
 */
public class RoundRobinScheduler implements SchedulingAlgorithm {

    private final int quantum;

    public RoundRobinScheduler(int quantum) {
        this.quantum = quantum;
    }

    @Override
    public PCB selectNextProcess(List<PCB> readyProcesses, PCB currentProcess) {
        // Not yet implemented
        return null;
    }

    public int getQuantum() { return quantum; }

    @Override
    public String getName() { return "Round Robin (q=" + quantum + ")"; }
}
