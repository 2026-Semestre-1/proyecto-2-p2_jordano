package simuladorminipc.process;

import simuladorminipc.model.PCB;
import simuladorminipc.model.ProcessState;

import java.util.*;

/**
 * Owns and exposes every process queue in the system.
 * <p>
 * Queues maintained:
 * <ul>
 *   <li><b>newQueue</b>      – processes admitted but not yet in memory.</li>
 *   <li><b>readyQueue</b>    – processes in memory, waiting for CPU.</li>
 *   <li><b>blockedQueue</b>  – processes waiting for I/O.</li>
 *   <li><b>suspendedQueue</b>– swapped-out processes.</li>
 *   <li><b>terminatedQueue</b>– completed processes (for statistics).</li>
 * </ul>
 * </p>
 */
public class QueueManager {

    private final Queue<PCB>     newQueue        = new LinkedList<>();
    private final List<PCB>      readyQueue      = new ArrayList<>();
    private final List<PCB>      blockedQueue    = new ArrayList<>();
    private final List<PCB>      suspendedQueue  = new ArrayList<>();
    private final List<PCB>      terminatedQueue = new ArrayList<>();

    // ── Enqueue / dequeue helpers ────────────────────────────────────────────

    /**
     * Adds a process to the new-queue if not already present.
     *
     * @param p process in NEW state to admit
     */
    public void admitToNew(PCB p) {
        if (!newQueue.contains(p)) newQueue.add(p);
    }

    /**
     * Moves a process to the ready queue.
     * Removes it from all other queues first.
     *
     * @param p process to move
     */
    public void moveToReady(PCB p) {
        removeFromAll(p);
        if (!readyQueue.contains(p)) readyQueue.add(p);
    }

    /**
     * Moves a process to the blocked queue.
     * Removes it from all other queues first.
     *
     * @param p process to block
     */
    public void moveToBlocked(PCB p) {
        removeFromAll(p);
        if (!blockedQueue.contains(p)) blockedQueue.add(p);
    }

    /**
     * Moves a process to the suspended queue.
     * Removes it from all other queues first.
     *
     * @param p process to suspend
     */
    public void moveToSuspended(PCB p) {
        removeFromAll(p);
        if (!suspendedQueue.contains(p)) suspendedQueue.add(p);
    }

    /**
     * Moves a process to the terminated queue.
     * Removes it from all other queues first.
     * <b>A process in the terminated queue is never removed by {@link #removeFromAll}.</b>
     *
     * @param p process that has terminated
     */
    public void moveToTerminated(PCB p) {
        removeFromAll(p);
        if (!terminatedQueue.contains(p)) terminatedQueue.add(p);
    }

    /**
     * Removes a PCB from every active queue (new, ready, blocked, suspended).
     * The terminated queue is intentionally excluded.
     *
     * @param p the process to remove
     */
    public void removeFromAll(PCB p) {
        newQueue.remove(p);
        readyQueue.remove(p);
        blockedQueue.remove(p);
        suspendedQueue.remove(p);
        // Do NOT remove from terminatedQueue once placed there
    }

    // ── Accessors ────────────────────────────────────────────────────────────

    /** Returns the new (not-yet-admitted) process queue. */
    public Queue<PCB>  getNewQueue()        { return newQueue; }

    /** Returns the ready-to-run process list. */
    public List<PCB>   getReadyQueue()      { return readyQueue; }

    /** Returns the list of processes currently blocked on I/O. */
    public List<PCB>   getBlockedQueue()    { return blockedQueue; }

    /** Returns the list of swapped-out (suspended) processes. */
    public List<PCB>   getSuspendedQueue()  { return suspendedQueue; }

    /** Returns the list of processes that have terminated (historical record). */
    public List<PCB>   getTerminatedQueue() { return terminatedQueue; }

    /** Returns {@code true} if the ready queue is non-empty. */
    public boolean     hasReadyProcesses()  { return !readyQueue.isEmpty(); }

    /** Returns {@code true} if the new queue is non-empty. */
    public boolean     hasNewProcesses()    { return !newQueue.isEmpty(); }

    /**
     * Returns all live processes across new, ready, blocked, and suspended queues.
     * Does NOT include terminated processes.
     *
     * @return mutable copy of the live process set
     */
    public List<PCB>   getAllLive() {
        List<PCB> all = new ArrayList<>();
        all.addAll(newQueue);
        all.addAll(readyQueue);
        all.addAll(blockedQueue);
        all.addAll(suspendedQueue);
        return all;
    }

    /**
     * Returns all processes that have ever been admitted (live + terminated).
     *
     * @return mutable list of all known processes
     */
    public List<PCB>   getAll() {
        List<PCB> all = getAllLive();
        all.addAll(terminatedQueue);
        return all;
    }

    /**
     * Returns the total number of processes across all queues.
     *
     * @return sum of sizes of all five queues
     */
    public int totalProcesses() {
        return newQueue.size() + readyQueue.size()
             + blockedQueue.size() + suspendedQueue.size()
             + terminatedQueue.size();
    }

    @Override
    public String toString() {
        return "QueueManager[new=" + newQueue.size()
             + " ready=" + readyQueue.size()
             + " blocked=" + blockedQueue.size()
             + " suspended=" + suspendedQueue.size()
             + " terminated=" + terminatedQueue.size() + "]";
    }
}
