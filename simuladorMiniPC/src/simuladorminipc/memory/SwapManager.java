package simuladorminipc.memory;

import simuladorminipc.model.PCB;

import java.util.LinkedList;
import java.util.Queue;

/**
 * Placeholder for swap-space management.
 * <p>
 * In a production OS this component would move inactive pages/segments to
 * secondary storage when RAM is full, and restore them on demand.
 * Here it maintains a simple suspended-process queue.
 * </p>
 *
 * <p>Reference: Stallings 9th Ed. 9.2 (Memory Management: Swapping).</p>
 *
 * @see MemoryManager
 */
public class SwapManager {

    /** Processes whose memory has been swapped out. */
    private final Queue<PCB> swappedOut = new LinkedList<>();

    /**
     * Simulates swapping out a process: frees its physical RAM and moves it to
     * the suspended-process queue.
     * <p>The process state should be set to SUSPENDED_READY or SUSPENDED_BLOCKED
     * by the caller before invoking this method.</p>
     *
     * @param process the process to swap out
     * @param mm      the memory manager used to free the physical pages
     */
    public void swapOut(PCB process, MemoryManager mm) {
        mm.free(process);
        swappedOut.add(process);
    }

    /**
     * Swaps in the next eligible process if sufficient RAM is available.
     *
     * @param mm memory manager used to allocate physical pages for the swapped-in process
     * @return the process that was swapped in, or {@code null} if the queue is empty
     *         or there is insufficient free RAM
     */
    public PCB swapIn(MemoryManager mm) {
        PCB candidate = swappedOut.peek();
        if (candidate == null) return null;
        if (mm.allocate(candidate)) {
            swappedOut.poll();
            return candidate;
        }
        return null;
    }

    /** Returns {@code true} if the swap queue contains at least one process. */
    public boolean hasSwappedProcesses() { return !swappedOut.isEmpty(); }

    /**
     * Returns the number of processes currently in the swap queue.
     *
     * @return non-negative count
     */
    public int     swappedCount()         { return swappedOut.size(); }
}
