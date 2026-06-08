package simuladorminipc.process;

import simuladorminipc.memory.MemoryManager;
import simuladorminipc.model.PCB;
import simuladorminipc.model.ProcessState;

import java.util.*;

/**
 * Responsible for:
 * <ol>
 *   <li>Receiving new PCBs from the caller (Kernel) and placing them in the
 *       new queue.</li>
 *   <li>Admitting processes from the new queue to memory when space is
 *       available (called each tick).</li>
 *   <li>Verifying whether all submitted processes have finished.</li>
 * </ol>
 *
 * @see QueueManager
 * @see StateManager
 * @see simuladorminipc.memory.MemoryManager
 */
public class ProcessManager {

    private static final int MAX_CONCURRENT_PROCESSES = 5;

    private final QueueManager  queueManager;
    private final StateManager  stateManager;
    private final MemoryManager memoryManager;

    /**
     * Constructs a process manager.
     *
     * @param queueManager  system queue manager
     * @param stateManager  process state transition manager
     * @param memoryManager physical memory allocator
     */
    public ProcessManager(QueueManager  queueManager,
                          StateManager  stateManager,
                          MemoryManager memoryManager) {
        this.queueManager  = queueManager;
        this.stateManager  = stateManager;
        this.memoryManager = memoryManager;
    }

    /**
     * Submits a PCB to the system.  The process enters the new queue
     * with state NEW; it will be admitted to memory on the next suitable tick.
     */
    public void submit(PCB process) {
        if (process.getState() != ProcessState.NEW)
            throw new IllegalArgumentException(
                "Only NEW processes may be submitted. PID=" + process.getPid());
        queueManager.admitToNew(process);
    }

    /**
     * Tries to promote waiting NEW processes into memory and the ready queue.
     * Called once per tick by the Kernel.
     *
     * @param currentTick the current clock tick (used for arrival-time filtering)
     */
    public void loadArrivingProcesses(long currentTick) {
        Queue<PCB> newQ = queueManager.getNewQueue();
        // Count processes already in memory: ready + blocked + running (RUNNING is removed from ready)
        int inMemory = queueManager.getReadyQueue().size()
                     + queueManager.getBlockedQueue().size();
        // Also count any process currently running (not in any queue)
        for (PCB p : queueManager.getAllLive()) {
            if (p.getState() == ProcessState.RUNNING) inMemory++;
        }

        // Snapshot to avoid ConcurrentModificationException
        List<PCB> candidates = new ArrayList<>(newQ);
        for (PCB p : candidates) {
            if (p.getArrivalTime() > currentTick) continue;
            if (inMemory >= MAX_CONCURRENT_PROCESSES)  break;
            if (memoryManager.allocate(p)) {
                stateManager.admitToReady(p);
                inMemory++;
            }
        }
    }

    /**
     * Returns {@code true} when every submitted process has reached TERMINATED state
     * and all active queues (new, ready, blocked, suspended) are empty.
     *
     * @return {@code true} if the simulation is complete
     */
    public boolean allFinished() {
        return queueManager.getNewQueue().isEmpty()
            && queueManager.getReadyQueue().isEmpty()
            && queueManager.getBlockedQueue().isEmpty()
            && queueManager.getSuspendedQueue().isEmpty();
    }

    /**
     * Returns the maximum number of processes that may reside in memory concurrently.
     *
     * @return maximum concurrent process count
     */
    public int getMaxConcurrentProcesses() { return MAX_CONCURRENT_PROCESSES; }
}
