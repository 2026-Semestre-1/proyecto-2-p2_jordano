package simuladorminipc.cpu;

import simuladorminipc.model.PCB;
import simuladorminipc.model.ProcessState;

/**
 * Performs context switches between processes.
 * <p>
 * This is the only class allowed to move a PCB in/out of the CPU.
 * It saves the outgoing process's register snapshot (already live in the PCB's
 * RegisterSet because the CPU works directly on it) and loads the incoming
 * process's registers into the CPU.
 * </p>
 *
 * <h3>Context-switch count</h3>
 * Each call to {@link #dispatch(PCB, CPU)} that actually changes the running
 * process increments the internal counter, which the statistics module reads.
 */
public class Dispatcher {

    private long contextSwitches;

    /**
     * Switches the CPU from its currently running process to {@code next}.
     *
     * <ol>
     *   <li>If the CPU is not idle, save the current process state to READY
     *       (the caller must decide the actual target state before invoking this).</li>
     *   <li>Load {@code next} into the CPU.</li>
     *   <li>Update accounting fields on {@code next}.</li>
     * </ol>
     *
     * @param next   process to dispatch (must not be null)
     * @param cpu    the CPU to dispatch onto
     */
    public void dispatch(PCB next, CPU cpu) {
        PCB current = cpu.getCurrentProcess();

        if (current != null && current != next) {
            // Current process registers are already up-to-date in the PCB
            // (CPU operates on the live RegisterSet reference).
            // Only increment switch counter when we actually change processes.
            contextSwitches++;
        }

        cpu.loadProcess(next);
        next.setState(ProcessState.RUNNING);
        next.setCpuId(cpu.getId());
    }

    /**
     * Removes the currently running process from the CPU without loading a
     * successor (leaves CPU idle).  Used when no ready process is available.
     *
     * @param cpu target CPU
     * @return the process that was removed, or null if CPU was already idle
     */
    public PCB preempt(CPU cpu) {
        PCB current = cpu.getCurrentProcess();
        if (current != null) contextSwitches++;
        return cpu.releaseProcess();
    }

    /**
     * Returns the cumulative number of context switches performed.
     *
     * @return non-negative context switch count
     */
    public long getContextSwitches() { return contextSwitches; }
}
