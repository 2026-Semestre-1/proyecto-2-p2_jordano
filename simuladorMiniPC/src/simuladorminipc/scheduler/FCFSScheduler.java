package simuladorminipc.scheduler;

import simuladorminipc.model.PCB;

import java.util.List;

/**
 * First-Come First-Served (FCFS) scheduler – non-preemptive.
 * Selects the process with the earliest arrival time.
 * If a process is currently running it continues until it finishes or blocks.
 *
 * <p>Tie-breaking: equal arrival times are broken by lowest PID.</p>
 */
public class FCFSScheduler implements SchedulingAlgorithm {

    /**
     * Selects the next process to run using FCFS (arrival order).
     * Non-preemptive: if a process is currently running it is left to continue.
     *
     * @param readyProcesses current ready queue (unmodified)
     * @param currentProcess currently running process, or {@code null} if idle
     * @return the process with the earliest arrival time, or {@code null} if already running / queue is empty
     */
    @Override
    public PCB selectNextProcess(List<PCB> readyProcesses, PCB currentProcess) {
        if (currentProcess != null) return null; // non-preemptive: let it run
        if (readyProcesses.isEmpty()) return null;

        PCB selected = readyProcesses.get(0);
        for (PCB p : readyProcesses) {
            if (p.getArrivalTime() < selected.getArrivalTime()) selected = p;
            else if (p.getArrivalTime() == selected.getArrivalTime()
                  && p.getPid() < selected.getPid()) selected = p;
        }
        return selected;
    }

    @Override
    public String getName() { return "FCFS"; }
}
